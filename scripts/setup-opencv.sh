#!/usr/bin/env bash
# Download and set up OpenCV Android SDK for CI / local build.
# Run from the project root:
#   bash scripts/setup-opencv.sh

set -e

OPENCV_VERSION="4.10.0"
OPENCV_SDK_ZIP="opencv-${OPENCV_VERSION}-android-sdk.zip"
OPENCV_DOWNLOAD_URL="https://github.com/opencv/opencv/releases/download/${OPENCV_VERSION}/${OPENCV_SDK_ZIP}"
PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
EXTRACT_DIR="${PROJECT_ROOT}/opencv-sdk"
SDK_DIR="${EXTRACT_DIR}/OpenCV-android-sdk"

echo "Setting up OpenCV Android SDK ${OPENCV_VERSION}..."

mkdir -p "${EXTRACT_DIR}"
cd "${EXTRACT_DIR}"

if [ ! -f "${OPENCV_SDK_ZIP}" ]; then
    echo "Downloading ${OPENCV_SDK_ZIP}..."
    curl -sL --fail -o "${OPENCV_SDK_ZIP}" "${OPENCV_DOWNLOAD_URL}"
else
    echo "Found existing ${OPENCV_SDK_ZIP}, skipping download."
fi

if [ ! -d "${SDK_DIR}" ]; then
    echo "Extracting..."
    unzip -q "${OPENCV_SDK_ZIP}"
else
    echo "SDK already extracted, skipping extraction."
fi

# Copy native libraries into the app module.
echo "Copying native libraries..."
rm -rf "${PROJECT_ROOT}/app/src/main/jniLibs"
mkdir -p "${PROJECT_ROOT}/app/src/main/jniLibs"
cp -R "${SDK_DIR}/sdk/native/libs/"* "${PROJECT_ROOT}/app/src/main/jniLibs/"

# OpenCV's prebuilt .so depends on libc++_shared.so, which the SDK does not ship.
# Copy it from the Android NDK so the APK contains the required C++ runtime.
echo "Looking for Android NDK to bundle libc++_shared.so..."
NDK_DIR="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}"
if [ -z "$NDK_DIR" ] && [ -n "$ANDROID_HOME" ] && [ -d "$ANDROID_HOME/ndk" ]; then
    # Use the newest installed NDK if no explicit env var is set.
    NDK_DIR="$(ls -d "$ANDROID_HOME"/ndk/* 2>/dev/null | sort -V | tail -n 1)"
fi

if [ -d "$NDK_DIR" ]; then
    echo "Using NDK: $NDK_DIR"
    # Determine host prebuilt directory.
    case "$(uname -s)" in
        Linux*) HOST_PREBUILT="linux-x86_64" ;;
        Darwin*) HOST_PREBUILT="darwin-x86_64" ;;
        CYGWIN*|MINGW*|MSYS*) HOST_PREBUILT="windows-x86_64" ;;
        *) HOST_PREBUILT="linux-x86_64" ;;
    esac

    # ABI -> NDK sysroot triple mapping.
    declare -A ABI_TRIPLE=(
        ["arm64-v8a"]="aarch64-linux-android"
        ["armeabi-v7a"]="arm-linux-androideabi"
        ["x86"]="i686-linux-android"
        ["x86_64"]="x86_64-linux-android"
    )

    SYSROOT_LIB="$NDK_DIR/toolchains/llvm/prebuilt/$HOST_PREBUILT/sysroot/usr/lib"
    for ABI in "${!ABI_TRIPLE[@]}"; do
        SRC="$SYSROOT_LIB/${ABI_TRIPLE[$ABI]}/libc++_shared.so"
        DST="${PROJECT_ROOT}/app/src/main/jniLibs/$ABI/libc++_shared.so"
        if [ -f "$SRC" ]; then
            echo "  Copying libc++_shared.so for $ABI"
            cp "$SRC" "$DST"
        else
            echo "  Warning: libc++_shared.so not found for $ABI at $SRC"
        fi
    done
else
    echo "Warning: Android NDK not found. libc++_shared.so will not be bundled."
    echo "Install the NDK or set ANDROID_NDK_HOME / ANDROID_NDK_ROOT."
fi

# Make the OpenCV Java module available so settings.gradle.kts can include it.
echo "Linking OpenCV Java module..."
rm -rf "${PROJECT_ROOT}/opencv"
mkdir -p "${PROJECT_ROOT}/opencv"
cp -R "${SDK_DIR}/sdk/java" "${PROJECT_ROOT}/opencv/sdk"

# Replace OpenCV's old build.gradle with a modern Kotlin DSL version
cat > "${PROJECT_ROOT}/opencv/sdk/build.gradle.kts" <<'EOF'
plugins {
    id("com.android.library")
}

android {
    namespace = "org.opencv"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }

    sourceSets["main"].java.srcDirs("src")
    sourceSets["main"].res.srcDirs("res")
    sourceSets["main"].manifest.srcFile("AndroidManifest.xml")
}
EOF

echo "OpenCV setup complete."

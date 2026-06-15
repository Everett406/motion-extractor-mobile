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

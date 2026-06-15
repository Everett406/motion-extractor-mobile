# Download and set up OpenCV Android SDK for local Windows build.
# Run from PowerShell at the project root:
#   .\scripts\setup-opencv.ps1

$ErrorActionPreference = "Stop"

$OpenCvVersion = "4.10.0"
$OpenCvSdkZip = "opencv-${OpenCvVersion}-android-sdk.zip"
$OpenCvDownloadUrl = "https://github.com/opencv/opencv/releases/download/${OpenCvVersion}/${OpenCvSdkZip}"
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$ExtractDir = Join-Path $ProjectRoot "opencv-sdk"
$SdkDir = Join-Path $ExtractDir "OpenCV-android-sdk"

Write-Host "Setting up OpenCV Android SDK ${OpenCvVersion}..."

New-Item -ItemType Directory -Force -Path $ExtractDir | Out-Null
Set-Location $ExtractDir

if (-not (Test-Path $OpenCvSdkZip)) {
    Write-Host "Downloading ${OpenCvSdkZip}..."
    Invoke-WebRequest -Uri $OpenCvDownloadUrl -OutFile $OpenCvSdkZip -UseBasicParsing
} else {
    Write-Host "Found existing ${OpenCvSdkZip}, skipping download."
}

if (-not (Test-Path $SdkDir)) {
    Write-Host "Extracting..."
    Expand-Archive -Path $OpenCvSdkZip -DestinationPath $ExtractDir -Force
} else {
    Write-Host "SDK already extracted, skipping extraction."
}

# Copy native libraries into the app module.
Write-Host "Copying native libraries..."
$JniLibsDir = Join-Path $ProjectRoot "app\src\main\jniLibs"
if (Test-Path $JniLibsDir) {
    Remove-Item -Recurse -Force $JniLibsDir
}
New-Item -ItemType Directory -Force -Path $JniLibsDir | Out-Null
Copy-Item -Path (Join-Path $SdkDir "sdk\native\libs\*") -Destination $JniLibsDir -Recurse -Force

# OpenCV's prebuilt .so depends on libc++_shared.so, which the SDK does not ship.
# Copy it from the Android NDK so the APK contains the required C++ runtime.
Write-Host "Looking for Android NDK to bundle libc++_shared.so..."
$NdkDir = $env:ANDROID_NDK_HOME
if (-not $NdkDir) { $NdkDir = $env:ANDROID_NDK_ROOT }
if ((-not $NdkDir) -and (Test-Path "$env:ANDROID_HOME\ndk")) {
    $NdkDir = Get-ChildItem "$env:ANDROID_HOME\ndk" -Directory | Sort-Object Name | Select-Object -Last 1 | ForEach-Object { $_.FullName }
}

if ($NdkDir -and (Test-Path $NdkDir)) {
    Write-Host "Using NDK: $NdkDir"
    $HostPrebuilt = "windows-x86_64"
    $AbiTriple = @{
        "arm64-v8a"   = "aarch64-linux-android"
        "armeabi-v7a" = "arm-linux-androideabi"
        "x86"         = "i686-linux-android"
        "x86_64"      = "x86_64-linux-android"
    }
    $SysrootLib = Join-Path $NdkDir "toolchains\llvm\prebuilt\$HostPrebuilt\sysroot\usr\lib"
    foreach ($Abi in $AbiTriple.Keys) {
        $Src = Join-Path $SysrootLib "$($AbiTriple[$Abi])\libc++_shared.so"
        $Dst = Join-Path $JniLibsDir "$Abi\libc++_shared.so"
        if (Test-Path $Src) {
            Write-Host "  Copying libc++_shared.so for $Abi"
            Copy-Item -Path $Src -Destination $Dst -Force
        } else {
            Write-Host "  Warning: libc++_shared.so not found for $Abi at $Src"
        }
    }
} else {
    Write-Host "Warning: Android NDK not found. libc++_shared.so will not be bundled."
    Write-Host "Install the NDK or set ANDROID_NDK_HOME / ANDROID_NDK_ROOT."
}

# Make the OpenCV Java module available so settings.gradle.kts can include it.
Write-Host "Linking OpenCV Java module..."
$OpencvModuleDir = Join-Path $ProjectRoot "opencv"
if (Test-Path $OpencvModuleDir) {
    Remove-Item -Recurse -Force $OpencvModuleDir
}
New-Item -ItemType Directory -Force -Path $OpencvModuleDir | Out-Null
Copy-Item -Path (Join-Path $SdkDir "sdk\java") -Destination (Join-Path $OpencvModuleDir "sdk") -Recurse -Force

# Replace OpenCV's old build.gradle with a modern Kotlin DSL version.
$BuildGradleContent = @"
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
"@
$BuildGradlePath = Join-Path $OpencvModuleDir "sdk\build.gradle.kts"
$BuildGradleContent | Out-File -FilePath $BuildGradlePath -Encoding utf8

Write-Host "OpenCV setup complete."

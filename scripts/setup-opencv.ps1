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

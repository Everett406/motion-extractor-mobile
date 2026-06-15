pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Motion Extractor"
include(":app")

// OpenCV module is downloaded and set up by scripts/setup-opencv.sh in CI.
// If the opencv module directory exists (local dev or after setup), include it.
if (file("opencv/sdk").exists()) {
    include(":opencv")
    project(":opencv").projectDir = file("opencv/sdk")
}

// settings.gradle.kts
pluginManagement {
    repositories {
        // Required for the Android Gradle plugin
        google()
        // Gradle Plugin Portal (default)
        gradlePluginPortal()
        // Optional fallback
        mavenCentral()
    }
}

// If you use version catalogs (libs.versions.toml) you can also declare them here:
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

// Include your app module (adjust if you have other modules)
include(":app")

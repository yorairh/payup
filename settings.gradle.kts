pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
// The web build adds its own repositories for downloading Node.js, Yarn and Binaryen, so project repositories are allowed.
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "PayUp"
include(":shared")
include(":androidApp")
include(":webApp")

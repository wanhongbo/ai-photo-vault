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

rootProject.name = "PhotoVault"
include(":app")
include(":core:domain")
include(":core:data")
include(":core:ai")
include(":core:ai-mlkit")
include(":feature:ai")

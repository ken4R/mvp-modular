pluginManagement {
    includeBuild("build-logic")
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

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "mvp-modular"

// Apps (UI uniquement)
include(":androidApp")

// Module parapluie KMP : exporte le framework iOS et assemble les modules
include(":shared")

// Modules communs
include(":core:common")
include(":core:designsystem")
include(":core:network")

// Features
include(":feature:todo")
include(":feature:weather")
include(":feature:settings")


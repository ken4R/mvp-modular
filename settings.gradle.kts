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
include(":core:database")
include(":core:database-testing")
include(":core:securestorage")
include(":core:notifications")
include(":core:location")
include(":core:deeplink")
include(":core:analytics")
include(":core:connectivity")
include(":core:files")

// Features
include(":feature:todo")
include(":feature:weather")
include(":feature:settings")
include(":feature:auth")
include(":feature:catalog")

// Écrans partagés en Compose Multiplatform (comparaison avec le natif, ADR 0010)
include(":feature:todo-compose")


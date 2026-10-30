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

// Parapluies iOS des apps de démo (ADR 0027) : chacun produit un framework "Shared" réduit à une feature
include(":umbrella:todo-demo")
include(":umbrella:todo-compose-demo")
include(":umbrella:weather-demo")
include(":umbrella:settings-demo")
include(":umbrella:auth-demo")
include(":umbrella:catalog-demo")
include(":umbrella:onboarding-demo")

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
include(":core:crashreporting")
include(":core:featureflags")

// Features
include(":feature:todo")
include(":feature:weather")
include(":feature:settings")
include(":feature:auth")
include(":feature:catalog")
include(":feature:onboarding")

// Écrans partagés en Compose Multiplatform (comparaison avec le natif, ADR 0010)
include(":feature:todo-compose")


plugins {
    alias(libs.plugins.mvp.kmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.common)
            implementation(projects.core.securestorage)
        }
    }
}

android {
    namespace = "com.mvp.feature.auth"

    // Faux service de connexion réservé aux builds de développement (isDemoAuthEnabled).
    // Une vraie constante (pas BuildConfig.DEBUG, non constant dans une bibliothèque) : R8 l'élimine en release.
    buildFeatures { buildConfig = true }
    buildTypes {
        debug { buildConfigField("boolean", "DEMO_AUTH", "true") }
        release { buildConfigField("boolean", "DEMO_AUTH", "false") }
    }
}

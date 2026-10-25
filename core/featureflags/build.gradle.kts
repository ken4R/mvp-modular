plugins {
    alias(libs.plugins.mvp.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}

// Feature flags : features en développement coupées en release, features livrées pilotables à distance par un
// JSON publié sur GitHub Pages (remote-config/flags.json). ADR 0021.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.common)
            implementation(projects.core.network)
            implementation(projects.core.files)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
        }
    }
}

android {
    namespace = "com.mvp.core.featureflags"

    // Une vraie constante par type de build (pas BuildConfig.DEBUG, non constant dans une bibliothèque) :
    // R8 élimine en release le code derrière une feature en développement (ADR 0021, comme DEMO_AUTH en ADR 0013).
    buildFeatures { buildConfig = true }
    buildTypes {
        debug { buildConfigField("boolean", "DEV_FLAGS", "true") }
        release { buildConfigField("boolean", "DEV_FLAGS", "false") }
    }
}

plugins {
    alias(libs.plugins.mvp.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.common)
            implementation(projects.core.network)
            // Widget Météo (ADR 0026) : dernière météo chiffrée, lien vers l'onglet Météo.
            implementation(projects.core.securestorage)
            implementation(projects.core.deeplink)
        }
        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
        }
    }
}

android {
    namespace = "com.mvp.feature.weather"
}

plugins {
    alias(libs.plugins.mvp.kmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.common)
            api(libs.ktor.client.core)
            api(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
        // Un moteur HTTP par plateforme, choisi automatiquement par HttpClient().
        // Android : moteur HttpURLConnection plutôt qu'OkHttp, dont la version tirée par Ktor
        // exige compileSdk 37 (non supporté par AGP 8.13).
        androidMain.dependencies {
            implementation(libs.ktor.client.android)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}

android {
    namespace = "com.mvp.core.network"
}

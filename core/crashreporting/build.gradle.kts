plugins {
    alias(libs.plugins.mvp.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}

// Suivi des plantages avec consentement : plantages Kotlin enregistrés sans donnée personnelle, envoyés au
// lancement suivant à un « sink » (journal local de debug pour l'instant, un vrai outil plus tard). ADR 0020.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.common)
            implementation(projects.core.files)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}

android {
    namespace = "com.mvp.core.crashreporting"
}

plugins {
    alias(libs.plugins.mvp.kmp.library)
}

// Mesure d'audience avec consentement : file d'attente en mémoire, envoyée par lots à un « sink »
// (journal local de debug pour l'instant, un vrai outil plus tard). Voir ADR 0009.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.common)
        }
    }
}

android {
    namespace = "com.mvp.core.analytics"
}

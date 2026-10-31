plugins {
    alias(libs.plugins.mvp.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.common)
            // Session en cours chiffrée (elle contient le titre d'une tâche), alerte de fin de phase, lien vers la tâche.
            implementation(projects.core.securestorage)
            implementation(projects.core.notifications)
            implementation(projects.core.deeplink)
        }
    }
}

android {
    namespace = "com.mvp.feature.focus"
}

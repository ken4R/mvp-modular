plugins {
    alias(libs.plugins.mvp.kmp.library)
}

// Stockage chiffré clé / valeur : Android Keystore, Keychain iOS. Aucune dépendance externe,
// uniquement les API des deux plateformes.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.common)
        }
    }
}

android {
    namespace = "com.mvp.core.securestorage"
}

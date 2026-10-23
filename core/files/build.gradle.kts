plugins {
    alias(libs.plugins.mvp.kmp.library)
}

// Fichiers privés de l'app (pièces jointes des tâches...) : dossier no_backup sur Android, Application Support
// exclu des sauvegardes et protégé par Data Protection sur iOS (ADR 0014).
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.common)
        }
    }
}

android {
    namespace = "com.mvp.core.files"
}

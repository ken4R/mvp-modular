plugins {
    alias(libs.plugins.mvp.kmp.library)
}

// Base SQLite en mémoire pour les tests commonTest des features (à ajouter en
// dépendance de commonTest uniquement, jamais de commonMain).
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.sqldelight.runtime)
        }
        // Les tests Android tournent sur la JVM : driver JDBC plutôt que le framework Android.
        androidMain.dependencies {
            implementation(libs.sqldelight.sqlite.driver)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
        }
    }
}

android {
    namespace = "com.mvp.core.database.testing"
}

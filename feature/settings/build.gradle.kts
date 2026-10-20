plugins {
    alias(libs.plugins.mvp.kmp.library)
    alias(libs.plugins.sqldelight)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.common)
            implementation(projects.core.database)
            implementation(projects.core.securestorage)
        }
        commonTest.dependencies {
            implementation(projects.core.databaseTesting)
        }
    }
}

android {
    namespace = "com.mvp.feature.settings"
}

// Schéma dans src/commonMain/sqldelight : SQLDelight génère SettingsDatabase et ses requêtes typées.
sqldelight {
    databases {
        create("SettingsDatabase") {
            packageName.set("com.mvp.feature.settings.data.db")
        }
    }
}

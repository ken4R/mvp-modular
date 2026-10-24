plugins {
    alias(libs.plugins.mvp.kmp.library)
    alias(libs.plugins.sqldelight)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.common)
            implementation(projects.core.database)
        }
        commonTest.dependencies {
            implementation(projects.core.databaseTesting)
        }
    }
}

android {
    namespace = "com.mvp.feature.onboarding"
}

// Schéma dans src/commonMain/sqldelight : SQLDelight génère OnboardingDatabase et ses requêtes typées.
sqldelight {
    databases {
        create("OnboardingDatabase") {
            packageName.set("com.mvp.feature.onboarding.data.db")
        }
    }
}

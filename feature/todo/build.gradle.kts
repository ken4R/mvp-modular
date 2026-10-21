plugins {
    alias(libs.plugins.mvp.kmp.library)
    alias(libs.plugins.sqldelight)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.common)
            implementation(projects.core.database)
            implementation(projects.core.notifications)
        }
        commonTest.dependencies {
            implementation(projects.core.databaseTesting)
        }
    }
}

android {
    namespace = "com.mvp.feature.todo"
}

// Schéma dans src/commonMain/sqldelight : SQLDelight génère TodoDatabase et ses requêtes typées.
sqldelight {
    databases {
        create("TodoDatabase") {
            packageName.set("com.mvp.feature.todo.data.db")
        }
    }
}

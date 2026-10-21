plugins {
    alias(libs.plugins.mvp.kmp.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.common)
            implementation(projects.core.network)
            implementation(projects.core.database)
        }
        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
            implementation(projects.core.databaseTesting)
        }
    }
}

android {
    namespace = "com.mvp.feature.catalog"
}

// Cache hors ligne du catalogue : schéma dans src/commonMain/sqldelight.
sqldelight {
    databases {
        create("CatalogDatabase") {
            packageName.set("com.mvp.feature.catalog.data.db")
        }
    }
}

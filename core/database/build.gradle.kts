plugins {
    alias(libs.plugins.mvp.kmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.common)
            api(libs.sqldelight.runtime)
            api(libs.sqldelight.coroutines.extensions)
        }
        // Un driver SQLite par plateforme : framework Android, SQLite système sur iOS.
        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
        }
    }
}

android {
    namespace = "com.mvp.core.database"
}

plugins {
    alias(libs.plugins.mvp.kmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.common)
        }
    }
}

android {
    namespace = "com.mvp.feature.settings"
}

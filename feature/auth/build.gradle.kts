plugins {
    alias(libs.plugins.mvp.kmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.common)
            implementation(projects.core.securestorage)
        }
    }
}

android {
    namespace = "com.mvp.feature.auth"
}

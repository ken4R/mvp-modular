plugins {
    alias(libs.plugins.mvp.kmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            api(libs.koin.core)
        }
    }
}

android {
    namespace = "com.mvp.core.common"
}

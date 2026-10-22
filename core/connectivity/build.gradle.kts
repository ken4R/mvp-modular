plugins {
    alias(libs.plugins.mvp.kmp.library)
}

// Accès à internet : ConnectivityManager sur Android, NWPathMonitor (Network.framework) sur iOS (voir ADR 0012).
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.common)
        }
    }
}

android {
    namespace = "com.mvp.core.connectivity"
}

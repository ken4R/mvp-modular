plugins {
    alias(libs.plugins.mvp.kmp.library)
}

// Position de l'appareil : LocationManager sur Android (sans Google Play Services),
// CoreLocation sur iOS (voir ADR 0005).
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.common)
        }
    }
}

android {
    namespace = "com.mvp.core.location"
}

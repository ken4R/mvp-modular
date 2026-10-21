plugins {
    alias(libs.plugins.mvp.kmp.library)
}

// Notifications locales programmées : WorkManager + NotificationManager sur Android,
// UNUserNotificationCenter sur iOS (voir ADR 0004).
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.common)
        }
        androidMain.dependencies {
            implementation(libs.androidx.work.runtime)
        }
    }
}

android {
    namespace = "com.mvp.core.notifications"
}

plugins {
    alias(libs.plugins.mvp.kmp.library)
    // Compilateur Compose (plugin Kotlin) : les écrans @Composable de ce module tournent sur Android et iOS.
    alias(libs.plugins.compose.compiler)
}

// Écran Tâches en Compose Multiplatform, à comparer à l'écran natif (ADR 0010).
// La logique reste celle de feature:todo : seuls l'UI et son ViewModel sont ici.
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.feature.todo)
            implementation(projects.core.designsystem)
            implementation(libs.compose.mp.runtime)
            implementation(libs.compose.mp.foundation)
            implementation(libs.compose.mp.ui)
            implementation(libs.compose.mp.material3)
            implementation(libs.jetbrains.lifecycle.viewmodel.compose)
            implementation(libs.koin.core)
        }
    }
}

android {
    namespace = "com.mvp.feature.todocompose"
}

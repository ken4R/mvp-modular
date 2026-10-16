plugins {
    alias(libs.plugins.mvp.kmp.library)
}

kotlin {
    // Framework "Shared" consommé par l'app iOS. Chaque module exporté
    // devient visible en Swift (ajouter ici les futurs modules feature).
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
            export(projects.core.common)
            export(projects.core.designsystem)
            export(projects.feature.todo)
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.core.common)
            api(projects.core.designsystem)
            api(projects.feature.todo)
        }
    }
}

android {
    namespace = "com.mvp.shared"
}

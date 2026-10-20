import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.mvp.kmp.library)
}

kotlin {
    // Framework "Shared" consommé par l'app iOS sous forme de XCFramework, via le package
    // SPM iosApp/Packages/SharedKMP (voir scripts/build-ios-framework.sh). Chaque module
    // exporté devient visible en Swift (ajouter ici les futurs modules feature).
    val xcFramework = XCFramework("Shared")
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            // Dynamique : SPM l'embarque une seule fois même si plusieurs packages en dépendent.
            isStatic = false
            binaryOption("bundleId", "com.mvp.shared")
            export(projects.core.common)
            export(projects.core.designsystem)
            export(projects.feature.todo)
            export(projects.feature.weather)
            xcFramework.add(this)
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.core.common)
            api(projects.core.designsystem)
            api(projects.feature.todo)
            api(projects.feature.weather)
            // Non exporté : les types Ktor n'ont pas à apparaître côté Swift.
            implementation(projects.core.network)
        }
    }
}

android {
    namespace = "com.mvp.shared"
}

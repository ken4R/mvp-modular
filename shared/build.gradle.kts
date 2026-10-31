plugins {
    alias(libs.plugins.mvp.kmp.library)
}

// Framework "Shared" de l'app iOS complète, consommé sous forme de XCFramework via le package SPM
// iosApp/Packages/SharedKMP (voir scripts/build-ios-framework.sh). Chaque module exporté devient visible en Swift
// (ajouter ici les futurs modules feature). Les apps de démo lient chacune un parapluie réduit (umbrella/, ADR 0027).
iosUmbrellaFramework(
    exported = listOf(
        projects.core.common,
        projects.core.designsystem,
        projects.core.deeplink,
        projects.core.analytics,
        projects.core.crashreporting,
        projects.core.connectivity,
        projects.core.featureflags,
        projects.feature.todo,
        projects.feature.weather,
        projects.feature.settings,
        projects.feature.auth,
        projects.feature.catalog,
        projects.feature.onboarding,
        projects.feature.focus,
    ),
    // Non exportés : les types Ktor n'ont pas à apparaître côté Swift.
    linked = listOf(
        projects.core.network,
        projects.core.database,
        projects.core.files,
        projects.core.securestorage,
        projects.core.notifications,
        projects.core.location,
    ),
)

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Écran Compose Multiplatform (ADR 0010) : api pour que l'app Android l'affiche ; pas exporté vers
            // Swift (les @Composable n'y servent à rien), iOS passe par ComposeScreens (iosMain).
            api(projects.feature.todoCompose)
        }
    }
}

android {
    namespace = "com.mvp.shared"
}

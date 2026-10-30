plugins {
    alias(libs.plugins.mvp.kmp.library)
}

// Parapluie de l'app de démo OnboardingDemo (ADR 0027) : un framework iOS « Shared » réduit, à la place de celui
// de l'app complète. Exportés : ce que le package Swift de la feature et sa démo utilisent. Liés sans être
// exportés : la présentation enregistre les choix de notifications, de consentement et de verrou dans
// feature:settings.
demoUmbrella(
    exported = listOf(
        projects.core.common,
        projects.core.designsystem,
        projects.feature.onboarding,
        projects.feature.settings,
    ),
    linked = listOf(
        projects.core.database,
        projects.core.securestorage,
    ),
)

android {
    namespace = "com.mvp.umbrella.onboardingdemo"
}

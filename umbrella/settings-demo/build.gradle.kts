plugins {
    alias(libs.plugins.mvp.kmp.library)
}

// Parapluie de l'app de démo SettingsDemo (ADR 0027) : un framework iOS « Shared » réduit, à la place de celui de
// l'app complète. Exportés : ce que le package Swift de la feature et sa démo utilisent (l'écran Réglages montre
// aussi consentements, journal de mesure, plantages, feature flags, déconnexion et « Revoir la présentation »).
// Liés sans être exportés : le stockage (core:database, core:files, core:securestorage) et le téléchargement des
// flags distants (core:network).
demoUmbrella(
    exported = listOf(
        projects.core.common,
        projects.core.designsystem,
        projects.feature.settings,
        projects.core.analytics,
        projects.core.crashreporting,
        projects.core.featureflags,
        projects.feature.auth,
        projects.feature.onboarding,
    ),
    linked = listOf(
        projects.core.network,
        projects.core.database,
        projects.core.files,
        projects.core.securestorage,
    ),
)

android {
    namespace = "com.mvp.umbrella.settingsdemo"
}

plugins {
    alias(libs.plugins.mvp.kmp.library)
}

// Parapluie de l'app de démo AuthDemo (ADR 0027) : un framework iOS « Shared » réduit, à la place de celui de
// l'app complète. Exportés : ce que le package Swift de la feature et sa démo utilisent. Liés sans être exportés :
// la session est gardée dans le trousseau (core:securestorage).
demoUmbrella(
    exported = listOf(
        projects.core.common,
        projects.core.designsystem,
        projects.feature.auth,
    ),
    linked = listOf(
        projects.core.securestorage,
    ),
)

android {
    namespace = "com.mvp.umbrella.authdemo"
}

plugins {
    alias(libs.plugins.mvp.kmp.library)
}

// Parapluie de l'app de démo CatalogDemo (ADR 0027) : un framework iOS « Shared » réduit, à la place de celui de
// l'app complète. Exportés : ce que le package Swift de la feature et sa démo utilisent. Liés sans être exportés :
// feature:catalog appelle dummyjson (core:network) et garde un cache SQLite (core:database).
demoUmbrella(
    exported = listOf(
        projects.core.common,
        projects.core.designsystem,
        projects.feature.catalog,
        projects.core.connectivity,
    ),
    linked = listOf(
        projects.core.network,
        projects.core.database,
    ),
)

android {
    namespace = "com.mvp.umbrella.catalogdemo"
}

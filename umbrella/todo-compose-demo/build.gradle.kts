plugins {
    alias(libs.plugins.mvp.kmp.library)
}

// Parapluie de l'app de démo TodoComposeDemo (ADR 0027) : un framework iOS « Shared » réduit, à la place de celui
// de l'app complète. Exportés : ce que le package Swift de la feature et sa démo utilisent. Liés sans être
// exportés : l'écran Compose suit le thème de feature:settings ; le reste est le graphe de feature:todo.
demoUmbrella(
    exported = listOf(
        projects.core.common,
        projects.core.designsystem,
        projects.feature.todo,
    ),
    linked = listOf(
        projects.feature.todoCompose,
        projects.feature.settings,
        projects.core.deeplink,
        projects.core.database,
        projects.core.files,
        projects.core.securestorage,
        projects.core.notifications,
        projects.core.location,
        projects.core.connectivity,
        projects.core.analytics,
        projects.feature.auth,
    ),
)

android {
    namespace = "com.mvp.umbrella.todocomposedemo"
}

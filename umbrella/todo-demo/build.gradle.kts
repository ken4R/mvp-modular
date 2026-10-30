plugins {
    alias(libs.plugins.mvp.kmp.library)
}

// Parapluie de l'app de démo TodoDemo (ADR 0027) : un framework iOS « Shared » réduit, à la place de celui de
// l'app complète. Exportés : ce que le package Swift de la feature et sa démo utilisent. Liés sans être exportés :
// le graphe de feature:todo a besoin du réglage Notifications et du verrou (feature:settings), de la session
// (feature:auth), de la mesure d'audience, de la connectivité, de la position et des notifications.
demoUmbrella(
    exported = listOf(
        projects.core.common,
        projects.core.designsystem,
        projects.feature.todo,
        projects.core.deeplink,
        projects.feature.settings,
    ),
    linked = listOf(
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
    namespace = "com.mvp.umbrella.tododemo"
}

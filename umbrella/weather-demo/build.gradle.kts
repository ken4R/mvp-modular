plugins {
    alias(libs.plugins.mvp.kmp.library)
}

// Parapluie de l'app de démo WeatherDemo (ADR 0027) : un framework iOS « Shared » réduit, à la place de celui de
// l'app complète. Exportés : ce que le package Swift de la feature et sa démo utilisent. Liés sans être exportés :
// feature:weather appelle Open-Meteo (core:network), garde la dernière position (core:securestorage) et s'efface à
// la déconnexion (session de feature:auth).
demoUmbrella(
    exported = listOf(
        projects.core.common,
        projects.core.designsystem,
        projects.feature.weather,
        projects.core.connectivity,
    ),
    linked = listOf(
        projects.core.network,
        projects.core.securestorage,
        projects.core.location,
        projects.core.deeplink,
        projects.feature.auth,
    ),
)

android {
    namespace = "com.mvp.umbrella.weatherdemo"
}

package com.mvp.core.deeplink.domain

import com.mvp.core.deeplink.domain.model.DeepLink

/**
 * Format des liens de l'app, avec un schéma d'URL propre à l'app (pas encore de domaine pour les liens
 * vérifiés, voir ADR 0008) :
 *
 * - `mvpmodular://tasks` et `mvpmodular://tasks/<id de tâche>`
 * - `mvpmodular://weather`
 * - `mvpmodular://catalog` et `mvpmodular://catalog/products/<id de produit>`
 * - `mvpmodular://settings`
 *
 * N'importe quelle app ou page web peut ouvrir ces liens : la lecture est stricte (identifiants au format
 * attendu, rien de plus dans le chemin), et la requête (`?…`) comme le fragment (`#…`) sont ignorés.
 * Une autre app peut déclarer le même schéma : un lien ne porte jamais de donnée sensible (jeton, titre…),
 * et l'app ne le confie qu'à elle-même (intent explicite, `userInfo` d'une notification).
 */
object DeepLinks {
    const val SCHEME = "mvpmodular"

    private const val PREFIX = "$SCHEME://"
    private const val MAX_LENGTH = 512

    // Identifiants des tâches : des UUID, générés par l'app.
    private val TODO_ID = Regex("[A-Za-z0-9-]{1,64}")

    // Identifiants DummyJSON : entiers positifs ; 18 chiffres au plus pour tenir dans un Long.
    private val PRODUCT_ID = Regex("[0-9]{1,18}")

    /** Destination du lien, ou `null` s'il ne correspond à aucun écran de l'app. */
    fun parse(url: String): DeepLink? {
        if (url.length > MAX_LENGTH || !url.startsWith(PREFIX, ignoreCase = true)) return null
        val path = url.substring(PREFIX.length).substringBefore('#').substringBefore('?').removeSuffix("/")
        val segments = path.split('/')
        val arguments = segments.drop(1)
        return when (segments.first().lowercase()) {
            "tasks" -> when (arguments.size) {
                0 -> DeepLink.Tasks()
                1 -> arguments[0].takeIf(TODO_ID::matches)?.let { DeepLink.Tasks(it) }
                else -> null
            }
            "weather" -> DeepLink.Weather.takeIf { arguments.isEmpty() }
            "catalog" -> when {
                arguments.isEmpty() -> DeepLink.Catalog()
                arguments.size == 2 && arguments[0].equals("products", ignoreCase = true) -> arguments[1]
                    .takeIf(PRODUCT_ID::matches)
                    ?.toLongOrNull()
                    ?.takeIf { it > 0 }
                    ?.let { DeepLink.Catalog(it) }
                else -> null
            }
            "settings" -> DeepLink.Settings.takeIf { arguments.isEmpty() }
            else -> null
        }
    }

    /** Lien vers [link], au format lu par [parse]. */
    fun url(link: DeepLink): String = PREFIX + when (link) {
        is DeepLink.Tasks -> "tasks" + link.todoId?.let { "/$it" }.orEmpty()
        DeepLink.Weather -> "weather"
        is DeepLink.Catalog -> "catalog" + link.productId?.let { "/products/$it" }.orEmpty()
        DeepLink.Settings -> "settings"
    }
}

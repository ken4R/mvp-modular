package com.mvp.core.notifications

/**
 * Notification affichée à chaque arrivée dans le cercle ([latitude], [longitude], [radiusMeters]) (ADR 0023).
 * [id] est unique dans son groupe. [deepLink] : lien ouvert par un tap (`null` : l'app s'ouvre simplement).
 * [expiresAtEpochMillis] : plus rien ne part après (fin de session) ; seul Android sait l'appliquer, iOS l'ignore.
 */
data class LocationNotification(
    val id: String,
    val title: String,
    val body: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int,
    val deepLink: String? = null,
    val expiresAtEpochMillis: Long? = null,
)

/**
 * Notifications locales déclenchées par le lieu, regroupées par feature ([group]) comme [LocalNotificationScheduler].
 * Android : alertes de proximité de `LocationManager` ; iOS : `UNLocationNotificationTrigger`.
 */
interface LocationNotificationScheduler {

    /**
     * `true` si le système peut les déclencher : Android, position exacte et « Toujours autoriser » (Android 10+) ;
     * iOS, position autorisée (« Lorsque l'app est active » suffit) et « Position exacte ».
     */
    suspend fun canDeliver(): Boolean

    /**
     * Remplace les notifications posées du [group] par [notifications] (au plus [MAX_NOTIFICATIONS], les premières).
     * Seules celles qui ont changé sont retirées ou reposées : reposer une alerte inchangée la ferait repartir sur
     * Android quand on est déjà dans la zone. Sans [canDeliver], toutes celles du groupe sont retirées.
     */
    suspend fun replaceAll(group: String, notifications: List<LocationNotification>)

    /** Retire les notifications posées du [group] et celles déjà affichées. */
    suspend fun cancelAll(group: String)

    companion object {
        /** iOS surveille au plus 20 régions par app. */
        const val MAX_NOTIFICATIONS = 20
    }
}

/** Ce qu'il faut retirer (identifiants déjà posés) et poser (identifiant et notification) pour arriver à l'état voulu. */
internal data class LocationChanges(
    val toRemove: Set<String>,
    val toAdd: List<Pair<String, LocationNotification>>,
)

/**
 * Différence entre les notifications déjà posées ([registered], identifiants « groupe:id:empreinte ») et
 * [wanted]. Une notification modifiée change d'empreinte : l'ancienne est retirée, la nouvelle posée.
 */
internal fun planLocationChanges(
    group: String,
    registered: Set<String>,
    wanted: List<LocationNotification>,
): LocationChanges {
    val target = wanted
        .distinctBy { it.id }
        .take(LocationNotificationScheduler.MAX_NOTIFICATIONS)
        .associateBy { it.identifier(group) }
    val ownRegistered = registered.filter { it.startsWith("$group:") }.toSet()
    return LocationChanges(
        toRemove = ownRegistered - target.keys,
        toAdd = target.filterKeys { it !in ownRegistered }.map { (identifier, notification) -> identifier to notification },
    )
}

/** Identifiant posé dans le système : groupe, id, puis empreinte de tout ce qui définit la notification. */
internal fun LocationNotification.identifier(group: String): String = "$group:$id:${fingerprint()}"

/** Étiquette de la notification affichée : le groupe et l'id, sans l'empreinte (comme les rappels à l'heure). */
internal fun LocationNotification.displayTag(group: String): String = "$group:$id"

private fun LocationNotification.fingerprint(): String =
    listOf(latitude, longitude, radiusMeters, title, body, deepLink, expiresAtEpochMillis)
        .joinToString("|")
        .hashCode()
        .toUInt()
        .toString(16)

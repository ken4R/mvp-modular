# ADR 0023 — Rappels de tâches par lieu

- Statut : accepté
- Date : 2026-10-26

## Contexte

Une tâche doit pouvoir rappeler quelque chose « quand j'arrive au bureau ». Sans serveur, sans carte et sans envoyer la position nulle part. Le Kotlin partagé garde la logique (quels rappels poser, quand les retirer), comme pour les rappels à l'heure ([ADR 0004](0004-rappels-notifications-locales.md)). Les rappels par lieu doivent disparaître à la déconnexion, et ne jamais partir quand le réglage Notifications est coupé ou la tâche terminée.

## Options

- **Choix du lieu** : carte (Google Maps demande une clé et Play Services, MapKit seulement sur iOS), recherche d'adresse (géocodage : l'adresse part chez Apple ou Google), ou **« ici »** : la position actuelle de l'appareil, avec un rayon et un nom libre (« Bureau »).
- **Android** : Geofencing API de Google Play Services (fiable, mais une dépendance de plus, absente sans services Google, écartée par l'ADR 0005), ou **`LocationManager.addProximityAlert`** du système.
- **iOS** : surveillance de régions `CLLocationManager` (autorisation « Toujours », l'app est relancée en arrière-plan pour poster la notification), ou **`UNLocationNotificationTrigger`** : la notification elle-même est déclenchée par le lieu, iOS s'occupe de tout, avec l'autorisation « Lorsque l'app est active ».
- **Déclenchement** : une seule fois, ou **à chaque arrivée** tant que la tâche n'est pas terminée.

## Décision

- **Lieu = position actuelle**, précise : `LocationProvider.currentPosition(LocationAccuracy.Precise)` (`core:location`). Android exige `ACCESS_FINE_LOCATION` et une mesure à 150 m près au plus ; iOS exige la « Position exacte ». Rayon au choix 100, 200 (par défaut) ou 500 m, nom libre de 40 caractères (« Ce lieu » par défaut). Au plus 20 rappels par lieu actifs (tâches non terminées), la limite d'iOS.
- **Stockage** : quatre colonnes de `todo.db` (`location_latitude`, `location_longitude`, `location_radius`, `location_label`, migration `6.sqm`). Propres à l'appareil comme `reminder_at` : jamais dans la file de synchronisation, jamais dans le partage, l'export calendrier, le widget ou l'analytics. Effacées avec les tâches à la déconnexion. La base est déjà hors sauvegardes, avec `secure_delete` (ADR 0013).
- **À chaque arrivée** tant que la tâche est à faire : pas d'état « déjà parti » à garder en double, et ça correspond à « quand j'arrive au bureau ». Cocher la tâche ou retirer le rappel l'arrête.
- **`core:notifications`** gagne `LocationNotificationScheduler` (`replaceAll` / `cancelAll` par groupe, `canDeliver`) :
  - **Android** : `addProximityAlert` avec un `PendingIntent` explicite vers `LocationNotificationReceiver` (non exporté). `FLAG_MUTABLE` est obligatoire, le système y ajoute `KEY_PROXIMITY_ENTERING` ; l'intent explicite empêche de le détourner. Le récepteur poste la notification « Rappel » (même canal et même présentation que les rappels à l'heure, contenu masqué sur l'écran verrouillé), à l'entrée seulement, au plus une fois par heure et par tâche pour ne pas répéter quand le GPS oscille à la limite. L'alerte expire à la fin de la session. Permissions : `ACCESS_FINE_LOCATION` puis `ACCESS_BACKGROUND_LOCATION` (Android 10+, « Toujours autoriser »), demandées par la fiche de la tâche, au moment où l'utilisateur pose un rappel.
  - **iOS** : `UNLocationNotificationTrigger` (région circulaire, entrée seulement, répété), aucune autorisation « Toujours », l'app n'est jamais réveillée.
  - **Réconciliation par différence** : chaque rappel est identifié par « groupe:id:empreinte » (empreinte = hachage du lieu, du rayon, du texte, du lien et de l'expiration). Seuls les rappels ajoutés, modifiés ou retirés sont touchés. Reposer une alerte inchangée la ferait repartir tout de suite sur Android quand on est déjà sur place. Android garde la liste des identifiants posés dans des `SharedPreferences` privées (ids et empreintes, jamais de coordonnées) et vérifie que leur `PendingIntent` existe encore (un redémarrage ou un arrêt forcé les efface).
- **`SyncTodoLocationRemindersUseCase`** (comme `SyncTodoRemindersUseCase`) : combine les tâches, le réglage Notifications, la session et la disponibilité (permissions). Sans session : tout annuler, notifications affichées comprises. Notifications coupées ou permissions manquantes : aucun rappel posé. Lancé au démarrage par chaque app ; sur Android aussi au redémarrage du téléphone et à la mise à jour de l'app (`BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`), qui effacent les alertes.
- **Disponibilité** : `ObserveLocationRemindersAvailableUseCase` (bandeau « les rappels par lieu ne partent pas » dans la fiche) et `RefreshLocationRemindersAvailabilityUseCase`, appelé par l'UI au retour au premier plan et après une demande de permission (accordée dans les paramètres du système, elle ne prévient pas l'app).
- **iOS, limite de 64 notifications programmées** : 20 places sont gardées aux rappels par lieu, les rappels à l'heure passent de 64 à 44 au plus (les plus proches d'abord, comme avant).

## Conséquences

- La position précise du lieu est gardée sur l'appareil, dans la base des tâches (privée, hors sauvegardes, effacée à la déconnexion). Elle ne part nulle part : ni serveur, ni géocodage, ni tiers. La position mesurée pour poser le rappel n'est pas envoyée non plus.
- Android : `ACCESS_BACKGROUND_LOCATION` est sensible pour Google Play (déclaration et vidéo à fournir avant publication). Sans elle, l'app explique que les rappels par lieu ne partent pas. Les alertes de proximité du système sont moins réactives que la Geofencing API (souvent plusieurs minutes en arrière-plan, Android 8+) ; passer à Play Services plus tard ne touche que `core:notifications`.
- Android : une alerte posée alors qu'on est déjà dans la zone part tout de suite (le système voit une entrée) ; la différence ci-dessus et le délai d'une heure limitent ces répétitions.
- iOS : `UNLocationNotificationTrigger` n'a pas d'expiration. Un rappel par lieu peut donc encore partir après l'expiration de la session (7 jours) si l'app n'est pas rouverte entre-temps ; il est retiré au lancement suivant. Avec un vrai backend, envisager un texte générique sans le titre de la tâche.
- iOS : avec la position approximative (choix par défaut de l'app, `NSLocationDefaultAccuracyReduced`), les rappels par lieu ne marchent pas : la fiche demande d'activer « Position exacte » dans Réglages. La météo garde la position approximative.
- Le texte de la notification est le titre de la tâche, comme pour les rappels à l'heure ; sur Android il est copié dans l'intent de l'alerte (mémoire du système) jusqu'à son retrait.

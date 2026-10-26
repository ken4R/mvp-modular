# Plan — Rappels de tâches par lieu

Objectif : « me rappeler cette tâche en arrivant ici ». La fiche d'une tâche pose un rappel sur la position actuelle, avec un rayon et un nom ; une notification « Rappel » part à chaque arrivée dans la zone tant que la tâche est à faire. Rien ne part si le réglage Notifications est coupé, et tout est annulé à la déconnexion. La position reste sur l'appareil. Choix techniques : [ADR 0023](../adr/0023-rappels-par-lieu.md).

1. **`core:common`** : `LocationAccuracy` (`Approximate`, `Precise`) en paramètre de `LocationProvider.currentPosition` (approximative par défaut, la météo ne change pas).
2. **`core:location`** : position précise. Android : `ACCESS_FINE_LOCATION` exigée, mesure à 150 m près au plus, dernière position connue gardée seulement si elle a moins d'une minute. iOS : `kCLLocationAccuracyBest`, « Position exacte » exigée.
3. **`core:notifications`** : `LocationNotification`, `LocationNotificationScheduler` (`canDeliver`, `replaceAll`, `cancelAll`), différence par empreinte (`planLocationChanges`, testée en `commonTest`).
   - Android : `ProximityLocationNotificationScheduler` (`addProximityAlert`), `LocationNotificationReceiver` ; affichage commun avec `NotificationWorker` (`ReminderNotifications`).
   - iOS : `UserNotificationsLocationNotificationScheduler` (`UNLocationNotificationTrigger`) ; rappels à l'heure limités à 44.
4. **`feature:todo`** :
   - `LocationReminder` (latitude, longitude, rayon, nom) et `Todo.locationReminder` ; migration `6.sqm`, non synchronisé.
   - `SetLocationReminderHereUseCase`, `RemoveLocationReminderUseCase`, `SyncTodoLocationRemindersUseCase`, `ObserveLocationRemindersAvailableUseCase`, `RefreshLocationRemindersAvailabilityUseCase`.
   - `LocationReminderScheduler` (domain) implémenté par `NotificationLocationReminderScheduler` (data).
   - `TodoUserSessionObserver` annule aussi les rappels par lieu.
   - Filtre « Avec rappel » : compte aussi les rappels par lieu.
   - Tests : use cases, persistance, migration v6 → v7, synchronisation qui garde le lieu, déconnexion.
5. **`shared`** : modules Koin, `UseCases` iOS (`setLocationReminderHere`, `removeLocationReminder`, `refreshLocationRemindersAvailability`, `observeLocationRemindersAvailable`, `startTodoLocationReminders`).
6. **Android** : permissions `ACCESS_FINE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`, `RECEIVE_BOOT_COMPLETED` ; récepteurs `LocationNotificationReceiver` et `LocationRemindersRestoreReceiver` (redémarrage, mise à jour) ; section « Rappel par lieu » de la fiche (rayon, nom, « Me le rappeler en arrivant ici », « Retirer », explications de permission) ; ligne « En arrivant : Bureau » dans la liste ; synchronisation lancée par `MvpApplication`.
7. **iOS** : même section dans `TodoDetailView`, bouton vers Réglages si la position exacte manque ; `TodoReminders.start()` lance aussi les rappels par lieu ; texte `NSLocationWhenInUseUsageDescription` mis à jour (app, démos Tâches, Météo, Onboarding), clé ajoutée à `TodoDemo`, vérifiée par la CI.
8. **Vérification** : CI (tests KMP Android + iOS, build Android, build Xcode avec les démos), revues de code et de sécurité, recette ci-dessous.

## Recette manuelle

Le plus simple est l'émulateur Android (position simulée dans « Extended controls > Location ») et le simulateur iOS (Features > Location > Custom Location).

- Réglages : notifications activées.
- Simuler une position A. Fiche d'une tâche > « Rappel par lieu » : nom « Bureau », rayon 200 m, « Me le rappeler en arrivant ici ».
  - Android : accepter la position exacte, puis « Toujours autoriser » dans l'écran du système. iOS : « Lorsque l'app est active » avec « Position exacte » activée.
  - La fiche affiche « En arrivant à Bureau (200 m) », la liste « En arrivant : Bureau ».
- Mettre l'app en arrière-plan, simuler une position à 2 km, puis revenir en A : la notification « Rappel » arrive (Android : parfois après quelques minutes). Un tap ouvre la tâche.
- Cocher la tâche, refaire l'aller-retour : rien. La décocher : le rappel repart à l'arrivée suivante.
- Android : refuser la position exacte (approximative seulement) : message « Autorisez la position exacte ». Refuser « Toujours » : bandeau « Les rappels par lieu ne partent pas » avec « Autoriser ».
- iOS : couper « Position exacte » dans Réglages > MVP Modular > Position : le bandeau apparaît au retour dans l'app, avec « Ouvrir Réglages ».
- Couper les notifications dans Réglages de l'app : aucun rappel par lieu ne part.
- Android : redémarrer l'émulateur sans ouvrir l'app, refaire l'aller-retour : la notification arrive.
- Se déconnecter puis refaire l'aller-retour : rien. Se reconnecter : les tâches et leurs lieux ont disparu (données effacées).
- Poser 20 rappels par lieu : le 21e est refusé avec « 20 rappels par lieu au maximum ».

# Plan — Rappels de tâches

Objectif : une tâche peut recevoir un rappel (date et heure) ; une notification locale part à cette heure, sur Android et iOS. Aucun rappel ne part tant que le réglage Notifications est coupé, ni pour une tâche terminée. Les rappels sont annulés à la déconnexion. `feature:todo` et `feature:settings` ne dépendent pas l'une de l'autre. Choix techniques : [ADR 0004](../adr/0004-rappels-notifications-locales.md).

1. **`core:common`** : `NotificationsPreference` (contrat « les notifications de l'app sont-elles activées ? ») et `SessionExpiry` (fin de la session, fournie par `feature:auth`).
2. **`core:notifications`** (nouveau) : `LocalNotificationScheduler` (`replaceAll` / `cancelAll` par groupe) ; WorkManager + `NotificationManager` sur Android, `UNUserNotificationCenter` sur iOS ; `notificationsModule` (`expect`/`actual`).
3. **`feature:settings`** : `SettingsNotificationsPreference` (lit le réglage Notifications), lié dans Koin. Test.
4. **`feature:todo`** :
   - `Todo.reminderAt` (epoch ms, `null` sans rappel), colonne `reminder_at` et migration `1.sqm` de `todo.db`.
   - `SetTodoReminderUseCase` (date future obligatoire, `null` retire le rappel), `ObserveRemindersEnabledUseCase`, `SyncTodoRemindersUseCase` (programme les rappels à venir des tâches non terminées, avant la fin de session, quand les notifications sont activées ; aucun sinon ; tout annuler sans session).
   - `ReminderScheduler` (domain) implémenté par `NotificationReminderScheduler` (data, sur `core:notifications`).
   - `TodoUserSessionObserver` annule aussi les rappels à la déconnexion.
   - Tests : use cases, persistance du rappel, migration v1 → v2, déconnexion.
5. **`shared`** : modules Koin, `UseCases.setTodoReminder()`, `observeRemindersEnabled(...)`, `startTodoReminders()` pour iOS.
6. **Android** : bouton « Rappel » sur chaque tâche (sélecteurs de date et d'heure système), « Retirer », message si les notifications sont coupées ; synchronisation lancée par `MvpApplication`.
7. **iOS** : bouton cloche sur chaque tâche, feuille avec `DatePicker`, message si les notifications sont coupées ; `TodoReminders.start()` lance la synchronisation et affiche les rappels même app ouverte ; démo `TodoDemo` avec notifications activées.
8. **Vérification** : CI (tests KMP Android + iOS, build Android, build Xcode avec les démos), revues, recette manuelle ci-dessous.

## Recette manuelle (Android et iOS)

- Réglages : activer les notifications (accepter la permission).
- Tâches : « Rappel » sur une tâche, choisir une heure dans 2 minutes. La ligne affiche « Rappel le … ». Mettre l'app en arrière-plan : la notification arrive (titre « Rappel », texte = la tâche), un tap ouvre l'app. iOS : elle s'affiche aussi app ouverte.
- Choisir une heure passée : message « Choisissez une date à venir ».
- Cocher la tâche avant l'heure : pas de notification. La décocher : le rappel est reprogrammé.
- Couper les notifications dans Réglages : le message « Activez les notifications… » apparaît sur l'écran Tâches et aucun rappel ne part. Les réactiver : les rappels à venir sont reprogrammés.
- « Retirer » le rappel : pas de notification.
- Tuer et relancer l'app (Android : redémarrer le téléphone) : le rappel est toujours là et part à l'heure.
- Se déconnecter avant l'heure : aucune notification, et les rappels déjà affichés disparaissent du centre de notifications.
- Android : l'heure d'envoi peut glisser de quelques minutes quand le téléphone est en veille (Doze), voir l'ADR.

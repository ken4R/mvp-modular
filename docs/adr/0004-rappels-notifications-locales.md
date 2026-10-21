# ADR 0004 — Rappels de tâches : notifications locales `core:notifications`

- Statut : accepté
- Date : 2026-10-21

## Contexte

Une tâche peut recevoir un rappel. La notification ne doit partir que si le réglage Notifications de l'app est activé (il suit déjà la permission système), et `feature:todo` ne doit pas dépendre de `feature:settings`. Les rappels doivent survivre au redémarrage de l'app (et du téléphone sur Android) et disparaître à la déconnexion. Pas de serveur : tout est local.

## Options

- Android : **AlarmManager** (alarmes exactes : permission `SCHEDULE_EXACT_ALARM` non accordée par défaut depuis Android 14 ; alarmes perdues au redémarrage du téléphone, il faut un récepteur `BOOT_COMPLETED`) ou **WorkManager** (travail différé, persistant, reprogrammé après redémarrage, aucune permission ; l'heure peut glisser de quelques minutes en veille).
- iOS : **`UNUserNotificationCenter`** avec un déclencheur temporel, appelé depuis Kotlin/Native ou depuis Swift.
- Lien avec le réglage : dépendance directe `feature:todo` → `feature:settings`, ou **contrat dans `core:common`** comme `UserSessionObserver`.
- Programmation : appels « programmer / annuler » à chaque action de l'utilisateur, ou **réconciliation** : à chaque changement des tâches ou du réglage, on recalcule l'ensemble des rappels voulus et on remplace ceux du système.

## Décision

- Nouveau module **`core:notifications`** : `LocalNotificationScheduler.replaceAll(group, notifications)` / `cancelAll(group)`, `expect`/`actual`.
  - Android : WorkManager (`androidx.work:work-runtime`, seule dépendance ajoutée), un travail unique par rappel, étiqueté par groupe (et par son heure, pour laisser partir à la réconciliation un rappel échu que la veille ou un redémarrage a retardé) ; le worker poste la notification (canal « Rappels », contenu masqué sur l'écran verrouillé sécurisé) seulement si la permission est accordée. Un tap ouvre l'app.
  - iOS : `UNUserNotificationCenter` depuis Kotlin/Native, identifiants préfixés par le groupe. `cancelAll` retire aussi les notifications déjà affichées. Un délégué Swift (`TodoReminders`) les affiche quand l'app est au premier plan.
- **`NotificationsPreference`** dans `core:common`, implémenté par `feature:settings`, lu par `feature:todo`.
- **Réconciliation** : `SyncTodoRemindersUseCase` combine les tâches et le réglage, et remplace les rappels programmés par ceux des tâches non terminées dont l'heure est à venir (aucun si le réglage est coupé). Lancé une fois au démarrage par chaque app. Pas d'état à garder en double, et cocher, supprimer ou couper les notifications annule naturellement le rappel.
- Session : `SessionExpiry` (`core:common`, fourni par `feature:auth`) donne la fin de la session. Seuls les rappels qui tombent avant cette fin sont programmés, pour qu'aucun ne s'affiche après l'expiration, même app fermée ; sans session valide, tous les rappels sont annulés (programmés et affichés).
- Déconnexion : `TodoUserSessionObserver` efface les tâches puis annule les rappels (programmés et affichés).
- Stockage : `reminder_at` (epoch ms) dans `todo.db`, migration SQLDelight `1.sqm`.

## Conséquences

- Sur Android, un rappel peut arriver avec quelques minutes de retard en veille profonde ; acceptable pour un prototype. Passer à AlarmManager exact plus tard ne touche que `core:notifications`.
- Le titre de la tâche est copié dans la base interne de WorkManager (stockage privé de l'app, sauvegarde Android désactivée) jusqu'à l'envoi ou l'annulation.
- Le titre de la tâche est le texte de la notification. Android masque le contenu sur l'écran verrouillé sécurisé (`VISIBILITY_PRIVATE`) ; sur iOS, l'affichage sur l'écran verrouillé suit le réglage « Aperçus » de l'utilisateur. Si des titres sensibles sont attendus, passer à un texte générique.
- Un rappel posé au-delà de la fin de session n'est programmé qu'après une nouvelle connexion qui le couvre.
- iOS limite à 64 notifications programmées par app : `core:notifications` ne programme que les 64 plus proches, les suivantes le sont à la réconciliation suivante.
- Toute autre feature qui veut des notifications locales réutilise `core:notifications` avec son propre groupe.

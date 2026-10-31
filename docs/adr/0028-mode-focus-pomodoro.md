# ADR 0028 — Mode focus (Pomodoro) sur une tâche

- Statut : accepté
- Date : 2026-10-31

## Contexte

L'utilisateur veut se concentrer sur une tâche avec la méthode Pomodoro (25 min de travail, 5 min de pause, une pause longue toutes les 4 sessions), et voir le temps restant sans ouvrir l'app : notification en cours sur Android, Live Activity sur iOS (écran verrouillé, Dynamic Island). Toute la logique doit rester en KMP ; les apps ne gardent que l'UI et l'affichage système. Le titre d'une tâche est une donnée personnelle (ADR 0011 : rien de l'utilisateur hors de l'app quand le verrou Face ID / empreinte est activé).

## Options

- **Compte à rebours tenu par l'app** (un tic par seconde qui met à jour la notification / la Live Activity) : simple, mais exige un processus vivant. Sur Android, un service au premier plan (Android 14 : type `specialUse`, justification à donner au Play Store) ; sur iOS, impossible (app suspendue en arrière-plan, mises à jour de Live Activity limitées).
- **Compte à rebours du système, jusqu'à une heure de fin** : KMP calcule `endsAt` ; Android affiche un chronomètre décompté (`setUsesChronometer` + `setChronometerCountDown`), iOS `Text(timerInterval:)` et `ProgressView(timerInterval:)`. Les mises à jour n'ont lieu qu'aux changements d'état (pause, phase suivante, arrêt). La fin de phase, app arrêtée ou suspendue, est signalée par une notification locale programmée.
- **Notifications push** pour mettre à jour la Live Activity : pas de backend.

## Décision

- **Module `feature:focus`**, sans dépendance vers `feature:todo` : l'app passe l'id et le titre de la tâche (`StartFocus`). Branché dans `shared` (Koin, export iOS).
- **Une session à la fois**, enregistrée chiffrée (`core:securestorage`, clé `focus.session`, JSON) pour survivre à l'arrêt de l'app et aux actions de la notification ; une valeur illisible ou incohérente compte comme absente. Lecture-écriture sous verrou (`FocusRepository.update`) : l'écran et un bouton de la notification ne s'écrasent pas.
- **Durées** 25 / 5 / 15 min, pause longue après 4 concentrations menées à terme (`FocusDurations`, injecté). Le temps de pause ne compte pas : à la reprise, `endsAt` = maintenant + temps restant. Une phase finie attend l'utilisateur (« Commencer la pause », « Reprendre la concentration ») : pas d'enchaînement automatique, qui exigerait un processus vivant. « Passer » écourte la phase ; une concentration écourtée ne compte pas.
- **États** : `ObserveFocusUseCase` réémet aux changements, au changement du verrou et une fois à `endsAt` (Running → Finished) ; `ObserveFocusTimerUseCase` tique chaque seconde pour l'écran seulement. Sans session de connexion valide (`SessionExpiry`), rien n'est montré ; la déconnexion efface la session et son alerte (`UserSessionObserver`).
- **Alerte de fin** par `core:notifications` (groupe `focus`, WorkManager / `UNUserNotificationCenter`), seulement si le réglage Notifications est activé, comme les rappels. Son texte ne contient jamais le titre de la tâche ; un tap ouvre la tâche (`mvpmodular://tasks/<id>`), par le chemin des liens profonds (connexion et verrou vérifiés).
- **Hors de l'app**, le titre de la tâche (`FocusState.lockScreenTitle`) est remplacé par « Session de concentration » quand le verrou Face ID / empreinte est activé.
- **Android** : notification en cours sans service au premier plan, canal « Mode focus » d'importance basse (ni son ni bandeau), redessinée par `FocusNotifier` (démarré avec l'Application) ; `setTimeoutAfter(endsAt)` la retire à la fin si le processus est mort, l'alerte de fin prend le relais. Contenu privé sur l'écran verrouillé sécurisé (`VISIBILITY_PRIVATE` + version publique sans titre). Boutons Pause / Reprendre / Commencer la pause / Arrêter par `FocusActionReceiver`, non exporté, PendingIntent explicites et immuables.
- **iOS** : Live Activity ActivityKit (iOS 16.2+ côté app, vue 16.1+ dans l'extension des widgets), sans push. Attributs dans le produit SPM `FocusActivityData`, sans dépendance, partagé avec l'extension qui ne charge pas Kotlin (comme ADR 0011). `staleDate` = fin de phase : l'extension affiche « terminée » même si l'app était suspendue. `NSSupportsLiveActivities` dans l'Info.plist de l'app.
- **Entrée** : bouton « Mode focus (Pomodoro) » de la fiche d'une tâche à faire, fourni par l'app au package / à l'écran Tâches (closure) : `FeatureTodo` ne dépend pas de `FeatureFocus`. Écran Focus à la place de la liste dans l'onglet Tâches (derrière le verrou, pas en feuille), bandeau « Ouvrir » sur la liste pendant une session.
- **Design system** : nouveau token `typography.display` (64 / 72) pour le compte à rebours.

## Conséquences

- Aucune permission ni type de service au premier plan supplémentaire sur Android. En contrepartie, Android 14+ laisse l'utilisateur balayer la notification en cours ; la session continue dans l'app (bandeau).
- En veille profonde (Doze), l'alerte de fin peut arriver avec quelques minutes de retard (WorkManager, ADR 0004). Le chronomètre de la notification et la Live Activity restent exacts.
- Réglage Notifications coupé : pas d'alerte de fin, mais la notification en cours (Android, permission système requise) et la Live Activity restent, ce sont des affichages d'état. Le réglage est lu au lancement de chaque phase : le changer en cours de phase vaut pour la suivante.
- iOS 16.0 / 16.1 : pas de Live Activity (écran Focus et alerte de fin seulement). Live Activities désactivées par l'utilisateur : idem.
- Une Live Activity ne peut être créée qu'app au premier plan : une session restaurée au lancement l'est ; une demande refusée est retentée à la mise à jour suivante.
- Pas encore de statistiques des sessions ni de durées réglables (`FocusDurations` est prêt pour une préférence) ; pas de synchronisation entre appareils.
- Pas d'app de démo iOS : la feature s'ouvre depuis la fiche d'une tâche (`FeatureTodo`), dans l'app complète.

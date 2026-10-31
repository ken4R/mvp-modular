# Plan — Mode focus (Pomodoro) sur une tâche

Objectif : lancer une session Pomodoro (25 min de concentration, 5 min de pause, 15 min toutes les 4) sur une tâche, et voir le temps restant hors de l'app : notification en cours sur Android, Live Activity sur iOS. Choix techniques : [ADR 0028](../adr/0028-mode-focus-pomodoro.md).

1. **`feature:focus`, domain** : `FocusPhase`, `FocusDurations`, `FocusSession`, `FocusState` / `FocusStatus`, `FocusTimer`, `FocusFormat` ; `FocusRepository` et `FocusAlarm` ; use cases `StartFocusUseCase`, `PauseFocusUseCase`, `ResumeFocusUseCase`, `StartNextFocusPhaseUseCase`, `StopFocusUseCase`, `ObserveFocusUseCase`, `ObserveFocusTimerUseCase`.
2. **`feature:focus`, data** : `SecureFocusRepository` (stockage chiffré, JSON validé), `NotificationFocusAlarm` (core:notifications, réglage Notifications, sans titre), `FocusUserSessionObserver` (déconnexion). Koin `focusModule`, façade Swift `FocusUseCases` (iosMain).
3. **Tests `commonTest`** (`FocusUseCasesTest`, 21 tests) : démarrage et alerte, validation, titre coupé, remplacement de session, pause / reprise (temps de pause non compté), fin de phase, pause longue après 4, phase écourtée non comptée, arrêt, notifications coupées, titre masqué avec le verrou, session de connexion absente ou expirée, déconnexion, redémarrage, valeur stockée invalide, tic par seconde, minuteur en pause, format.
4. **`shared`** : `focusModule` dans `sharedModules`, `feature:focus` exporté vers iOS.
5. **`core:designsystem`** : token `typography.display` (Android `displayLarge`, iOS `DS.Typography.display`).
6. **Android** : bouton « Mode focus (Pomodoro) » dans `TodoDetailScreen`, `FocusScreen` + `FocusViewModel` dans l'onglet Tâches, bandeau sur la liste, `FocusNotifier` (notification en cours) et `FocusActionReceiver` (boutons), déclarés dans `MvpApplication` et le manifeste.
7. **iOS** : package `FeatureFocus` (`FocusView`, `FocusBanner`, `FocusViewModel`, `FocusLiveActivity`), produit `FocusActivityData`, vue `Widget/FocusLiveActivityWidget.swift` dans l'extension, `NSSupportsLiveActivities`, bouton dans `TodoDetailView` (closure fournie par `AppTabsView`).
8. **Vérification** : CI (tests KMP Android + iOS, builds Android et Xcode), revues de code et de sécurité, recette ci-dessous.

## Recette manuelle

Pour aller vite, raccourcir les durées dans `FocusDurations()` (`focusModule`) le temps de l'essai, sans le committer.

- Fiche d'une tâche à faire > « Mode focus (Pomodoro) » : l'écran Focus affiche 25:00 qui décompte, « Concentration ». Android 13+ : la permission de notification est demandée si elle manque. Une tâche terminée n'a pas le bouton.
- Android : la notification « Mode focus » montre le titre et un chronomètre décompté, sans son. Verrouiller le téléphone (écran sécurisé) : « Session de concentration », sans le titre.
- iOS 16.2+ : verrouiller l'iPhone, la Live Activity montre le titre, la phase, le compte à rebours et la barre de progression ; Dynamic Island (iPhone 14 Pro et plus) : minuteur compact.
- « Pause » : le temps se fige (notification « en pause · 23:30 restantes », Live Activity figée). Attendre, « Reprendre » : le décompte repart du même temps.
- Android : boutons « Pause », « Reprendre », « Arrêter » de la notification, app fermée depuis les apps récentes : l'action est appliquée et la notification redessinée.
- « Réduire » : retour à la liste, bandeau « Concentration 24:12 · titre » avec « Ouvrir ». Changer d'onglet puis revenir : la session continue.
- Fin de phase, app en arrière-plan : alerte « Concentration terminée — Place à la pause courte (5 min). » (si le réglage Notifications est activé), sans le titre de la tâche. Un tap ouvre l'app sur la tâche. La Live Activity passe à « Terminée · ensuite : pause courte ».
- « Commencer la pause courte » : 05:00 ; à la fin, « Reprendre la concentration ». Après 4 concentrations menées à terme : « Commencer la pause longue » (15:00).
- « Passer » pendant une concentration : pause courte tout de suite, la concentration écourtée n'est pas comptée.
- Réglages > Sécurité : activer le déverrouillage biométrique pendant une session : notification et Live Activity affichent « Session de concentration ».
- Réglage Notifications coupé : pas d'alerte de fin ; la notification en cours (Android) et la Live Activity restent.
- « Arrêter » : la notification et la Live Activity disparaissent, l'écran revient à la liste.
- Se déconnecter pendant une session : notification et Live Activity disparaissent ; reconnecté, aucune session.
- Tuer l'app pendant une session puis la relancer : la session reprend (temps écoulé compris).
- TalkBack / VoiceOver : le compte à rebours est lu en minutes (« Environ 24 minutes restantes »), pas à chaque seconde.

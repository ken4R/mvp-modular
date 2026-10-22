# Plan — Widget Tâches de l'écran d'accueil

Objectif : un widget d'écran d'accueil sur Android (Glance) et iOS (WidgetKit) qui montre les tâches à faire, se vide à la déconnexion et ne montre rien quand le déverrouillage Face ID / empreinte est activé. Choix techniques : [ADR 0011](../adr/0011-widgets-ecran-accueil.md).

1. **`core:common`** : contrat `AppLockPreference` (réglage Sécurité activé), fourni par `feature:settings` (`SettingsAppLockPreference`).
2. **`feature:todo`** : `TodoWidgetState` / `TodoWidgetItem` et `ObserveTodoWidgetUseCase` (session, verrou, tâches). Tests `TodoWidgetUseCaseTest` : ordre (rappel le plus proche d'abord), 5 tâches au plus mais toutes comptées, sans session, session expirée, verrou activé, suivi des changements, erreur.
3. **`shared`** : `UseCases.observeTodoWidget(...)` pour Swift.
4. **Android** : Glance 1.1.1, `widget/TodoWidget.kt` (widget + receiver), `widget/TodoWidgetUpdater.kt` lancé par `MvpApplication`, `ui/theme/WidgetTheme.kt` (tokens), `res/xml/todo_widget_info.xml`, receiver dans le manifeste.
5. **iOS** : package `FeatureTodoWidget` (produits `FeatureTodoWidget` et `TodoWidgetData`), extension `TodoWidgetExtension` (sources `Packages/FeatureTodoWidget/Widget`), App Group `group.com.mvp.ios` sur l'app et l'extension, `TodoWidgetPublisher.start()` dans `iOSApp`. Pas d'app de démo : un widget s'essaie avec l'app complète.
6. **CI** : l'extension est construite avec `iosApp` ; une étape vérifie qu'elle est embarquée et qu'elle ne lie pas `Shared.framework`.

## Recette manuelle (simulateur / émulateur)

Poser le widget :

- iOS : appui long sur l'écran d'accueil → « + » → « MVP Modular » (ou « iosApp ») → « Tâches », petit ou moyen. Après `xcodegen`, lancer le schéma `iosApp`.
- Android : appui long sur l'écran d'accueil → Widgets → « MVP Modular » → « Tâches ».

À vérifier :

- Connecté, verrou coupé : le widget affiche « À faire · N » et les tâches non terminées, celles qui ont un rappel en premier. Ajouter, cocher, supprimer une tâche dans l'app : le widget suit en quelques secondes (iOS limite parfois la fréquence des redessins).
- Tap sur le widget : l'app s'ouvre sur Tâches. Tap sur une ligne (Android ; iOS en moyen format) : la tâche est mise en évidence.
- Moyen format iOS / widget Android : l'heure du rappel est affichée sous la tâche.
- Se déconnecter : le widget affiche « Connectez-vous pour voir vos tâches ici. », sans aucune tâche.
- Réglages → Sécurité → activer le déverrouillage : le widget affiche « Tâches masquées » ; le désactiver : les tâches reviennent.
- Verrou activé, tap sur le widget : l'écran de déverrouillage apparaît d'abord.
- iOS, galerie des widgets : l'aperçu montre des tâches d'exemple (« Appeler Paul »…), pas les vôtres.
- iOS, vrai iPhone 15+ en mode En veille (en charge, à l'horizontale, verrouillé) : les titres sont masqués.
- iOS sur un vrai iPhone : l'App Group demande un compte Apple Developer payant. Sans lui, le widget affiche « Ouvrez l'app pour voir vos tâches. ».

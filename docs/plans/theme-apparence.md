# Plan — Thème sombre et couleur d'accent

Objectif : choisir dans Réglages le thème de l'app (celui de l'appareil, clair ou sombre) et sa couleur d'accent, appliqués tout de suite à tous les écrans des deux apps, écran Compose Multiplatform compris. Décision : [ADR 0015](../adr/0015-theme-apparence.md).

1. **`core:designsystem`** : `ThemeMode` (System / Light / Dark), `AccentColor` (Blue / Teal / Violet / Orange / Pink), `Appearance` (thème + accent, `isDark(systemDark)`). `DesignSystem.colors(darkTheme, accent)` : la palette claire ou sombre dont seules `primary`, `onPrimary` et `highlight` changent avec l'accent. `colors(darkTheme)` garde l'accent bleu (widgets). Tailles `accentSwatch` et `selectionBorder` pour les pastilles. Test `DesignSystemTest` : contrastes WCAG AA (4,5:1) de chaque accent en clair et en sombre.
2. **`feature:settings`** : `UserSettings.appearance`, clés `appearance.theme_mode` / `appearance.accent` (nom de l'entrée, valeur inconnue = défaut), `ObserveAppearanceUseCase` (ne réémet que si l'apparence change), `SetThemeModeUseCase`, `SetAccentColorUseCase`. L'apparence est gardée à la déconnexion. Test `AppearanceUseCasesTest`.
3. **`shared`** : `UseCases.observeAppearance`, `setThemeMode`, `setAccentColor` ; `AppearanceIosKt.themeMode(name:)` / `accentColor(name:)` pour Swift. `ComposeScreens.todo` passe l'apparence à l'écran Compose.
4. **`feature:todo-compose`** : `TodoComposeScreen(appearance = …)` → `SharedMvpTheme(appearance)`.
5. **Android** : `AppearanceViewModel` (lié à l'activité, pas à la session) → `MvpTheme(appearance)`, icônes des barres système selon le thème appliqué ; `MvpColors.highlight` suit le thème choisi. Réglages : section « Apparence » (puces Appareil / Clair / Sombre, pastilles d'accent).
6. **iOS** : `DSThemeMode` / `DSAccent` (noms Kotlin en `rawValue`), `@Environment(\.dsAccent)` passé à `DS.colors(colorScheme, accent)` dans toutes les vues, `AppearanceRoot` (FeatureSettings) à la racine de l'app et de la démo Réglages : style imposé aux fenêtres, accent dans l'environnement, teinte des contrôles. Réglages : section « Apparence » (sélecteur segmenté, pastilles).

## Recette manuelle (simulateur / émulateur)

- Premier lancement : thème de l'appareil, accent bleu. Changer le thème de l'appareil : l'app suit.
- Réglages → Sombre : toute l'app passe en sombre tout de suite (onglets, fiche d'une tâche, catalogue, météo, bandeau hors ligne), appareil resté en clair. Les icônes de la barre d'état restent lisibles (Android).
- Clair avec l'appareil en sombre, puis retour à « Appareil » : l'app suit de nouveau l'appareil.
- Chaque accent : boutons, titres de section, interrupteurs, onglet choisi et tâche mise en évidence (lien `mvpmodular://tasks/<id>`) prennent la couleur, en clair et en sombre.
- Écran Compose Multiplatform (onglet Tâches) : même thème et même accent que les écrans natifs.
- Redémarrer l'app : thème et accent restent. Se déconnecter : l'écran de connexion garde le thème et l'accent, le reste des réglages est effacé.
- VoiceOver / TalkBack : chaque pastille annonce le nom de la couleur et si elle est choisie.
- Widgets : toujours l'accent bleu (voir ADR 0015).

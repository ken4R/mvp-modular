# ADR 0015 — Thème sombre et couleur d'accent

- Statut : accepté
- Date : 2026-10-24

## Contexte

Les deux apps suivent déjà le thème clair / sombre de l'appareil, avec une palette unique dans `core:designsystem`. On veut laisser l'utilisateur forcer le clair ou le sombre et choisir une couleur d'accent, depuis Réglages, sur Android (Compose), iOS (SwiftUI) et l'écran Compose Multiplatform (ADR 0010), sans valeur visuelle en dur dans les apps.

## Décision

**Les types d'apparence sont dans `core:designsystem`** : `ThemeMode`, `AccentColor` et `Appearance` sont des valeurs visuelles pures, sans plateforme. `feature:settings` (qui les stocke) en dépend désormais, `feature:todo-compose` (qui les affiche) en dépendait déjà ; pas de contrat dans `core:common` ni de correspondance entre deux enums. C'est la seule dépendance d'un `domain` hors `core:common`, admise parce que ces types n'ont ni plateforme ni logique.

**L'accent ne change que trois tokens** : `primary`, `onPrimary` et `highlight`, en clair et en sombre (`DesignSystem.colors(darkTheme, accent)`). Le reste de la palette (fonds, surfaces, erreur, `secondary` des bandeaux) ne bouge pas : un choix de couleur ne peut pas rendre un texte illisible. Cinq accents fixes plutôt qu'une couleur libre, chacun vérifié en test (contraste WCAG AA 4,5:1 contre son texte, le fond, les surfaces, et du texte sur `highlight`). `colors(darkTheme)` reste l'accent bleu.

**Stockage dans la base des réglages** (clé / valeur, sans migration) : `appearance.theme_mode` et `appearance.accent`, valeur = nom de l'entrée de l'enum (ne pas renommer les entrées), valeur inconnue = défaut.

**Gardée à la déconnexion** : c'est une préférence de l'appareil, sans donnée personnelle. La déconnexion vide la base des réglages et y réécrit l'apparence dans la même transaction (`clearExceptAppearance`) : l'app ne repasse jamais par l'apparence par défaut en cours de route ; l'écran de connexion et le compte suivant gardent le thème choisi. Le texte de la section Compte le dit.

**Lue à la racine, connecté ou non** : `ObserveAppearanceUseCase` ne réémet que si l'apparence change (pas de recomposition de toute l'app quand un autre réglage bouge).
- Android : `AppearanceViewModel` lié à l'activité → `MvpTheme(appearance)`, qui fournit aussi la palette complète (`MvpColors.highlight` suivait le thème de l'appareil, il suit le thème choisi). Les icônes de la barre d'état suivent le thème appliqué (`enableEdgeToEdge` avec `SystemBarStyle.auto`) ; la barre de navigation garde son voile par défaut, lisible aussi sur Android 8 qui n'a pas d'icônes de navigation sombres.
- iOS : `AppearanceRoot` (FeatureSettings) impose le style aux fenêtres (`overrideUserInterfaceStyle`) plutôt que `.preferredColorScheme`, dont le retour à « thème de l'appareil » n'est pas fiable et qui ne couvre pas les écrans UIKit / Compose ; l'accent passe par l'environnement (`\.dsAccent`), lu par chaque vue avec `colorScheme`. Swift garde ses propres enums (`DSThemeMode`, `DSAccent`) avec les noms Kotlin en `rawValue`, l'export renommant les entrées des enums Kotlin.
- Compose Multiplatform : `TodoComposeScreen(appearance)`. Sur iOS, `ComposeScreens.todo` lui passe le flux de l'apparence ; le thème forcé lui arrive par le style de la fenêtre.

## Conséquences

- Les widgets (Android Glance, iOS WidgetKit) gardent l'accent bleu et suivent le thème de l'appareil : ils tournent hors de l'app et liraient l'apparence par le App Group / un stockage partagé, à ajouter si besoin.
- iOS : le style est imposé aux fenêtres ouvertes au démarrage et à chaque changement ; une nouvelle scène iPad l'obtient à l'apparition de sa racine.
- Au lancement, la première image peut être au thème de l'appareil le temps de lire la base (quelques millisecondes), et la fenêtre de démarrage Android suit toujours l'appareil.
- Ajouter un accent : une entrée dans `AccentColor`, ses trois couleurs dans `accentTokens`, un libellé dans chaque app ; le test de contraste le vérifie.

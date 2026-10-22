# ADR 0010 — Un écran en Compose Multiplatform, comparé au natif

- Statut : accepté (expérience ; les écrans natifs restent la référence)
- Date : 2026-10-22

## Contexte

Jusqu'ici, seule la logique est partagée : chaque écran existe deux fois, en Jetpack Compose (Android) et en SwiftUI (iOS), avec un ViewModel par plateforme. Compose Multiplatform (JetBrains) permet de partager aussi l'interface. Avant de l'adopter ou non pour les prochaines features, on veut le mesurer sur un vrai écran du prototype, en le comparant à l'écran SwiftUI existant.

Contraintes : Kotlin 2.4.21, AGP 8.13 et compileSdk 36 (PR #11 : les bibliothèques construites sur Compose 1.11 ne passent pas), une seule bibliothèque Kotlin `Shared` pour iOS, le design system `core:designsystem` comme seule source des valeurs visuelles.

## Options

1. **Ne rien partager côté UI** (situation actuelle).
2. **Un écran en Compose Multiplatform à côté du natif**, pour comparer sans rien casser.
3. **Basculer une feature entière en Compose Multiplatform** et retirer son écran natif.

## Décision

**Option 2**, sur l'écran **Tâches**, la feature de référence :

- **Module `feature:todo-compose`** : `TodoComposeScreen` (Material 3) et `TodoComposeViewModel` (ViewModel `androidx.lifecycle` multiplatform de JetBrains), écrits une fois dans `commonMain`. Il appelle les mêmes use cases que l'écran natif, pris dans le graphe Koin déjà démarré : mêmes données, mêmes règles (titre obligatoire, tri, mesure d'audience, effacement à la déconnexion).
- **Périmètre** : liste, ajout avec validation, case à cocher, suppression, date du rappel affichée, message « Activez les notifications ». Restent dans l'écran natif : le choix d'un rappel (sélecteurs date / heure système) et la mise en évidence par un lien profond. Le lien profond vers une tâche referme la version Compose.
- **Thème** : `SharedMvpTheme` lit `core:designsystem` comme `MvpTheme` (Android) et `DS` (iOS). Aucune valeur en dur.
- **Android** : bouton « Version Compose MP » sur l'écran Tâches ; le même composable s'affiche à la place de l'écran natif, retour par « Fermer » ou le bouton retour.
- **iOS** : `ComposeUIViewController` dans `feature:todo-compose` (iosMain), exposé à Swift par `ComposeScreens.todo(onClose:)` dans `shared`. Le package SPM `FeatureTodoCompose` l'enveloppe dans un `UIViewControllerRepresentable` (`TodoComposeView`), ouvert en feuille depuis le bouton « Version Compose MP » de l'écran SwiftUI, et seul dans l'app de démo `TodoComposeDemo`.
- **Versions** : Compose Multiplatform **1.9.3** (Material 3 1.9.0), qui s'appuie sur Jetpack Compose 1.9, la version que l'app Android utilise déjà (`compose-bom` 2025.09.00) : l'app Android ne change pas de Compose. Les versions 1.10 à 1.12 tirent Jetpack Compose 1.10 à 1.12, à éviter tant qu'AGP et compileSdk ne montent pas. `koin-compose` n'est pas utilisé : la 4.2.2 tire Compose Multiplatform 1.10. Pas de plugin Gradle `org.jetbrains.compose` : le compilateur est le plugin Kotlin `compose-compiler` déjà utilisé, les bibliothèques sont dans `libs.versions.toml`.
- **Mesure du poids** : la CI affiche la taille de l'APK debug, de l'app iOS debug et du binaire `Shared` release arm64 à chaque build.

## Comparaison

| Critère | SwiftUI natif (`FeatureTodo`) | Compose Multiplatform (`feature:todo-compose`) |
|---|---|---|
| Code de l'écran | Écrit deux fois : Compose Android (`TodoScreen` + `TodoViewModel`, 320 lignes) et SwiftUI (`TodoListView` + `TodoViewModel` + `TodoReminders`, 400 lignes), choix du rappel et liens profonds compris. | Écrit une fois : 330 lignes Kotlin (écran 130, ViewModel 90, thème 70, format de date par plateforme 25), plus 45 lignes Swift / Kotlin pour l'enveloppe iOS ; sans le choix du rappel ni les liens. Les règles d'état (messages d'erreur, consigne de rappels) ne sont écrites et testées qu'une fois, en `commonTest`, là où le natif les a en double, sans test côté Swift. |
| Rendu et ressenti iOS | Composants UIKit / SwiftUI : `List`, glissement pour supprimer, `TextField` système, police San Francisco, Dynamic Type, animations et retour haptique d'iOS. | Composants Material 3 dessinés par Compose (Skia sur Metal) : une app Android sur iPhone. Pas de glissement pour supprimer, cases à cocher Material, champ de texte Compose (sélection, loupe et menu copier / coller imités). Défilement et inertie proches d'iOS mais pas identiques. |
| Accessibilité | VoiceOver, Dynamic Type, Contrôle vocal, Contraste élevé : natifs, sans rien faire. | VoiceOver passe par la couche d'accessibilité de Compose (arbre sémantique traduit pour UIKit) : lecture et activation des éléments, mais à vérifier élément par élément sur appareil. La taille de texte suit le réglage iOS via la densité de police de Compose ; les rotors et le Contrôle vocal sont moins complets. |
| Poids | Rien de plus. | Le moteur de rendu Skia et les bibliothèques Compose s'ajoutent au binaire `Shared` de toutes les apps iOS, qu'elles affichent l'écran ou non. Mesures CI ci-dessous. Android : presque rien, Compose y est déjà. |
| Temps de build | Swift compilé par Xcode, incrémental. | Liaison Kotlin/Native plus longue (Compose + Skia) à chaque build iOS, release surtout. Mesures CI ci-dessous. |
| Intégration iOS | Tout l'écosystème SwiftUI (navigation, feuilles, `.searchable`, widgets, App Intents). | Un `UIViewController` opaque : navigation, barre d'outils et feuilles côté Swift ne voient pas l'intérieur ; passer des données ou des actions = paramètres Kotlin (`onClose`). Clé `CADisableMinimumFrameDurationOnPhone` obligatoire dans l'Info.plist (sinon l'écran s'arrête au lancement). Le clavier est géré par Compose (`ignoresSafeArea(.keyboard)` côté SwiftUI). |
| Outillage | Aperçus Xcode, Instruments, débogueur de vues. | Aperçus et inspecteur Compose sur Android seulement ; sur iOS, débogage du code Kotlin limité. |
| Design system | `DS` (pont SwiftUI). | `SharedMvpTheme`, mêmes tokens. Un troisième pont vers les tokens, qui doit suivre les deux autres. |

### Mesures (CI, macOS 15, Xcode 26)

| Mesure | Avant (main) | Avec l'écran Compose |
|---|---|---|
| APK debug Android | 16,3 Mo | À COMPLÉTER |
| `iosApp.app` debug simulateur | À COMPLÉTER | À COMPLÉTER |
| Binaire `Shared` release iPhone (arm64) | À COMPLÉTER | À COMPLÉTER |
| Job CI iOS | À COMPLÉTER | À COMPLÉTER |

## Conséquences

- Les deux apps ont l'écran Tâches en deux versions, natif par défaut : chacun peut comparer sur son appareil avec les mêmes données.
- Recommandation pour la suite : garder le natif pour les écrans centraux de l'app (le ressenti iOS et l'accessibilité y comptent le plus), et réserver Compose Multiplatform aux écrans internes ou secondaires (debug, réglages avancés, formulaires) si le gain de code l'emporte sur le poids mesuré. Décision à prendre après l'essai sur appareil.
- `shared` dépend désormais de Compose Multiplatform : tout futur module UI partagé suit `feature:todo-compose` (composables `internal` ou non exportés vers Swift, un point d'entrée `UIViewController` dans `ComposeScreens`).
- Monter Compose Multiplatform au-delà de 1.9 attend la montée d'AGP / compileSdk, comme Coil.
- Si l'expérience s'arrête : retirer le module, `ComposeScreens`, le package `FeatureTodoCompose`, sa démo et les deux boutons ; aucune donnée n'est concernée.

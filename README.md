# MVP Modular

Prototype mobile **iOS + Android** avec **Kotlin Multiplatform**.
Toute la logique est en KMP ; les apps natives ne gèrent que l'UI.

## Architecture

```
androidApp (Jetpack Compose)      iosApp (SwiftUI)
        │  ViewModel + écrans            │  ObservableObject + vues
        └──────────────┬─────────────────┘
                       ▼
                    shared            ← module parapluie KMP
                       │                (framework iOS "Shared", initKoin, coordination)
                       │
                       ▼
                  feature:todo        ← premier module feature (modèle à copier)
                  ├─ domain : Todo, TodoRepository, use cases
                  ├─ data   : TodoRepositoryImpl, TodoLocalDataSource (mémoire)
                  └─ di     : todoModule
                       │
            ┌──────────┴───────────┐
            ▼                      ▼
     core:designsystem        core:common     ← clean archi
     (tokens visuels :        ├─ domain : entités, interfaces de repository, use cases
      couleurs, typo,         ├─ data   : implémentations de repository, data sources
      espacements, rayons)    └─ di     : module Koin
```

Règles de dépendance :
- `domain` ne dépend de rien d'autre (pas de data, pas de plateforme).
- `data` implémente les interfaces du `domain`.
- Les apps ne parlent qu'aux **use cases** exposés par `shared`.
- `shared` est le seul module connu des apps : il assemble tous les modules KMP (`sharedModules` dans `KoinInit.kt`) et les exporte vers iOS.

## Briques fournies par `core:common`

| Élément | Rôle |
|---|---|
| `AppResult` / `AppError` | Résultat typé succès / échec, sans exceptions jusqu'à l'UI |
| `UseCase` / `FlowUseCase` | Base des cas d'usage (one-shot ou flux), avec dispatcher injecté |
| `safeCall` | Convertit les exceptions techniques en `AppError` dans les repositories |
| `AppDispatchers` | Dispatchers injectables (remplaçables en test) |

## Design system (`core:designsystem`)

Source unique des valeurs visuelles, partagée par les deux plateformes : couleurs claires/sombres (ARGB), typographie, espacements et rayons, via l'objet `DesignSystem`.
- **Android** : `MvpTheme` (`androidApp/.../ui/theme/MvpTheme.kt`) construit le `MaterialTheme` à partir des tokens ; `Spacing.md` etc. pour les dimensions.
- **iOS** : `DS` (`iosApp/iosApp/DesignSystem+SwiftUI.swift`) expose `DS.colors(scheme)`, `DS.Spacing`, `DS.Typography`.

Changer une couleur ou une taille dans le module KMP met à jour les deux apps.

## Feature `todo`

Module de référence pour les prochaines features : ajouter, cocher et supprimer des tâches.
- **Use cases** : `ObserveTodosUseCase` (Flow, tâches terminées en dernier), `AddTodoUseCase` (titre obligatoire, 80 caractères max), `ToggleTodoUseCase`, `DeleteTodoUseCase`.
- **Data** : stockage en mémoire derrière `TodoLocalDataSource`, remplaçable par SQLDelight ou Room KMP sans toucher au domaine.
- **Android** : `ui/todo/TodoViewModel.kt` + `TodoScreen.kt`.
- **iOS** : `TodoViewModel.swift` + `TodoListView.swift`. Les Flow sont exposés à Swift par callbacks via `UseCases.observeTodos(...)`, qui renvoie une `Subscription` à annuler.

## Ajouter un module

1. Créer `feature/xxx/build.gradle.kts` :
   ```kotlin
   plugins { alias(libs.plugins.mvp.kmp.library) }
   kotlin { sourceSets { commonMain.dependencies { api(projects.core.common) } } }
   android { namespace = "com.mvp.feature.xxx" }
   ```
2. `include(":feature:xxx")` dans `settings.gradle.kts`.
3. Dans `shared` : `api(projects.feature.xxx)`, `export(projects.feature.xxx)` et ajouter son module Koin à `sharedModules`.
4. Pour iOS, exposer ses use cases dans `UseCases` (`shared/src/iosMain/.../KoinIos.kt`), avec un helper à callbacks pour chaque Flow.

Le plus simple est de copier `feature/todo` et de renommer.

Le plugin de convention `mvp.kmp.library` (`build-logic/`) configure les cibles Android/iOS, le SDK et les dépendances de test ; toutes les versions sont dans `gradle/libs.versions.toml`.

## Lancer

- **Tests KMP** : `./gradlew allTests`
- **Android** : ouvrir le dossier dans Android Studio, lancer `androidApp`.
- **iOS** : `brew install xcodegen && cd iosApp && xcodegen && open iosApp.xcodeproj`. Le build Xcode compile le framework Kotlin via `:shared:embedAndSignAppleFrameworkForXcode`.

## Environnement cloud (Claude Code)

Pour que Claude puisse compiler KMP et Android dans ses sessions cloud, l'environnement doit :
1. **Réseau** : autoriser `dl.google.com` et `maven.google.com` (SDK Android et plugin Android Gradle), en plus des gestionnaires de paquets par défaut.
2. **Variable d'environnement** : `ANDROID_HOME=/opt/android-sdk`.
3. **Setup script** : installer le SDK Android avec le contenu de `scripts/install-android-sdk.sh`.

Au démarrage de chaque session, le hook `scripts/session-start.sh` (déclaré dans `.claude/settings.json`) écrit `local.properties` et précharge les dépendances Gradle. En local, il ne fait rien.

Vérification : `./gradlew allTests :androidApp:assembleDebug`.

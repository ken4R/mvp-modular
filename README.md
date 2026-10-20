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
         feature:todo        feature:weather
         (modèle à copier)   (réseau : Open-Meteo)
         ├─ domain : entités, interfaces de repository, use cases
         ├─ data   : repository, data sources (mémoire / HTTP)
         └─ di     : module Koin
                       │
            ┌──────────┼─────────────────┐
            ▼          ▼                 ▼
 core:designsystem  core:common     core:network  ← client HTTP Ktor partagé
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

`core:network` ajoute le réseau ([ADR 0001](docs/adr/0001-reseau-ktor.md)) : `createHttpClient()` (Ktor, JSON, délais, moteur OkHttp / Darwin), `safeApiCall` (exceptions Ktor → `AppError`) et `networkModule` (un `HttpClient` unique). Les features l'utilisent dans leur couche `data` uniquement.

## Design system (`core:designsystem`)

Source unique des valeurs visuelles, partagée par les deux plateformes : couleurs claires/sombres (ARGB), typographie, espacements et rayons, via l'objet `DesignSystem`.
- **Android** : `MvpTheme` (`androidApp/.../ui/theme/MvpTheme.kt`) construit le `MaterialTheme` à partir des tokens ; `Spacing.md` etc. pour les dimensions.
- **iOS** : `DS` (package SPM `iosApp/Packages/DesignSystemUI`) expose `DS.colors(scheme)`, `DS.Spacing`, `DS.Typography`.

Changer une couleur ou une taille dans le module KMP met à jour les deux apps.

## Feature `todo`

Module de référence pour les prochaines features : ajouter, cocher et supprimer des tâches.
- **Use cases** : `ObserveTodosUseCase` (Flow, tâches terminées en dernier), `AddTodoUseCase` (titre obligatoire, 80 caractères max), `ToggleTodoUseCase`, `DeleteTodoUseCase`.
- **Data** : stockage en mémoire derrière `TodoLocalDataSource`, remplaçable par SQLDelight ou Room KMP sans toucher au domaine.
- **Android** : `ui/todo/TodoViewModel.kt` + `TodoScreen.kt`.
- **iOS** : package SPM `iosApp/Packages/FeatureTodo` (`TodoViewModel.swift` + `TodoListView.swift`) et son app de démo `TodoDemo`. Les Flow sont exposés à Swift par callbacks via `UseCases.observeTodos(...)`, qui renvoie une `Subscription` à annuler.

## Feature `weather`

Météo actuelle d'une ville, via l'API gratuite [Open-Meteo](https://open-meteo.com) (sans clé). Premier appel réseau du projet.
- **Use cases** : `SearchCitiesUseCase` (2 caractères minimum, sans appel réseau sinon), `GetCurrentWeatherUseCase` (température, ressenti, humidité, vent, condition).
- **Data** : `OpenMeteoRemoteDataSource` (géocodage + prévisions) derrière `WeatherRemoteDataSource` ; les codes météo WMO sont traduits en `WeatherCondition`. Tests avec un MockEngine Ktor.
- **iOS** : package SPM `iosApp/Packages/FeatureWeather` (`WeatherViewModel.swift` + `WeatherView.swift`), onglet « Météo » de l'app complète, et app de démo `WeatherDemo` ouverte sur « Paris ».
- **Android** : pas encore d'écran.

## iOS : une feature = un package SPM

```
iosApp/
├─ project.yml              ← XcodeGen : app complète + une app de démo par feature
├─ iosApp/                  ← app complète (iOSApp.swift) : assemble les features
└─ Packages/
   ├─ SharedKMP/            ← XCFramework Kotlin "Shared" (généré, non versionné)
   ├─ DesignSystemUI/       ← pont SwiftUI vers core:designsystem (DS, Color(argb:))
   ├─ FeatureTodo/
   │  ├─ Sources/FeatureTodo/   ← vues + ViewModel, seule la vue d'entrée est public
   │  └─ Demo/TodoDemoApp.swift ← app de démo : la feature seule, avec données d'exemple
   └─ FeatureWeather/           ← même structure, démo WeatherDemo
```

- Une feature iOS dépend uniquement de `SharedKMP` (la logique) et de `DesignSystemUI` (le visuel), jamais d'une autre feature.
- La démo est optionnelle : c'est une cible app de `project.yml` dont les sources sont `Packages/<Feature>/Demo`. Elle sert à développer et tester la feature sans passer par l'app complète.
- Le framework Kotlin est dynamique : SPM l'embarque une seule fois, même si plusieurs packages en dépendent.

Ajouter une feature iOS `Xxx` :
1. Copier `iosApp/Packages/FeatureTodo` en `FeatureXxx` et renommer le package, la cible et les fichiers.
2. Dans `project.yml` : déclarer le package sous `packages`, l'ajouter aux dépendances de `iosApp` et, si besoin, créer la cible `XxxDemo` sur le modèle de `TodoDemo`.
3. Ajouter le scheme `XxxDemo` à la boucle de build iOS de `.github/workflows/ci.yml`.

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
- **iOS** : `scripts/build-ios-framework.sh && cd iosApp && xcodegen && open iosApp.xcodeproj`, puis choisir le scheme `iosApp` (app complète, onglets Tâches et Météo) ou une démo de feature (`TodoDemo`, `WeatherDemo`). Le script doit tourner une première fois avant d'ouvrir Xcode, car les packages SPM ont besoin du XCFramework Kotlin ; ensuite la cible `KotlinShared` le relance à chaque build.

## Environnement cloud (Claude Code)

Pour que Claude puisse compiler KMP et Android dans ses sessions cloud, l'environnement doit :
1. **Réseau** : autoriser `dl.google.com` et `maven.google.com` (SDK Android et plugin Android Gradle), en plus des gestionnaires de paquets par défaut.
2. **Variable d'environnement** : `ANDROID_HOME=/opt/android-sdk`.
3. **Setup script** : installer le SDK Android avec le contenu de `scripts/install-android-sdk.sh`.

Au démarrage de chaque session, le hook `scripts/session-start.sh` (déclaré dans `.claude/settings.json`) écrit `local.properties` et précharge les dépendances Gradle. En local, il ne fait rien.

Vérification : `./gradlew allTests :androidApp:assembleDebug`.

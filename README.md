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
         feature:todo        feature:weather          feature:settings   feature:auth   feature:catalog
         (modèle à copier)   (réseau : Open-Meteo)                       (connexion, session)
         feature:todo-compose ← écran Tâches en Compose Multiplatform (UI partagée, ADR 0010)
         feature:onboarding   ← présentation du premier lancement (ADR 0016)
         ├─ domain : entités, interfaces de repository, use cases
         ├─ data   : repository, data sources (SQLite / HTTP)
         └─ di     : module Koin
                       │
            ┌──────────┼─────────────────┐
            ▼          ▼                 ▼
 core:designsystem  core:common     core:network  ← client HTTP Ktor partagé
                                    core:database ← SQLite (SQLDelight), une base par feature
                                    core:securestorage ← Keystore / Keychain (secrets, profil)
                                    core:notifications ← notifications locales programmées
                                    core:location ← position de l'appareil (approximative)
                                    core:deeplink ← liens profonds mvpmodular://…
                                    core:analytics ← mesure d'audience avec consentement
                                    core:connectivity ← accès à internet (mode hors ligne)
                                    core:files ← fichiers privés hors sauvegardes (pièces jointes)
     (tokens visuels :        ├─ domain : entités, interfaces de repository, use cases
      couleurs, typo,         ├─ data   : implémentations de repository, data sources
      espacements, rayons)    └─ di     : module Koin
```

Règles de dépendance :
- `domain` ne dépend de rien d'autre (pas de data, pas de plateforme). Seule exception : les types de valeurs purs de `core:designsystem` (`ThemeMode`, `AccentColor`, `Appearance`), stockés par `feature:settings` ([ADR 0015](docs/adr/0015-theme-apparence.md)).
- `data` implémente les interfaces du `domain`.
- Les apps ne parlent qu'aux **use cases** exposés par `shared`.
- Exception : un module UI Compose Multiplatform (`feature:todo-compose`, [ADR 0010](docs/adr/0010-ecran-compose-multiplatform.md)) dépend de sa feature et son écran est affiché par l'app Android via `shared` ; il ne contient que de l'UI et un ViewModel.
- `shared` est le seul module connu des apps : il assemble tous les modules KMP (`sharedModules` dans `KoinInit.kt`) et les exporte vers iOS.

## Briques fournies par `core:common`

| Élément | Rôle |
|---|---|
| `AppResult` / `AppError` | Résultat typé succès / échec, sans exceptions jusqu'à l'UI |
| `UseCase` / `FlowUseCase` | Base des cas d'usage (one-shot ou flux), avec dispatcher injecté |
| `safeCall` | Convertit les exceptions techniques en `AppError` dans les repositories |
| `AppDispatchers` | Dispatchers injectables (remplaçables en test) |

`core:database` ajoute la persistance locale ([ADR 0002](docs/adr/0002-persistance-sqldelight.md)) : `DatabaseDriverFactory` ouvre une base SQLDelight par feature (driver Android / iOS), hors sauvegardes et avec `secure_delete` ([ADR 0013](docs/adr/0013-durcissement-distribution.md)) et `databaseModule` la fournit via Koin. `core:database-testing` donne une base en mémoire aux tests `commonTest`.

`core:securestorage` ajoute le stockage chiffré ([ADR 0003](docs/adr/0003-connexion-stockage-chiffre.md)) : `SecureStorage` (clé / valeur) chiffré par l'Android Keystore (AES-GCM) ou rangé dans le Keychain iOS, pour les secrets et les données personnelles (session, profil).

`core:notifications` ajoute les notifications locales ([ADR 0004](docs/adr/0004-rappels-notifications-locales.md)) : `LocalNotificationScheduler` programme ou annule les notifications d'un groupe (WorkManager sur Android, `UNUserNotificationCenter` sur iOS). Le réglage Notifications est partagé par `NotificationsPreference` (`core:common`), fourni par `feature:settings`.

`core:location` ajoute la position de l'appareil ([ADR 0005](docs/adr/0005-localisation-meteo.md)) : `LocationProvider` (contrat dans `core:common`) donne une position approximative, via `LocationManager` sur Android (sans Google Play Services) et CoreLocation sur iOS. Il échoue avec `AppError.PermissionDenied` si l'utilisateur refuse la localisation, et `AppError.Unavailable` si aucune position n'est obtenue.

`core:deeplink` ajoute les liens profonds ([ADR 0008](docs/adr/0008-liens-profonds.md), [plan](docs/plans/liens-profonds.md)) : `DeepLinks` lit et écrit les liens `mvpmodular://tasks[/<id>]`, `weather`, `catalog[/products/<id>]` et `settings` en une destination typée `DeepLink`. `OpenDeepLinkUseCase` met le lien reçu en attente (un lien non reconnu mène à l'onglet Tâches), `ObservePendingDeepLinkUseCase` le donne à la navigation de l'app, qui l'affiche une fois connectée et déverrouillée puis appelle `ConsumeDeepLinkUseCase`. Le lien en attente est oublié à la déconnexion (`UserSessionObserver.onLogout`), pas à la connexion. Un tap sur un rappel ouvre la tâche par ce même chemin.

`core:analytics` ajoute la mesure d'audience avec consentement ([ADR 0009](docs/adr/0009-analytics-consentement.md), [plan](docs/plans/analytics-consentement.md)) : les features et les apps passent par `AnalyticsTracker` (contrat dans `core:common`, événements fermés `AnalyticsEvent` sans donnée personnelle) ou `TrackEventUseCase`. Rien n'est gardé ni envoyé sans le consentement « Mesure d'audience » de Réglages (`AnalyticsConsent`, fourni par `feature:settings`), relu à chaque événement et avant chaque envoi. La file d'attente est en mémoire, envoyée par lots à un `AnalyticsSink` (journal local de debug pour l'instant : brancher un vrai outil = une nouvelle implémentation). Retirer le consentement la vide tout de suite (`AnalyticsConsentObserver`), la déconnexion efface tout (`UserSessionObserver`). Chaque choix de consentement est daté et lié à la version du texte (`ConsentTexts`).

`core:connectivity` dit si l'appareil a accès à internet ([ADR 0012](docs/adr/0012-mode-hors-ligne.md)) : `ConnectivityObserver` (contrat dans `core:common`), `ConnectivityManager` sur Android (réseau validé, permission `ACCESS_NETWORK_STATE`), `NWPathMonitor` sur iOS. `ObserveConnectivityUseCase` alimente le bandeau « Hors ligne » des écrans Météo et Catalogue (le Catalogue se réactualise au retour du réseau).

`core:files` range les fichiers privés d'une feature ([ADR 0014](docs/adr/0014-notes-pieces-jointes.md)) : `PrivateFileStoreFactory.create("<dossier>")` donne un `PrivateFileStore` (écrire, lire, chemin, supprimer, tout supprimer). Android : dossier `no_backup` de l'app ; iOS : `Application Support`, exclu des sauvegardes, fichiers protégés par Data Protection complète. Les noms sont des identifiants choisis par l'app (`[A-Za-z0-9_-]`), jamais un nom donné par l'utilisateur.

`core:network` ajoute le réseau ([ADR 0001](docs/adr/0001-reseau-ktor.md)) : `createHttpClient()` (Ktor, JSON, délais, moteur Android / Darwin), `safeApiCall` (exceptions Ktor → `AppError`) et `networkModule` (un `HttpClient` unique). Les features l'utilisent dans leur couche `data` uniquement.

## Design system (`core:designsystem`)

Source unique des valeurs visuelles, partagée par les deux plateformes : couleurs claires/sombres (ARGB), typographie, espacements et rayons, via l'objet `DesignSystem`.
- **Android** : `MvpTheme` (`androidApp/.../ui/theme/MvpTheme.kt`) construit le `MaterialTheme` à partir des tokens ; `Spacing.md` etc. pour les dimensions.
- **iOS** : `DS` (package SPM `iosApp/Packages/DesignSystemUI`) expose `DS.colors(colorScheme, accent)` (accent lu dans `@Environment(\.dsAccent)`), `DS.Spacing`, `DS.Typography`.

Changer une couleur ou une taille dans le module KMP met à jour les deux apps.

Thème et accent ([ADR 0015](docs/adr/0015-theme-apparence.md)) : `ThemeMode` (appareil / clair / sombre) et `AccentColor` (5 accents) choisis dans Réglages, enregistrés par `feature:settings` (`ObserveAppearanceUseCase`) et appliqués à la racine des apps : `MvpTheme(appearance)` sur Android, `AppearanceRoot` + `@Environment(\.dsAccent)` sur iOS (`DS.colors(colorScheme, accent)`), `TodoComposeScreen(appearance)` pour l'écran Compose. L'accent ne change que `primary`, `onPrimary` et `highlight`.

## Feature `todo`

Module de référence pour les prochaines features : ajouter, cocher et supprimer des tâches.
- **Use cases** : `ObserveTodosUseCase` (Flow, tâches terminées en dernier), `AddTodoUseCase` (titre obligatoire, 80 caractères max), `ToggleTodoUseCase`, `DeleteTodoUseCase`.
- **Rappels** ([plan](docs/plans/rappels-taches.md), [ADR 0004](docs/adr/0004-rappels-notifications-locales.md)) : `SetTodoReminderUseCase` (date à venir, `null` pour retirer), `ObserveRemindersEnabledUseCase`, et `SyncTodoRemindersUseCase`, lancé une fois par chaque app, qui programme une notification pour chaque tâche non terminée dont le rappel est à venir, seulement si le réglage Notifications est activé et avant la fin de la session (`SessionExpiry`, fourni par `feature:auth`). La déconnexion ou l'expiration de la session annule tous les rappels.
- **Widget de l'écran d'accueil** ([plan](docs/plans/widgets.md), [ADR 0011](docs/adr/0011-widgets-ecran-accueil.md)) : `ObserveTodoWidgetUseCase` donne ce que le widget peut montrer : rien sans session valide (`SignedOut`) ni avec le déverrouillage Face ID / empreinte activé (`Locked`, via `AppLockPreference` fourni par `feature:settings`), sinon les tâches non terminées, rappel le plus proche d'abord. Android : widget Glance `widget/TodoWidget.kt`, redessiné par `TodoWidgetUpdater`. iOS : package `FeatureTodoWidget` (l'app recopie l'état dans un fichier de l'App Group) et extension `TodoWidgetExtension`, qui ne charge pas le framework Kotlin.
- **Mode hors ligne** ([plan](docs/plans/mode-hors-ligne.md), [ADR 0012](docs/adr/0012-mode-hors-ligne.md)) : chaque modification (titre, état, suppression ; pas le rappel, propre à l'appareil) entre dans une file d'envoi SQLite dans la même transaction. `StartTodoSyncUseCase`, lancé une fois par chaque app, envoie la file puis récupère le serveur dès que l'appareil est en ligne avec une session, à chaque modification, et réessaie après 2 s, 4 s… jusqu'à une minute. Conflits : la modification la plus récente gagne, tâche par tâche, et une suppression n'est pas annulée par une version plus ancienne. `ObserveTodoSyncStatusUseCase` donne le bandeau (« Hors ligne · N modifications en attente », « Synchronisation impossible » + `SyncTodosUseCase` pour « Réessayer »). Pas encore de backend : `FakeTodoRemoteDataSource`, en mémoire, derrière `TodoRemoteDataSource`. La déconnexion attend la synchronisation en cours puis efface aussi la file.
- **Notes et pièces jointes** ([plan](docs/plans/notes-pieces-jointes.md), [ADR 0014](docs/adr/0014-notes-pieces-jointes.md)) : `UpdateTodoNoteUseCase` (2 000 caractères max, synchronisée avec la tâche), `ObserveTodoAttachmentsUseCase`, `AddTodoAttachmentUseCase` (photo JPEG, PDF ou texte UTF-8, contenu vérifié, 10 Mo et 10 pièces jointes max par tâche, nom affiché nettoyé), `DeleteTodoAttachmentUseCase`. Les pièces jointes restent sur l'appareil (`core:files`, dossier `todo-attachments`) et partent avec leur tâche ou à la déconnexion. Les apps réenregistrent les photos en JPEG (2 048 px, sans métadonnées) et les affichent elles-mêmes (photo, PDF, texte), sans les confier à une autre app. Android : `TodoDetailScreen` (tap sur le titre) ; iOS : `TodoDetailView` (bouton ⓘ).
- **Recherche et filtres** ([plan](docs/plans/recherche-filtres.md)) : `SearchText` (core:common, sans casse ni accents), `ObserveFilteredTodosUseCase` (`TodoFilter` : titre et note, À faire / Terminées, avec rappel, avec pièce jointe) et `ObserveFilteredCatalogUseCase` (`CatalogFilter` : nom et marque, 4★ et plus, tri par prix ou note, sur les pages déjà chargées). Filtrage sur l'appareil, hors ligne compris.
- **Partage** ([plan](docs/plans/partage-tache.md), [ADR 0017](docs/adr/0017-partage-tache.md)) : `ShareTodoUseCase` donne le texte confié à la feuille de partage du système (titre, état, lien `mvpmodular://tasks/<id>`, la note seulement si l'utilisateur la coche, jamais de pièce jointe). Android : « Partager » dans `TodoDetailScreen` (`ACTION_SEND`) ; iOS : `ShareLink` dans `TodoDetailView`. Le lien ouvre la tâche après la connexion et le verrou.
- **Ajout à la voix** ([plan](docs/plans/ajout-vocal.md), [ADR 0018](docs/adr/0018-ajout-vocal.md)) : Siri (`AddTodoIntent`, `MvpAppShortcuts`) et Google Assistant (App Action `CREATE_THING`, raccourci « Nouvelle tâche » dans `res/xml/shortcuts.xml`) ouvrent l'app avec le titre dicté dans « Nouvelle tâche » (`StartTodoDraftUseCase`), après la connexion et le verrou. Rien n'est ajouté sans le bouton « Ajouter ».
- **Data** : tâches stockées en SQLite (SQLDelight, `todo.db`, migrations `1.sqm` pour la colonne `reminder_at`, `2.sqm` pour la synchronisation, `3.sqm` pour les notes et pièces jointes) derrière `TodoLocalDataSource` ; elles survivent au redémarrage. Une implémentation en mémoire sert aux tests des use cases.
- **Android** : `ui/todo/TodoViewModel.kt` + `TodoScreen.kt` (bouton « Rappel » : date puis heure) ; `MvpApplication` lance la synchronisation des rappels.
- **iOS** : package SPM `iosApp/Packages/FeatureTodo` (`TodoViewModel.swift` + `TodoListView.swift`, cloche et feuille de rappel ; `TodoReminders.start()` à appeler au lancement) et son app de démo `TodoDemo`. Les Flow sont exposés à Swift par callbacks via `UseCases.observeTodos(...)`, qui renvoie une `Subscription` à annuler.

## Feature `weather`

Météo actuelle d'une ville, via l'API gratuite [Open-Meteo](https://open-meteo.com) (sans clé). Premier appel réseau du projet.
- **Use cases** : `SearchCitiesUseCase` (2 caractères minimum, sans appel réseau sinon), `GetCurrentWeatherUseCase` (température, ressenti, humidité, vent, condition), `GetLocalWeatherUseCase` (météo à la position de l'appareil, arrondie à 2 décimales, soit environ 1 km, avant l'appel ; rien n'est enregistré). [Plan](docs/plans/meteo-position.md).
- **Data** : `OpenMeteoRemoteDataSource` (géocodage + prévisions) derrière `WeatherRemoteDataSource` ; les codes météo WMO sont traduits en `WeatherCondition`. Tests avec un MockEngine Ktor.
- **iOS** : package SPM `iosApp/Packages/FeatureWeather` (`WeatherViewModel.swift` + `WeatherView.swift`, bouton « Météo à ma position »), onglet « Météo » de l'app complète, et app de démo `WeatherDemo` ouverte sur « Paris ». La permission « Lorsque l'app est active » est demandée par la logique KMP ; son texte est `NSLocationWhenInUseUsageDescription` dans `iosApp/project.yml`.
- **Android** : `ui/weather/WeatherViewModel.kt` + `WeatherScreen.kt`, onglet « Météo » entre « Tâches » et « Réglages ». « Météo à ma position » demande la permission de position approximative (`ACCESS_COARSE_LOCATION`) ; après un refus, un bouton ouvre la page de l'app dans les paramètres.

## Feature `settings`

Page Réglages : profil, notifications, consentements et version de l'app.
- **Use cases** : `ObserveSettingsUseCase` (Flow), `UpdateProfileUseCase` (nom obligatoire, 50 caractères max ; e-mail facultatif mais valide), `SetNotificationsEnabledUseCase`, `SetConsentUseCase` (mesure d'audience, marketing, personnalisation).
- **Règles** : au premier lancement, notifications coupées et aucun consentement accordé (opt-in RGPD). Activer les notifications demande d'abord la permission système (iOS, Android 13+) ; si elle est refusée, le réglage reste coupé. Si elle est retirée dans les paramètres système, le réglage se recoupe au retour dans l'app.
- **Data** : réglages stockés en SQLite (SQLDelight, `settings.db`, table clé / valeur) derrière `SettingsLocalDataSource` ; ils survivent au redémarrage. Le profil (nom, e-mail), donnée personnelle, est dans le stockage chiffré (`SecureProfileLocalDataSource`), pré-rempli à la connexion et effacé à la déconnexion.
- **Android** : `ui/settings/SettingsViewModel.kt` + `SettingsScreen.kt`, onglet « Réglages ».
- **iOS** : package SPM `iosApp/Packages/FeatureSettings` (`SettingsViewModel.swift` + `SettingsView.swift`), onglet « Réglages » de l'app complète, et app de démo `SettingsDemo`. Le Flow est exposé par `UseCases.observeSettings(...)`.
- **Verrou Face ID / empreinte** ([plan](docs/plans/deverrouillage-biometrique.md), [ADR 0007](docs/adr/0007-deverrouillage-biometrique.md)) : réglage « Sécurité », coupé par défaut, effacé à la déconnexion. `SetAppLockEnabledUseCase`, `ObserveAppLockUseCase`, `LockAppUseCase` (passage en arrière-plan), `UnlockAppUseCase` (après une authentification système réussie). Une fois activé, l'app demande Face ID / Touch ID / empreinte, avec repli sur le code de l'appareil, à chaque lancement et à chaque retour au premier plan ; « Se déconnecter » reste possible depuis l'écran verrouillé. Android : `ui/applock/` (BiometricPrompt système, sans androidx.biometric), écran masqué dans les apps récentes. iOS : `AppLockGate` (package `FeatureSettings`), LocalAuthentication.

## Feature `auth`

Connexion devant l'app : sans session, seul l'écran de connexion est accessible ([ADR 0003](docs/adr/0003-connexion-stockage-chiffre.md)).
- **Use cases** : `LoginUseCase` (e-mail valide, mot de passe obligatoire), `LogoutUseCase`, `ObserveSessionUseCase` (Flow du compte connecté, `null` si déconnecté ou session expirée).
- **Data** : pas encore de backend, `FakeAuthRemoteDataSource` accepte n'importe quel e-mail avec le mot de passe `demo1234` (session de 7 jours), **dans les builds debug seulement** : un build release refuse toute connexion (`UnavailableAuthRemoteDataSource`, [ADR 0013](docs/adr/0013-durcissement-distribution.md)). La session est gardée dans `core:securestorage` ; le jeton ne sort jamais de la feature.
- **Lien avec les autres features** : sans dépendance entre elles, via `UserSessionObserver` (`core:common`). À la connexion, le profil des Réglages est pré-rempli avec le compte ; à la déconnexion, les tâches (et leurs rappels), les réglages et le profil sont effacés, puis la session. Toute feature qui stocke des données de l'utilisateur doit déclarer le sien.
- **Android** : `ui/auth/SessionViewModel.kt` (écran racine), `LoginViewModel.kt` + `LoginScreen.kt` ; déconnexion dans la section « Compte » des Réglages.
- **iOS** : package SPM `iosApp/Packages/FeatureAuth` : `SessionGate { ... }` enveloppe les onglets de l'app, `LoginView` ; app de démo `AuthDemo`. Le Flow est exposé par `UseCases.observeSession(...)`.

## Feature `catalog`

Catalogue de produits : liste paginée avec images, fiche détail, consultable hors ligne. Données de démonstration de [DummyJSON](https://dummyjson.com) (sans clé). [Plan](docs/plans/catalogue.md), [ADR 0006](docs/adr/0006-catalogue-cache-images.md).
- **Use cases** : `ObserveCatalogUseCase` (le cache, en Flow), `RefreshCatalogUseCase` (première page, remplace le cache), `LoadMoreProductsUseCase` (20 produits de plus, aucun appel quand tout est chargé), `GetProductUseCase` (le cache d'abord, le serveur sinon).
- **Data** : `DummyJsonRemoteDataSource` et cache SQLDelight `catalog.db` (produits, images, total, date du dernier chargement). Seules les images en HTTPS sont gardées. Catalogue public : pas de `UserSessionObserver`, le cache survit à la déconnexion.
- **iOS** : package SPM `iosApp/Packages/FeatureCatalog` (`CatalogView` dans une `NavigationStack`, `ProductDetailView`, `ImageLoader` avec cache disque), onglet « Catalogue », app de démo `CatalogDemo`.
- **Android** : `ui/catalog/CatalogViewModel.kt` + `CatalogScreen.kt`, onglet « Catalogue » entre « Météo » et « Réglages » ; images avec Coil 3 (module réseau Ktor).

## Feature `onboarding`

Présentation du premier lancement ([plan](docs/plans/onboarding.md), [ADR 0016](docs/adr/0016-presentation-premier-lancement.md)) : accueil, puis notifications, position, verrou Face ID / empreinte (si l'appareil le permet) et mesure d'audience, chacun expliqué avant la demande du système. Tout se remet à plus tard, « Passer » termine tout de suite.

- **Domain** : `OnboardingStep`, `OnboardingTexts` (communs aux deux apps), `ObserveOnboardingRequiredUseCase`, `CompleteOnboardingUseCase`, `RestartOnboardingUseCase` (Réglages > « Revoir la présentation »), `GetOnboardingStepsUseCase`.
- **Data** : base `onboarding.db` (version vue), sans `UserSessionObserver` : vue une fois par appareil, gardée à la déconnexion. Les choix passent par les use cases de `feature:settings` (notifications, verrou, consentement).
- **Android** : `ui/onboarding/` (`OnboardingScreen` + `OnboardingViewModel`), affiché par `MainActivity` après la connexion et le verrou, avant les onglets.
- **iOS** : package SPM `iosApp/Packages/FeatureOnboarding` (`OnboardingGate { ... }` autour des onglets, dans `AppLockGate`), app de démo `OnboardingDemo`.

## Écran en Compose Multiplatform (`feature:todo-compose`)

Expérience de comparaison ([ADR 0010](docs/adr/0010-ecran-compose-multiplatform.md), [plan](docs/plans/ecran-compose-multiplatform.md)) : l'écran Tâches écrit **une seule fois** en Compose Multiplatform, UI comprise, à côté des écrans natifs qui restent la référence.
- **Module** : `TodoComposeScreen` (Material 3), `TodoComposeViewModel` (ViewModel `androidx.lifecycle` multiplatform), thème `SharedMvpTheme` construit depuis `core:designsystem`. Mêmes use cases que l'écran natif (`feature:todo`), donc mêmes données. Liste, ajout, case à cocher, suppression, rappel affiché ; le choix d'un rappel et les liens profonds restent dans l'écran natif.
- **Android** : bouton « Version Compose MP » de l'écran Tâches ; retour par « Fermer » ou le bouton retour.
- **iOS** : même bouton dans l'écran SwiftUI ; la version Compose prend sa place dans l'onglet (pas de feuille : elle passerait au-dessus du verrou et du cache d'aperçu), retour par « Fermer ». Package SPM `iosApp/Packages/FeatureTodoCompose` (`TodoComposeView`, enveloppe `UIViewControllerRepresentable` de `ComposeScreens.todo(...)`) et app de démo `TodoComposeDemo`, à comparer à `TodoDemo`. Compose Multiplatform exige `CADisableMinimumFrameDurationOnPhone` dans l'Info.plist (`project.yml`).
- **Version** : Compose Multiplatform 1.9.3, sur Jetpack Compose 1.9 comme le reste de l'app Android, sans le plugin Gradle `org.jetbrains.compose`.

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
   ├─ FeatureWeather/           ← même structure, démo WeatherDemo
   ├─ FeatureSettings/          ← même structure, démo SettingsDemo
   ├─ FeatureAuth/              ← écran de connexion + SessionGate, démo AuthDemo
   ├─ FeatureCatalog/           ← liste paginée + fiche + cache d'images, démo CatalogDemo
   ├─ FeatureOnboarding/        ← présentation du premier lancement + OnboardingGate, démo OnboardingDemo
   ├─ FeatureTodoCompose/       ← écran Tâches en Compose Multiplatform (UIViewController), démo TodoComposeDemo
   └─ FeatureTodoWidget/        ← widget Tâches : recopie pour l'extension (Sources/) + extension WidgetKit (Widget/)
```

- Une feature iOS dépend uniquement de `SharedKMP` (la logique) et de `DesignSystemUI` (le visuel), jamais d'une autre feature. Exception : l'extension WidgetKit ne lie que `TodoWidgetData`, sans dépendance, car elle ne charge pas le framework Kotlin ([ADR 0011](docs/adr/0011-widgets-ecran-accueil.md)).
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
- **iOS** : `scripts/build-ios-framework.sh && cd iosApp && xcodegen && open iosApp.xcodeproj`, puis choisir le scheme `iosApp` (app complète : connexion, puis onglets Tâches, Météo, Catalogue et Réglages) ou une démo de feature (`TodoDemo`, `WeatherDemo`, `SettingsDemo`, `AuthDemo`, `CatalogDemo`, `TodoComposeDemo`). Le script doit tourner une première fois avant d'ouvrir Xcode, car les packages SPM ont besoin du XCFramework Kotlin ; ensuite la cible `KotlinShared` le relance à chaque build.

## Environnement cloud (Claude Code)

Pour que Claude puisse compiler KMP et Android dans ses sessions cloud, l'environnement doit :
1. **Réseau** : autoriser `dl.google.com` et `maven.google.com` (SDK Android et plugin Android Gradle), en plus des gestionnaires de paquets par défaut.
2. **Variable d'environnement** : `ANDROID_HOME=/opt/android-sdk`.
3. **Setup script** : installer le SDK Android avec le contenu de `scripts/install-android-sdk.sh`.

Au démarrage de chaque session, le hook `scripts/session-start.sh` (déclaré dans `.claude/settings.json`) écrit `local.properties` et précharge les dépendances Gradle. En local, il ne fait rien.

Vérification : `./gradlew allTests :androidApp:assembleDebug`.

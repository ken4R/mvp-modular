# ADR 0027 — Un framework KMP (parapluie) par app de démo iOS

- Statut : accepté
- Date : 2026-10-30

## Contexte

Chaque feature iOS a une app de démo (`TodoDemo`, `WeatherDemo`…) pour la développer sans l'app complète. Toutes liaient pourtant le même framework Kotlin « Shared » que l'app complète : toutes les features, Ktor, et Compose Multiplatform avec Skia (ADR 0010). La liaison Kotlin/Native de ce framework est l'étape la plus longue d'un build iOS ; une démo payait celle de tout le projet. La [PR #36](https://github.com/ken4R/mvp-modular/pull/36) a déjà réduit le coût à une tranche par build ; il restait le contenu du framework.

Youssef veut un parapluie par app de démo : un framework qui ne contient que la feature de la démo et ce dont elle a besoin.

Contraintes : les packages SPM des features sont partagés entre l'app complète et leur démo, dans un seul projet Xcode ; ils font `import Shared` et dépendent du package `SharedKMP`, dont le chemin du XCFramework est fixe. Toute l'API Swift tenait dans un seul objet `UseCases` de `shared`, qui référence toutes les features.

## Options

- a. **Un projet Xcode par démo**, avec son propre package `SharedKMP` : les packages de features pointent vers `../SharedKMP` en dur ; il faudrait les dupliquer ou rendre `Package.swift` dépendant d'une variable d'environnement, qu'Xcode ne transmet pas de façon fiable à la résolution des packages.
- b. **Des frameworks aux noms différents** (`SharedTodo`, `SharedWeather`…) : chaque package Swift devrait importer un module différent selon l'app qui le lie, impossible avec un seul `Package.swift`.
- c. **Même nom, même emplacement, contenu différent** : chaque démo a sa cible Kotlin qui compile son parapluie et le copie à la place de celui de l'app complète, juste avant le build Swift. Il faut que l'API Swift d'une feature soit la même dans tous les frameworks qui la contiennent : `UseCases` est découpé en une façade par module.

## Décision

Option c.

- **Façades par module** : `object TodoUseCases : SwiftUseCases()` dans `feature/todo/src/iosMain/.../ios/`, et de même `WeatherUseCases`, `SettingsUseCases`, `AuthUseCases`, `CatalogUseCases`, `OnboardingUseCases`, `DeepLinkUseCases`, `AnalyticsUseCases`, `CrashReportingUseCases`, `FeatureFlagsUseCases`, `ConnectivityUseCases`. `SwiftUseCases` et `Subscription` sont dans `core:common` (iosMain) ; les helpers à callbacks des Flow y sont factorisés (`subscribe`, `launchSubscription`) et cachés à Swift. Les constructeurs pour Swift suivent leur module (`TodoIosKt.todoFilter`, `CatalogIosKt.catalogFilter`, `SettingsIosKt.consentType`, `FeatureFlagsIosKt.featureFlag`, `AppearanceIosKt` dans `core:designsystem`). Swift écrit `TodoUseCases.shared.addTodo()` au lieu de `UseCases.shared.addTodo()`.
- **Parapluies** : un module `umbrella/<nom>-demo` par démo. `demoUmbrella(exported, linked)` (build-logic) déclare le framework « Shared » ; `exported` est ce que le package Swift et la démo utilisent, `linked` ce dont le graphe Koin a besoin. `KoinIos.kt` y définit `umbrellaModules` et `doInitKoin()`, que les démos appelaient déjà. `shared` utilise la même fonction (`iosUmbrellaFramework`).
- **Graphe Koin vérifié** : les features se parlent par des interfaces de `core:common` (session, réglage Notifications, consentements…), un module oublié ne se voit qu'à l'exécution. `UmbrellaKoinTest`, ajouté à l'iosTest de chaque parapluie, résout chaque définition sur le simulateur.
- **Xcode** : une cible agrégée `Kotlin<Nom>Demo` par démo, même script que `KotlinShared` avec le réglage `KMP_MODULE=umbrella:<nom>-demo`, que `scripts/build-ios-framework.sh` lit. La CI vérifie que chaque démo embarque bien son parapluie (absence de la façade d'un module qui n'y est pas) et affiche la taille de chaque framework.

Contenu des parapluies :

| Démo | Exportés (en plus de core:common, core:designsystem) | Liés pour le graphe Koin |
|---|---|---|
| TodoDemo | todo, deeplink, settings | database, files, securestorage, notifications, location, connectivity, analytics, auth |
| TodoComposeDemo | todo | todo-compose, settings, deeplink et les liés de TodoDemo |
| WeatherDemo | weather, connectivity | network, securestorage, location, deeplink, auth |
| SettingsDemo | settings, analytics, crashreporting, featureflags, auth, onboarding | network, database, files, securestorage |
| AuthDemo | auth | securestorage |
| CatalogDemo | catalog, connectivity | network, database |
| OnboardingDemo | onboarding, settings | database, securestorage |

## Conséquences

- Une démo ne lie plus Compose ni Skia (sauf TodoComposeDemo), ni Ktor quand sa feature n'appelle pas le réseau : liaison plus courte et framework plus petit. Le gain mesuré est dans les logs de la CI (étape « Chaque démo embarque son parapluie »).
- Passer d'une démo à une autre, ou à l'app complète, remplace le framework de `Packages/SharedKMP` : la première compilation après le changement relie le framework de la cible et recompile les packages Swift qui en dépendent. Rester sur la même cible ne coûte rien de plus qu'avant.
- La CI lie un framework de plus par démo et un exécutable de test par parapluie.
- Une nouvelle feature avec démo demande son parapluie ; un module ajouté au graphe d'une feature doit être ajouté aux parapluies qui la contiennent, ce que `UmbrellaKoinTest` signale.
- Les `getAll()` d'un parapluie ne voient que ses modules : dans OnboardingDemo, retirer un consentement ne purge pas la mesure d'audience ni les plantages (modules absents) ; dans AuthDemo, la déconnexion n'efface que ce que la démo contient. `UmbrellaKoinTest` ne le signale pas, c'est voulu pour une démo.
- `ComposeScreens` existe en deux copies (shared et le parapluie de TodoComposeDemo) : il lit le thème de `feature:settings`, dont `feature:todo-compose` ne dépend pas.
- Les packages Swift compilent contre le framework de la cible en cours : un package qui appellerait la façade d'un module absent du parapluie de sa démo ne compile plus pour cette démo (la CI construit chaque démo).
- Pistes : une démo peut remplacer un module réel par un faux dans son `umbrellaModules` (session de démo, réseau simulé) sans toucher à l'app complète.

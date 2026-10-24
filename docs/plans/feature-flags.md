# Plan — Feature flags

Objectif : merger une feature pas finie sans la montrer en release, et afficher ou masquer une feature livrée sans nouvelle version. Choix techniques : [ADR 0021](../adr/0021-feature-flags.md).

1. **`core:featureflags`** (nouveau module, `mvp.kmp.library`) : `FeatureFlag` (catalogue, étape, défaut), `FeatureFlagResolver` (règles de priorité), use cases `ObserveFeatureFlagUseCase`, `ObserveFeatureFlagsUseCase`, `SetFeatureFlagOverrideUseCase`, `ClearFeatureFlagOverridesUseCase`, `RefreshRemoteFlagsUseCase`. Data : `isDevBuild()` (Android `BuildConfig.DEV_FLAGS`, iOS `Platform.isDebugBinary`), `RemoteFlagSource` + `HostedJsonFlagSource` (Ktor, 16 Ko max, `schemaVersion` 1), `FlagCacheStore` et `FlagOverrideStore` (fichiers privés `core:files`), `FeatureFlagRepositoryImpl`. Tests `FeatureFlagsTest` (résolution, release, cache, réponses invalides via MockEngine).
2. **`remote-config/flags.json`** + workflow `pages.yml` (déploie seulement ce dossier) + vérification du format en CI.
3. **`shared`** : `featureFlagsModule` dans `sharedModules`, module exporté vers Swift ; `UseCases.observeFeatureFlag(key)`, `observeFeatureFlags`, `refreshRemoteFlags`, override.
4. **Android** : rafraîchissement au lancement et au retour au premier plan ; onglet Catalogue derrière `catalog` ; carte `preview_banner` ; section « Feature flags (debug) » dans Réglages.
5. **iOS** : idem dans `iOSApp` (scenePhase), onglet Catalogue, `FeatureSettings` (section sous `#if DEBUG`).

## Recette manuelle

- Build debug, Réglages > « Feature flags (debug) » : `catalog` (Livrée, défaut, activé) et `preview_banner` (En développement, désactivé). Activer `preview_banner` : la carte « Bientôt » apparaît dans Réglages. Couper `catalog` : l'onglet Catalogue disparaît. « Réinitialiser » remet tout.
- Mettre `"catalog": false` dans le JSON publié, relancer l'app (ou revenir au premier plan après 15 min) : l'onglet disparaît ; origine « distant » dans le menu debug.
- Mode avion au lancement : l'app démarre avec les dernières valeurs connues.
- Build release : pas de menu debug, `preview_banner` jamais visible même avec `"preview_banner": true` dans le JSON.

# Plan — Suivi des plantages avec consentement

Objectif : savoir quand et où l'app plante, sans rien collecter sans accord et sans aucune donnée personnelle. Choix techniques : [ADR 0020](../adr/0020-suivi-plantages.md).

1. **`core:common`** : contrats `CrashReportingConsent` (consentement suivi en continu) et `CrashReportingConsentObserver` (prévenu au retrait).
2. **`core:crashreporting`** (nouveau module, `mvp.kmp.library`) : `CrashReport`, `CrashScrubber` (règles de nettoyage), `StartCrashReportingUseCase` (branche le gestionnaire une fois, envoie les rapports du lancement précédent), `ObserveCrashJournalUseCase` (journal de debug). Data : `CrashReporter`, `PendingCrashStore` (fichiers JSON dans `core:files`, 10 au plus), `CrashReportSink` + `DebugCrashReportSink`, gestionnaire Android (`Thread.setDefaultUncaughtExceptionHandler`) et iOS (`setUnhandledExceptionHook`), `CrashUserSessionObserver`. Tests `CrashReportingTest`.
3. **`feature:settings`** : `ConsentType.CrashReports` et son texte versionné (v1), `SettingsCrashReportingConsent`, `SetConsentUseCase` prévient les `CrashReportingConsentObserver`. Tests dans `SettingsUseCasesTest`.
4. **`feature:onboarding`** : étape `CrashReports` après la mesure d'audience (accepter / refuser au même poids), `ONBOARDING_VERSION` inchangée.
5. **`shared`** : `crashReportingModule` dans `sharedModules`, module exporté vers Swift ; `UseCases.startCrashReporting()`, `observeCrashJournal`, `crashForTest()`, `consentType(name)`.
6. **Android** : `MvpApplication` lance `StartCrashReportingUseCase` ; étape d'onboarding ; section « Rapports de plantage (debug) » dans Réglages (builds de debug).
7. **iOS** : `iOSApp` lance `startCrashReporting()` ; `FeatureOnboarding` (étape `CrashReports`) ; `FeatureSettings` (section debug sous `#if DEBUG`).

## Recette manuelle (simulateur / émulateur, build de debug)

- Premier lancement (ou Réglages > « Revoir la présentation ») : après « Mesure d'audience », l'étape « Rapports de plantage » ; « Accepter » et « Refuser » ont le même poids. Le choix apparaît daté dans Réglages > Consentements, indépendant de la mesure d'audience.
- Consentement refusé : Réglages > « Provoquer un plantage » : l'app s'arrête. La relancer : « Envoyés : 0 ».
- Consentement accepté : « Provoquer un plantage », relancer : un rapport avec l'heure, `com.mvp.core.crashreporting.domain.model.TestCrashException` et la première ligne de pile. Le texte « Plantage de test (debug) » (message de l'exception) n'apparaît nulle part.
- Avec un rapport dans le journal : retirer le consentement vide le journal (« Envoyés : 0 ») ; « Se déconnecter » le vide aussi, et le consentement repart refusé pour le compte suivant.
- Android, crash d'un autre fil (coroutine) : même résultat. iOS : seul un plantage Kotlin est vu, pas un `fatalError` Swift (limite connue, ADR 0020).
- Build release : la section « Rapports de plantage (debug) » n'apparaît pas.

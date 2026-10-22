# Plan — Mesure d'audience avec consentement

Objectif : un module de suivi d'événements qui n'envoie rien sans l'accord donné dans Réglages, date chaque accord, et est prêt à brancher un vrai outil plus tard, sur Android et iOS. Choix techniques : [ADR 0009](../adr/0009-analytics-consentement.md).

1. **`core:common`** : contrats `AnalyticsEvent` / `AnalyticsScreen`, `AnalyticsTracker`, `AnalyticsConsent`, `AnalyticsConsentObserver`.
2. **`core:analytics`** (nouveau module KMP) : `QueuedAnalyticsRepository` (file en mémoire, lots, consentement relu), `AnalyticsSink` + `DebugAnalyticsSink`, `AnalyticsUserSessionObserver`, use cases `TrackEventUseCase`, `FlushAnalyticsUseCase`, `ObserveAnalyticsJournalUseCase`. Tests : rien sans consentement, envoi daté par lot, lot plein envoyé seul, retrait qui vide la file et change de session, consentement devenu invalide avant l'envoi, échec d'envoi gardé, mesure qui n'échoue jamais, file bornée, déconnexion, événements sans donnée personnelle.
3. **`feature:settings`** : `Consent.decidedAt` / `textVersion`, `ConsentChoice`, `ConsentTexts` (textes versionnés communs aux deux apps), `SetConsentUseCase` qui date le choix et prévient la mesure au retrait, `SettingsAnalyticsConsent`. Tests : choix daté, seul le retrait d'Analytics arrête la mesure, accord sur un ancien texte ou non daté redemandé.
4. **`feature:todo`** : `AddTodoUseCase` et `ToggleTodoUseCase` mesurent `task_added` / `task_completed` (sans le titre). Test dédié.
5. **`shared`** : `core:analytics` exporté vers Swift, `UseCases.trackEvent()`, `flushAnalytics()`, `observeAnalyticsJournal(...)`.
6. **Android** : `AnalyticsViewModel` (onglet affiché, envoi en arrière-plan), Réglages : texte et date de chaque consentement, journal de mesure (debug).
7. **iOS** : `AppTabsView` mesure l'onglet, `iOSApp` envoie en arrière-plan, `FeatureSettings` : texte et date des consentements, journal de mesure (debug, visible aussi dans `SettingsDemo`).
8. **Vérification** : CI, revues, recette manuelle ci-dessous.

## Recette manuelle (simulateur / émulateur, build de debug)

- Réglages → Consentements : « Mesure d'audience » est coupé, avec « Pas encore choisi : refusé par défaut ». Le journal de mesure (en bas) affiche 0 en attente, 0 envoyé.
- Sans consentement : changer d'onglet, ajouter et cocher une tâche, taper « Événement test » : le journal reste vide.
- Activer « Mesure d'audience » : la ligne affiche « Accepté le <date> (texte v1) ». Changer d'onglet, ajouter une tâche, la cocher : les événements `screen_viewed screen=…`, `task_added`, `task_completed` apparaissent « En attente », sans le titre de la tâche.
- « Envoyer maintenant » : ils passent dans « Envoyés ». Au 10e événement en attente, l'envoi part seul. Mettre l'app en arrière-plan puis revenir : la file est envoyée.
- Avoir des événements en attente puis couper « Mesure d'audience » : le journal se vide tout de suite (en attente et envoyés, comme un outil qui oublie l'appareil), la ligne affiche « Refusé le <date> (texte v1) ». Plus rien ne s'ajoute ensuite.
- Fermer et relancer l'app : la date du choix est conservée ; les événements en attente non envoyés ont disparu (mémoire seulement).
- Se déconnecter puis se reconnecter : consentement revenu à « Pas encore choisi », journal vide.
- Mise à jour depuis une version précédente avec « Mesure d'audience » déjà activé : après la mise à jour, il est coupé et redemandé (l'ancien accord n'était pas daté).
- Build de release (Android `assembleRelease`, iOS schéma Release) : le journal de mesure n'apparaît pas.

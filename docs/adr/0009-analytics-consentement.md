# ADR 0009 — Mesure d'audience avec consentement

- Statut : accepté
- Date : 2026-10-22

## Contexte

Le prototype doit pouvoir mesurer l'usage (onglets ouverts, tâches ajoutées ou terminées) pour plus tard brancher un vrai outil (Firebase Analytics, Amplitude, PostHog, Matomo...). Le RGPD et la directive ePrivacy demandent un consentement préalable, libre et retirable à tout moment, et de pouvoir prouver ce qui a été accepté et quand. Réglages a déjà un consentement « Mesure d'audience », refusé par défaut (PR #6), mais il n'était ni daté ni lié à un texte.

## Options

1. **Brancher tout de suite un SDK** (Firebase...) : demande un compte, une clé, et la plupart des SDK collectent dès leur initialisation (identifiant d'appareil, événements automatiques) sauf configuration soignée.
2. **Module maison derrière une interface, sans outil réel** : les événements passent par une file KMP filtrée par le consentement, puis par un « sink » remplaçable. Pour l'instant le sink est un journal local visible dans les builds de debug.

## Décision

**Option 2**, module KMP `core:analytics` :

- **Contrats dans `core:common`**, pour que les features ne dépendent ni de l'outil ni de Réglages : `AnalyticsEvent` (liste fermée), `AnalyticsTracker` (enregistrer un événement), `AnalyticsConsent` (fourni par `feature:settings`), `AnalyticsConsentObserver` (prévenu au retrait du consentement, fourni par `core:analytics`).
- **Événements fermés, sans donnée personnelle** : `screen_viewed` (avec l'écran parmi une liste fixe), `task_added`, `task_completed`, `debug_test`. Jamais de texte saisi (titre de tâche, ville), ni nom, ni e-mail, ni identifiant de compte ou d'appareil. Chaque événement porte l'heure et un **identifiant de session aléatoire**, tiré à chaque lancement et renouvelé au retrait du consentement et à la déconnexion.
- **Où l'on mesure** : `AddTodoUseCase` et `ToggleTodoUseCase` (une fois pour les deux apps), les onglets dans chaque app (`TrackEventUseCase`).
- **Consentement relu à chaque événement et avant chaque envoi** (lecture SQLite, pas de cache). Sans accord valide, l'événement est ignoré et la file est vidée.
- **Retrait immédiat** : `SetConsentUseCase` appelle les `AnalyticsConsentObserver` dès qu'Analytics est retiré, avant de confirmer à l'écran : la file en attente est abandonnée, la session de mesure renouvelée, l'outil remis à zéro (`AnalyticsSink.reset`, l'opt-out d'un vrai SDK) après la fin d'un envoi déjà commencé. Un compteur d'« époque » empêche un événement dont le consentement a été lu juste avant le retrait (ou la déconnexion) d'entrer dans la file. Si l'enregistrement du retrait échoue, l'erreur est affichée et le consentement reste accordé en base : la mesure reprendrait.
- **Consentement daté et versionné** : chaque choix enregistre la date (epoch ms) et la version du texte affiché. Les textes sont dans KMP (`ConsentTexts`), communs aux deux apps. Changer un texte = augmenter sa version : les accords donnés sur une ancienne version (ou non datés, enregistrés avant cette PR) sont lus comme jamais donnés et redemandés. Clés ajoutées à la table clé / valeur de Réglages, sans migration.
- **File en mémoire seulement**, bornée à 100 événements, envoyée par lots de 10 ou quand l'app passe en arrière-plan. Rien de la mesure n'est écrit sur le disque ; si l'app est tuée, les événements en attente sont perdus (acceptable pour de la mesure d'audience). Un envoi raté garde les événements pour le suivant.
- **Sink remplaçable** (`AnalyticsSink`) : `DebugAnalyticsSink` garde les 50 derniers événements en mémoire pour le **journal de mesure**, affiché dans Réglages des builds de debug seulement (`FLAG_DEBUGGABLE` sur Android, `#if DEBUG` sur iOS), avec « Événement test » et « Envoyer maintenant ».
- **Déconnexion** : `AnalyticsUserSessionObserver` vide la file, le journal (`AnalyticsSink.reset`) et renouvelle la session. Le consentement, lui, est effacé avec les réglages : le compte suivant repart sans accord.
- **La mesure ne fait jamais échouer une action** : `track` avale ses erreurs.

## Conséquences

- Brancher un vrai outil : une implémentation d'`AnalyticsSink` déclarée dans `analyticsModule`, rien d'autre. Il faudra alors : désactiver la collecte automatique du SDK et son identifiant publicitaire, ne l'initialiser qu'après consentement, appeler son opt-out / reset au retrait et à la déconnexion, mettre à jour le texte du consentement (nom de l'outil, pays de traitement) en augmentant sa version, et l'App Privacy (iOS) / Data safety (Android) des stores.
- Seule la décision la plus récente est gardée, pas l'historique de tous les choix. Pour une preuve de consentement côté serveur, il faudra envoyer chaque décision (date, version, choix) au backend quand il existera.
- Les événements en attente ne survivent pas à l'arrêt de l'app. Un lot plein part dans une portée propre au module, sans faire attendre l'action mesurée. Sur Android, l'envoi à la mise en arrière-plan part du `ViewModel` de l'activité : si l'activité est détruite aussitôt (retour arrière), il peut être annulé et la file attend le prochain envoi. Avec un vrai outil, passer par `ProcessLifecycleOwner` ou WorkManager.
- `screen_viewed` peut être compté deux fois pour un même onglet (rotation, retour depuis l'écran de verrouillage).
- `DebugAnalyticsSink` est aussi câblé en release (journal masqué, 50 événements gardés en mémoire seulement, et seulement avec consentement). Le remplacer par le vrai outil, ou par un sink vide en release, avant toute diffusion.

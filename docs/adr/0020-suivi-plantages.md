# ADR 0020 — Suivi des plantages avec consentement

- Statut : accepté
- Date : 2026-10-24

## Contexte

Pour corriger les plantages vus par les testeurs, il faut savoir où l'app plante. Un rapport de plantage est une donnée d'usage de l'appareil : le RGPD et ePrivacy demandent un accord préalable, libre et retirable, distinct de la mesure d'audience (ADR 0009) puisque la finalité est différente. Un rapport ne doit contenir aucune donnée personnelle : les messages d'exception citent souvent ce que l'utilisateur a saisi (titre de tâche, note, ville).

## Options

1. **Brancher Sentry ou Crashlytics tout de suite** : Crashlytics demande un projet Firebase et `google-services.json` / `GoogleService-Info.plist` ; Sentry KMP demande un DSN et le SDK Sentry Cocoa lié à l'app iOS. Les deux s'initialisent en général au lancement, avant de connaître le consentement, et envoient par défaut des données (identifiant d'installation, modèle, fil d'événements, parfois messages et captures) qu'il faut couper une à une.
2. **Module maison derrière une interface, sans outil réel** : capture des plantages Kotlin dans KMP, rapport nettoyé, file sur l'appareil et « sink » remplaçable, comme pour la mesure d'audience.

## Décision

**Option 2**, module KMP `core:crashreporting` :

- **Consentement séparé « Rapports de plantage »** (`ConsentType.CrashReports`, texte v1 dans `ConsentTexts`), refusé par défaut, daté et versionné comme les autres, dans Réglages > Consentements et dans la présentation du premier lancement (étape après la mesure d'audience, accepter et refuser au même poids). `ONBOARDING_VERSION` n'est pas augmentée : les utilisateurs existants ne revoient pas toute la présentation, le consentement reste refusé et se donne dans Réglages.
- **Contrats dans `core:common`** : `CrashReportingConsent` (flux du consentement, fourni par `feature:settings`) et `CrashReportingConsentObserver` (fourni par `core:crashreporting`, prévenu par `SetConsentUseCase` avant de confirmer le retrait).
- **Capture** : `Thread.setDefaultUncaughtExceptionHandler` sur Android (Kotlin et Java, tout fil), `setUnhandledExceptionHook` sur iOS (exceptions Kotlin non rattrapées, y compris celles qui remontent vers Swift sans `@Throws`). Le gestionnaire d'origine est toujours appelé ensuite : l'app s'arrête comme avant. Sur iOS, le hook termine lui-même l'app (`terminateWithUnhandledException`), sans quoi une coroutine en échec ne l'arrêterait plus.
- **Au moment du plantage, rien n'est lu sur le disque** : le consentement est suivi en mémoire (faux au tout début du lancement, avant sa première lecture : un plantage dans ces quelques millisecondes n'est pas enregistré). Le rapport est écrit dans un fichier du dossier privé `crash_reports` (`core:files` : hors sauvegardes ; Data Protection complète sur iOS, donc un plantage iPhone verrouillé n'est pas enregistré). Au plus 10 rapports en attente.
- **Contenu d'un rapport** : identifiant aléatoire, heure, système et sa version (pas de modèle d'appareil), type de l'exception et de ses causes (5 au plus), lignes de pile (40 au plus par exception, 300 caractères par ligne). **Jamais le message d'une exception.** `CrashScrubber` masque en plus tout ce qui ressemble à un e-mail, un chemin de dossier personnel (seul le nom du fichier source reste) ou une longue suite de chiffres. Ni identifiant de compte ou d'appareil, ni fil d'événements, ni capture d'écran.
- **Envoi au lancement suivant** (`StartCrashReportingUseCase`, appelé par chaque app au démarrage) : le consentement est relu ; accordé, chaque rapport part au `CrashReportSink` puis son fichier est effacé ; refusé entre-temps, les rapports sont effacés sans être envoyés. Un envoi raté garde les rapports restants pour le lancement suivant.
- **Retrait du consentement et déconnexion** : plus rien n'est enregistré, les rapports en attente sont effacés et l'outil remis à zéro (`CrashReportSink.reset`). Le consentement est effacé avec les réglages à la déconnexion.
- **Sink remplaçable** : `DebugCrashReportSink` garde les 20 derniers rapports en mémoire, montrés dans Réglages des builds de debug (« Rapports de plantage (debug) »), avec « Provoquer un plantage » qui fait vraiment planter l'app (`TestCrashException`).

## Conséquences

- iOS : les plantages Swift / Objective-C (`fatalError`, accès hors limites, signal) ne sont pas vus, seulement ceux de KMP. Les couvrir demande un vrai outil (Sentry Cocoa, Crashlytics, PLCrashReporter) ou MetricKit (`MXCrashDiagnostic`, livré par le système le lendemain, sans consentement à gérer côté outil).
- Brancher un vrai outil : une implémentation de `CrashReportSink` dans `crashReportingModule`, qui envoie les `CrashReport` déjà nettoyés (API d'envoi manuel de l'outil). Il faudra alors : ne pas laisser le SDK installer son propre gestionnaire ni s'initialiser avant consentement, couper son identifiant d'installation, ses breadcrumbs et captures, garder le DSN / la clé hors du dépôt (configuration de build, vide par défaut), mettre à jour le texte du consentement (nom de l'outil, pays de traitement) en augmentant sa version, et l'App Privacy (iOS) / Data safety (Android) des stores.
- Build release Android : R8 renomme les classes, les piles sont illisibles sans le fichier `mapping.txt` du build ; il faudra le garder (ou l'envoyer à l'outil) pour chaque version distribuée. Le numéro de version de l'app n'est pas encore dans le rapport.
- `DebugCrashReportSink` est aussi câblé en release (journal masqué, rien ne quitte l'appareil). Le remplacer par le vrai outil avant toute diffusion.
- Un plantage pendant l'écriture d'un rapport le perd (écriture atomique : pas de fichier à moitié écrit) ; un fichier illisible est effacé à la lecture.

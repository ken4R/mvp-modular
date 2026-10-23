# ADR 0013 — Durcissement avant distribution

- Statut : accepté
- Date : 2026-10-23

## Contexte

Avant une première distribution (TestFlight, Play Console en test interne), quatre suivis de sécurité restaient ouverts depuis les ADR 0002, 0003 et 0012 :
- iOS : les bases SQLite (tâches, file d'envoi hors ligne, réglages, catalogue) partaient dans les sauvegardes iCloud / Finder ;
- les lignes supprimées (une tâche, les données effacées à la déconnexion) restaient lisibles dans les pages libres du fichier SQLite ;
- le build release Android n'était ni réduit ni obfusqué ;
- le faux service de connexion (n'importe quel e-mail, mot de passe public `demo1234`) tournait dans tous les builds, y compris un build distribué.

## Décision

**Sauvegardes iOS.** `NativeDatabaseDriverFactory` ouvre les bases dans le dossier par défaut de SQLiter (`Application Support/databases`, les bases existantes y restent) et marque ce dossier `NSURLIsExcludedFromBackupKey`, ce qui couvre tout son contenu, journaux WAL compris. Android était déjà couvert (`allowBackup="false"`, dossier `no_backup`).

**`secure_delete`.** `PRAGMA secure_delete = ON` à l'ouverture de chaque connexion : iOS par `onCreateConnection` de SQLiter, Android par `onConfigure` du callback SQLDelight (avant la création ou la migration du schéma). SQLite remet à zéro le contenu supprimé au lieu de seulement marquer la page libre. Coût : une écriture de plus par suppression, négligeable pour ces volumes.

**R8 en release (Android).** `isMinifyEnabled` et `isShrinkResources` dans `androidApp`, règles `proguard-android-optimize.txt` + `androidApp/proguard-rules.pro` (traces lisibles avec le fichier de mapping, `-dontwarn` pour les classes JVM référencées par Ktor / coroutines mais absentes d'Android). Les bibliothèques fournissent leurs règles (kotlinx.serialization, Ktor, Koin, WorkManager, Compose).

**Faux service de connexion réservé aux builds de développement.** `isDemoAuthEnabled()` (`feature:auth`, expect / actual) :
- Android : constante `BuildConfig.DEMO_AUTH` de `feature:auth`, vraie en debug, fausse en release. C'est une vraie constante (contrairement à `BuildConfig.DEBUG` d'une bibliothèque) : R8 replie la condition et retire `FakeAuthRemoteDataSource` de l'APK release. La CI le vérifie (le mot de passe de démo est présent dans le dex debug, absent du dex release).
- iOS : `Platform.isDebugBinary` du framework Kotlin. La cible Xcode `KotlinShared` compile maintenant le framework dans la configuration Xcode (Release pour une archive). Le code du faux service reste dans le binaire, mais n'est jamais instancié.

Hors développement, `UnavailableAuthRemoteDataSource` refuse toute connexion (`AppError.Unavailable`) et une session du faux service restée sur l'appareil n'est plus acceptée (comptes `demo:`). L'écran de connexion n'affiche l'indice `demo1234` qu'en debug (`BuildConfig.DEMO_AUTH` / `#if DEBUG`).

## Conséquences

- Un build distribué ne permet plus de se connecter tant qu'un vrai backend n'est pas branché sur `AuthRemoteDataSource` : la distribution TestFlight / Play Console sert d'abord à valider l'installation et l'écran de connexion, ou attend le vrai backend.
- La CI construit aussi le release Android (R8), ce qui détecte une règle manquante. Le release iOS n'est pas construit en CI (trop long) : la seule différence de code est `Platform.isDebugBinary`, la liaison release du framework est déjà faite par l'étape de suivi du poids.
- Un plantage R8 au lancement (réflexion non couverte par une règle) ne se voit qu'en lançant l'APK release : à essayer sur un émulateur avant la première distribution.
- Une session `demo:` restée sur l'appareil est ignorée mais pas effacée : elle n'ouvre rien, et la prochaine connexion avec un vrai compte efface les données de l'ancien (changement de compte, ADR 0003).
- Seuls les artefacts release (APK / AAB release, archive Xcode hors Debug) se distribuent : un build debug envoyé à des testeurs embarque le faux service.
- Mode WAL (iOS, journal par défaut de SQLiter) : une trame ancienne du fichier `-wal` peut garder une ligne supprimée jusqu'à sa réécriture. Accepté (fichier dans le bac à sable, chiffré par Data Protection) ; un `wal_checkpoint(TRUNCATE)` après l'effacement de déconnexion le fermerait si besoin.
- Restent ouverts : lier le jeton à la biométrie avant le vrai backend (ADR 0007), et les points serveur du mode hors ligne (ADR 0012).

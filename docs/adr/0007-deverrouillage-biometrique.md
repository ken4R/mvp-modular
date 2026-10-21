# ADR 0007 — Déverrouillage Face ID / empreinte

- Statut : accepté
- Date : 2026-10-21

## Contexte

L'app garde une session de 7 jours (ADR 0003) : quiconque tient le téléphone déverrouillé ouvre l'app sans rien saisir. On veut pouvoir rouvrir l'app par biométrie (Face ID / Touch ID / empreinte), en option, sur Android et iOS.

## Options

1. **Barrière d'interface** : la session reste lisible comme aujourd'hui ; l'app masque tout son contenu tant que le système (LocalAuthentication / BiometricPrompt) n'a pas confirmé l'utilisateur.
2. **Clé liée à la biométrie** : la session (ou la clé qui la chiffre) n'est lisible qu'après authentification (Keychain avec `SecAccessControl` `.userPresence`, clé Keystore `setUserAuthenticationRequired` + `BiometricPrompt.CryptoObject`).

## Décision

**Option 1, barrière d'interface**, pour ce prototype :

- La session est lue hors de l'écran : les rappels (WorkManager, `SyncTodoRemindersUseCase`) vérifient sa validité en arrière-plan, sans utilisateur devant l'écran. Une session liée à la biométrie casserait les rappels ou demanderait de séparer « session valide » et « jeton lisible ».
- Il n'y a pas encore de serveur : le jeton ne protège rien côté réseau. Les données à protéger sont surtout à l'écran.
- Les données au repos restent protégées comme avant par le chiffrement de l'appareil, le Keystore et le Keychain (`ThisDeviceOnly`).

Règles :

- Réglage **« Sécurité » dans Réglages, coupé par défaut**, stocké avec les autres réglages (SQLite) et effacé à la déconnexion. L'activer demande d'abord une authentification réussie : on vérifie qu'elle marche sur l'appareil.
- **Verrouillée à chaque lancement et à chaque passage en arrière-plan**, sans délai de grâce. L'état « déverrouillé » ne vit qu'en mémoire (`AppLockRepositoryImpl`) ; une rotation Android ne verrouille pas.
- Tant que l'app est verrouillée, **le contenu n'est pas construit** (Compose / SwiftUI) ; avant la première lecture de l'état, l'écran est vide.
- **Authentification système uniquement** : biométrie forte ou code de l'appareil. iOS : `LAPolicy.deviceOwnerAuthentication`. Android 11+ : `android.hardware.biometrics.BiometricPrompt` avec `BIOMETRIC_STRONG | DEVICE_CREDENTIAL` ; Android 8 à 10 : écran de confirmation du verrouillage de l'appareil (`KeyguardManager`). On évite `androidx.biometric` : sa version stable demande une `FragmentActivity` et, avant Android 9, un thème AppCompat que l'app n'a pas.
- **Activer comme couper** le réglage demande une authentification réussie : quelqu'un qui trouve l'app ouverte ne peut pas retirer le verrou.
- **Repli** : si aucun code ni biométrie n'est configuré (retirés après l'activation), l'écran verrouillé le dit. « Se déconnecter » y est toujours proposé : il efface les données de l'appareil puis ramène à la connexion, sans rien montrer (une personne qui tient le téléphone peut donc effacer les données, pas les lire).
- **Aperçu dans le sélecteur d'apps masqué** quand le verrou est activé : iOS recouvre l'app dès que la scène n'est plus active ; Android 13+ coupe la capture des apps récentes, avant Android 13 `FLAG_SECURE` (qui bloque aussi les captures d'écran).

## Conséquences

- Simple, sans changement de `core:securestorage` ni de `feature:auth`, et les rappels continuent de marcher.
- Limite connue : sur un appareil rooté / jailbreaké, ou avec un outil d'instrumentation, la barrière d'interface peut être contournée, et la base SQLite des tâches n'est pas chiffrée par l'app. Avant de brancher un vrai serveur, lier le jeton à la biométrie (option 2), en gardant pour les rappels une simple date d'expiration lisible sans authentification.
- Le contenu étant retiré à chaque verrouillage, l'onglet affiché et une saisie en cours sont perdus au retour. Un délai de grâce (par exemple 30 s) pourra être ajouté si c'est gênant.
- Android 8 à 10 : l'écran de confirmation du système accepte le code, et parfois une biométrie moins sûre selon le constructeur. Android 11+ impose la biométrie de classe forte.
- Le réglage ne protège pas ce qu'iOS / Android affichent hors de l'app (contenu des notifications de rappel sur l'écran verrouillé).

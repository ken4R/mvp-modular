# ADR 0003 — Connexion : faux service local et stockage chiffré `core:securestorage`

- Statut : accepté
- Date : 2026-10-20

## Contexte

L'app doit demander une connexion avant d'ouvrir les onglets, garder la session dans le Keychain (iOS) et le Keystore (Android), alimenter le profil des Réglages et effacer les données locales à la déconnexion (reporté par l'ADR 0002). Le prototype n'a pas de backend.

## Options

- **Faux service local derrière une interface** : déterministe, sans réseau, testable en CI ; remplaçable par un vrai backend sans toucher au domaine ni aux apps.
- **API publique de démonstration** (type DummyJSON) via `core:network` : vrai appel HTTP, mais dépendance à un tiers hors de notre contrôle, comptes imposés, et rien de réutilisable pour notre futur backend.
- Stockage chiffré : **androidx.security-crypto** (`EncryptedSharedPreferences`, déprécié par Google) ou une librairie KMP tierce (KVault, multiplatform-settings) contre **les API système directement** (Keystore + AES-GCM, Keychain Services).

## Décision

- `feature:auth` : `AuthRemoteDataSource` avec une implémentation `FakeAuthRemoteDataSource` (n'importe quel e-mail, mot de passe `demo1234`, session de 7 jours, jeton aléatoire). Un vrai backend se branchera sur cette interface (Ktor via `core:network`).
- Nouveau module **`core:securestorage`** : `SecureStorage` (clé / valeur), `expect`/`actual` sur les API système, sans dépendance ajoutée.
  - Android : clé AES-256 générée dans l'Android Keystore, valeurs chiffrées en AES-GCM (nom de la clé en donnée authentifiée) dans un fichier de préférences privé. Une valeur indéchiffrable (clé perdue, restauration) est effacée et lue comme absente.
  - iOS : une entrée Keychain par clé, `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly` (ni sauvegarde iCloud / Finder, ni transfert d'appareil). Le Keychain survivant à la désinstallation, ses entrées sont effacées au premier lancement d'une nouvelle installation.
- Session (jeton, compte, expiration) et **profil des Réglages** (nom, e-mail) vont dans ce stockage. Le profil laissé en clair dans `settings.db` par la version précédente y est migré puis effacé de la base.
- Coordination sans dépendance entre features : `UserSessionObserver` (`core:common`). Chaque feature qui garde des données de l'utilisateur en déclare un dans son module Koin (`bind UserSessionObserver::class`) ; `LoginUseCase` et `LogoutUseCase` les reçoivent tous (`getAll()`).
  - Connexion : les features sont prévenues (`onLogin`) ; Réglages pré-remplit un profil vide avec le nom et l'e-mail du compte.
  - Déconnexion : chaque feature efface ses données (tâches, réglages, profil), **puis** la session. Si l'effacement échoue, la session est gardée pour pouvoir réessayer.
  - À la connexion, les données locales sont effacées avant d'ouvrir la session, sauf si le **même** compte se reconnecte après expiration. Sans session connue (premier lancement, sauvegarde restaurée sans le Keychain, clé Keystore perdue), on efface aussi : rien ne passe d'un utilisateur à l'autre.
- L'UI ne voit que `UserAccount` (id, nom, e-mail) : le jeton ne sort pas de `feature:auth`.

## Conséquences

- Le faux service n'est pas une protection : le mot de passe de démonstration est public (affiché sur l'écran de connexion). Il ne tourne que dans les builds de développement : un build distribué refuse toute connexion (ADR 0013). L'autorisation réelle devra être faite côté serveur, avec révocation du jeton à la déconnexion et renouvellement (refresh token).
- L'expiration est vérifiée à chaque émission de la session (lancement, connexion, déconnexion), pas en continu pendant que l'app est ouverte.
- Les implémentations Keystore / Keychain ne sont pas couvertes par les tests `commonTest` (pas de Keystore sur la JVM de test, Keychain soumis aux entitlements sur simulateur) : elles se vérifient en recette manuelle sur appareil ou simulateur.
- Une nouvelle feature qui stocke des données de l'utilisateur doit déclarer son `UserSessionObserver`, sinon ses données survivront à la déconnexion.

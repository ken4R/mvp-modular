# Plan — Connexion utilisateur

Objectif : un écran de connexion devant l'app (Android et iOS), une session gardée dans le Keychain / Keystore, un profil des Réglages chiffré et pré-rempli par le compte, et l'effacement des données locales à la déconnexion. Choix techniques : [ADR 0003](../adr/0003-connexion-stockage-chiffre.md).

1. **`core:common`** : `UserAccount` et `UserSessionObserver` (contrat entre la connexion et les features).
2. **`core:securestorage`** : `SecureStorage` + `secureStorageModule` (`expect`/`actual`) : Keystore + AES-GCM sur Android, Keychain sur iOS.
3. **`feature:auth`** : `Credentials`, `Session`, `AuthRepository`, `LoginUseCase` (validation, changement de compte), `LogoutUseCase` (effacement puis session), `ObserveSessionUseCase` ; `FakeAuthRemoteDataSource`, `SecureSessionLocalDataSource`. Tests : connexion, validation, mauvais mot de passe, redémarrage, expiration, changement de compte, déconnexion et échec d'effacement.
4. **`feature:settings`** : profil dans `SecureProfileLocalDataSource` (migration depuis `settings.db`), `SettingsUserSessionObserver` (pré-remplissage, effacement). Tests : chiffrement, migration, pré-remplissage, effacement.
5. **`feature:todo`** : `TodoUserSessionObserver` (effacement des tâches), test.
6. **`shared`** : modules Koin, export de `feature:auth`, `UseCases.login()` / `logout()` / `observeSession(...)` pour iOS.
7. **Android** : `SessionViewModel` (écran racine), `LoginScreen` + `LoginViewModel`, section « Compte » des Réglages avec la déconnexion.
8. **iOS** : package `FeatureAuth` (`SessionGate`, `LoginView`), démo `AuthDemo`, section « Compte » des Réglages ; scheme `AuthDemo` dans la CI.
9. **Vérification** : CI (tests KMP Android + iOS, build Android, build Xcode avec les démos). Recette manuelle ci-dessous.

## Recette manuelle (Android et iOS)

- Premier lancement : écran de connexion. Mauvais mot de passe : « E-mail ou mot de passe incorrect », champ mot de passe vidé. E-mail invalide : message de validation.
- Connexion avec `prenom.nom@exemple.fr` / `demo1234` : onglets visibles, Réglages affiche « Connecté avec … » et le profil pré-rempli « Prenom Nom ».
- Ajouter des tâches, changer un réglage, tuer et relancer l'app : toujours connecté, données présentes.
- Se déconnecter : retour à l'écran de connexion, formulaire vide. Se reconnecter : aucune tâche, réglages par défaut, profil pré-rempli à nouveau.
- iOS : désinstaller puis réinstaller l'app : écran de connexion (la session du Keychain a été effacée).
- Mise à jour depuis la version précédente avec un profil rempli : après connexion du même nom, le profil saisi avant est conservé (migré du SQLite vers le stockage chiffré).

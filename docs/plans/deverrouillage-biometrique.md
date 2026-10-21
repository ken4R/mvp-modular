# Plan — Déverrouillage Face ID / empreinte

Objectif : rouvrir l'app par Face ID / Touch ID / empreinte (ou le code de l'appareil), en option, sur Android et iOS. Choix techniques : [ADR 0007](../adr/0007-deverrouillage-biometrique.md).

1. **`feature:settings`** : réglage `appLockEnabled` (clé `app_lock.enabled`, coupé par défaut), `AppLockRepository` (état en mémoire), use cases `SetAppLockEnabledUseCase`, `ObserveAppLockUseCase`, `LockAppUseCase`, `UnlockAppUseCase`. Tests : coupé par défaut, activation sans se reverrouiller, verrouillée au lancement, arrière-plan puis déverrouillage, désactivation, déconnexion.
2. **`shared`** : `UseCases.observeAppLock(...)`, `setAppLockEnabled()`, `lockApp()`, `unlockApp()` pour iOS.
3. **Android** : `ui/applock/` (`DeviceAuthentication`, `AppLockViewModel`, `LockScreen`), verrou dans `MainActivity` (après la connexion, `onStop` hors rotation), aperçu masqué dans les apps récentes, interrupteur « Sécurité » dans Réglages.
4. **iOS** : `AppLockGate` et `DeviceAuthenticator` dans `FeatureSettings`, autour des onglets de l'app et de la démo `SettingsDemo` ; interrupteur dans Réglages ; `NSFaceIDUsageDescription` pour l'app et `SettingsDemo`.
5. **Vérification** : CI, revues, recette manuelle ci-dessous.

## Recette manuelle (appareil réel de préférence)

- Réglages → Sécurité → activer : Face ID / empreinte demandé. Annuler : l'interrupteur reste coupé. Réussir : il reste activé, l'app reste ouverte.
- Quitter l'app (écran d'accueil) puis revenir : écran « App verrouillée », le prompt s'ouvre seul. Réussir : on retrouve l'app.
- Dans le sélecteur d'apps, l'aperçu de l'app ne montre pas son contenu (verrou activé).
- Tuer l'app puis la relancer : verrouillée.
- Annuler le prompt : on reste sur l'écran verrouillé, « Déverrouiller » le rouvre. Échouer plusieurs fois : repli sur le code de l'appareil.
- Android : tourner l'écran pendant que l'app est ouverte : pas de verrouillage.
- Retirer le code de l'appareil (ce qui retire aussi la biométrie), revenir : message « Aucun code… », « Se déconnecter » ramène à la connexion, données effacées.
- Se déconnecter puis se reconnecter : le verrou est coupé.
- Couper le réglage : plus de verrouillage.
- Simulateur iOS : Features → Face ID → Enrolled, puis Matching Face / Non-matching Face. Émulateur Android : définir un code, enregistrer une empreinte, puis `adb -e emu finger touch 1`.

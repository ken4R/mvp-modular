# Plan — Présentation du premier lancement

Objectif : au premier lancement, présenter l'app et expliquer chaque autorisation avant que le système ne la demande (notifications, position, verrou Face ID / empreinte, mesure d'audience). Chaque étape se remet à plus tard, « Passer » termine tout de suite, et la présentation n'est montrée qu'une fois par appareil, même après une déconnexion. Décision : [ADR 0016](../adr/0016-presentation-premier-lancement.md).

1. **`feature:onboarding`** (nouveau module KMP) : `OnboardingStep` (Welcome, Notifications, Location, AppLock, Analytics), `OnboardingTexts` (textes communs aux deux apps), `ONBOARDING_VERSION`. Base `onboarding.db` (clé / valeur, clé `completed_version`), sans `UserSessionObserver`. Use cases `ObserveOnboardingRequiredUseCase` (version vue absente ou plus ancienne), `CompleteOnboardingUseCase`, `RestartOnboardingUseCase`, `GetOnboardingStepsUseCase` (le verrou n'est proposé que si l'appareil a une biométrie ou un code). Test `OnboardingUseCasesTest`.
2. **`shared`** : module exporté vers Swift, `onboardingModule` dans `sharedModules`, `UseCases.observeOnboardingRequired`, `getOnboardingSteps`, `completeOnboarding`, `restartOnboarding`.
3. **Choix enregistrés par les use cases existants** de `feature:settings`, pour le compte connecté, comme depuis Réglages : `SetNotificationsEnabledUseCase` (si la permission est accordée), `SetAppLockEnabledUseCase` (après une authentification réussie), `SetConsentUseCase` (accepté ou refusé, daté, version du texte). La position n'est qu'une permission système.
4. **Android** : `ui/onboarding/OnboardingViewModel` (lié à l'activité) + `OnboardingScreen`, affiché dans `MainActivity` après la connexion et le verrou, avant les onglets. Permissions `POST_NOTIFICATIONS` (Android 13+), `ACCESS_COARSE_LOCATION`, `rememberDeviceAuthentication`. Réglages → À propos : « Revoir la présentation ».
5. **iOS** : package `FeatureOnboarding` (`OnboardingGate`, `OnboardingView`, `OnboardingViewModel`, demandes système dans `PermissionRequests.swift`), placé dans `iOSApp` à l'intérieur d'`AppLockGate`. App de démo `OnboardingDemo` (présentation remontrée à chaque ouverture), ajoutée à la CI. Réglages → À propos : « Revoir la présentation ».

## Recette manuelle (simulateur / émulateur)

- Installation neuve, connexion : la présentation s'affiche avant les onglets, « Étape 1 sur 5 » (4 sans code ni biométrie sur l'appareil).
- Notifications → « Autoriser » : la demande système apparaît ; acceptée, Réglages → Notifications est activé. Refusée : on passe à la suite, le réglage reste coupé. Android 12 et moins : pas de demande système.
- Position → « Autoriser » : demande système (approximative par défaut sur iOS) ; refusée ou acceptée, on passe à la suite. Météo → « Météo à ma position » marche sans nouvelle demande si acceptée.
- Verrou → « Activer » : Face ID / empreinte / code demandé ; réussi, Réglages → Sécurité est activé et l'app se verrouille au retour d'arrière-plan. Annulé : on reste sur l'étape.
- Mesure d'audience : « Accepter » et « Refuser » ont le même poids ; le choix apparaît daté dans Réglages → Consentements.
- « Passer » à n'importe quelle étape : les onglets s'affichent, rien d'autre n'est changé.
- Redémarrer l'app : plus de présentation. Se déconnecter puis se reconnecter (même compte ou un autre) : plus de présentation.
- Réglages → « Revoir la présentation » : elle repart de la première étape, puis revient aux onglets.
- Lien `mvpmodular://tasks` reçu pendant la présentation : il s'ouvre une fois la présentation terminée.
- Thème sombre et accent choisis : la présentation les suit. VoiceOver / TalkBack : le titre de chaque étape est annoncé comme un titre.

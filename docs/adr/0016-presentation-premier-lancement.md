# ADR 0016 — Présentation du premier lancement

- Statut : accepté
- Date : 2026-10-24

## Contexte

L'app demande quatre autorisations, chacune au moment où une fonction en a besoin : notifications (Réglages, pour les rappels), position (Météo), Face ID / empreinte (Réglages > Sécurité) et le consentement Mesure d'audience (Réglages > Consentements). On veut, au premier lancement, présenter l'app et expliquer chacune avant la demande du système, sans rien imposer, et ne le faire qu'une fois par appareil.

## Décision

**Un module `feature:onboarding`** pour l'état « déjà vue » et le contenu de la présentation (étapes, textes communs aux deux apps), sans dépendance vers une autre feature. Les choix eux-mêmes passent par les use cases existants de `feature:settings`, appelés par l'UI comme depuis Réglages : une seule façon d'activer les notifications, le verrou ou un consentement, avec les mêmes règles (permission accordée d'abord, authentification réussie avant le verrou, consentement daté avec la version de son texte, `ConsentTexts`, affiché tel quel dans l'étape).

**Après la connexion, avant les onglets** (et après le verrou s'il est activé). Notifications, verrou et consentement sont des réglages du compte : les enregistrer avant la connexion les ferait porter par la session suivante, et la connexion d'un nouveau compte remet le verrou à zéro. Un lien profond reçu pendant la présentation attend les onglets, comme pendant le verrou.

**Une fois par appareil** : base SQLite `onboarding.db` à part, sans `UserSessionObserver`, donc gardée à la déconnexion (comme l'apparence, ADR 0015). Un autre compte sur le même appareil ne la revoit pas : ses autorisations système sont déjà données ou refusées, ses réglages se font dans Réglages. On stocke la version vue (`ONBOARDING_VERSION`) plutôt qu'un booléen : ajouter une étape importante et augmenter la version la remontre à tous. Valeur absente ou illisible = jamais vue.

**Rien n'est obligatoire** : chaque étape a « Plus tard » (ou « Refuser » pour le consentement, de même poids visuel que « Accepter »), et « Passer » termine tout de suite. Passer ne refuse rien : le consentement reste non choisi (donc refusé par défaut, ADR 0009) et peut être donné plus tard. Si l'état de la présentation est illisible, l'app ne l'impose pas (onglets affichés). Réglages > À propos > « Revoir la présentation » la remontre.

**Le verrou n'est proposé que si l'appareil a une biométrie ou un code** (`GetOnboardingStepsUseCase`), la position reste une simple permission système (rien n'est stocké par l'app).

## Conséquences

- Les utilisateurs déjà installés voient la présentation une fois après la mise à jour (aucune version vue enregistrée).
- iOS : `OnboardingGate` est dans `AppLockGate` ; si le verrou vient d'être activé et que l'app passe en arrière-plan pendant la présentation, elle repart de la première étape au déverrouillage. Android garde l'étape (ViewModel lié à l'activité).
- Android : `ACCESS_COARSE_LOCATION` refusée deux fois n'est plus redemandée par le système ; l'étape passe alors à la suite sans alerte. Le réglage se change dans les paramètres Android, comme depuis l'écran Météo.
- Ajouter une étape : une entrée dans `OnboardingStep`, son texte dans `OnboardingTexts`, son action dans chaque app (`OnboardingScreen`, `OnboardingViewModel.swift`), et `ONBOARDING_VERSION + 1` si ceux qui ont déjà vu la présentation doivent la revoir.

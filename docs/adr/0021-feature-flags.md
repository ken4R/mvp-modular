# ADR 0021 — Feature flags (développement et distants)

- Statut : accepté
- Date : 2026-10-24

## Contexte

Deux besoins :

1. **Développement** : merger une feature pas finie dans `main` sans qu'elle apparaisse dans les builds distribués, et pouvoir l'essayer en debug.
2. **Production** : afficher ou masquer une feature déjà livrée sans publier de nouvelle version (coupure d'urgence, ouverture progressive).

La logique reste dans KMP : les deux apps posent la même question, via un use case exposé par `shared`.

## Options pour la source distante

1. **JSON statique hébergé sur GitHub Pages**, lu avec le client Ktor existant (`core:network`) : gratuit, sans SDK, modifié par un commit relu en PR.
2. **Firebase Remote Config** : console, ouverture par pourcentage et ciblage, mais projet Firebase, `google-services.json` / `GoogleService-Info.plist` et SDK natifs sur les deux apps (pas de SDK KMP officiel), données envoyées à Google (identifiant d'installation).
3. **Fausse source distante** en attendant un backend.

Choix de Youssef (2026-10-24) : **option 1**, derrière une interface pour pouvoir passer à Firebase ou à un backend plus tard sans toucher aux apps.

## Décision

Module KMP `core:featureflags` (plugin `mvp.kmp.library`), exporté par `shared`.

- **Catalogue dans le code** : `enum class FeatureFlag(key, stage, defaultEnabled)`. `stage` vaut `InDevelopment` (pas finie) ou `Released` (livrée, pilotable à distance). La clé (`snake_case`, stable) est le nom utilisé dans le JSON distant. Un flag inconnu du catalogue est ignoré : le distant ne peut piloter que ce qui est déjà dans le binaire.
- **Type de build** : `isDevBuild()` (expect/actual) vaut `BuildConfig.DEV_FLAGS` sur Android (vraie constante par type de build, comme `DEMO_AUTH` en ADR 0013) et `Platform.isDebugBinary` sur iOS.
- **Résolution d'un flag** (`FeatureFlagResolver`, fonction pure, testée) :
  - `InDevelopment` en release : **toujours faux**, quelles que soient les valeurs distantes ou locales. En debug : réglage local s'il existe, sinon faux.
  - `Released` : réglage local (debug seulement), sinon valeur distante en cache, sinon `defaultEnabled`.
- **Use cases** (`AppResult` / `Flow`, testés en `commonTest`) : `ObserveFeatureFlagUseCase(flag)` → `Flow<Boolean>` (l'UI masque ou affiche l'entrée de la feature en direct) ; `ObserveFeatureFlagsUseCase` (liste pour le menu debug : valeur, origine, étape) ; `SetFeatureFlagOverrideUseCase` / `ClearFeatureFlagOverridesUseCase` (refusés en release, `AppError`) ; `RefreshRemoteFlagsUseCase` (appelé au lancement et au retour au premier plan, au plus une fois toutes les 15 minutes, jamais bloquant).
- **Source distante** (`RemoteFlagSource`, implémentation `HostedJsonFlagSource`) : `GET` HTTPS de `https://ken4r.github.io/mvp-modular/flags.json`, URL fixée par build (propriété Gradle / constante, pas modifiable à l'exécution). Format :
  ```json
  { "schemaVersion": 1, "flags": { "catalog": true } }
  ```
  Réponse refusée en entier si elle dépasse 16 Ko, si `schemaVersion` n'est pas 1 ou si le JSON est invalide ; une valeur non booléenne est ignorée flag par flag. Pas de cookie, pas d'en-tête d'identification, aucun identifiant d'appareil ou de compte envoyé.
- **Cache** : la dernière réponse valide est gardée dans un fichier privé (`core:files`, hors sauvegardes) avec son heure de récupération ; au lancement les flags partent du cache (ou des valeurs par défaut), puis le rafraîchissement met à jour le flux. Réseau absent, timeout (5 s) ou réponse refusée : le cache est gardé tel quel. Les flags ne sont pas des données de l'utilisateur : pas de `UserSessionObserver`, le cache survit à la déconnexion.
- **Réglages locaux (debug)** : stockés dans le même dossier privé, ignorés en release même s'ils existent. Menu « Feature flags (debug) » dans Réglages : chaque flag avec son étape, sa valeur et son origine (défaut, distant, local), un interrupteur, « Réinitialiser » et « Rafraîchir le distant ».
- **Publication du JSON** : fichier `remote-config/flags.json` dans le dépôt, publié par un workflow GitHub Pages (`pages.yml`) qui ne déploie que ce dossier. Changer un flag en prod = une PR sur ce fichier ; une CI vérifie qu'il respecte le format et ne cite que des clés du catalogue.
- **Premiers flags** : `catalog` (`Released`, défaut vrai : coupe l'onglet Catalogue à distance) et `preview_banner` (`InDevelopment` : petite carte « Bientôt » dans Réglages, pour montrer le mécanisme de bout en bout).

## Conséquences

- Pour Youssef : activer GitHub Pages sur le dépôt (Settings > Pages > Source : GitHub Actions). GitHub Pages sur un dépôt privé demande un forfait payant ; sinon le dépôt doit être public, ou le JSON hébergé ailleurs (seule l'URL change).
- Délai : une modification du JSON est vue au prochain rafraîchissement (lancement, ou retour au premier plan après 15 minutes), plus le cache CDN de GitHub Pages (environ 10 minutes). Ce n'est pas un coupe-circuit instantané.
- Intégrité : HTTPS seulement, contenu non signé. Quelqu'un qui prendrait le contrôle du dépôt ou de Pages ne pourrait qu'allumer ou éteindre des features `Released` déjà livrées (jamais une `InDevelopment`) ; aucun flag ne doit protéger une vérification de sécurité (connexion, verrou biométrique, consentements), qui ne passe donc jamais derrière un flag.
- Une feature `InDevelopment` est seulement masquée en release : son code est dans le binaire (sauf si R8 l'élimine grâce à la constante). Ne rien y mettre de secret.
- Pas de ciblage ni de pourcentage. Si on en a besoin, passer à Firebase Remote Config ou à un backend : une nouvelle implémentation de `RemoteFlagSource`, le reste ne change pas.
- Quand une feature passe `InDevelopment` → `Released`, on change son étape dans l'enum ; quand elle est stable, on retire le flag et le code mort.

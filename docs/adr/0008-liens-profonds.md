# ADR 0008 — Liens profonds

- Statut : accepté
- Date : 2026-10-22

## Contexte

Un lien, ou le tap sur un rappel de tâche (ADR 0004), doit ouvrir l'app directement sur le bon écran : une tâche, l'onglet Météo, la fiche d'un produit du catalogue, Réglages. L'app a une connexion (ADR 0003) et un verrou Face ID / empreinte optionnel (ADR 0007) : un lien ne doit rien montrer avant eux.

## Options

1. **Schéma d'URL propre à l'app** (`mvpmodular://…`) : déclaré dans l'Info.plist (iOS) et par un `intent-filter` (Android). Rien à héberger.
2. **Liens vérifiés** (liens universels iOS, App Links Android) : des liens `https://` d'un domaine de l'app, qui ouvrent l'app si elle est installée et le site sinon.

## Décision

**Option 1, schéma `mvpmodular://`**, parce que l'app n'a pas encore de domaine. Formats :

| Lien | Écran |
|---|---|
| `mvpmodular://tasks` | Tâches |
| `mvpmodular://tasks/<id>` | Tâches, la tâche mise en évidence quelques secondes |
| `mvpmodular://weather` | Météo |
| `mvpmodular://catalog` | Catalogue |
| `mvpmodular://catalog/products/<id>` | Fiche du produit |
| `mvpmodular://settings` | Réglages |

Règles :

- **La logique est dans KMP** (`core:deeplink`) : `DeepLinks.parse` transforme le lien en `DeepLink` typé, `OpenDeepLinkUseCase` le met en attente, la navigation de chaque app l'observe et le retire une fois affiché (`ConsumeDeepLinkUseCase`). Les apps ne font que changer d'onglet et ouvrir l'écran.
- **Un lien ne fait que naviguer** : aucun lien n'ajoute, ne coche, ne supprime ni ne modifie un réglage. La requête (`?…`) et le fragment (`#…`) sont ignorés.
- **Lecture stricte** : schéma et écran connus, rien de plus dans le chemin, identifiant de tâche `[A-Za-z0-9-]{1,64}`, identifiant de produit entier positif, lien de 512 caractères au plus. Sinon le lien est **non reconnu** et mène à l'écran par défaut, l'onglet Tâches.
- **Identifiant inconnu** : tâche absente (supprimée, autre compte) → la liste des tâches avec « Tâche introuvable » (`FindTodoUseCase` renvoie `AppError.NotFound`) ; produit absent → la fiche affiche « Produit introuvable. », comme pour un produit du cache.
- **L'état de l'app passe d'abord** : les onglets ne sont construits qu'une fois connecté et déverrouillé, et c'est là que le lien en attente est lu. Un lien reçu déconnecté est donc affiché après la connexion, un lien reçu verrouillé après le déverrouillage. Un seul lien attend : le plus récent. Il ne vit qu'en mémoire (perdu si le système tue l'app avant qu'il soit affiché) et il est oublié à une déconnexion explicite (`UserSessionObserver.onLogout`), mais pas à la connexion qui suit (qui efface pourtant les données restantes des features).
- **Rappels** : chaque notification porte le lien de sa tâche (`LocalNotification.deepLink`). Android : intent explicite vers l'activité de l'app (`ACTION_VIEW` + le lien), un `PendingIntent` par notification. iOS : `userInfo["deepLink"]`, lu par le délégué de `UNUserNotificationCenter`, posé dès le lancement.
- **Android** : `MainActivity` en `launchMode="singleTask"`, pour qu'un lien ouvert depuis une autre app revienne à l'activité existante (`onNewIntent`) au lieu d'en empiler une seconde dans la tâche de l'app appelante, et `taskAffinity=""` pour qu'une autre app ne puisse pas attirer l'activité dans sa tâche (détournement de tâche, « StrandHogg », possible jusqu'à Android 10). Le lien n'est pas rejoué après une rotation ni à la réouverture depuis les apps récentes.

## Conséquences

- N'importe quelle app ou page web peut ouvrir l'app sur l'un de ces écrans, et une autre app pourrait déclarer le même schéma (sur Android, l'utilisateur choisit alors l'app ; sur iOS, le choix n'est pas défini). C'est acceptable parce qu'un lien ne fait que naviguer et ne transporte aucune donnée sensible : il ne faut jamais y mettre de jeton, de code de connexion ni d'action.
- Pour passer aux **liens vérifiés** plus tard, il faudra : un domaine en HTTPS ; iOS, le fichier `apple-app-site-association` servi sur `/.well-known/` et l'entitlement `com.apple.developer.associated-domains` (`applinks:<domaine>`) ; Android, `assetlinks.json` sur `/.well-known/` avec l'empreinte SHA-256 du certificat de signature, et un `intent-filter` `https` avec `android:autoVerify="true"`. `DeepLinks.parse` devra alors accepter aussi `https://<domaine>/…` avec les mêmes chemins.

# ADR 0011 — Widget Tâches de l'écran d'accueil

- Statut : accepté
- Date : 2026-10-22

## Contexte

Youssef veut un widget d'écran d'accueil sur iOS (WidgetKit) et Android (Jetpack Glance). Un widget est visible **hors de l'app** : sur l'écran d'accueil, et sur iOS aussi en mode En veille (StandBy), iPhone verrouillé. Or l'app protège déjà les tâches par la connexion (ADR 0003) et, en option, par Face ID / empreinte (ADR 0007). Le widget ne doit pas contourner ces protections.

Les deux plateformes diffèrent sur un point : un widget Android tourne **dans le processus de l'app** et peut appeler le code KMP ; un widget iOS tourne dans **une extension séparée**, avec environ 30 Mo de mémoire, sans accès à la base SQLite ni au Keychain de l'app.

## Options

Contenu :

1. **Tâches à faire** : les tâches non terminées, rappel le plus proche d'abord.
2. **Météo** : demanderait la position et le réseau depuis le widget (permission de localisation en arrière-plan sur iOS, appels Open-Meteo à chaque rafraîchissement).

Partage des données avec l'extension iOS :

- a. **L'extension charge le framework Kotlin** et lit la base : framework de 39 Mo en release (ADR 0010) pour un processus limité à ~30 Mo, base et Keychain à déplacer dans un App Group pour que l'extension y ait accès.
- b. **L'app écrit un petit fichier** dans le conteneur d'un App Group, que l'extension lit.

## Décision

**Option 1 (tâches) et option b sur iOS.** La météo pourra suivre en second widget.

- **La logique est dans KMP** : `ObserveTodoWidgetUseCase` (`feature:todo`) donne un `TodoWidgetState` :
  - `SignedOut` sans session valide (absente **ou expirée**, `SessionExpiry`) ;
  - `Locked` si le déverrouillage Face ID / empreinte est **activé** dans Réglages (`AppLockPreference`, nouveau contrat dans `core:common`, fourni par `feature:settings`). Peu importe que l'app soit déverrouillée à cet instant : le widget est vu hors de l'app, donc sans authentification ;
  - sinon `Tasks` : le nombre de tâches non terminées, les 5 premières (rappel le plus proche d'abord, puis l'ordre de la liste), la fin de session `visibleUntil` et un lien profond par tâche (`mvpmodular://tasks/<id>`, ADR 0008).
  - Les deux états sans tâche ne portent **aucune donnée** de l'utilisateur. En cas d'erreur, les apps n'affichent rien non plus.
- **Android** (`androidApp/.../widget/`) : `TodoWidget` (Glance) lit le use case à chaque mise à jour ; `TodoWidgetUpdater`, lancé par `MvpApplication`, redessine le widget à chaque changement (tâche, déconnexion, verrou). Rien n'est recopié hors de la base de l'app. La mise à jour périodique (30 min, le minimum Android) efface les tâches après la fin de session même si l'app n'a pas tourné. Un tap ouvre `MainActivity` par un **intent explicite** avec le lien de la tâche : connexion et verrou passent d'abord, comme pour tout lien. Couleurs et textes viennent des tokens (`ui/theme/WidgetTheme.kt`).
- **iOS** :
  - package `FeatureTodoWidget` à deux produits : `FeatureTodoWidget` (dans l'app, `TodoWidgetPublisher.start()` au lancement) recopie l'état KMP dans un fichier JSON et appelle `WidgetCenter.reloadTimelines` ; `TodoWidgetData` (app + extension, sans dépendance) définit le format et l'emplacement du fichier ;
  - extension `TodoWidgetExtension` (`project.yml`), petit et moyen formats, qui ne lie **que** `TodoWidgetData` (vérifié par la CI) ;
  - fichier dans `Library/Caches` du conteneur de l'App Group `group.com.mvp.ios`, exclu des sauvegardes, protection `completeUntilFirstUserAuthentication` (lisible après le premier déverrouillage de l'appareil, comme la session dans le Keychain), écriture atomique. S'il ne peut être écrit, il est supprimé ;
  - la timeline ajoute une entrée sans tâche à `visibleUntil` : à la fin de session, le widget se vide sans que l'app soit rouverte ;
  - titres marqués `privacySensitive()` : masqués en mode En veille tant que l'iPhone est verrouillé ;
  - la galerie des widgets montre un exemple fictif, jamais les vraies tâches ;
  - l'extension ne charge pas `DesignSystemUI` (qui dépend du framework Kotlin) : la couleur principale est recopiée depuis les tokens dans le fichier, le reste utilise les styles système (`.headline`, `.secondary`, marges par défaut du widget), sans valeur en dur.
- Le widget ne fait que **lire** : pas de case à cocher interactive (elle demanderait d'écrire dans la base depuis l'extension iOS).

## Conséquences

- La déconnexion vide le widget sur les deux plateformes, dans la seconde (l'app est au premier plan à ce moment-là). Activer le verrou le vide aussi.
- iOS : avec le verrou coupé, les titres des 5 premières tâches sont dans un fichier non chiffré par l'app (mais protégé par le chiffrement de l'appareil) du conteneur partagé. C'est le prix d'un widget iOS ; le verrou activé n'y laisse rien.
- iOS : un **App Group** demande un compte Apple Developer payant pour installer sur un vrai iPhone. Sans lui (simulateur sans signature, CI), `containerURL` peut être `nil` : le widget dit d'ouvrir l'app.
- Android : les titres affichés sont tenus par le lanceur, comme pour tout widget. Pas d'écran verrouillé concerné (Android ne propose plus de widgets sur l'écran verrouillé des téléphones).
- Glance 1.1.1 ajouté (`androidx.glance:glance-appwidget`, compileSdk 34+).
- Un second widget (météo, ou tâches interactives sur iOS 17 avec App Intents) réutiliserait le même schéma : état calculé dans KMP, recopié pour l'extension iOS.

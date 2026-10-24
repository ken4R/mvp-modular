# ADR 0018 — Ajouter une tâche à la voix (Siri, Google Assistant)

- Statut : accepté
- Date : 2026-10-24

## Contexte

L'utilisateur veut dicter une tâche sans ouvrir l'app : « Dis Siri, ajoute une tâche dans MVP Modular », « Hey Google, crée … dans MVP Modular ». L'app a une connexion (ADR 0003) et un verrou Face ID / empreinte optionnel (ADR 0007) : une commande vocale arrive aussi app verrouillée, téléphone verrouillé, ou déconnecté. Un raccourci peut aussi être lancé par une automatisation (Raccourcis iOS) ou, sur Android, par n'importe quelle app qui envoie l'intent à l'activité exportée.

## Options

1. **Ajout direct en arrière-plan** : l'intent appelle `AddTodoUseCase` sans ouvrir l'app. Il faudrait décider seul si l'utilisateur est connecté et si le verrou est levé, et une autre app (Android) ou une automatisation pourrait remplir la liste à l'insu de l'utilisateur.
2. **Ouvrir l'app avec le titre prérempli** : le titre dicté attend comme un lien profond (ADR 0008), puis l'onglet Tâches le met dans le champ « Nouvelle tâche » une fois connecté et déverrouillé. L'utilisateur relit et touche « Ajouter ».
3. **Ajout direct seulement connecté et verrou désactivé, sinon option 2** : deux comportements à expliquer, et toujours le risque d'ajouts envoyés par une autre app.

## Décision

**Option 2**, la même sur les deux plateformes.

- **KMP** : `StartTodoDraftUseCase` (`feature:todo`) met en attente `DeepLink.NewTask(title)` (`core:deeplink`), dans le même emplacement unique que les liens : affiché seulement par les onglets (donc après la connexion, le verrou et la présentation du premier lancement), le plus récent remplace le précédent, oublié à la déconnexion, en mémoire seulement. Le titre est borné (1 000 caractères lus), mis sur une ligne (retours à la ligne et caractères de contrôle remplacés par des espaces), coupé à 80 caractères comme `AddTodoUseCase`. Un titre vide ouvre le champ sans toucher au texte déjà saisi.
- **Jamais dans une URL** : `DeepLinks.parse` ne produit jamais `NewTask`, et `DeepLinks.url(NewTask)` donne `mvpmodular://tasks` sans le titre. La règle d'ADR 0008 tient : un lien ne fait que naviguer.
- **Rien n'est ajouté sans le bouton « Ajouter »**, qui passe par `AddTodoUseCase` (validation, synchronisation, mesure `TaskAdded` avec consentement). Le raccourci lui-même n'envoie aucun événement de mesure (un nouvel événement changerait le texte du consentement, ADR 0009).
- **iOS** : App Intent `AddTodoIntent` (cible de l'app, `openAppWhenRun`), paramètre texte demandé par Siri, et `MvpAppShortcuts` (`AppShortcutsProvider`) avec des phrases en français. Le projet passe en langue de développement `fr` et l'app s'affiche « MVP Modular » (nom attendu par `\(.applicationName)`). L'intent ne fait que poser le brouillon puis laisse l'app s'ouvrir : `SessionGate`, `AppLockGate` et `OnboardingGate` passent d'abord.
- **Android** : `res/xml/shortcuts.xml` : App Action `actions.intent.CREATE_THING` (paramètre `thing.name` → extra `title`) et raccourci statique « Nouvelle tâche » (appui long sur l'icône), tous deux vers `MainActivity` par intent explicite (`com.mvp.android.action.NEW_TASK`). `MainActivity` lit l'extra en se protégeant d'extras mal formés et ignore l'intent rejoué depuis les apps récentes, comme pour les liens.

## Conséquences

- Il reste un tap de confirmation : c'est voulu, la commande vocale ne peut rien ajouter seule, ni être utilisée par une autre app pour écrire dans la liste.
- Un titre dicté déconnecté est prérempli après la connexion, quel que soit le compte, comme un lien (ADR 0008) : c'est l'utilisateur de l'appareil qui vient de le dicter.
- **Google Assistant** ne propose les App Actions qu'à une app publiée sur le Play Store (ou avec le plugin de test App Actions d'Android Studio, connecté au compte de la Play Console). Google remplace peu à peu l'Assistant par Gemini : à revoir avant une publication. Le raccourci statique, lui, marche partout dès Android 7.1.
- **Siri** : les phrases ne marchent qu'avec la langue de Siri en français ; pour d'autres langues, il faudra un catalogue `AppShortcuts.xcstrings`. Le raccourci apparaît aussi dans l'app Raccourcis et dans Spotlight.
- Un ajout vraiment silencieux (« c'est ajouté ») demanderait de lier l'action au verrou (authentification de l'intent) et un vrai backend ; à rediscuter à ce moment-là.

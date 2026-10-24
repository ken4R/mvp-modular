# Plan — Ajouter une tâche à la voix

Objectif : « Dis Siri, ajoute une tâche dans MVP Modular » / « Hey Google, crée … dans MVP Modular » ouvre l'app avec la tâche dictée dans « Nouvelle tâche », prête à être ajoutée. Choix techniques : [ADR 0018](../adr/0018-ajout-vocal.md).

1. **`core:deeplink`** : destination `DeepLink.NewTask(title)`, jamais lue ni écrite dans une URL (`DeepLinksTest`).
2. **`feature:todo`** : `StartTodoDraftUseCase` (titre borné, sur une ligne, 80 caractères max, mis en attente), câblé dans `todoModule`. Test `StartTodoDraftUseCaseTest`.
3. **`shared`** : `UseCases.startTodoDraft()` pour Swift.
4. **Android** : `res/xml/shortcuts.xml` (App Action `CREATE_THING`, raccourci statique « Nouvelle tâche »), déclaré sur `MainActivity` ; `MainActivity` transmet l'extra `title` à `DeepLinkViewModel.startDraft` ; `AppTabs` passe le titre à `TodoScreen` (`draftRequest`), qui le met dans le champ.
5. **iOS** : `AddTodoIntent` et `MvpAppShortcuts` dans la cible de l'app ; `AppTabsView` passe le titre à `TodoListView` (`draftRequest`). `project.yml` : langue de développement `fr`, nom affiché « MVP Modular ».

## Recette manuelle (simulateur / émulateur)

- iOS : lancer l'app une fois (Siri n'enregistre les phrases qu'après une installation lancée). App Raccourcis : « Ajouter une tâche » apparaît sous MVP Modular. Le lancer, taper « Acheter du pain » : l'app s'ouvre sur Tâches, « Acheter du pain » dans le champ ; rien n'est ajouté tant qu'on ne touche pas « Ajouter ».
- iOS, Siri en français (Réglages > Siri > Langue) : « Dis Siri, ajoute une tâche dans MVP Modular », dicter le titre : même résultat.
- Verrou activé, app en arrière-plan : lancer le raccourci : Face ID d'abord, puis le champ prérempli. Déconnecté : écran de connexion d'abord, puis le champ prérempli. Titre en attente sur l'écran verrouillé, puis « Se déconnecter » : après la reconnexion, le champ est vide (titre oublié).
- Titre de plus de 80 caractères ou sur plusieurs lignes : une seule ligne, coupée à 80.
- Texte déjà saisi dans « Nouvelle tâche », puis raccourci avec un titre : le titre dicté le remplace (voulu, ADR 0018).
- Android, raccourci statique : appui long sur l'icône, « Nouvelle tâche » : l'app s'ouvre sur Tâches, le texte déjà saisi est gardé.
- Android, titre : `adb shell am start -n com.mvp.android/.MainActivity -a com.mvp.android.action.NEW_TASK --es title "Acheter du pain"` : champ prérempli, avec les mêmes règles de verrou et de connexion. Rouvrir l'app depuis les apps récentes : le titre ne revient pas.
- Android, Google Assistant : seulement avec le plugin de test App Actions d'Android Studio (compte de la Play Console) ou une fois l'app publiée.

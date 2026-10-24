# Plan — Partager une tâche

Objectif : depuis la fiche d'une tâche, l'envoyer par la feuille de partage du système, avec un lien qui rouvre l'app sur la tâche. Choix techniques : [ADR 0017](../adr/0017-partage-tache.md).

1. **`feature:todo`** : `TodoShareRequest`, `TodoShare`, `ShareTodoUseCase` (titre sur une ligne, état, note seulement si demandée, lien `mvpmodular://tasks/<id>` de `core:deeplink`, `NotFound` pour une tâche absente), câblé dans `todoModule`. Test `ShareTodoUseCaseTest` (note absente par défaut, ajoutée sur demande, tâche terminée sans note, lien relu par `DeepLinks.parse`, tâche absente).
2. **`shared`** : `UseCases.shareTodo()` pour Swift.
3. **Android** : bouton « Partager » dans l'en-tête de `TodoDetailScreen` ; dialogue « Inclure la note » si la tâche a une note ; `Intent.ACTION_SEND` en `text/plain` (objet + texte) dans `Intent.createChooser`. `TodoDetailViewModel` enregistre d'abord un brouillon de note modifié.
4. **iOS** : `ShareLink` dans l'en-tête de `TodoDetailView` (menu « Sans la note » / « Avec la note » si la tâche a une note) ; `TodoDetailViewModel` prépare les deux textes avec `ShareTodoUseCase` à chaque changement de la tâche.
5. **Liens** : rien de nouveau, `mvpmodular://tasks/<id>` existe déjà (ADR 0008).

## Recette manuelle (simulateur / émulateur)

- Tâche sans note : ouvrir la fiche, « Partager » : la feuille du système s'ouvre avec « Tâche : <titre> », « État : à faire » et le lien. Choisir Notes / Messages / Gmail : le texte arrive complet (objet = « Tâche : <titre> » dans un mail).
- Tâche avec une note : Android, le dialogue s'ouvre avec « Inclure la note » décochée ; partager sans cocher : la note n'apparaît pas. Recommencer en cochant : elle apparaît sous « Note : ». iOS, le menu propose « Sans la note » et « Avec la note », même vérification.
- Modifier la note sans l'enregistrer puis partager avec la note : Android l'enregistre puis partage la nouvelle version ; iOS affiche « Avec la note (enregistrez-la d'abord) » désactivé.
- Aucune pièce jointe n'est jamais proposée ni envoyée, même sur une tâche qui en a.
- Copier le lien partagé et l'ouvrir : `adb shell am start -a android.intent.action.VIEW -d "mvpmodular://tasks/<id>"` ; iOS `xcrun simctl openurl booted "mvpmodular://tasks/<id>"`. L'app s'ouvre sur l'onglet Tâches, la tâche mise en évidence.
- Même lien, app verrouillée (verrou activé, app en arrière-plan) : déverrouillage d'abord, puis la tâche. Déconnecté : connexion d'abord, puis l'onglet Tâches avec « Tâche introuvable » (la reconnexion repart d'une liste vide).
- Supprimer la tâche puis rouvrir le lien : « Tâche introuvable ».
- iOS, feuille de partage ouverte (ou Mail ouvert depuis elle), verrou activé : passer en arrière-plan : l'aperçu du sélecteur d'apps ne montre pas la feuille ; au retour, l'écran de verrouillage est affiché, la feuille a disparu.
- Android, Gmail ouvert depuis le partage : les apps récentes montrent Gmail à part, l'aperçu de l'app reste masqué. Sélecteur ouvert, verrou activé : passer en arrière-plan puis revenir, noter si le sélecteur réapparaît au-dessus du verrou (risque accepté, ADR 0017).

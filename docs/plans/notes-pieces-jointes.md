# Plan — Notes et pièces jointes des tâches

Objectif : une note libre et des pièces jointes (photo, PDF, texte) sur chaque tâche, stockées sur l'appareil, effacées à la déconnexion, hors sauvegardes. Choix techniques : [ADR 0014](../adr/0014-notes-pieces-jointes.md).

1. **`core:files`** (nouveau module) : `PrivateFileStore`, `PrivateFileStoreFactory`, `AndroidPrivateFileStore` (`no_backup`), `NativePrivateFileStore` (Application Support, exclu des sauvegardes, Data Protection complète), `filesModule`. Test `PrivateFileStoreTest` (commonTest, lancé sur les deux plateformes avec le vrai système de fichiers).
2. **`feature:todo`** :
   - base : colonne `note`, table `TodoAttachmentEntity`, migration `3.sqm`, `selectAll` avec le nombre de pièces jointes ;
   - domain : `Todo.note` / `attachmentCount`, `AttachmentType`, `TodoAttachment`, `NewTodoAttachment`, `TodoNote`, `UpdateTodoNoteUseCase`, `ObserveTodoAttachmentsUseCase`, `AddTodoAttachmentUseCase`, `DeleteTodoAttachmentUseCase` ;
   - data : `SqlDelightTodoLocalDataSource` (fichiers via `PrivateFileStore`, note dans la file d'envoi et dans `TodoRecord`), `InMemoryTodoLocalDataSource`, `TodoRepositoryImpl` ;
   - tests : `TodoNoteAndAttachmentUseCasesTest` (formats, contenu déguisé, tailles, nombre, nom nettoyé, suppression), `SqlDelightTodoLocalDataSourceTest` (note, pièces jointes et fichiers, échec d'écriture, suppression, déconnexion, note reçue, migration 1 → 4), `TodoSyncTest` (la note voyage, les pièces jointes non ; suppression reçue).
3. **`shared`** : `filesModule`, `UseCases.updateTodoNote()`, `addTodoAttachment()`, `deleteTodoAttachment()`, `observeTodoAttachments(...)`, `bytesOf(NSData)`.
4. **Android** : `TodoDetailScreen` + `TodoDetailViewModel` (fiche à la place de la liste), `AttachmentReader` (sélecteurs, photo réenregistrée en JPEG), `AttachmentPreview` (photo, PDF, texte). Résumé « Note · N pièces jointes » sous le titre ; un tap sur le titre ouvre la fiche.
5. **iOS** : package `FeatureTodo` : `TodoDetailView` + `TodoDetailViewModel`, `AttachmentFiles` (JPEG via ImageIO, miniatures, lecture des fichiers), bouton ⓘ / trombone sur chaque ligne.

## Recette manuelle (simulateur / émulateur)

- Ouvrir une tâche (tap sur le titre sur Android, bouton ⓘ sur iOS), écrire une note, « Enregistrer la note » : « Note » apparaît sous le titre dans la liste. Modifier la note puis « Retour » sans enregistrer : elle est enregistrée quand même.
- Ajouter une photo : elle apparaît avec sa miniature ; un tap l'affiche en grand, « Fermer » revient à la fiche. La liste affiche « 1 pièce jointe ».
- Ajouter un PDF de plusieurs pages et un fichier `.txt` : aperçu dans l'app, pages défilantes pour le PDF.
- Essayer un fichier d'un autre format (dans « Fichiers » / le sélecteur Android, seuls PDF, texte et images sont proposés) ou de plus de 10 Mo : message d'erreur, rien n'est ajouté.
- Ajouter 10 pièces jointes : les boutons d'ajout se désactivent.
- Photo prise avec la position activée : après l'ajout, vérifier qu'elle n'a plus de métadonnées (fichier dans le dossier de l'app : `no_backup/todo-attachments` sur Android, `Application Support/todo-attachments` sur iOS).
- Retirer une pièce jointe (bouton « Retirer » sur Android, balayage sur iOS), supprimer une tâche qui en a : les fichiers disparaissent du dossier.
- Verrou Face ID / empreinte activé, une photo ouverte en grand : passer en arrière-plan, l'aperçu des apps récentes ne montre rien ; au retour, l'app demande le déverrouillage.
- Se déconnecter puis se reconnecter : plus de note ni de pièce jointe, dossier vide.
- Deux appareils sur le même faux serveur n'existent pas encore en vrai (serveur en mémoire par app) : la synchronisation de la note est couverte par `TodoSyncTest`.

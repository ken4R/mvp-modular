# Plan — Corbeille avec annulation

Objectif : « Annuler » juste après la suppression d'une tâche, et une corbeille où elle reste restaurable 30 jours, sur Android et iOS. La corbeille suit le compte sur ses autres appareils par la file d'envoi du mode hors ligne et disparaît à la déconnexion. Choix techniques : [ADR 0025](../adr/0025-corbeille.md).

1. **`feature:todo`, domain** :
   - `TrashedTodo`, `TrashItem` (jours restants) et les règles `TodoTrash` (30 jours, `daysLeft`, `expiryCutoff`).
   - `TodoTrashRepository` ; `DeleteTodoUseCase` met à la corbeille ; `RestoreTodoUseCase`, `ObserveTrashUseCase`, `DeleteTodoPermanentlyUseCase`, `EmptyTrashUseCase`, `PurgeExpiredTrashUseCase`.
2. **`feature:todo`, data** :
   - Colonne `trashed_at` (migration `8.sqm`), exclue de la liste, de `get` et des statistiques ; `selectTrash`, `selectTrashedIdsBefore`.
   - `trash`, `restore`, `observeTrash`, `deleteTrashed`, `deleteTrashedBefore` dans `TodoLocalDataSource` (SQLite et mémoire) ; une tâche de la corbeille ne se modifie pas.
   - Synchronisation : `TodoRecord.trashedAt`, lu par `selectPending`, validé à la réception.
   - Tests `commonTest` (`TodoTrashTest`) : annulation, tâche cachée et non modifiable, statistiques, jours restants, purge à 30 jours, suppression définitive, vider, synchronisation entre deux appareils, conflit, réception invalide, déconnexion, rappels annulés puis rétablis.
3. **`shared`** : Koin, `UseCases.restoreTodo()`, `deleteTodoPermanently()`, `emptyTrash()`, `purgeExpiredTrash()`, `observeTrash(...)`.
4. **Android** : snackbar « Annuler » dans `TodoScreen`, bouton « Corbeille », `TodoTrashScreen` + `TodoTrashViewModel`, purge au démarrage (`MvpApplication`).
5. **iOS** (`FeatureTodo`) : bandeau « Annuler » dans `TodoListView`, bouton « Corbeille », `TodoTrashView` + `TodoTrashViewModel`, purge au démarrage (`TodoSync.start()`).
6. **Vérification** : CI (tests KMP Android + iOS, builds Android et Xcode), revues de code et de sécurité, recette ci-dessous.

## Recette manuelle

- Créer A, B, C ; supprimer B (Android : bouton de la ligne ; iOS : balayer vers la gauche). Elle disparaît, le message « « B » placée dans la corbeille » propose « Annuler » : B revient entre A et C.
- Supprimer B sans annuler : « Corbeille » la liste avec « Supprimée définitivement dans 30 jours ». « Restaurer » : elle revient à sa place, avec ses pièces jointes et son rappel s'il est encore à venir.
- Une tâche avec rappel dans la corbeille : aucune notification à l'heure prévue.
- « Supprimer définitivement » puis confirmer : elle quitte la corbeille. « Vider » puis confirmer : la corbeille est vide.
- Lien profond vers une tâche de la corbeille : « Tâche introuvable ».
- Hors ligne : supprimer une tâche, le bandeau affiche « 1 modification en attente » ; de retour en ligne, il disparaît.
- Se déconnecter puis se reconnecter : la corbeille est vide.
- Lecteur d'écran : le message de suppression est annoncé, « Annuler » est atteignable (iOS : le bandeau reste 15 s).
- 30 jours : pas testable à la main sans changer l'heure de l'appareil (avancer de 31 jours puis relancer l'app : la tâche n'est plus dans la corbeille). Couvert par `TodoTrashTest`.

# ADR 0025 — Corbeille avec annulation

- Statut : accepté
- Date : 2026-10-27

## Contexte

Supprimer une tâche était immédiat et définitif : la ligne, ses pièces jointes (fichiers compris) et une suppression dans la file d'envoi ([ADR 0012](0012-mode-hors-ligne.md)). L'utilisateur veut un bouton « Annuler » juste après une suppression, et pouvoir restaurer une tâche pendant 30 jours. La corbeille doit suivre le compte sur ses autres appareils, passer par la file d'envoi et disparaître à la déconnexion comme le reste des tâches (`UserSessionObserver`).

## Options

- **Annulation seule, côté écran** : retarder la vraie suppression de quelques secondes. Rien à synchroniser, mais pas de restauration à 30 jours, et une app tuée pendant le délai perd soit la suppression soit l'annulation.
- **Table séparée** (`TrashedTodoEntity`) où l'on recopie la tâche : la liste n'a pas à filtrer, mais chaque colonne existe deux fois, les pièces jointes changent de propriétaire, et la synchronisation doit déplacer des lignes entre tables.
- **Suppression douce** : une colonne `trashed_at` sur la tâche, envoyée au serveur comme le titre. La suppression définitive reste la suppression actuelle (`isDeleted`).

## Décision

- **Colonne `trashed_at`** (epoch ms, `NULL` hors corbeille) dans `TodoEntity`, migration `8.sqm` (schéma v9). `DeleteTodoUseCase` met la tâche à la corbeille au lieu de la supprimer. Une tâche de la corbeille n'est lue que par la corbeille : liste, recherche, liens profonds, partage, calendrier, widget, statistiques et rappels (à l'heure et par lieu, qui suivent la liste) l'ignorent, et ses modifications échouent comme pour une tâche absente (`NotFound`). Elle garde tout le reste (note, échéance, rappels, priorité, clé d'ordre, pièces jointes) : restaurée, elle revient à sa place, avec ses rappels encore à venir.
- **Synchronisation** : `TodoRecord.trashedAt`, envoyé avec la tâche ; la mise à la corbeille et la restauration sont des modifications datées comme les autres (la plus récente gagne, le serveur à égalité). L'heure de mise à la corbeille est celle de la modification ; une version reçue dont `trashedAt` est après son `updatedAt` (ou négatif) est ignorée. La suppression définitive part comme avant (`isDeleted`).
- **30 jours** (`TodoTrash.RETENTION_DAYS`), comptés depuis `trashed_at`. `PurgeExpiredTrashUseCase` supprime définitivement ce qui a fait son temps ; chaque app le lance au démarrage et à l'ouverture de la corbeille. `ObserveTrashUseCase` donne les jours restants (arrondis au jour supérieur, jamais plus de 30 même si l'heure vient d'une horloge en avance) et ne liste plus une tâche expirée en attendant la purge.
- **Use cases** : `RestoreTodoUseCase` (« Annuler » et « Restaurer »), `ObserveTrashUseCase`, `DeleteTodoPermanentlyUseCase` (seulement depuis la corbeille), `EmptyTrashUseCase`, `PurgeExpiredTrashUseCase`, sur un `TodoTrashRepository` séparé de `TodoRepository` (les faux dépôts des tests et de l'écran Compose Multiplatform n'ont pas à changer).
- **Pièces jointes** : gardées (lignes et fichiers) dans la corbeille, supprimées avec la tâche à la suppression définitive. On ne peut pas en ajouter à une tâche de la corbeille.
- **Déconnexion** : rien à ajouter, `clear()` vide déjà toute la table (corbeille comprise), la file d'envoi et les fichiers.
- **Android** : snackbar « « Titre » placée dans la corbeille » avec « Annuler » (durée longue de Material, environ 10 s) ; bouton « Corbeille » à côté de « Statistiques », écran avec « Restaurer », « Supprimer définitivement » et « Vider », les deux derniers après confirmation.
- **iOS** : bandeau `StatusBanner` en bas de la liste avec « Annuler » pendant 6 s (15 s avec VoiceOver, qui annonce le message) ; même écran Corbeille, confirmations par `confirmationDialog`.

## Conséquences

- Supprimer n'efface plus rien de l'appareil pendant 30 jours : une tâche jetée et ses pièces jointes restent dans `todo.db` et le dossier privé (exclus des sauvegardes, effacés à la déconnexion, ADR 0013 et 0014). « Supprimer définitivement » et « Vider » restent disponibles pour effacer tout de suite.
- Comme tout conflit (ADR 0012), il se règle par tâche entière : une modification faite ailleurs après la mise à la corbeille fait revenir la tâche, et une mise à la corbeille plus récente qu'une modification faite ailleurs la jette sur tous les appareils.
- La purge n'a lieu que quand l'app tourne. Un appareil jamais relancé garde ses tâches expirées ; un autre appareil les purge et envoie la suppression. Le serveur réel devra purger lui aussi (et borner `trashedAt` à son horloge, comme `updatedAt`).
- L'écran Compose Multiplatform (ADR 0010) met aussi à la corbeille, sans « Annuler » ni écran Corbeille : la tâche se restaure depuis l'écran natif.
- L'heure de mise à la corbeille est ramenée à l'horloge de l'appareil (reçue ou locale) : une heure en avance ne retarde pas la purge.
- Compatibilité : un client qui n'envoie pas `trashedAt` (version antérieure) ferait revenir une tâche jetée à sa prochaine modification. Le vrai serveur devra garder `trashedAt` quand une version ne le porte pas.
- La corbeille n'est pas dans le widget, l'analytics ni le partage.

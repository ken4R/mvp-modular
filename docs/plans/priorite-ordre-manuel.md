# Plan — Priorités et glisser-déposer des tâches

Objectif : donner une priorité à une tâche (aucune, basse, moyenne, haute) et réordonner la liste au doigt, sur Android et iOS. L'ordre et la priorité suivent la tâche sur les autres appareils, par la file d'envoi du mode hors ligne. Choix techniques : [ADR 0024](../adr/0024-priorite-ordre-manuel.md).

1. **`feature:todo`, domain** :
   - `OrderKey` (clés fractionnaires base 62) et `TodoOrder` (`planMove`, `nextPosition`) dans `domain/order`.
   - `TodoPriority`, `TodoPriorityChange`, `TodoMove` ; `Todo.priority`, `Todo.position`.
   - `TodoFilter.byPriority` (tri, pas filtre) et `allowsReordering` ; `ObserveFilteredTodosUseCase` trie par priorité quand il est choisi.
   - `MoveTodoUseCase`, `SetTodoPriorityUseCase` ; `TodoRepository.setPriority`, `setPositions`.
2. **`feature:todo`, data** :
   - Colonnes `priority` et `position` (migration `7.sqm` : clé `h` + `rowid` pour les tâches existantes), liste triée par `position` puis `rowid`, `selectLastPosition`.
   - Une nouvelle tâche prend la clé après la dernière ; `setPositions` écrit plusieurs clés dans une transaction.
   - Synchronisation : `TodoRecord.priority` et `position`, envoyés comme le titre ; clé invalide reçue ignorée, clé vide gardée locale.
   - Tests `commonTest` : `OrderKeyTest`, `TodoOrderingUseCasesTest`, tri par priorité, persistance, envoi, réception, migration v7 → v8, synchronisation entre deux appareils.
3. **`shared`** : Koin (`MoveTodoUseCase`, `SetTodoPriorityUseCase`), `UseCases.moveTodo()`, `setTodoPriority()`, `todoPriorityChange(...)`, `todoFilter(..., byPriority)`.
4. **Android** : dépendance `sh.calvin.reorderable` ; poignée « ≡ » et glisser-déposer dans `TodoScreen`, actions d'accessibilité « Monter » / « Descendre », puce « Par priorité » dans les filtres, libellé de priorité sous le titre ; section « Priorité » dans la fiche.
5. **iOS** (`FeatureTodo`) : `onMove` dans la `List`, actions VoiceOver, bouton « Par priorité », libellé de priorité ; sélecteur « Priorité » dans `TodoDetailView`.
6. **Vérification** : CI (tests KMP Android + iOS, build Android debug et release, build Xcode avec les démos), revues de code et de sécurité, recette ci-dessous.

## Recette manuelle

- Créer quatre tâches A, B, C, D. Android : maintenir la poignée « ≡ » de D et la glisser entre A et B. iOS : appui long sur D puis glisser. L'ordre devient A, D, B, C et le reste après avoir quitté et relancé l'app.
- Cocher B : elle passe dans les terminées. Essayer de glisser C sous B : elle reste en bas des tâches à faire.
- Fiche de C > Priorité « Haute » : la liste affiche « Priorité haute » en rouge sous C. Puce « Par priorité » : C passe en tête des tâches à faire, la poignée disparaît (Android) et le glisser ne marche plus (iOS). Retirer la puce : l'ordre manuel revient.
- Avec un filtre (« À faire ») ou une recherche : un glisser-déposer place la tâche après sa voisine visible.
- Lecteur d'écran (TalkBack / VoiceOver) : sur une ligne, actions « Monter » et « Descendre ».
- Hors ligne (mode avion) : déplacer une tâche, le bandeau affiche « 1 modification en attente » ; de retour en ligne, il disparaît.

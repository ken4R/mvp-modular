# ADR 0024 — Priorité et ordre manuel des tâches

- Statut : accepté
- Date : 2026-10-27

## Contexte

L'utilisateur veut donner une priorité à une tâche et réordonner la liste au doigt (glisser-déposer), sur Android et iOS. L'ordre et la priorité doivent suivre la tâche sur ses autres appareils, par la file d'envoi du mode hors ligne ([ADR 0012](0012-mode-hors-ligne.md)) : une modification par tâche, la plus récente gagne (`updatedAt`), le serveur gagne à égalité. Jusqu'ici la liste suivait l'ordre de création (`rowid`), propre à chaque appareil.

## Options

- **Ordre** :
  - un index entier par tâche (0, 1, 2…) : déplacer une tâche renumérote toutes celles entre l'ancienne et la nouvelle place, donc autant de modifications dans la file, et deux appareils qui réordonnent en même temps écrasent mutuellement leurs tâches ;
  - un `Double` à mi-chemin des voisines : une seule tâche modifiée, mais la précision s'épuise après une cinquantaine de déplacements au même endroit ;
  - **une clé fractionnaire en texte** (fractional indexing, utilisé par Figma ou Linear) : il existe toujours une clé entre deux autres, une seule tâche change par déplacement, les clés restent courtes.
- **Glisser-déposer Android** : à la main avec `detectDragGesturesAfterLongPress` (défilement automatique, animations et décalages à écrire), ou **la bibliothèque `sh.calvin.reorderable`** (LazyColumn réordonnable, Apache 2.0, maintenue, seule dépendance : Compose foundation).
- **iOS** : `List` + **`onMove`**, natif (appui long puis glisser sans mode édition depuis iOS 16).
- **Priorité et tri** : la liste toujours triée par priorité (le glisser-déposer ne vaudrait qu'à l'intérieur d'une priorité), ou **l'ordre manuel par défaut avec un tri « Par priorité » au choix**.

## Décision

- **`OrderKey`** (`feature:todo`, domain) : clés fractionnaires de David Greenspan (« Implementing Fractional Indexing »), en base 62, comparées caractère par caractère (même ordre en Kotlin et en SQLite, collation binaire). `between(avant, après)` et `between(avant, après, n)`. Une clé valide fait au plus 64 caractères. `""` veut dire « pas de clé ».
- **`Todo.position`** (clé) et **`Todo.priority`** (`TodoPriority` : `None`, `Low`, `Medium`, `High`, niveaux 0 à 3), colonnes `position` et `priority` de `todo.db` (migration `7.sqm`). La migration donne aux tâches existantes la clé `h` + `rowid` sur 8 chiffres : l'ordre de création est gardé, et ce sont des clés valides.
- **Liste** triée par `position`, puis par `rowid` à clé égale (deux appareils peuvent créer la même clé au même moment). Les tâches à faire restent en premier : un déplacement reste dans son groupe (à faire ou terminées). Une nouvelle tâche va en bas.
- **`MoveTodoUseCase(TodoMove(todoId, afterTodoId))`** : la tâche est posée juste après celle affichée au-dessus d'elle une fois lâchée (`null` : tout en haut). `TodoOrder.planMove` calcule les clés à écrire : en général seulement celle de la tâche déplacée ; aussi celles des suivantes qui avaient la même clé que la voisine du dessus, et celles des tâches sans clé (triées en tête), qui en reçoivent une. Chaque tâche réécrite part au serveur en entier et peut écraser une modification faite ailleurs : on en réécrit le moins possible. Tout le groupe n'est renuméroté que si une clé invalide traîne ailleurs qu'en tête ou si une clé produite serait refusée (plus de 64 caractères, plus petite partie entière). Une nouvelle tâche prend la clé après la dernière ; s'il n'y en a plus de valide (dernière clé reçue d'un serveur, déjà au plus long), toutes les tâches sont renumérotées. Lâchée à sa place, rien n'est écrit. Marche aussi sur une liste filtrée : la tâche se place après sa voisine visible.
- **`SetTodoPriorityUseCase(TodoPriorityChange(todoId, priority))`**. Paramètres en classes de premier niveau, appelables depuis Swift ; `todoPriorityChange` (`KoinIos.kt`) construit le paramètre depuis le nom Kotlin de la priorité.
- **Synchronisation** : la priorité et la clé entrent dans `TodoRecord` et dans la file d'envoi comme le titre (une modification datée par tâche). Une version reçue avec une clé invalide est ignorée, comme un titre trop long ; une clé vide (serveur qui ne connaît pas l'ordre) laisse la tâche à sa place.
- **Tri « Par priorité »** : `TodoFilter.byPriority` trie chaque groupe par priorité décroissante puis par ordre manuel. Ce n'est pas un filtre (`isActive` ne change pas, « Tout afficher » le garde) ; tant qu'il est choisi, le glisser-déposer est désactivé (`TodoFilter.allowsReordering`).
- **Android** : `sh.calvin.reorderable` 2.5.1, poignée « ≡ » au début de chaque ligne (le tap sur la ligne ouvre toujours la fiche), retour haptique au début du glissement. La liste suit le doigt localement, l'ordre lâché est montré tout de suite puis remplacé par celui de la base. Actions d'accessibilité « Monter » et « Descendre » sur chaque ligne. Priorité : puces « Aucune / Basse / Moyenne / Haute » dans la fiche, libellé « Priorité haute » coloré sous le titre (couleurs du thème : erreur pour haute, accent pour moyenne).
- **iOS** : `ForEach.onMove` dans la `List`, appui long puis glisser ; un déplacement qui ferait changer de groupe est refusé. Actions VoiceOver « Monter » et « Descendre ». Sélecteur segmenté « Priorité » dans la fiche, même libellé dans la liste.

## Conséquences

- Déplacer une tâche n'envoie qu'une modification (sauf clés en double ou renumérotation, rares). Deux appareils qui déplacent des tâches différentes ne se gênent pas.
- Comme le reste de la synchronisation (ADR 0012), le conflit se règle par tâche entière : déplacer une tâche sur un appareil pendant qu'on renomme la même sur un autre garde la modification la plus récente des deux, pas les deux. Une fusion champ par champ reste à faire côté serveur, avec le vrai backend.
- Les clés de la migration viennent du `rowid`, propre à chaque appareil, et ne partent pas tout de suite : la première modification d'une tâche envoie la clé de l'appareil qui l'a faite. Sur un autre appareil où les tâches sont arrivées dans un autre ordre, une tâche ancienne peut changer de place une fois. Sans vrai backend (le faux serveur est vidé à chaque lancement), ça ne concerne que les essais.
- Un déplacement lit la liste puis écrit les clés, hors transaction : une synchronisation appliquée entre les deux peut donner un ordre approché, jamais une erreur.
- Android : la bibliothèque ne prévoit pas de refuser un déplacement ; une ligne tirée vers l'autre groupe tremble à la frontière, sans effet sur les données.
- Deux appareils qui créent hors ligne une tâche au même moment peuvent lui donner la même clé : l'ordre entre elles dépend alors de l'appareil (rowid), jusqu'au prochain déplacement de l'une d'elles.
- Le serveur doit garder `priority` et `position` tels quels et refuser une clé de plus de 64 caractères ou hors base 62 ; l'app les ignore de toute façon.
- Une dépendance Android de plus (`sh.calvin.reorderable`, sans réseau ni permission, sans réflexion : rien à ajouter pour R8). La retirer plus tard ne touche que `TodoScreen`.
- L'écran Compose Multiplatform (ADR 0010) et le widget suivent le nouvel ordre sans changement, sans glisser-déposer ni priorité.
- L'ordre et la priorité ne sont pas dans le partage d'une tâche, l'export calendrier ni l'analytics.

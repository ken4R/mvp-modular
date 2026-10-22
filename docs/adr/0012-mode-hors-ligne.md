# ADR 0012 — Mode hors ligne : tâches modifiées hors ligne puis synchronisées

- Statut : accepté
- Date : 2026-10-22

## Contexte

Youssef veut un mode hors ligne complet : les tâches restent modifiables sans réseau, puis se synchronisent quand il revient, avec une gestion des conflits. Le prototype n'a **pas encore de backend** (connexion factice, ADR 0003) : la synchronisation doit donc être écrite derrière une interface, avec un faux serveur, pour qu'un vrai backend (Supabase, Firebase, API maison) ne soit qu'une nouvelle implémentation.

Les tâches sont déjà stockées en SQLite (ADR 0002) : l'app marche déjà hors ligne pour elles. Ce qui manque : savoir ce qui n'est pas encore parti, savoir si l'appareil est en ligne, envoyer et réessayer, départager deux appareils, et le montrer à l'utilisateur. Météo et Catalogue dépendent du réseau ; le Catalogue a déjà un cache (ADR 0006).

## Options

File d'envoi :

1. **Journal de toutes les opérations** (ajout, cochée, décochée, supprimée…), rejoué dans l'ordre.
2. **Une ligne par tâche modifiée** (la dernière version gagne localement), le contenu étant lu dans la table des tâches au moment de l'envoi.

Conflits (la même tâche modifiée sur deux appareils) :

- a. **Le dernier écrit gagne** (LWW), à l'heure de la modification, tâche par tâche.
- b. Fusion champ par champ, ou demander à l'utilisateur.
- c. CRDT.

Connectivité :

- **Android** : `ConnectivityManager` (réseau par défaut, capacité « internet validé »), sans bibliothèque.
- **iOS** : `NWPathMonitor` (Network.framework), sans permission.

## Décision

**Option 2 et option a.**

- **`core:common`** : contrat `ConnectivityObserver` (`observeIsOnline()`), comme `LocationProvider`.
- **`core:connectivity`** : `AndroidConnectivityObserver` (en ligne = réseau par défaut avec `NET_CAPABILITY_INTERNET` **et** `NET_CAPABILITY_VALIDATED` : un Wi-Fi sans internet ou un portail captif comptent comme hors ligne ; permission `ACCESS_NETWORK_STATE`, sans danger, déclarée par l'app) et `NetworkPathConnectivityObserver` (iOS, `nw_path_status_satisfied`). `ObserveConnectivityUseCase` pour les bandeaux des écrans Météo et Catalogue.
- **File d'envoi** (`feature:todo`, `todo.db`, migration `2.sqm`) : table `TodoPendingChange(todo_id, is_deleted, changed_at)`, une ligne par tâche modifiée, et colonne `updated_at` dans `TodoEntity`. `SqlDelightTodoLocalDataSource` y écrit **dans la même transaction** que la modification : rien ne se perd si l'app est tuée entre les deux. Les tâches qui existaient avant la migration y entrent avec l'heure 0 (la plus ancienne possible).
- **Ce qui est synchronisé** : le titre, l'état (faite / à faire) et la suppression. **Le rappel reste propre à l'appareil** : c'est une notification programmée localement (ADR 0004), et la changer ne touche ni `updated_at` ni la file. Une version reçue du serveur garde le rappel local.
- **Heure des modifications** : l'horloge de l'appareil, mais toujours strictement après la modification précédente de la même tâche (`max(maintenant, précédente + 1)`), pour que deux modifications dans la même milliseconde ou une horloge qui recule ne se confondent pas.
- **Conflits : le dernier écrit gagne**, tâche par tâche :
  - le serveur garde, pour chaque tâche, la version la plus récente (à égalité, celle reçue : c'est un renvoi) ; les suppressions sont gardées (« pierres tombales ») pour atteindre les autres appareils et empêcher une version plus ancienne de faire réapparaître la tâche ;
  - l'appareil applique une version reçue seulement si elle est plus récente que la sienne **et** si la tâche n'a pas de modification en attente (celle-ci part au prochain envoi et le serveur départage).
- **Synchronisation** (`TodoSyncRepositoryImpl`) : envoie toute la file, retire de la file ce qui a été accepté (sauf une tâche modifiée à nouveau pendant l'envoi, reconnue à son `changed_at`), puis récupère toutes les tâches du compte et les applique. Une seule synchronisation à la fois (`Mutex`).
- **Quand** (`StartTodoSyncUseCase`, lancé une fois par chaque app) : tant que l'appareil est en ligne **et** qu'une session est ouverte, une synchronisation au départ et à chaque retour du réseau, puis une à chaque modification locale. Un échec est retenté après 2 s, 4 s, 8 s… jusqu'à une minute ; une coupure ou une déconnexion annule les essais. `SyncTodosUseCase` : bouton « Réessayer » (échoue tout de suite hors ligne, sans appel).
- **Déconnexion** (`TodoUserSessionObserver`) : attend la fin d'une synchronisation en cours (même verrou), puis efface les tâches **et la file d'envoi** (des modifications jamais envoyées sont perdues, comme le reste des données de l'appareil) et annule les rappels. Jusqu'à la connexion suivante (`onLogin`), toute synchronisation échoue sans rien envoyer ni récupérer : la session n'est effacée qu'après les features, et une synchronisation ne doit pas réécrire les tâches de l'ancien compte entre les deux.
- **Faux serveur** (`FakeTodoRemoteDataSource`) : en mémoire, 400 ms par aller-retour, applique la même règle que le vrai, répond `AppError.Network` quand l'appareil est hors ligne. Il n'a qu'un compte : il est vidé à la déconnexion. Il est perdu à la fermeture de l'app (la base locale garde tout).
- **Écrans** : bandeau `StatusBanner` (tokens `secondary` / `onSecondary`) — Tâches : « Hors ligne · N modifications en attente » ou « Synchronisation impossible · … » avec « Réessayer » ; Météo : « Hors ligne · la météo s'affichera au retour du réseau » ; Catalogue : « Hors ligne · catalogue enregistré… », et l'actualisation ou la page suivante qui ont échoué sont relancées au retour du réseau. Rien n'est affiché quand tout va bien (pas de « Synchronisation… » à chaque tâche cochée).

## Conséquences

- Un vrai backend = une implémentation de `TodoRemoteDataSource` (Ktor, `safeApiCall`, jeton de `feature:auth`) qui applique la même règle côté serveur ; `signOut()` y oublie le jeton. `fetchAll()` récupère tout le compte : à remplacer par un envoi incrémental (`depuis`) quand le nombre de tâches grandira.
- LWW perd une modification en cas de vrai conflit (deux appareils modifient la même tâche hors ligne) : celle faite en dernier gagne, entière. Acceptable pour une liste de tâches ; une fusion par champ (b) serait l'étape suivante si besoin.
- LWW fait confiance aux horloges des appareils : une horloge très en avance gagne les conflits. Un vrai serveur pourra dater lui-même les modifications à la réception (au prix d'un ordre moins fidèle hors ligne).
- Les pierres tombales ne sont jamais purgées par le faux serveur ; un vrai serveur les purgera après un délai (30 jours par exemple), au-delà duquel un appareil resté hors ligne repartira de zéro.
- L'ordre des tâches suit l'ordre d'arrivée sur chaque appareil (rowid), pas l'ordre de création ailleurs.
- La file ne contient que des identifiants et des heures (les titres restent dans `TodoEntity`) ; elle est effacée à la déconnexion avec le reste. Les deux tables sont dans `todo.db`, déjà concerné par le suivi « exclure les bases SQLite des sauvegardes iCloud ».
- L'écran Tâches en Compose Multiplatform (ADR 0010) n'affiche pas le bandeau : il reste une expérience de comparaison.
- Le widget (ADR 0011) suit tout seul : il lit la même base, mise à jour par la synchronisation.
- Sur le simulateur iOS, le réseau est celui du Mac : pour essayer le mode hors ligne, couper le Wi-Fi du Mac (ou utiliser Network Link Conditioner). Sur l'émulateur Android : mode Avion.

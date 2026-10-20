# ADR 0002 — Persistance locale : SQLDelight dans un module `core:database`

- Statut : accepté
- Date : 2026-10-20

## Contexte

Les tâches (`feature:todo`) et les réglages (`feature:settings`) étaient gardés en mémoire et repartaient à zéro à chaque lancement. Il faut les conserver sur l'appareil, sur Android et iOS, avec la logique en KMP et sans toucher au domaine : chaque feature a déjà son interface `*LocalDataSource` dans `data/source`.

## Options

- **SQLDelight 2.4** : KMP mature, SQL vérifié à la compilation, driver Android (framework SQLite) et iOS (SQLite système), `Flow` via `coroutines-extensions`. Pas de KSP.
- **Room KMP** : demande KSP, aligné version par version sur Kotlin, plus lourd à configurer pour deux tables.
- **DataStore** pour les réglages + une base pour les tâches : deux technologies pour un même besoin.

## Décision

- **SQLDelight** pour les deux features, une base (un fichier) par feature : `todo.db`, `settings.db`. Chaque feature garde son schéma `.sq` dans `src/commonMain/sqldelight` et reste indépendante des autres.
- Nouveau module **`core:database`** : `DatabaseDriverFactory` (ouvre une base pour un schéma) et `databaseModule` (Koin, `expect`/`actual`). Android : `AndroidSqliteDriver` avec le `Context` de `androidContext(...)`, dans le dossier `no_backup`. iOS : `NativeSqliteDriver`, framework lié à `-lsqlite3`.
- Tâches : une ligne par tâche, l'ordre de création (rowid) conservé par des `UPDATE`. Réglages : table clé / valeur, une clé absente vaut la valeur par défaut, donc ajouter un réglage ne demande pas de migration.
- **`core:database-testing`** : base en mémoire pour les tests `commonTest` (driver JDBC sur la JVM, driver natif sur iOS). Les tests recréent une data source sur la même base pour simuler un redémarrage.
- Les implémentations en mémoire restent, pour les tests des use cases.

## Conséquences

- Évolution du schéma : incrémenter la version via des fichiers de migration `.sqm` SQLDelight ; ne jamais renommer une clé de réglage sans migration.
- Sécurité (MASVS-STORAGE) : la base n'est pas chiffrée. Elle contient le nom et l'e-mail du profil, saisis par l'utilisateur et peu sensibles ; elle reste dans le bac à sable de l'app, hors sauvegardes Android (`allowBackup="false"`, dossier `no_backup`) et chiffrée au repos par iOS (Data Protection). Les secrets (jetons de la future connexion) n'iront **pas** dans cette base mais dans le Keychain / Keystore.
- Le framework iOS exporte aussi les classes générées par SQLDelight des features ; Swift ne doit passer que par `UseCases`.

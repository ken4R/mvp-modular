# Plan — Persistance locale des tâches et des réglages

Objectif : les tâches et les réglages survivent au redémarrage de l'app, sur Android et iOS, sans changer le domaine ni l'UI. Choix techniques : [ADR 0002](../adr/0002-persistance-sqldelight.md).

1. **Build** : SQLDelight 2.4.1 dans `libs.versions.toml` (runtime, coroutines, drivers Android / natif / JDBC, plugin).
2. **`core:database`** : `DatabaseDriverFactory` + `databaseModule` (`expect`/`actual`), ajouté à `sharedModules` ; `shared` lie `-lsqlite3`.
3. **`core:database-testing`** : `createInMemoryDriver(schema)` pour `commonTest`.
4. **`feature:todo`** : `TodoEntity.sq`, `SqlDelightTodoLocalDataSource`, branché dans `todoModule`. Tests : persistance après redémarrage, ordre conservé, suppression, émission du Flow.
5. **`feature:settings`** : `SettingEntity.sq` (clé / valeur), `SqlDelightSettingsLocalDataSource`, branché dans `settingsModule`. Tests : valeurs par défaut, persistance après redémarrage, mises à jour successives.
6. **Apps** : rien à changer (Android fournit déjà `androidContext`, iOS n'a besoin de rien).
7. **Vérification** : CI (tests KMP Android + iOS, build Android, build Xcode). Recette manuelle : ajouter des tâches et changer des réglages, tuer l'app, la relancer, tout est là.

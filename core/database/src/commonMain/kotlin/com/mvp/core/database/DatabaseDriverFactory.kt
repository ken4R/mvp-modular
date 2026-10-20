package com.mvp.core.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema

/**
 * Ouvre une base SQLite sur l'appareil. Chaque feature déclare sa propre base SQLDelight
 * (son schéma, son fichier) et l'ouvre avec cette fabrique, injectée par [databaseModule].
 * La base est créée au premier lancement, puis migrée quand la version du schéma augmente.
 */
fun interface DatabaseDriverFactory {
    fun create(schema: SqlSchema<QueryResult.Value<Unit>>, name: String): SqlDriver
}

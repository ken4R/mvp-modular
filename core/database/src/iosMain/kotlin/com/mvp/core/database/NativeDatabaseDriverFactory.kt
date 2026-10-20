package com.mvp.core.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.native.NativeSqliteDriver

/** Bases dans le dossier de l'app, protégées par le chiffrement iOS (Data Protection). */
internal class NativeDatabaseDriverFactory : DatabaseDriverFactory {
    override fun create(schema: SqlSchema<QueryResult.Value<Unit>>, name: String): SqlDriver =
        NativeSqliteDriver(schema, name)
}

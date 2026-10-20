package com.mvp.core.database

import android.content.Context
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

internal class AndroidDatabaseDriverFactory(private val context: Context) : DatabaseDriverFactory {

    // Dossier no_backup : les bases restent hors des sauvegardes et transferts Android.
    override fun create(schema: SqlSchema<QueryResult.Value<Unit>>, name: String): SqlDriver =
        AndroidSqliteDriver(schema, context, name, useNoBackupDirectory = true)
}

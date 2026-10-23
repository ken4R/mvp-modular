package com.mvp.core.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

internal class AndroidDatabaseDriverFactory(private val context: Context) : DatabaseDriverFactory {

    // Dossier no_backup : les bases restent hors des sauvegardes et transferts Android.
    override fun create(schema: SqlSchema<QueryResult.Value<Unit>>, name: String): SqlDriver =
        AndroidSqliteDriver(schema, context, name, callback = SecureDeleteCallback(schema), useNoBackupDirectory = true)
}

/** Active `secure_delete` à chaque ouverture, avant la création ou la migration du schéma. */
private class SecureDeleteCallback(schema: SqlSchema<QueryResult.Value<Unit>>) : AndroidSqliteDriver.Callback(schema) {
    override fun onConfigure(db: SupportSQLiteDatabase) {
        super.onConfigure(db)
        // Le PRAGMA renvoie une ligne : il passe par query (execSQL le refuserait), lue pour l'exécuter.
        db.query(SECURE_DELETE_PRAGMA).use { it.moveToFirst() }
    }
}

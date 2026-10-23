package com.mvp.core.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSLog
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask

/**
 * Bases dans le dossier de l'app, protégées par le chiffrement iOS (Data Protection), exclues des
 * sauvegardes iCloud et de l'ordinateur, et écrasées à la suppression (`secure_delete`).
 */
internal class NativeDatabaseDriverFactory : DatabaseDriverFactory {
    override fun create(schema: SqlSchema<QueryResult.Value<Unit>>, name: String): SqlDriver =
        NativeSqliteDriver(
            schema = schema,
            name = name,
            onConfiguration = { config ->
                config.copy(
                    extendedConfig = config.extendedConfig.copy(basePath = databaseDirectory()),
                    lifecycleConfig = config.lifecycleConfig.copy(
                        onCreateConnection = { connection ->
                            config.lifecycleConfig.onCreateConnection(connection)
                            connection.rawExecSql(SECURE_DELETE_PRAGMA)
                        },
                    ),
                )
            },
        )
}

/**
 * Le dossier par défaut de SQLiter (Application Support/databases) : les bases déjà créées y restent.
 * Le marquer exclu des sauvegardes couvre tout son contenu, journaux WAL compris.
 */
@OptIn(ExperimentalForeignApi::class)
private fun databaseDirectory(): String {
    val fileManager = NSFileManager.defaultManager
    val directory = fileManager
        .URLForDirectory(NSApplicationSupportDirectory, NSUserDomainMask, appropriateForURL = null, create = true, error = null)
        ?.URLByAppendingPathComponent(DATABASE_FOLDER, isDirectory = true)
        ?: error("Dossier Application Support introuvable")
    fileManager.createDirectoryAtURL(directory, withIntermediateDirectories = true, attributes = null, error = null)
    if (!directory.setResourceValue(true, forKey = NSURLIsExcludedFromBackupKey, error = null)) {
        NSLog("core:database : impossible d'exclure les bases des sauvegardes")
    }
    return directory.path ?: error("Chemin du dossier des bases introuvable")
}

private const val DATABASE_FOLDER = "databases"

package com.mvp.core.database.testing

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema

/**
 * Base vierge en mémoire, avec le [schema] déjà créé. Recréer une data source sur le même
 * driver simule un redémarrage de l'app : les données doivent toujours être là.
 */
expect fun createInMemoryDriver(schema: SqlSchema<QueryResult.Value<Unit>>): SqlDriver

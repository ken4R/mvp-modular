package com.mvp.feature.onboarding.data.source

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.mvp.feature.onboarding.data.db.OnboardingDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * État de la présentation dans sa propre base SQLite, hors de la session : aucun `UserSessionObserver`,
 * la déconnexion n'y touche pas (ADR 0016).
 */
internal class SqlDelightOnboardingLocalDataSource(
    database: OnboardingDatabase,
    private val dispatcher: CoroutineDispatcher,
) : OnboardingLocalDataSource {

    private val queries = database.onboardingStateEntityQueries

    // Valeur illisible (écrite par une version future ?) : lue comme jamais vue, la présentation est remontrée.
    override fun observeCompletedVersion(): Flow<Int?> =
        queries.selectByKey(KEY_COMPLETED_VERSION).asFlow().mapToOneOrNull(dispatcher).map { it?.toIntOrNull() }

    override fun setCompletedVersion(version: Int?) {
        if (version == null) {
            queries.deleteByKey(KEY_COMPLETED_VERSION)
        } else {
            queries.upsert(KEY_COMPLETED_VERSION, version.toString())
        }
    }
}

// Clé stockée sur l'appareil : ne pas la renommer sans migration.
private const val KEY_COMPLETED_VERSION = "completed_version"

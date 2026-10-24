package com.mvp.feature.onboarding.data.source

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Stockage local de l'état de la présentation. L'app utilise [SqlDelightOnboardingLocalDataSource] (SQLite,
 * persistant) ; l'implémentation en mémoire sert aux tests des use cases.
 */
interface OnboardingLocalDataSource {
    fun observeCompletedVersion(): Flow<Int?>
    fun setCompletedVersion(version: Int?)
}

internal class InMemoryOnboardingLocalDataSource(initial: Int? = null) : OnboardingLocalDataSource {

    private val completed = MutableStateFlow(initial)

    override fun observeCompletedVersion(): Flow<Int?> = completed.asStateFlow()

    override fun setCompletedVersion(version: Int?) {
        completed.value = version
    }
}

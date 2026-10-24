package com.mvp.feature.onboarding.domain.repository

import com.mvp.core.common.domain.result.AppResult
import kotlinx.coroutines.flow.Flow

/** Version de la présentation vue en entier ou passée sur cet appareil, ou `null` si jamais vue. */
interface OnboardingRepository {
    fun observeCompletedVersion(): Flow<Int?>
    suspend fun setCompletedVersion(version: Int?): AppResult<Unit>
}

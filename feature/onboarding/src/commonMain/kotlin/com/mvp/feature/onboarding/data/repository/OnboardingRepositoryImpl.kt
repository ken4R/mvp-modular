package com.mvp.feature.onboarding.data.repository

import com.mvp.core.common.data.safeCall
import com.mvp.core.common.domain.result.AppResult
import com.mvp.feature.onboarding.data.source.OnboardingLocalDataSource
import com.mvp.feature.onboarding.domain.repository.OnboardingRepository
import kotlinx.coroutines.flow.Flow

internal class OnboardingRepositoryImpl(private val local: OnboardingLocalDataSource) : OnboardingRepository {

    override fun observeCompletedVersion(): Flow<Int?> = local.observeCompletedVersion()

    override suspend fun setCompletedVersion(version: Int?): AppResult<Unit> = safeCall { local.setCompletedVersion(version) }
}

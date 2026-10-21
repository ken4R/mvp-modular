package com.mvp.feature.auth.data.session

import com.mvp.core.common.domain.session.SessionExpiry
import com.mvp.feature.auth.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Expose la fin de session aux autres features, sans le jeton. */
internal class AuthSessionExpiry(private val repository: AuthRepository) : SessionExpiry {
    override fun observeExpiresAt(): Flow<Long?> =
        repository.observeSession().map { it?.expiresAtEpochMillis }.distinctUntilChanged()
}

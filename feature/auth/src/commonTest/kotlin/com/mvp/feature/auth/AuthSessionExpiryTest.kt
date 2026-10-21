package com.mvp.feature.auth

import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.feature.auth.data.repository.AuthRepositoryImpl
import com.mvp.feature.auth.data.session.AuthSessionExpiry
import com.mvp.feature.auth.data.source.FakeAuthRemoteDataSource
import com.mvp.feature.auth.data.source.SecureSessionLocalDataSource
import com.mvp.feature.auth.domain.model.Credentials
import com.mvp.feature.auth.domain.usecase.LoginUseCase
import com.mvp.feature.auth.domain.usecase.LogoutUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days

class AuthSessionExpiryTest {

    private val scope = TestScope()
    private val dispatcher = StandardTestDispatcher(scope.testScheduler)
    private var now = 1_000_000L
    private val repository = AuthRepositoryImpl(
        FakeAuthRemoteDataSource(now = { now }, sessionDuration = 7.days, tokenGenerator = { "jeton" }),
        SecureSessionLocalDataSource(InMemorySecureStorage()),
        now = { now },
    )
    private val expiry = AuthSessionExpiry(repository)
    private val demo = Credentials("jean.dupont@exemple.fr", FakeAuthRemoteDataSource.DEMO_PASSWORD)

    @Test
    fun exposesSessionEndOnlyWhileLoggedIn() = scope.runTest {
        assertNull(expiry.observeExpiresAt().first())

        LoginUseCase(repository, emptyList(), dispatcher)(demo)
        assertEquals(1_000_000L + 7.days.inWholeMilliseconds, expiry.observeExpiresAt().first())

        LogoutUseCase(repository, emptyList(), dispatcher)(NoParams)
        assertNull(expiry.observeExpiresAt().first())
    }

    @Test
    fun expiredSessionIsAbsent() = scope.runTest {
        LoginUseCase(repository, emptyList(), dispatcher)(demo)

        now += 8.days.inWholeMilliseconds

        assertNull(expiry.observeExpiresAt().first())
    }
}

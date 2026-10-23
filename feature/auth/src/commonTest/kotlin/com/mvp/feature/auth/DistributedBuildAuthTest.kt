package com.mvp.feature.auth

import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.result.getOrNull
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.feature.auth.data.repository.AuthRepositoryImpl
import com.mvp.feature.auth.data.source.FakeAuthRemoteDataSource
import com.mvp.feature.auth.data.source.SecureSessionLocalDataSource
import com.mvp.feature.auth.data.source.UnavailableAuthRemoteDataSource
import com.mvp.feature.auth.domain.model.Credentials
import com.mvp.feature.auth.domain.usecase.LoginUseCase
import com.mvp.feature.auth.domain.usecase.ObserveSessionUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Build distribué (release) : le faux service est remplacé, ses sessions ne sont plus acceptées. */
class DistributedBuildAuthTest {

    private val scope = TestScope()
    private val dispatcher = StandardTestDispatcher(scope.testScheduler)
    private val storage = InMemorySecureStorage()
    private val observer = RecordingSessionObserver()
    private val demo = Credentials("jean.dupont@exemple.fr", FakeAuthRemoteDataSource.DEMO_PASSWORD)

    private fun distributedRepository() =
        AuthRepositoryImpl(UnavailableAuthRemoteDataSource(), SecureSessionLocalDataSource(storage), acceptsDemoSessions = false)

    @Test
    fun demoPasswordIsRefusedAsUnavailable() = scope.runTest {
        val repository = distributedRepository()

        val result = LoginUseCase(repository, listOf(observer), dispatcher)(demo)

        assertEquals(AppError.Unavailable, (result as AppResult.Failure).error)
        assertTrue(storage.values.isEmpty(), "aucune session ne doit être enregistrée")
        assertTrue(observer.logins.isEmpty())
    }

    @Test
    fun demoSessionLeftOnDeviceOpensNothing() = scope.runTest {
        // Session créée par un build de développement, puis l'app relancée en build distribué.
        val developmentRepository = AuthRepositoryImpl(FakeAuthRemoteDataSource(), SecureSessionLocalDataSource(storage))
        LoginUseCase(developmentRepository, listOf(observer), dispatcher)(demo)
        assertEquals(demo.email, ObserveSessionUseCase(developmentRepository, dispatcher)(NoParams).first().getOrNull()?.email)

        val session = ObserveSessionUseCase(distributedRepository(), dispatcher)(NoParams).first()

        assertNull(session.getOrNull())
    }
}

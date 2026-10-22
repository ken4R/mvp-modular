package com.mvp.feature.auth

import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.result.getOrNull
import com.mvp.core.common.domain.session.UserAccount
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.feature.auth.data.repository.AuthRepositoryImpl
import com.mvp.feature.auth.data.source.FakeAuthRemoteDataSource
import com.mvp.feature.auth.data.source.SecureSessionLocalDataSource
import com.mvp.feature.auth.domain.model.Credentials
import com.mvp.feature.auth.domain.usecase.LoginUseCase
import com.mvp.feature.auth.domain.usecase.LogoutUseCase
import com.mvp.feature.auth.domain.usecase.ObserveSessionUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

class AuthUseCasesTest {

    private val scope = TestScope()
    private val dispatcher = StandardTestDispatcher(scope.testScheduler)
    private val storage = InMemorySecureStorage()
    private val observer = RecordingSessionObserver()

    /** Horloge contrôlée par le test, partagée par le faux service et le repository. */
    private var now = 1_000_000L

    /** Nouvelle pile complète sur le même stockage : équivaut à un redémarrage de l'app. */
    private fun useCases(): Triple<LoginUseCase, LogoutUseCase, ObserveSessionUseCase> {
        val remote = FakeAuthRemoteDataSource(now = { now }, sessionDuration = 7.days, tokenGenerator = { "jeton-$now" })
        val repository = AuthRepositoryImpl(remote, SecureSessionLocalDataSource(storage), now = { now })
        return Triple(
            LoginUseCase(repository, listOf(observer), dispatcher),
            LogoutUseCase(repository, listOf(observer), dispatcher),
            ObserveSessionUseCase(repository, dispatcher),
        )
    }

    private var stack = useCases()
    private val login get() = stack.first
    private val logout get() = stack.second
    private val observe get() = stack.third

    private suspend fun account(): UserAccount? = observe(NoParams).first().getOrNull()

    private val demo = Credentials("jean.dupont@exemple.fr", FakeAuthRemoteDataSource.DEMO_PASSWORD)

    @Test
    fun firstLaunchHasNoSession() = scope.runTest {
        assertNull(account())
    }

    @Test
    fun loginOpensSessionWithAccountFromEmail() = scope.runTest {
        val result = login(demo)

        val expected = UserAccount(id = "demo:jean.dupont@exemple.fr", displayName = "Jean Dupont", email = "jean.dupont@exemple.fr")
        assertEquals(AppResult.Success(expected), result)
        assertEquals(expected, account())
    }

    @Test
    fun emailIsTrimmedAndLowercased() = scope.runTest {
        val result = login(demo.copy(email = "  Jean.Dupont@Exemple.FR "))

        assertEquals("jean.dupont@exemple.fr", result.getOrNull()?.email)
    }

    @Test
    fun sessionSurvivesRestart() = scope.runTest {
        login(demo)

        stack = useCases()

        assertEquals("jean.dupont@exemple.fr", account()?.email)
    }

    @Test
    fun tokenIsStoredButPasswordIsNot() = scope.runTest {
        login(demo)

        assertTrue(storage.values.containsValue("jeton-$now"))
        assertTrue(storage.values.values.none { it.contains(demo.password) })
    }

    @Test
    fun wrongPasswordIsUnauthorized() = scope.runTest {
        val result = login(demo.copy(password = "mauvais"))

        assertEquals(AppError.Unauthorized, (result as AppResult.Failure).error)
        assertNull(account())
    }

    @Test
    fun invalidInputIsRejectedWithoutCallingTheService() = scope.runTest {
        listOf(
            demo.copy(email = "  "),
            demo.copy(email = "pas-un-email"),
            demo.copy(email = "a".repeat(LoginUseCase.MAX_EMAIL_LENGTH) + "@exemple.fr"),
            demo.copy(password = ""),
            demo.copy(password = "x".repeat(LoginUseCase.MAX_PASSWORD_LENGTH + 1)),
        ).forEach { credentials ->
            val result = login(credentials)
            assertIs<AppError.Validation>((result as AppResult.Failure).error, "$credentials")
        }
        assertNull(account())
        assertEquals(0L, testScheduler.currentTime, "le faux service (et sa latence) ne doit pas être appelé")
    }

    @Test
    fun passwordIsMaskedInLogs() {
        assertTrue(FakeAuthRemoteDataSource.DEMO_PASSWORD !in demo.toString())
    }

    @Test
    fun loginIsNotALogout() = scope.runTest {
        // Première connexion : les données restantes sont effacées, mais ce n'est pas une déconnexion
        // (un lien profond reçu avant la connexion doit survivre).
        login(demo)

        assertEquals(1, observer.clearCount)
        assertEquals(0, observer.logoutCount)
    }

    @Test
    fun featuresAreToldAboutLogin() = scope.runTest {
        login(demo)

        assertEquals(listOf("jean.dupont@exemple.fr"), observer.logins.map { it.email })
    }

    @Test
    fun loginWithoutKnownPreviousAccountWipesLeftoverData() = scope.runTest {
        // Données sans session connue (sauvegarde restaurée, clé perdue) : on efface par prudence.
        login(demo)

        assertEquals(1, observer.clearCount)
    }

    @Test
    fun expiredSessionIsLoggedOut() = scope.runTest {
        login(demo)

        now += 8.days.inWholeMilliseconds
        stack = useCases()

        assertNull(account())
    }

    @Test
    fun sameAccountAfterExpiryKeepsItsData() = scope.runTest {
        login(demo)
        now += 8.days.inWholeMilliseconds
        stack = useCases()

        login(demo)

        assertEquals(1, observer.clearCount, "seul le premier login efface")
        assertEquals("jean.dupont@exemple.fr", account()?.email)
    }

    @Test
    fun otherAccountAfterExpiryWipesPreviousData() = scope.runTest {
        login(demo)
        now += 8.days.inWholeMilliseconds
        stack = useCases()

        login(demo.copy(email = "marie@exemple.fr"))

        assertEquals(2, observer.clearCount)
        assertEquals("marie@exemple.fr", account()?.email)
    }

    @Test
    fun logoutWipesDataThenSession() = scope.runTest {
        login(demo)

        assertEquals(AppResult.Success(Unit), logout(NoParams))

        assertEquals(2, observer.clearCount)
        assertEquals(1, observer.logoutCount)
        assertNull(account())
        assertTrue(storage.values.isEmpty(), "aucune trace de la session ne doit rester : ${storage.values.keys}")
    }

    @Test
    fun failedWipeKeepsSessionSoLogoutCanBeRetried() = scope.runTest {
        login(demo)
        observer.failClear = true

        assertIs<AppResult.Failure>(logout(NoParams))
        assertEquals("jean.dupont@exemple.fr", account()?.email)
        assertEquals(0, observer.logoutCount)

        observer.failClear = false
        assertEquals(AppResult.Success(Unit), logout(NoParams))
        assertNull(account())
    }

    @Test
    fun partiallyStoredSessionIsIgnored() = scope.runTest {
        login(demo)
        storage.remove("auth.session.user_id")

        stack = useCases()

        assertNull(account())
    }

    @Test
    fun unreadableStorageAtLaunchStillLetsUserLogIn() = scope.runTest {
        storage.failReads = true
        val emissions = mutableListOf<UserAccount?>()
        val job = launch { observe(NoParams).collect { emissions += it.getOrNull() } }
        advanceUntilIdle()

        storage.failReads = false
        login(demo)
        advanceUntilIdle()

        assertEquals(listOf(null, demo.email), emissions.map { it?.email })
        job.cancel()
    }

    @Test
    fun everyFeatureIsWipedEvenIfOneFails() = scope.runTest {
        val failing = RecordingSessionObserver(failClear = true)
        val other = RecordingSessionObserver()
        val remote = FakeAuthRemoteDataSource(now = { now })
        val repository = AuthRepositoryImpl(remote, SecureSessionLocalDataSource(storage), now = { now })
        LoginUseCase(repository, listOf(other), dispatcher)(demo)

        val result = LogoutUseCase(repository, listOf(failing, other), dispatcher)(NoParams)

        assertIs<AppResult.Failure>(result)
        assertEquals(1, failing.clearCount)
        assertEquals(2, other.clearCount, "effacée à la connexion puis à la déconnexion")
    }

    @Test
    fun failedWipeOnAccountChangeRefusesLoginAndKeepsPreviousSession() = scope.runTest {
        login(demo)
        now += 8.days.inWholeMilliseconds
        stack = useCases()
        observer.failClear = true

        assertIs<AppResult.Failure>(login(demo.copy(email = "marie@exemple.fr")))

        assertEquals(listOf("jean.dupont@exemple.fr"), observer.logins.map { it.email })
        assertTrue(storage.values.containsValue("jean.dupont@exemple.fr"))
    }

    @Test
    fun failedPrefillDoesNotBlockLogin() = scope.runTest {
        observer.failLogin = true

        assertIs<AppResult.Success<*>>(login(demo))
        assertEquals(demo.email, account()?.email)
    }
}

package com.mvp.core.featureflags

import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.core.featureflags.data.FeatureFlagRepositoryImpl
import com.mvp.core.featureflags.data.remote.HostedJsonFlagSource
import com.mvp.core.featureflags.data.remote.RemoteFlagSource
import com.mvp.core.featureflags.data.remote.RemoteFlagsParser
import com.mvp.core.featureflags.data.store.FlagStore
import com.mvp.core.featureflags.domain.model.FeatureFlag
import com.mvp.core.featureflags.domain.model.FeatureFlagResolver
import com.mvp.core.featureflags.domain.model.FlagInputs
import com.mvp.core.featureflags.domain.model.FlagOrigin
import com.mvp.core.featureflags.domain.usecase.ClearFeatureFlagOverridesUseCase
import com.mvp.core.featureflags.domain.usecase.ObserveFeatureFlagUseCase
import com.mvp.core.featureflags.domain.usecase.ObserveFeatureFlagsUseCase
import com.mvp.core.featureflags.domain.usecase.RefreshRemoteFlagsUseCase
import com.mvp.core.featureflags.domain.usecase.SetFeatureFlagOverrideUseCase
import com.mvp.core.files.PrivateFileStore
import com.mvp.core.network.createHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FeatureFlagsTest {

    private val scope = TestScope()
    private val dispatcher = StandardTestDispatcher(scope.testScheduler)
    private val files = InMemoryFileStore()
    private val remote = FakeRemoteFlagSource()
    private var clock = 1_000_000L

    private fun repository(isDevBuild: Boolean) =
        FeatureFlagRepositoryImpl(FlagStore(files), remote, isDevBuild, now = { clock })

    // --- Règle de priorité ---

    @Test
    fun inDevelopmentFlagIsAlwaysOffInRelease() {
        val inputs = FlagInputs(remote = mapOf("preview_banner" to true), overrides = mapOf("preview_banner" to true))
        val state = FeatureFlagResolver.resolve(FeatureFlag.PreviewBanner, inputs, isDevBuild = false)
        assertFalse(state.enabled)
        assertEquals(FlagOrigin.ReleaseLocked, state.origin)
    }

    @Test
    fun inDevelopmentFlagFollowsOverrideInDebugButNeverRemote() {
        val remoteOnly = FlagInputs(remote = mapOf("preview_banner" to true))
        assertFalse(FeatureFlagResolver.resolve(FeatureFlag.PreviewBanner, remoteOnly, isDevBuild = true).enabled)

        val overridden = FlagInputs(overrides = mapOf("preview_banner" to true))
        val state = FeatureFlagResolver.resolve(FeatureFlag.PreviewBanner, overridden, isDevBuild = true)
        assertTrue(state.enabled)
        assertEquals(FlagOrigin.Override, state.origin)
    }

    @Test
    fun releasedFlagUsesOverrideThenRemoteThenDefault() {
        assertEquals(
            FlagOrigin.Default to true,
            FeatureFlagResolver.resolve(FeatureFlag.Catalog, FlagInputs(), isDevBuild = false).let { it.origin to it.enabled },
        )
        val remoteOff = FlagInputs(remote = mapOf("catalog" to false))
        assertEquals(
            FlagOrigin.Remote to false,
            FeatureFlagResolver.resolve(FeatureFlag.Catalog, remoteOff, isDevBuild = false).let { it.origin to it.enabled },
        )
        val overridden = remoteOff.copy(overrides = mapOf("catalog" to true))
        assertEquals(
            FlagOrigin.Override to true,
            FeatureFlagResolver.resolve(FeatureFlag.Catalog, overridden, isDevBuild = true).let { it.origin to it.enabled },
        )
        // En release, un réglage local laissé sur l'appareil n'a aucun effet.
        assertEquals(
            FlagOrigin.Remote to false,
            FeatureFlagResolver.resolve(FeatureFlag.Catalog, overridden, isDevBuild = false).let { it.origin to it.enabled },
        )
    }

    // --- Lecture du JSON distant ---

    @Test
    fun parserKeepsOnlyBooleanValuesOfReleasedKnownFlags() {
        val json = """{"schemaVersion":1,"flags":{"catalog":false,"preview_banner":true,"inconnu":true}}"""
        assertEquals(mapOf("catalog" to false), RemoteFlagsParser.parse(json.encodeToByteArray()))

        val wrongTypes = """{"schemaVersion":1,"flags":{"catalog":"false"}}"""
        assertEquals(emptyMap<String, Boolean>(), RemoteFlagsParser.parse(wrongTypes.encodeToByteArray()))
    }

    @Test
    fun parserRejectsOtherSchemaVersionsInvalidJsonAndOversizedResponses() {
        assertFailsWith<Exception> { RemoteFlagsParser.parse("""{"schemaVersion":2,"flags":{}}""".encodeToByteArray()) }
        assertFailsWith<Exception> { RemoteFlagsParser.parse("""{"schemaVersion":"1","flags":{}}""".encodeToByteArray()) }
        assertFailsWith<Exception> { RemoteFlagsParser.parse("pas du json".encodeToByteArray()) }
        assertFailsWith<Exception> { RemoteFlagsParser.parse(ByteArray(RemoteFlagsParser.MAX_BYTES + 1) { ' '.code.toByte() }) }
    }

    @Test
    fun hostedSourceReadsTheJsonAndRefusesOversizedBodiesWithoutContentLength() = scope.runTest {
        val ok = HostedJsonFlagSource(client("""{"schemaVersion":1,"flags":{"catalog":false}}"""), URL)
        assertEquals(mapOf("catalog" to false), ok.fetch())

        val big = """{"schemaVersion":1,"flags":{"catalog":false},"x":"${"a".repeat(RemoteFlagsParser.MAX_BYTES)}"}"""
        assertFailsWith<Exception> { HostedJsonFlagSource(client(big), URL).fetch() }
    }

    @Test
    fun hostedSourceRequiresHttps() {
        assertFailsWith<IllegalArgumentException> { HostedJsonFlagSource(client("{}"), "http://exemple.fr/flags.json") }
    }

    // --- Use cases ---

    @Test
    fun remoteValueIsAppliedThenKeptInCacheAcrossLaunches() = scope.runTest {
        remote.values = mapOf("catalog" to false)
        val repo = repository(isDevBuild = false)
        val observe = ObserveFeatureFlagUseCase(repo, dispatcher)
        assertEquals(AppResult.Success(true), observe(FeatureFlag.Catalog).first())

        assertEquals(AppResult.Success(true), RefreshRemoteFlagsUseCase(repo, { clock }, dispatcher)(RefreshRemoteFlagsUseCase.Params()))
        assertEquals(AppResult.Success(false), observe(FeatureFlag.Catalog).first())

        // Lancement suivant hors ligne : la dernière valeur connue s'applique.
        remote.failure = IllegalStateException("hors ligne")
        val nextLaunch = repository(isDevBuild = false)
        assertEquals(AppResult.Success(false), ObserveFeatureFlagUseCase(nextLaunch, dispatcher)(FeatureFlag.Catalog).first())
        val refresh = RefreshRemoteFlagsUseCase(nextLaunch, { clock }, dispatcher)(RefreshRemoteFlagsUseCase.Params(force = true))
        assertIs<AppResult.Failure>(refresh)
        assertEquals(AppResult.Success(false), ObserveFeatureFlagUseCase(nextLaunch, dispatcher)(FeatureFlag.Catalog).first())
    }

    @Test
    fun refreshIsSkippedWithinFifteenMinutesUnlessForced() = scope.runTest {
        val repo = repository(isDevBuild = true)
        val refresh = RefreshRemoteFlagsUseCase(repo, { clock }, dispatcher)
        assertEquals(AppResult.Success(true), refresh(RefreshRemoteFlagsUseCase.Params()))
        clock += 60_000
        assertEquals(AppResult.Success(false), refresh(RefreshRemoteFlagsUseCase.Params()))
        assertEquals(AppResult.Success(true), refresh(RefreshRemoteFlagsUseCase.Params(force = true)))
        clock += RefreshRemoteFlagsUseCase.DEFAULT_MIN_INTERVAL_MILLIS
        assertEquals(AppResult.Success(true), refresh(RefreshRemoteFlagsUseCase.Params()))
        assertEquals(3, remote.calls)
    }

    @Test
    fun overridesWorkInDebugAndAreClearedByReset() = scope.runTest {
        val repo = repository(isDevBuild = true)
        val set = SetFeatureFlagOverrideUseCase(repo, dispatcher)
        assertEquals(AppResult.Success(Unit), set(SetFeatureFlagOverrideUseCase.Params(FeatureFlag.PreviewBanner, true)))
        assertEquals(AppResult.Success(true), ObserveFeatureFlagUseCase(repo, dispatcher)(FeatureFlag.PreviewBanner).first())

        val states = (ObserveFeatureFlagsUseCase(repo, dispatcher)(NoParams).first() as AppResult.Success).data
        assertEquals(FlagOrigin.Override, states.single { it.flag == FeatureFlag.PreviewBanner }.origin)

        // Gardé au lancement suivant, puis retiré par « Réinitialiser ».
        val nextLaunch = repository(isDevBuild = true)
        assertEquals(AppResult.Success(true), ObserveFeatureFlagUseCase(nextLaunch, dispatcher)(FeatureFlag.PreviewBanner).first())
        assertEquals(AppResult.Success(Unit), ClearFeatureFlagOverridesUseCase(nextLaunch, dispatcher)(NoParams))
        assertEquals(AppResult.Success(false), ObserveFeatureFlagUseCase(nextLaunch, dispatcher)(FeatureFlag.PreviewBanner).first())
        assertTrue(files.contents.keys.none { it == "overrides" })
    }

    @Test
    fun overridesAreRefusedAndIgnoredInRelease() = scope.runTest {
        // Fichier laissé par un build de debug.
        FlagStore(files).writeOverrides(mapOf("catalog" to false, "preview_banner" to true))
        val repo = repository(isDevBuild = false)

        val result = SetFeatureFlagOverrideUseCase(repo, dispatcher)(SetFeatureFlagOverrideUseCase.Params(FeatureFlag.PreviewBanner, true))
        assertIs<AppError.Validation>((result as AppResult.Failure).error)
        assertIs<AppResult.Failure>(ClearFeatureFlagOverridesUseCase(repo, dispatcher)(NoParams))
        assertEquals(AppResult.Success(true), ObserveFeatureFlagUseCase(repo, dispatcher)(FeatureFlag.Catalog).first())
        assertEquals(AppResult.Success(false), ObserveFeatureFlagUseCase(repo, dispatcher)(FeatureFlag.PreviewBanner).first())
    }

    @Test
    fun unreadableCacheFallsBackToDefaults() = scope.runTest {
        files.write("remote_cache", "{coupé".encodeToByteArray())
        val repo = repository(isDevBuild = false)
        assertEquals(AppResult.Success(true), ObserveFeatureFlagUseCase(repo, dispatcher)(FeatureFlag.Catalog).first())
        assertFalse("remote_cache" in files.contents)
    }

    private fun client(body: String) = createHttpClient(
        MockEngine {
            // Pas de Content-Length : le plafond doit venir de la lecture du corps.
            respond(ByteReadChannel(body.encodeToByteArray()), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        },
    )

    private companion object {
        const val URL = "https://exemple.fr/flags.json"
    }
}

private class FakeRemoteFlagSource : RemoteFlagSource {
    var values: Map<String, Boolean> = emptyMap()
    var failure: Throwable? = null
    var calls = 0

    override suspend fun fetch(): Map<String, Boolean> {
        calls++
        failure?.let { throw it }
        return values
    }
}

private class InMemoryFileStore : PrivateFileStore {
    val contents = mutableMapOf<String, ByteArray>()

    override fun write(name: String, bytes: ByteArray) {
        contents[name] = bytes
    }

    override fun read(name: String): ByteArray? = contents[name]

    override fun path(name: String): String = "/memoire/$name"

    override fun delete(name: String) {
        contents.remove(name)
    }

    override fun deleteAll() = contents.clear()

    override fun names(): List<String> = contents.keys.toList()
}

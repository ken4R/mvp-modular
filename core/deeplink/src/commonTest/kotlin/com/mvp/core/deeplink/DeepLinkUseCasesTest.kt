package com.mvp.core.deeplink

import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.core.deeplink.data.DeepLinkUserSessionObserver
import com.mvp.core.deeplink.data.InMemoryPendingDeepLinkRepository
import com.mvp.core.deeplink.domain.model.DeepLink
import com.mvp.core.deeplink.domain.usecase.ConsumeDeepLinkUseCase
import com.mvp.core.deeplink.domain.usecase.ObservePendingDeepLinkUseCase
import com.mvp.core.deeplink.domain.usecase.OpenDeepLinkUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeepLinkUseCasesTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = InMemoryPendingDeepLinkRepository()
    private val open = OpenDeepLinkUseCase(repository, dispatcher)
    private val observe = ObservePendingDeepLinkUseCase(repository, dispatcher)
    private val consume = ConsumeDeepLinkUseCase(repository, dispatcher)

    private suspend fun pending(): DeepLink? = (observe(NoParams).first() as AppResult.Success).data

    @Test
    fun openedLinkWaitsUntilConsumed() = runTest(dispatcher) {
        assertNull(pending())

        assertEquals(AppResult.Success(DeepLink.Catalog(5)), open("mvpmodular://catalog/products/5"))
        assertEquals(DeepLink.Catalog(5), pending())

        consume(DeepLink.Catalog(5))
        assertNull(pending())
    }

    @Test
    fun unknownLinkFallsBackToTasks() = runTest(dispatcher) {
        val result = open("mvpmodular://admin/delete-all")

        assertEquals(AppResult.Failure(AppError.Validation("Lien non reconnu")), result)
        assertEquals(DeepLink.Tasks(), pending())
    }

    @Test
    fun newerLinkReplacesOlderAndSurvivesItsConsumption() = runTest(dispatcher) {
        open("mvpmodular://weather")
        open("mvpmodular://settings")
        assertEquals(DeepLink.Settings, pending())

        // L'app finit d'afficher l'ancien lien après l'arrivée du nouveau : le nouveau reste en attente.
        consume(DeepLink.Weather)
        assertEquals(DeepLink.Settings, pending())
    }

    @Test
    fun pendingLinkIsForgottenAtLogout() = runTest(dispatcher) {
        open("mvpmodular://tasks/abc")

        DeepLinkUserSessionObserver(repository).clearUserData()

        assertNull(pending())
    }
}

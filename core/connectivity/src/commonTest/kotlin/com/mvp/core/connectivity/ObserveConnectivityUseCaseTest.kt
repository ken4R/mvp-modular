package com.mvp.core.connectivity

import com.mvp.core.common.domain.connectivity.ConnectivityObserver
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.core.connectivity.domain.usecase.ObserveConnectivityUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ObserveConnectivityUseCaseTest {

    private val scope = TestScope()
    private val dispatcher = StandardTestDispatcher(scope.testScheduler)

    @Test
    fun emitsOncePerChange() = scope.runTest {
        val states = MutableSharedFlow<Boolean>()
        val observe = ObserveConnectivityUseCase(observerOf(states), dispatcher)
        val emissions = mutableListOf<AppResult<Boolean>>()
        val job = launch { observe(NoParams).collect { emissions += it } }
        testScheduler.advanceUntilIdle()

        listOf(true, true, false, false, true).forEach {
            states.emit(it)
            testScheduler.advanceUntilIdle()
        }
        job.cancel()

        assertEquals<List<AppResult<Boolean>>>(listOf(true, false, true).map { AppResult.Success(it) }, emissions)
    }

    @Test
    fun systemFailureBecomesAppError() = scope.runTest {
        val failing = observerOf(flow { throw IllegalStateException("réseau illisible") })

        val result = ObserveConnectivityUseCase(failing, dispatcher)(NoParams).take(1).toList().single()

        assertIs<AppResult.Failure>(result)
    }

    private fun observerOf(states: Flow<Boolean>) = object : ConnectivityObserver {
        override fun observeIsOnline(): Flow<Boolean> = states
    }
}

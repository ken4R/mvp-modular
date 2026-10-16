package com.mvp.core.common

import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.result.fold
import com.mvp.core.common.domain.result.getOrNull
import com.mvp.core.common.domain.result.map
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppResultTest {

    @Test
    fun mapTransformsSuccess() {
        val result: AppResult<Int> = AppResult.Success(2)
        assertEquals(AppResult.Success(4), result.map { it * 2 })
    }

    @Test
    fun mapKeepsFailure() {
        val result: AppResult<Int> = AppResult.Failure(AppError.Network)
        assertEquals(AppResult.Failure(AppError.Network), result.map { it * 2 })
        assertNull(result.getOrNull())
    }

    @Test
    fun foldReturnsBranchValue() {
        val result: AppResult<String> = AppResult.Failure(AppError.NotFound)
        assertEquals("error", result.fold(onSuccess = { it }, onFailure = { "error" }))
    }
}

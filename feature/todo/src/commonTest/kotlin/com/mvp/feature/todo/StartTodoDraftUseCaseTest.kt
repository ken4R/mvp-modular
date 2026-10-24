package com.mvp.feature.todo

import com.mvp.core.common.domain.result.getOrNull
import com.mvp.core.deeplink.domain.model.DeepLink
import com.mvp.core.deeplink.domain.repository.PendingDeepLinkRepository
import com.mvp.feature.todo.domain.usecase.AddTodoUseCase
import com.mvp.feature.todo.domain.usecase.StartTodoDraftUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class StartTodoDraftUseCaseTest {

    private val scope = TestScope()
    private val dispatcher = StandardTestDispatcher(scope.testScheduler)
    private val pending = FakePendingDeepLinkRepository()
    private val startDraft = StartTodoDraftUseCase(pending, dispatcher)

    @Test
    fun dictatedTitleIsPendingWithoutAddingAnything() = scope.runTest {
        val draft = startDraft("  Acheter du pain ").getOrNull()

        assertEquals(DeepLink.NewTask("Acheter du pain"), draft)
        assertEquals(DeepLink.NewTask("Acheter du pain"), pending.value.value)
    }

    @Test
    fun lineBreaksControlAndFormatCharactersBecomeSpaces() = scope.runTest {
        startDraft("Appeler\nle\t\u0000 plombier\u200B \u202Eniap")

        // Espace sans chasse et inversion du sens d'affichage retirés aussi.
        assertEquals(DeepLink.NewTask("Appeler le plombier niap"), pending.value.value)
    }

    @Test
    fun tooLongTitleIsCutToTheTaskMaximum() = scope.runTest {
        startDraft("a".repeat(5_000))

        assertEquals(AddTodoUseCase.MAX_TITLE_LENGTH, (pending.value.value as DeepLink.NewTask).title.length)
    }

    @Test
    fun emojiCutInHalfIsDropped() = scope.runTest {
        startDraft("a".repeat(AddTodoUseCase.MAX_TITLE_LENGTH - 1) + "\uD83D\uDE00")

        assertEquals(DeepLink.NewTask("a".repeat(AddTodoUseCase.MAX_TITLE_LENGTH - 1)), pending.value.value)
    }

    @Test
    fun blankTitleOpensAnEmptyField() = scope.runTest {
        startDraft(" \n ")

        assertEquals(DeepLink.NewTask(""), pending.value.value)
    }

    @Test
    fun draftReplacesThePendingLink() = scope.runTest {
        pending.set(DeepLink.Weather)

        startDraft("Lire")

        assertEquals(DeepLink.NewTask("Lire"), pending.value.value)
    }
}

private class FakePendingDeepLinkRepository : PendingDeepLinkRepository {
    val value = MutableStateFlow<DeepLink?>(null)
    override fun observe(): Flow<DeepLink?> = value
    override fun set(link: DeepLink) {
        value.value = link
    }
    override fun consume(link: DeepLink) {
        if (value.value == link) value.value = null
    }
    override fun clear() {
        value.value = null
    }
}

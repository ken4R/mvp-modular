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
    fun lineBreaksAndControlCharactersBecomeSpaces() = scope.runTest {
        startDraft("Appeler\nle\t\u0000 plombier​")

        // Les caractères invisibles non-contrôle (espace sans chasse) restent : le champ les affiche tels quels.
        assertEquals(DeepLink.NewTask("Appeler le plombier​"), pending.value.value)
    }

    @Test
    fun tooLongTitleIsCutToTheTaskMaximum() = scope.runTest {
        startDraft("a".repeat(5_000))

        assertEquals(AddTodoUseCase.MAX_TITLE_LENGTH, (pending.value.value as DeepLink.NewTask).title.length)
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

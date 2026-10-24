package com.mvp.feature.todo

import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.result.getOrNull
import com.mvp.core.deeplink.domain.DeepLinks
import com.mvp.core.deeplink.domain.model.DeepLink
import com.mvp.feature.todo.data.repository.TodoRepositoryImpl
import com.mvp.feature.todo.data.source.InMemoryTodoLocalDataSource
import com.mvp.feature.todo.domain.model.Todo
import com.mvp.feature.todo.domain.model.TodoShareRequest
import com.mvp.feature.todo.domain.usecase.ShareTodoUseCase
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ShareTodoUseCaseTest {

    private val scope = TestScope()
    private val dispatcher = StandardTestDispatcher(scope.testScheduler)
    private val local = InMemoryTodoLocalDataSource(
        listOf(
            Todo(
                "3f2a-uuid",
                "Acheter du pain",
                isDone = false,
                reminderAt = 1_800_000_000_000,
                note = "Code de la porte : 4521",
                attachmentCount = 2,
            ),
            Todo("fini", "Appeler\nle plombier", isDone = true),
        ),
    )
    private val share = ShareTodoUseCase(TodoRepositoryImpl(local), dispatcher)

    @Test
    fun sharedTextHasTitleStateAndLinkButNotTheNoteByDefault() = scope.runTest {
        val shared = share(TodoShareRequest("3f2a-uuid")).getOrNull()!!

        assertEquals(
            "Tâche : Acheter du pain\nÉtat : à faire\n\nOuvrir dans MVP Modular : mvpmodular://tasks/3f2a-uuid",
            shared.text,
        )
        assertEquals("Tâche : Acheter du pain", shared.subject)
        assertFalse("4521" in shared.text)
    }

    @Test
    fun noteIsAddedOnlyWhenAsked() = scope.runTest {
        val shared = share(TodoShareRequest("3f2a-uuid", includeNote = true)).getOrNull()!!

        assertEquals(
            "Tâche : Acheter du pain\nÉtat : à faire\n\nNote :\nCode de la porte : 4521\n\n" +
                "Ouvrir dans MVP Modular : mvpmodular://tasks/3f2a-uuid",
            shared.text,
        )
    }

    @Test
    fun doneTaskWithoutNoteHasNoNoteSectionAndAOneLineSubject() = scope.runTest {
        val shared = share(TodoShareRequest("fini", includeNote = true)).getOrNull()!!

        assertEquals("Tâche : Appeler le plombier", shared.subject)
        assertTrue("État : terminée" in shared.text)
        assertFalse("Note :" in shared.text)
    }

    @Test
    fun sharedLinkOpensThisTaskInTheApp() = scope.runTest {
        val shared = share(TodoShareRequest("3f2a-uuid")).getOrNull()!!

        assertEquals(DeepLink.Tasks("3f2a-uuid"), DeepLinks.parse(shared.link))
        assertTrue(shared.text.endsWith(shared.link))
    }

    @Test
    fun missingTaskIsNotFound() = scope.runTest {
        val result = share(TodoShareRequest("supprimée"))

        assertIs<AppError.NotFound>(assertIs<AppResult.Failure>(result).error)
    }
}

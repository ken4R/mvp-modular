package com.mvp.feature.todo

import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.result.getOrNull
import com.mvp.feature.todo.data.repository.TodoRepositoryImpl
import com.mvp.feature.todo.data.source.InMemoryTodoLocalDataSource
import com.mvp.feature.todo.domain.model.AttachmentType
import com.mvp.feature.todo.domain.model.NewTodoAttachment
import com.mvp.feature.todo.domain.model.Todo
import com.mvp.feature.todo.domain.model.TodoNote
import com.mvp.feature.todo.domain.usecase.AddTodoAttachmentUseCase
import com.mvp.feature.todo.domain.usecase.DeleteTodoAttachmentUseCase
import com.mvp.feature.todo.domain.usecase.DeleteTodoUseCase
import com.mvp.feature.todo.domain.usecase.ObserveTodoAttachmentsUseCase
import com.mvp.feature.todo.domain.usecase.UpdateTodoNoteUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TodoNoteAndAttachmentUseCasesTest {

    private val scope = TestScope()
    private val dispatcher = StandardTestDispatcher(scope.testScheduler)
    private var nextId = 0
    private val local = InMemoryTodoLocalDataSource(listOf(Todo("t", "Acheter du pain", isDone = false)))
    private val repository = TodoRepositoryImpl(local, idGenerator = { "id-${nextId++}" })

    private val updateNote = UpdateTodoNoteUseCase(repository, dispatcher)
    private val observeAttachments = ObserveTodoAttachmentsUseCase(repository, dispatcher)
    private val addAttachment = AddTodoAttachmentUseCase(repository, dispatcher)
    private val deleteAttachment = DeleteTodoAttachmentUseCase(repository, dispatcher)
    private val deleteTodo = DeleteTodoUseCase(repository, dispatcher)

    private suspend fun attachments() = observeAttachments("t").first().getOrNull()!!

    private fun photo(fileName: String = "IMG_0001.jpg", todoId: String = "t") =
        NewTodoAttachment(todoId, fileName, "image/jpeg", JPEG)

    private fun validationMessage(result: AppResult<*>): String =
        assertIs<AppError.Validation>(assertIs<AppResult.Failure>(result).error).message

    @Test
    fun noteIsTrimmedSavedAndCanBeCleared() = scope.runTest {
        assertEquals("Complet, pas de seigle", updateNote(TodoNote("t", "  Complet, pas de seigle \n")).getOrNull()?.note)
        assertEquals("Complet, pas de seigle", local.get("t")?.note)

        assertEquals("", updateNote(TodoNote("t", "   ")).getOrNull()?.note)
    }

    @Test
    fun tooLongNoteIsRejectedAndMissingTaskIsNotFound() = scope.runTest {
        val tooLong = updateNote(TodoNote("t", "x".repeat(UpdateTodoNoteUseCase.MAX_NOTE_LENGTH + 1)))

        assertEquals("2000 caractères maximum", validationMessage(tooLong))
        assertEquals("", local.get("t")?.note)
        assertEquals(AppResult.Failure(AppError.NotFound), updateNote(TodoNote("inconnue", "Note")))
        assertIs<AppResult.Success<*>>(updateNote(TodoNote("t", "x".repeat(UpdateTodoNoteUseCase.MAX_NOTE_LENGTH))))
    }

    @Test
    fun photoPdfAndTextAreAttachedInOrder() = scope.runTest {
        addAttachment(photo())
        addAttachment(NewTodoAttachment("t", "Plan.pdf", "application/pdf", "%PDF-1.7\n".encodeToByteArray()))
        addAttachment(NewTodoAttachment("t", "Liste.txt", "text/plain; charset=utf-8", "Œufs, café".encodeToByteArray()))

        assertEquals(
            listOf("IMG_0001.jpg" to AttachmentType.Photo, "Plan.pdf" to AttachmentType.Pdf, "Liste.txt" to AttachmentType.Text),
            attachments().map { it.fileName to it.type },
        )
        assertEquals(3, local.get("t")?.attachmentCount)
    }

    @Test
    fun unsupportedFormatsAndDisguisedFilesAreRefused() = scope.runTest {
        val refused = listOf(
            NewTodoAttachment("t", "app.apk", "application/vnd.android.package-archive", byteArrayOf(0x50, 0x4B)),
            NewTodoAttachment("t", "image.png", "image/png", byteArrayOf(0x89.toByte(), 0x50)),
            // Annoncés JPEG, PDF ou texte, mais ce n'en sont pas.
            NewTodoAttachment("t", "photo.jpg", "image/jpeg", "%PDF-1.7".encodeToByteArray()),
            NewTodoAttachment("t", "doc.pdf", "application/pdf", JPEG),
            NewTodoAttachment("t", "notes.txt", "text/plain", byteArrayOf(0x41, 0x00, 0x42)),
            NewTodoAttachment("t", "notes.txt", "text/plain", byteArrayOf(0xC3.toByte(), 0x28)),
        )

        refused.forEach { assertIs<AppError.Validation>(assertIs<AppResult.Failure>(addAttachment(it)).error) }
        assertEquals(emptyList(), attachments())
    }

    @Test
    fun emptyAndTooLargeFilesAreRefused() = scope.runTest {
        assertEquals("Le fichier est vide", validationMessage(addAttachment(NewTodoAttachment("t", "a.txt", "text/plain", ByteArray(0)))))

        val tooLarge = ByteArray(AddTodoAttachmentUseCase.MAX_SIZE_BYTES + 1).also { JPEG.copyInto(it) }
        assertEquals("10 Mo maximum par pièce jointe", validationMessage(addAttachment(NewTodoAttachment("t", "a.jpg", "image/jpeg", tooLarge))))

        val maxSize = ByteArray(AddTodoAttachmentUseCase.MAX_SIZE_BYTES).also { JPEG.copyInto(it) }
        assertIs<AppResult.Success<*>>(addAttachment(NewTodoAttachment("t", "a.jpg", "image/jpeg", maxSize)))
    }

    @Test
    fun atMostTenAttachmentsPerTask() = scope.runTest {
        repeat(AddTodoAttachmentUseCase.MAX_ATTACHMENTS_PER_TODO) { addAttachment(photo()) }

        assertEquals("10 pièces jointes maximum par tâche", validationMessage(addAttachment(photo())))
        assertEquals(10, attachments().size)
    }

    @Test
    fun attachmentToAMissingTaskIsNotFound() = scope.runTest {
        assertEquals(AppResult.Failure(AppError.NotFound), addAttachment(photo(todoId = "inconnue")))
    }

    @Test
    fun fileNameIsCleanedForDisplay() = scope.runTest {
        val names = listOf(
            "../../secret/IMG_0001.jpg" to "IMG_0001.jpg",
            "C:\\Users\\Paul\\scan.jpg" to "scan.jpg",
            "  facture\u0000\u0007.jpg  " to "facture.jpg",
            // Inversion de sens : affiché tel quel, « photo » suivi de « gpj.exe » se lirait « photoexe.jpg ».
            "photo\u202Egpj.exe" to "photogpj.exe",
            "   " to "Photo.jpg",
            "x".repeat(150) to "x".repeat(AddTodoAttachmentUseCase.MAX_FILE_NAME_LENGTH),
        )

        names.forEach { (raw, _) -> addAttachment(photo(fileName = raw)) }

        assertEquals(names.map { it.second }, attachments().map { it.fileName })
    }

    @Test
    fun attachmentIsDeletedAloneOrWithItsTask() = scope.runTest {
        val first = addAttachment(photo()).getOrNull()!!
        addAttachment(photo())

        assertEquals(AppResult.Success(Unit), deleteAttachment(first.id))
        assertEquals(AppResult.Failure(AppError.NotFound), deleteAttachment(first.id))
        assertEquals(1, attachments().size)

        deleteTodo("t")
        assertEquals(emptyList(), attachments())
    }

    private companion object {
        val JPEG = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10)
    }
}

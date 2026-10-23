package com.mvp.feature.todo.domain.usecase

import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.UseCase
import com.mvp.feature.todo.domain.model.AttachmentType
import com.mvp.feature.todo.domain.model.NewTodoAttachment
import com.mvp.feature.todo.domain.model.TodoAttachment
import com.mvp.feature.todo.domain.repository.TodoRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first

/**
 * Joint une photo, un PDF ou un fichier texte à une tâche, sur l'appareil seulement (ADR 0014). Règles métier :
 * - format pris en charge ([AttachmentType]) et contenu conforme au format annoncé (signature JPEG ou PDF, texte
 *   UTF-8 sans caractère nul) : une app ne lit jamais un fichier déguisé ;
 * - 10 Mo maximum, fichier non vide ; 10 pièces jointes maximum par tâche ;
 * - nom affiché nettoyé : sans dossier, sans caractère de contrôle ni d'inversion de sens (qui ferait passer un
 *   `.exe` pour un `.jpg`), 100 caractères max, nom par défaut du format s'il ne reste rien.
 */
class AddTodoAttachmentUseCase(
    private val repository: TodoRepository,
    dispatcher: CoroutineDispatcher,
) : UseCase<NewTodoAttachment, TodoAttachment>(dispatcher) {

    override suspend fun execute(params: NewTodoAttachment): AppResult<TodoAttachment> {
        val type = AttachmentType.fromMimeType(params.mimeType)
            ?: return invalid("Format non pris en charge : photo, PDF ou fichier texte")
        val bytes = params.bytes
        when {
            bytes.isEmpty() -> return invalid("Le fichier est vide")
            bytes.size > MAX_SIZE_BYTES -> return invalid("10 Mo maximum par pièce jointe")
            !type.matches(bytes) -> return invalid("Le contenu du fichier ne correspond pas à son format")
        }
        if (repository.observeAttachments(params.todoId).first().size >= MAX_ATTACHMENTS_PER_TODO) {
            return invalid("$MAX_ATTACHMENTS_PER_TODO pièces jointes maximum par tâche")
        }
        return repository.addAttachment(params.todoId, cleanFileName(params.fileName, type), type, bytes)
    }

    companion object {
        const val MAX_SIZE_BYTES = 10 * 1024 * 1024
        const val MAX_ATTACHMENTS_PER_TODO = 10
        const val MAX_FILE_NAME_LENGTH = 100

        /** Nom affiché : dernier segment du chemin, sans caractères invisibles, coupé à [MAX_FILE_NAME_LENGTH]. */
        fun cleanFileName(raw: String, type: AttachmentType): String {
            val cleaned = raw.substringAfterLast('/').substringAfterLast('\\')
                .filterNot { it.isISOControl() || it in INVISIBLE_FORMATTING }
                .trim()
                .take(MAX_FILE_NAME_LENGTH)
                // Pas de moitié de caractère (emoji coupé en deux par take).
                .let { if (it.lastOrNull()?.isHighSurrogate() == true) it.dropLast(1) else it }
                .trim()
            return cleaned.ifEmpty { type.defaultFileName }
        }

        /** Inversions et isolements de sens, espaces de largeur nulle : ils déguisent l'extension affichée. */
        private const val INVISIBLE_FORMATTING =
            "\u200B\u200C\u200D\u200E\u200F\u202A\u202B\u202C\u202D\u202E\u2066\u2067\u2068\u2069\uFEFF"
    }
}

private fun invalid(message: String) = AppResult.Failure(AppError.Validation(message))

/** Le contenu correspond-il au format annoncé ? */
private fun AttachmentType.matches(bytes: ByteArray): Boolean = when (this) {
    AttachmentType.Photo -> bytes.startsWith(JPEG_SIGNATURE)
    AttachmentType.Pdf -> bytes.startsWith(PDF_SIGNATURE)
    AttachmentType.Text -> bytes.none { it == 0.toByte() } && bytes.isValidUtf8()
}

private val JPEG_SIGNATURE = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
private val PDF_SIGNATURE = "%PDF-".encodeToByteArray()

private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
    size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

private fun ByteArray.isValidUtf8(): Boolean = try {
    decodeToString(throwOnInvalidSequence = true)
    true
} catch (e: CharacterCodingException) {
    false
}

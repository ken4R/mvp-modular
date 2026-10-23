package com.mvp.feature.todo.domain.model

/**
 * Formats acceptés en pièce jointe (ADR 0014) : ceux que les deux apps savent afficher elles-mêmes, sans
 * confier le fichier à une autre app. Les photos sont réenregistrées en JPEG par les apps avant l'ajout, ce qui
 * retire leurs métadonnées (position GPS, appareil).
 */
enum class AttachmentType(val mimeType: String, val defaultFileName: String) {
    Photo("image/jpeg", "Photo.jpg"),
    Pdf("application/pdf", "Document.pdf"),
    Text("text/plain", "Texte.txt"),
    ;

    companion object {
        /** `null` pour un format non pris en charge. Ignore la casse et les paramètres (`; charset=utf-8`). */
        fun fromMimeType(mimeType: String): AttachmentType? {
            val bare = mimeType.substringBefore(';').trim().lowercase()
            return entries.firstOrNull { it.mimeType == bare }
        }
    }
}

/**
 * Pièce jointe d'une tâche, gardée sur l'appareil seulement. [localPath] : fichier à afficher (privé à l'app,
 * hors sauvegardes) ; [fileName] : nom affiché, jamais un chemin. [addedAt] : epoch ms.
 */
data class TodoAttachment(
    val id: String,
    val todoId: String,
    val fileName: String,
    val type: AttachmentType,
    val sizeBytes: Long,
    val addedAt: Long,
    val localPath: String,
) {
    // Pour Swift, qui renomme les entrées des enum Kotlin.
    val isPhoto: Boolean get() = type == AttachmentType.Photo
    val isPdf: Boolean get() = type == AttachmentType.Pdf
    val isText: Boolean get() = type == AttachmentType.Text
}

/**
 * Fichier choisi par l'utilisateur, à joindre à la tâche [todoId]. [fileName] : nom d'origine (nettoyé à
 * l'ajout) ; [mimeType] : type annoncé par le sélecteur, vérifié sur le contenu.
 */
class NewTodoAttachment(
    val todoId: String,
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray,
)

/** Note à enregistrer sur la tâche [todoId] ; vide pour la retirer. */
data class TodoNote(val todoId: String, val note: String)

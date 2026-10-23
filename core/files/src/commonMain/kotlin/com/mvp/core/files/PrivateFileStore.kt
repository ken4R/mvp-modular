package com.mvp.core.files

/**
 * Fichiers privés d'une feature, dans un dossier qui lui est propre : hors des sauvegardes, illisible par les
 * autres apps (ADR 0014). Les noms sont choisis par l'app (identifiants), jamais par l'utilisateur : seuls
 * lettres, chiffres, `-` et `_` sont acceptés, ce qui interdit de sortir du dossier (`../`). Les opérations sont
 * bloquantes : à appeler depuis un dispatcher d'entrées / sorties.
 */
interface PrivateFileStore {

    /** Écrit le fichier [name] en entier, ou rien en cas d'échec (écriture atomique). */
    fun write(name: String, bytes: ByteArray)

    /** Contenu du fichier [name], `null` s'il n'existe pas. */
    fun read(name: String): ByteArray?

    /** Chemin absolu du fichier [name], pour l'afficher (image, PDF) sans le recopier. */
    fun path(name: String): String

    /** Supprime le fichier [name] ; rien à faire s'il n'existe pas. */
    fun delete(name: String)

    /** Supprime tout le dossier (déconnexion) ; lève une exception s'il en reste quelque chose. */
    fun deleteAll()

    /** Noms des fichiers présents (fichiers temporaires exclus). */
    fun names(): List<String>

    companion object {
        private val validName = Regex("[A-Za-z0-9_-]{1,64}")

        fun isValidName(name: String): Boolean = validName.matches(name)

        internal fun requireValidName(name: String) =
            require(isValidName(name)) { "Nom de fichier refusé" }
    }
}

/** Ouvre le dossier privé [folder] (même règle de nom que les fichiers). */
fun interface PrivateFileStoreFactory {
    fun create(folder: String): PrivateFileStore
}

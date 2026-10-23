package com.mvp.core.files

import java.io.File
import java.io.IOException

/**
 * Fichiers dans [directory], sous le dossier `no_backup` de l'app : ni sauvegarde Google, ni transfert vers un
 * nouvel appareil (que `allowBackup="false"` ne bloque plus à partir d'Android 12). Les données de l'app sont
 * chiffrées par le chiffrement de l'appareil (File-Based Encryption).
 */
internal class AndroidPrivateFileStore(private val directory: File) : PrivateFileStore {

    override fun write(name: String, bytes: ByteArray) {
        val target = file(name)
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Dossier impossible à créer")
        // Fichier temporaire puis renommage : un fichier à moitié écrit n'a jamais le nom définitif.
        val temporary = File(directory, "$name$TEMPORARY_SUFFIX")
        try {
            temporary.writeBytes(bytes)
            if (!temporary.renameTo(target)) throw IOException("Fichier impossible à enregistrer")
        } finally {
            temporary.delete()
        }
    }

    override fun read(name: String): ByteArray? = file(name).takeIf { it.isFile }?.readBytes()

    override fun path(name: String): String = file(name).absolutePath

    override fun delete(name: String) {
        file(name).delete()
    }

    override fun deleteAll() {
        if (!directory.deleteRecursively() && directory.exists()) throw IOException("Dossier impossible à effacer")
    }

    override fun names(): List<String> = directory.list()?.filter { PrivateFileStore.isValidName(it) }.orEmpty()

    private fun file(name: String): File {
        PrivateFileStore.requireValidName(name)
        return File(directory, name)
    }
}

/** Contient un point : jamais un nom valide, donc jamais listé ni lu. */
private const val TEMPORARY_SUFFIX = ".tmp"

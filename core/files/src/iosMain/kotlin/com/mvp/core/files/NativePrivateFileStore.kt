package com.mvp.core.files

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSDataWritingAtomic
import platform.Foundation.NSDataWritingFileProtectionComplete
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileProtectionComplete
import platform.Foundation.NSFileProtectionKey
import platform.Foundation.NSLog
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile
import platform.posix.memcpy

/**
 * Fichiers dans [directory] (sous Application Support) : exclus des sauvegardes iCloud et de l'ordinateur, et
 * protégés par Data Protection « complète » (illisibles tant que l'iPhone est verrouillé). La suppression
 * reste possible verrouillé : une synchronisation en arrière-plan peut retirer les fichiers d'une tâche.
 */
@OptIn(ExperimentalForeignApi::class)
internal class NativePrivateFileStore(private val directory: String) : PrivateFileStore {

    private val fileManager get() = NSFileManager.defaultManager

    override fun write(name: String, bytes: ByteArray) {
        val path = path(name)
        ensureDirectory()
        val written = bytes.toNSData().writeToFile(
            path,
            options = NSDataWritingAtomic or NSDataWritingFileProtectionComplete,
            error = null,
        )
        check(written) { "Fichier impossible à enregistrer" }
    }

    override fun read(name: String): ByteArray? = NSData.dataWithContentsOfFile(path(name))?.toByteArray()

    override fun path(name: String): String {
        PrivateFileStore.requireValidName(name)
        return "$directory/$name"
    }

    override fun delete(name: String) {
        fileManager.removeItemAtPath(path(name), error = null)
    }

    override fun deleteAll() {
        fileManager.removeItemAtPath(directory, error = null)
    }

    override fun names(): List<String> =
        fileManager.contentsOfDirectoryAtPath(directory, error = null)
            ?.filterIsInstance<String>()
            ?.filter { PrivateFileStore.isValidName(it) }
            .orEmpty()

    /**
     * Créé à la première écriture (et recréé après une déconnexion), puis exclu des sauvegardes. Comme pour les
     * bases (core:database), un échec de l'exclusion est journalisé sans bloquer l'écriture.
     */
    private fun ensureDirectory() {
        if (!fileManager.fileExistsAtPath(directory)) {
            val created = fileManager.createDirectoryAtPath(
                directory,
                withIntermediateDirectories = true,
                attributes = mapOf<Any?, Any?>(NSFileProtectionKey to NSFileProtectionComplete),
                error = null,
            )
            check(created) { "Dossier impossible à créer" }
        }
        val excluded = NSURL.fileURLWithPath(directory, isDirectory = true)
            .setResourceValue(true, forKey = NSURLIsExcludedFromBackupKey, error = null)
        if (!excluded) NSLog("core:files : impossible d'exclure %@ des sauvegardes", directory)
    }

    companion object {
        /** Dossier [folder] sous Application Support (il n'est créé qu'à la première écriture). */
        fun inApplicationSupport(folder: String): NativePrivateFileStore {
            require(PrivateFileStore.isValidName(folder)) { "Nom de dossier refusé" }
            val base = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true)
                .firstOrNull() as? String
            return NativePrivateFileStore("${checkNotNull(base) { "Application Support introuvable" }}/$folder")
        }
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun ByteArray.toNSData(): NSData =
    if (isEmpty()) NSData() else usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    val bytes = ByteArray(size)
    if (size > 0) bytes.usePinned { memcpy(it.addressOf(0), this.bytes, length) }
    return bytes
}

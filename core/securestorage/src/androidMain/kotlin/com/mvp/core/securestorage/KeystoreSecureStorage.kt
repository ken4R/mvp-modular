package com.mvp.core.securestorage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.util.Base64
import java.security.KeyStore
import java.security.UnrecoverableKeyException
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Valeurs chiffrées en AES-256-GCM avec une clé générée dans l'Android Keystore (elle ne quitte
 * jamais le Keystore), puis rangées en Base64 dans un fichier de préférences privé à l'app.
 * Le nom de la clé sert de donnée authentifiée : une valeur recopiée sous une autre clé est rejetée.
 * Les sauvegardes Android sont désactivées (`allowBackup="false"`) ; une valeur restaurée sans
 * sa clé Keystore serait de toute façon indéchiffrable, donc ignorée.
 */
internal class KeystoreSecureStorage(context: Context) : SecureStorage {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var cachedKey: SecretKey? = null

    @Synchronized
    override fun get(key: String): String? {
        val stored = prefs.getString(key, null) ?: return null
        return try {
            decrypt(key, stored)
        } catch (e: Exception) {
            // Valeur altérée ou clé Keystore perdue : la valeur est définitivement inutilisable, on l'efface.
            // Toute autre erreur (Keystore momentanément indisponible) remonte, sans rien effacer.
            if (!e.isPermanentDecryptionFailure()) throw e
            cachedKey = null
            remove(key)
            null
        }
    }

    @Synchronized
    override fun put(key: String, value: String) {
        check(prefs.edit().putString(key, encrypt(key, value)).commit()) { "Écriture impossible dans le stockage sécurisé" }
    }

    @Synchronized
    override fun remove(key: String) {
        check(prefs.edit().remove(key).commit()) { "Suppression impossible dans le stockage sécurisé" }
    }

    private fun encrypt(key: String, value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        cipher.updateAAD(key.encodeToByteArray())
        val encrypted = cipher.doFinal(value.encodeToByteArray())
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(key: String, stored: String): String {
        val bytes = Base64.decode(stored, Base64.NO_WRAP)
        require(bytes.size > IV_SIZE) { "Valeur chiffrée invalide" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_SIZE_BITS, bytes, 0, IV_SIZE))
        cipher.updateAAD(key.encodeToByteArray())
        return cipher.doFinal(bytes, IV_SIZE, bytes.size - IV_SIZE).decodeToString()
    }

    private fun secretKey(): SecretKey = cachedKey ?: loadOrCreateKey().also { cachedKey = it }

    private fun loadOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_SIZE_BITS)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(spec)
            generateKey()
        }
    }

    private fun Exception.isPermanentDecryptionFailure() =
        this is AEADBadTagException ||
            this is KeyPermanentlyInvalidatedException ||
            this is UnrecoverableKeyException ||
            this is IllegalArgumentException // Base64 ou format invalide

    private companion object {
        const val PREFS_NAME = "secure_storage"
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "mvp_secure_storage"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_SIZE_BITS = 256
        const val IV_SIZE = 12
        const val TAG_SIZE_BITS = 128
    }
}

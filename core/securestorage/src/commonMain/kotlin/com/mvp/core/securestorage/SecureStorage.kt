package com.mvp.core.securestorage

/**
 * Stockage chiffré clé / valeur pour les secrets et les données personnelles (jeton de session,
 * profil). Android : valeurs chiffrées en AES-GCM avec une clé de l'Android Keystore.
 * iOS : Keychain, accessible après le premier déverrouillage, jamais sauvegardé ni transféré
 * vers un autre appareil. Fourni par [com.mvp.core.securestorage.di.secureStorageModule].
 *
 * Les appels sont synchrones et peuvent bloquer : à faire hors du main thread (dispatcher io).
 */
interface SecureStorage {
    /** `null` si la clé est absente ou si la valeur ne peut plus être déchiffrée. */
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)
}

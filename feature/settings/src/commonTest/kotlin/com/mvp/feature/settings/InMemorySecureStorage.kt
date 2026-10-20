package com.mvp.feature.settings

import com.mvp.core.securestorage.SecureStorage

/** Stockage « chiffré » en mémoire : recréer une data source dessus simule un redémarrage. */
class InMemorySecureStorage : SecureStorage {
    val values = mutableMapOf<String, String>()
    override fun get(key: String): String? = values[key]
    override fun put(key: String, value: String) { values[key] = value }
    override fun remove(key: String) { values.remove(key) }
}

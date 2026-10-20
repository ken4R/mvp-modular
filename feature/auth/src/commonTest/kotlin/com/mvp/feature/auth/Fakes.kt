package com.mvp.feature.auth

import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.session.UserAccount
import com.mvp.core.common.domain.session.UserSessionObserver
import com.mvp.core.securestorage.SecureStorage

/** Stockage « chiffré » en mémoire : recréer une data source dessus simule un redémarrage. */
class InMemorySecureStorage : SecureStorage {
    val values = mutableMapOf<String, String>()

    /** Simule un Keychain verrouillé ou un Keystore en erreur. */
    var failReads = false

    override fun get(key: String): String? = if (failReads) error("stockage illisible") else values[key]
    override fun put(key: String, value: String) { values[key] = value }
    override fun remove(key: String) { values.remove(key) }
}

/** Feature factice qui note ce que la connexion lui demande. */
class RecordingSessionObserver(var failClear: Boolean = false, var failLogin: Boolean = false) : UserSessionObserver {
    val logins = mutableListOf<UserAccount>()
    var clearCount = 0

    override suspend fun onLogin(account: UserAccount): AppResult<Unit> {
        logins += account
        return if (failLogin) AppResult.Failure(AppError.Unknown("échec")) else AppResult.Success(Unit)
    }

    override suspend fun clearUserData(): AppResult<Unit> {
        clearCount++
        if (failClear) return AppResult.Failure(AppError.Unknown("échec"))
        return AppResult.Success(Unit)
    }
}

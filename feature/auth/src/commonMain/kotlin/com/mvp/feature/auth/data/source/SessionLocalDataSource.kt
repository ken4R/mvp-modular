package com.mvp.feature.auth.data.source

import com.mvp.core.common.domain.session.UserAccount
import com.mvp.core.securestorage.SecureStorage
import com.mvp.feature.auth.domain.model.Session
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/** Session gardée sur l'appareil, y compris expirée (c'est au repository de la filtrer). */
interface SessionLocalDataSource {
    fun observe(): Flow<Session?>
    fun current(): Session?
    fun save(session: Session)
    fun clear()
}

/**
 * Session dans le stockage chiffré (Keystore / Keychain), une entrée par champ.
 * Lue une seule fois, au premier accès, puis gardée en mémoire pour être observée.
 */
internal class SecureSessionLocalDataSource(private val storage: SecureStorage) : SessionLocalDataSource {

    // Stockage illisible (Keychain verrouillé, Keystore en erreur) : lu comme « pas de session ».
    // Sinon le Flow observé par l'UI se terminerait en erreur et une connexion réussie ne
    // ferait plus changer d'écran. `read()` n'est pas suspend : aucune annulation n'est avalée.
    private val session by lazy { MutableStateFlow(runCatching { read() }.getOrNull()) }

    // Première lecture au moment de la collecte (sur le dispatcher io), pas à la création du Flow.
    override fun observe(): Flow<Session?> = flow { emitAll(session) }

    override fun current(): Session? = session.value

    override fun save(session: Session) {
        // Le jeton en dernier : une session à moitié écrite (champ manquant) est lue comme absente.
        storage.put(KEY_USER_ID, session.account.id)
        storage.put(KEY_DISPLAY_NAME, session.account.displayName)
        storage.put(KEY_EMAIL, session.account.email)
        storage.put(KEY_EXPIRES_AT, session.expiresAtEpochMillis.toString())
        storage.put(KEY_TOKEN, session.token)
        this.session.value = session
    }

    override fun clear() {
        // Le jeton d'abord : même interrompu, l'effacement ne laisse pas de session utilisable.
        ALL_KEYS.forEach(storage::remove)
        session.value = null
    }

    private fun read(): Session? {
        val token = storage.get(KEY_TOKEN) ?: return null
        val account = UserAccount(
            id = storage.get(KEY_USER_ID) ?: return null,
            displayName = storage.get(KEY_DISPLAY_NAME) ?: return null,
            email = storage.get(KEY_EMAIL) ?: return null,
        )
        val expiresAt = storage.get(KEY_EXPIRES_AT)?.toLongOrNull() ?: return null
        return Session(account = account, token = token, expiresAtEpochMillis = expiresAt)
    }

    private companion object {
        // Les clés sont stockées sur l'appareil : ne pas les renommer sans migration.
        const val KEY_TOKEN = "auth.session.token"
        const val KEY_USER_ID = "auth.session.user_id"
        const val KEY_DISPLAY_NAME = "auth.session.display_name"
        const val KEY_EMAIL = "auth.session.email"
        const val KEY_EXPIRES_AT = "auth.session.expires_at"
        val ALL_KEYS = listOf(KEY_TOKEN, KEY_USER_ID, KEY_DISPLAY_NAME, KEY_EMAIL, KEY_EXPIRES_AT)
    }
}

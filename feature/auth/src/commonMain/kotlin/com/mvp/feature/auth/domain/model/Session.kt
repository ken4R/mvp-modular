package com.mvp.feature.auth.domain.model

import com.mvp.core.common.domain.session.UserAccount

/** Identifiants saisis sur l'écran de connexion. Le mot de passe n'est jamais stocké. */
data class Credentials(val email: String, val password: String) {
    // Le mot de passe ne doit jamais apparaître dans un log ou un rapport de crash.
    override fun toString(): String = "Credentials(email=$email, password=***)"
}

/** Session ouverte : le jeton reste dans la couche data et le domaine, jamais dans l'UI. */
data class Session(
    val account: UserAccount,
    val token: String,
    val expiresAtEpochMillis: Long,
) {
    override fun toString(): String = "Session(account=$account, token=***, expiresAtEpochMillis=$expiresAtEpochMillis)"
}

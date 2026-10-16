package com.mvp.core.common.domain.error

/**
 * Erreurs métier partagées par tous les modules.
 * La couche data traduit les exceptions techniques en [AppError],
 * la couche UI (Android / iOS) décide comment les afficher.
 */
sealed interface AppError {
    data object Network : AppError
    data object NotFound : AppError
    data object Unauthorized : AppError
    data class Validation(val message: String) : AppError
    data class Unknown(val message: String? = null) : AppError
}

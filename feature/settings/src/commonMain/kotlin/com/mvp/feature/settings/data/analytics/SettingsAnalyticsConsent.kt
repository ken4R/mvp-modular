package com.mvp.feature.settings.data.analytics

import com.mvp.core.common.domain.analytics.AnalyticsConsent
import com.mvp.feature.settings.data.source.SettingsLocalDataSource
import com.mvp.feature.settings.domain.model.ConsentTexts
import com.mvp.feature.settings.domain.model.ConsentType
import kotlinx.coroutines.flow.first

/**
 * Le consentement « Mesure d'audience », lu par `core:analytics` avant de garder ou d'envoyer un événement.
 * Lu dans la base à chaque fois (pas de cache) : un retrait vaut dès qu'il est enregistré.
 * Un accord ne vaut que s'il porte sur la version actuelle du texte (le data source SQLite l'écarte
 * déjà à la lecture ; vérifié ici aussi pour ne pas dépendre de l'implémentation).
 */
internal class SettingsAnalyticsConsent(private val local: SettingsLocalDataSource) : AnalyticsConsent {
    override suspend fun isGranted(): Boolean =
        local.observe().first().consents.any { consent ->
            consent.type == ConsentType.Analytics &&
                consent.granted &&
                consent.textVersion == ConsentTexts.of(ConsentType.Analytics).version
        }
}

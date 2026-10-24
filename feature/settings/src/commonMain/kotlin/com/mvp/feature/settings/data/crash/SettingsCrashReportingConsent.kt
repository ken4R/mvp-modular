package com.mvp.feature.settings.data.crash

import com.mvp.core.common.domain.crash.CrashReportingConsent
import com.mvp.feature.settings.data.source.SettingsLocalDataSource
import com.mvp.feature.settings.domain.model.ConsentTexts
import com.mvp.feature.settings.domain.model.ConsentType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Le consentement « Rapports de plantage », suivi par `core:crashreporting`. Un accord ne vaut que s'il porte sur
 * la version actuelle du texte (vérifié ici aussi, comme pour la mesure d'audience).
 */
internal class SettingsCrashReportingConsent(private val local: SettingsLocalDataSource) : CrashReportingConsent {
    override fun observeGranted(): Flow<Boolean> =
        local.observe().map { settings ->
            settings.consents.any { consent ->
                consent.type == ConsentType.CrashReports &&
                    consent.granted &&
                    consent.textVersion == ConsentTexts.of(ConsentType.CrashReports).version
            }
        }.distinctUntilChanged()
}

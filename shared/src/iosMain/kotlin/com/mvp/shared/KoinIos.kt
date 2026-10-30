package com.mvp.shared

import com.mvp.shared.di.initKoin

/**
 * Appelé une seule fois au lancement de l'app iOS complète. Les use cases s'utilisent depuis Swift par la façade
 * de leur module (`TodoUseCases`, `SettingsUseCases`...), que chaque parapluie d'app de démo exporte aussi (ADR 0027).
 */
fun doInitKoin() {
    initKoin()
}

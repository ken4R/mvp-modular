package com.mvp.core.crashreporting.domain.usecase

import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.core.common.domain.usecase.UseCase
import com.mvp.core.crashreporting.domain.repository.CrashReportRepository
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Appelé par chaque app au lancement. Règles métier : rien n'est enregistré ni envoyé sans le consentement
 * « Rapports de plantage » (version actuelle du texte) ; les rapports d'un plantage précédent ne partent que si
 * ce consentement est toujours accordé, sinon ils sont effacés. Renvoie le nombre de rapports envoyés.
 */
class StartCrashReportingUseCase(
    private val repository: CrashReportRepository,
    dispatcher: CoroutineDispatcher,
) : UseCase<NoParams, Int>(dispatcher) {

    override suspend fun execute(params: NoParams): AppResult<Int> = repository.start()
}

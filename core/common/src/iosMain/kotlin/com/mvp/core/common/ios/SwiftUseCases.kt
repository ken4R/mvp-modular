package com.mvp.core.common.ios

import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.result.AppResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.core.component.KoinComponent
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

/** Abonnement à un Flow côté Swift : appeler `cancel()` quand la vue disparaît. */
class Subscription internal constructor(private val job: Job) {
    fun cancel() = job.cancel()
}

/**
 * Base des façades Swift de chaque module (`TodoUseCases`, `WeatherUseCases`...), dans son iosMain : un framework
 * iOS n'exporte ainsi que les façades des modules qu'il contient (un parapluie par app de démo, ADR 0027).
 * Koin n'est pas utilisable directement depuis Swift : chaque façade expose les use cases du module, et un helper
 * à callbacks par Flow, qui renvoie une [Subscription].
 */
@OptIn(ExperimentalObjCRefinement::class)
abstract class SwiftUseCases : KoinComponent {
    @HiddenFromObjC
    protected val scope: CoroutineScope = MainScope()

    /** Relaie chaque valeur du Flow à [onValue], chaque échec à [onError], sur le main thread. */
    @HiddenFromObjC
    protected fun <T> Flow<AppResult<T>>.subscribe(onValue: (T) -> Unit, onError: (AppError) -> Unit): Subscription {
        val job = onEach { result ->
            when (result) {
                is AppResult.Success -> onValue(result.data)
                is AppResult.Failure -> onError(result.error)
            }
        }.launchIn(scope)
        return Subscription(job)
    }

    /** Collecte le Flow jusqu'à l'annulation de l'abonnement (synchronisations à garder toute la vie de l'app). */
    @HiddenFromObjC
    protected fun Flow<*>.launchSubscription(): Subscription = Subscription(launchIn(scope))
}

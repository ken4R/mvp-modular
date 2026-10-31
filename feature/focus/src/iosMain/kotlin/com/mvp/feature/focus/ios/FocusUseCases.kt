package com.mvp.feature.focus.ios

import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.core.common.ios.Subscription
import com.mvp.core.common.ios.SwiftUseCases
import com.mvp.feature.focus.domain.model.FocusState
import com.mvp.feature.focus.domain.model.FocusTimer
import com.mvp.feature.focus.domain.usecase.ObserveFocusTimerUseCase
import com.mvp.feature.focus.domain.usecase.ObserveFocusUseCase
import com.mvp.feature.focus.domain.usecase.PauseFocusUseCase
import com.mvp.feature.focus.domain.usecase.ResumeFocusUseCase
import com.mvp.feature.focus.domain.usecase.StartFocusUseCase
import com.mvp.feature.focus.domain.usecase.StartNextFocusPhaseUseCase
import com.mvp.feature.focus.domain.usecase.StopFocusUseCase
import org.koin.core.component.get

/**
 * Point d'accès Swift aux use cases du mode focus (ADR 0028). Les use cases suspend s'appellent en `async` côté
 * Swift ; les Flow passent par des callbacks, appelés sur le main thread (voir SwiftUseCases).
 */
object FocusUseCases : SwiftUseCases() {
    fun startFocus(): StartFocusUseCase = get()
    fun pauseFocus(): PauseFocusUseCase = get()
    fun resumeFocus(): ResumeFocusUseCase = get()
    fun startNextFocusPhase(): StartNextFocusPhaseUseCase = get()
    fun stopFocus(): StopFocusUseCase = get()

    /** Session en cours (`nil` sans session), à recopier dans la Live Activity : réémise aux changements et à la fin de phase. */
    fun observeFocus(onState: (FocusState?) -> Unit, onError: (AppError) -> Unit): Subscription =
        get<ObserveFocusUseCase>()(NoParams).subscribe(onState, onError)

    /** Compte à rebours de l'écran Focus, réémis à chaque seconde affichée. */
    fun observeFocusTimer(onTimer: (FocusTimer?) -> Unit, onError: (AppError) -> Unit): Subscription =
        get<ObserveFocusTimerUseCase>()(NoParams).subscribe(onTimer, onError)
}

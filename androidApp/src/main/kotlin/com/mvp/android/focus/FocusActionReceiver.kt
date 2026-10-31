package com.mvp.android.focus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.feature.focus.domain.usecase.PauseFocusUseCase
import com.mvp.feature.focus.domain.usecase.ResumeFocusUseCase
import com.mvp.feature.focus.domain.usecase.StartNextFocusPhaseUseCase
import com.mvp.feature.focus.domain.usecase.StopFocusUseCase
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/**
 * Boutons de la notification du mode focus (ADR 0028). Non exporté : seuls les PendingIntent explicites de
 * [FocusNotifier] l'appellent. Les use cases relisent la session ; la notification est redessinée par FocusNotifier,
 * démarré avec l'Application (Koin aussi), même si l'app était arrêtée.
 */
class FocusActionReceiver : BroadcastReceiver(), KoinComponent {

    override fun onReceive(context: Context, intent: Intent) {
        val action: suspend () -> Unit = when (intent.action) {
            ACTION_PAUSE -> { { get<PauseFocusUseCase>()(NoParams) } }
            ACTION_RESUME -> { { get<ResumeFocusUseCase>()(NoParams) } }
            ACTION_NEXT -> { { get<StartNextFocusPhaseUseCase>()(NoParams) } }
            ACTION_STOP -> { { get<StopFocusUseCase>()(NoParams) } }
            else -> return
        }
        val pending = goAsync()
        MainScope().launch {
            try {
                action()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_PAUSE = "com.mvp.android.focus.PAUSE"
        const val ACTION_RESUME = "com.mvp.android.focus.RESUME"
        const val ACTION_NEXT = "com.mvp.android.focus.NEXT"
        const val ACTION_STOP = "com.mvp.android.focus.STOP"
    }
}

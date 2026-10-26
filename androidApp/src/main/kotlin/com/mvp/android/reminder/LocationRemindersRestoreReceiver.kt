package com.mvp.android.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.feature.todo.domain.usecase.SyncTodoLocationRemindersUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/**
 * Le redémarrage du téléphone et la mise à jour de l'app effacent les alertes de proximité (ADR 0023) : elles sont
 * reposées ici, sans attendre que l'utilisateur rouvre l'app. Koin est déjà démarré par `MvpApplication`. Non
 * exporté : seul le système envoie ces diffusions.
 */
class LocationRemindersRestoreReceiver : BroadcastReceiver(), KoinComponent {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                // Une seule réconciliation suffit ; le système laisse une dizaine de secondes à un récepteur.
                withTimeoutOrNull(RESTORE_TIMEOUT_MS) { get<SyncTodoLocationRemindersUseCase>()(NoParams).first() }
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val RESTORE_TIMEOUT_MS = 8_000L
    }
}

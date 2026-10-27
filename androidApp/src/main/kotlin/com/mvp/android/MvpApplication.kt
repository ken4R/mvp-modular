package com.mvp.android

import android.app.Application
import com.mvp.android.di.androidAppModule
import com.mvp.android.widget.TodoWidgetUpdater
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.core.crashreporting.domain.usecase.StartCrashReportingUseCase
import com.mvp.feature.todo.domain.usecase.PurgeExpiredTrashUseCase
import com.mvp.feature.todo.domain.usecase.StartTodoSyncUseCase
import com.mvp.feature.todo.domain.usecase.SyncTodoLocationRemindersUseCase
import com.mvp.feature.todo.domain.usecase.SyncTodoRemindersUseCase
import com.mvp.shared.di.initKoin
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext

class MvpApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidContext(this@MvpApplication)
            modules(androidAppModule)
        }
        // Plantages : enregistrés seulement avec le consentement Rapports de plantage, ceux du lancement précédent
        // partent maintenant (ADR 0020).
        MainScope().launch { get<StartCrashReportingUseCase>()(NoParams) }
        // Rappels de tâches : alignés sur les tâches, le réglage Notifications et la session tant que
        // le processus vit (portée jamais annulée, comme l'Application).
        get<SyncTodoRemindersUseCase>()(NoParams).launchIn(MainScope())
        // Rappels par lieu (ADR 0023) : alertes de proximité alignées sur les tâches, le réglage, la session et les
        // permissions. Aussi relancé au redémarrage du téléphone (LocationRemindersRestoreReceiver).
        get<SyncTodoLocationRemindersUseCase>()(NoParams).launchIn(MainScope())
        // Mode hors ligne : les modifications des tâches partent dès que le réseau revient (ADR 0012).
        get<StartTodoSyncUseCase>()(NoParams).launchIn(MainScope())
        // Corbeille (ADR 0025) : les tâches jetées depuis plus de 30 jours sont supprimées définitivement.
        MainScope().launch { get<PurgeExpiredTrashUseCase>()(NoParams) }
        // Widget Tâches : redessiné à chaque changement, effacé à la déconnexion ou quand le verrou est activé.
        TodoWidgetUpdater(this, get()).start(MainScope())
    }
}

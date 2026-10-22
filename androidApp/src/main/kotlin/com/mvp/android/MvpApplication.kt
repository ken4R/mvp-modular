package com.mvp.android

import android.app.Application
import com.mvp.android.di.androidAppModule
import com.mvp.android.widget.TodoWidgetUpdater
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.feature.todo.domain.usecase.SyncTodoRemindersUseCase
import com.mvp.shared.di.initKoin
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.launchIn
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext

class MvpApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidContext(this@MvpApplication)
            modules(androidAppModule)
        }
        // Rappels de tâches : alignés sur les tâches, le réglage Notifications et la session tant que
        // le processus vit (portée jamais annulée, comme l'Application).
        get<SyncTodoRemindersUseCase>()(NoParams).launchIn(MainScope())
        // Widget Tâches : redessiné à chaque changement, effacé à la déconnexion ou quand le verrou est activé.
        TodoWidgetUpdater(this, get()).start(MainScope())
    }
}

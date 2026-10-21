package com.mvp.android

import android.app.Application
import com.mvp.android.di.androidAppModule
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.feature.todo.domain.usecase.SyncTodoRemindersUseCase
import com.mvp.shared.di.initKoin
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.launchIn
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.android.get

class MvpApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidContext(this@MvpApplication)
            modules(androidAppModule)
        }
        // Rappels de tâches : alignés sur les tâches et le réglage Notifications tant que l'app vit.
        get<SyncTodoRemindersUseCase>()(NoParams).launchIn(MainScope())
    }
}

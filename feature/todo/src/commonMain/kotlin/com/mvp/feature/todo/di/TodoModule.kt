package com.mvp.feature.todo.di

import com.mvp.core.common.dispatcher.AppDispatchers
import com.mvp.core.common.domain.session.UserSessionObserver
import com.mvp.core.database.DatabaseDriverFactory
import com.mvp.feature.todo.data.db.TodoDatabase
import com.mvp.feature.todo.data.reminder.NotificationReminderScheduler
import com.mvp.feature.todo.data.repository.TodoRepositoryImpl
import com.mvp.feature.todo.data.session.TodoUserSessionObserver
import com.mvp.feature.todo.data.source.SqlDelightTodoLocalDataSource
import com.mvp.feature.todo.data.source.TodoLocalDataSource
import com.mvp.feature.todo.domain.reminder.ReminderScheduler
import com.mvp.feature.todo.domain.repository.TodoRepository
import com.mvp.feature.todo.domain.usecase.AddTodoUseCase
import com.mvp.feature.todo.domain.usecase.DeleteTodoUseCase
import com.mvp.feature.todo.domain.usecase.FindTodoUseCase
import com.mvp.feature.todo.domain.usecase.ObserveRemindersEnabledUseCase
import com.mvp.feature.todo.domain.usecase.ObserveTodosUseCase
import com.mvp.feature.todo.domain.usecase.SetTodoReminderUseCase
import com.mvp.feature.todo.domain.usecase.SyncTodoRemindersUseCase
import com.mvp.feature.todo.domain.usecase.ToggleTodoUseCase
import org.koin.dsl.bind
import org.koin.dsl.module
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
private val systemNow: () -> Long = { Clock.System.now().toEpochMilliseconds() }

val todoModule = module {
    // Data
    single { TodoDatabase(get<DatabaseDriverFactory>().create(TodoDatabase.Schema, "todo.db")) }
    single<TodoLocalDataSource> { SqlDelightTodoLocalDataSource(get(), get<AppDispatchers>().io) }
    single<TodoRepository> { TodoRepositoryImpl(get()) }
    single<ReminderScheduler> { NotificationReminderScheduler(get()) }
    // Prévenu par feature:auth : les tâches sont effacées et leurs rappels annulés à la déconnexion.
    single { TodoUserSessionObserver(get(), get()) } bind UserSessionObserver::class

    // Domain
    factory { ObserveTodosUseCase(get(), get<AppDispatchers>().io) }
    // AnalyticsTracker vient de core:analytics : il ne garde rien sans le consentement Analytics.
    factory { AddTodoUseCase(get(), get(), get<AppDispatchers>().io) }
    factory { ToggleTodoUseCase(get(), get(), get<AppDispatchers>().io) }
    factory { DeleteTodoUseCase(get(), get<AppDispatchers>().io) }
    factory { FindTodoUseCase(get(), get<AppDispatchers>().io) }
    factory { SetTodoReminderUseCase(get(), systemNow, get<AppDispatchers>().io) }
    // NotificationsPreference vient de feature:settings, SessionExpiry de feature:auth : sans dépendance directe.
    factory { ObserveRemindersEnabledUseCase(get(), get<AppDispatchers>().io) }
    factory { SyncTodoRemindersUseCase(get(), get(), get(), get(), systemNow, get<AppDispatchers>().io) }
}

package com.mvp.feature.todo.di

import com.mvp.core.common.dispatcher.AppDispatchers
import com.mvp.core.common.domain.session.UserSessionObserver
import com.mvp.core.database.DatabaseDriverFactory
import com.mvp.core.files.PrivateFileStoreFactory
import com.mvp.feature.todo.data.db.TodoDatabase
import com.mvp.feature.todo.data.reminder.NotificationReminderScheduler
import com.mvp.feature.todo.data.repository.TodoRepositoryImpl
import com.mvp.feature.todo.data.session.TodoUserSessionObserver
import com.mvp.feature.todo.data.source.SqlDelightTodoLocalDataSource
import com.mvp.feature.todo.data.source.TodoLocalDataSource
import com.mvp.feature.todo.data.sync.FakeTodoRemoteDataSource
import com.mvp.feature.todo.data.sync.TodoRemoteDataSource
import com.mvp.feature.todo.data.sync.TodoSyncLocalDataSource
import com.mvp.feature.todo.data.sync.TodoSyncRepositoryImpl
import com.mvp.feature.todo.domain.reminder.ReminderScheduler
import com.mvp.feature.todo.domain.repository.TodoRepository
import com.mvp.feature.todo.domain.repository.TodoSyncRepository
import com.mvp.feature.todo.domain.usecase.AddTodoAttachmentUseCase
import com.mvp.feature.todo.domain.usecase.AddTodoUseCase
import com.mvp.feature.todo.domain.usecase.DeleteTodoAttachmentUseCase
import com.mvp.feature.todo.domain.usecase.DeleteTodoUseCase
import com.mvp.feature.todo.domain.usecase.FindTodoUseCase
import com.mvp.feature.todo.domain.usecase.ObserveFilteredTodosUseCase
import com.mvp.feature.todo.domain.usecase.ObserveRemindersEnabledUseCase
import com.mvp.feature.todo.domain.usecase.ObserveTodoAttachmentsUseCase
import com.mvp.feature.todo.domain.usecase.ObserveTodoSyncStatusUseCase
import com.mvp.feature.todo.domain.usecase.ObserveTodoWidgetUseCase
import com.mvp.feature.todo.domain.usecase.ObserveTodosUseCase
import com.mvp.feature.todo.domain.usecase.SetTodoReminderUseCase
import com.mvp.feature.todo.domain.usecase.ShareTodoUseCase
import com.mvp.feature.todo.domain.usecase.StartTodoSyncUseCase
import com.mvp.feature.todo.domain.usecase.SyncTodoRemindersUseCase
import com.mvp.feature.todo.domain.usecase.SyncTodosUseCase
import com.mvp.feature.todo.domain.usecase.ToggleTodoUseCase
import com.mvp.feature.todo.domain.usecase.UpdateTodoNoteUseCase
import org.koin.dsl.bind
import org.koin.dsl.module
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
private val systemNow: () -> Long = { Clock.System.now().toEpochMilliseconds() }

val todoModule = module {
    // Data
    single { TodoDatabase(get<DatabaseDriverFactory>().create(TodoDatabase.Schema, "todo.db")) }
    // Une seule base pour les tâches et la file d'envoi de la synchronisation (ADR 0012) ; le contenu des pièces
    // jointes dans un dossier privé, hors sauvegardes (core:files, ADR 0014).
    single {
        SqlDelightTodoLocalDataSource(
            get(),
            get<AppDispatchers>().io,
            get<PrivateFileStoreFactory>().create(ATTACHMENTS_FOLDER),
            systemNow,
        )
    }
    single<TodoLocalDataSource> { get<SqlDelightTodoLocalDataSource>() }
    single<TodoSyncLocalDataSource> { get<SqlDelightTodoLocalDataSource>() }
    single<TodoRepository> { TodoRepositoryImpl(get()) }
    single<ReminderScheduler> { NotificationReminderScheduler(get()) }
    // Pas encore de backend : faux serveur en mémoire. ConnectivityObserver vient de core:connectivity.
    single<TodoRemoteDataSource> { FakeTodoRemoteDataSource(get()) }
    single { TodoSyncRepositoryImpl(get(), get()) }
    single<TodoSyncRepository> { get<TodoSyncRepositoryImpl>() }
    // Prévenu par feature:auth : les tâches et leurs modifications en attente sont effacées, leurs rappels
    // annulés à la déconnexion, après la synchronisation en cours.
    single { TodoUserSessionObserver(get(), get(), get()) } bind UserSessionObserver::class

    // Domain
    factory { ObserveTodosUseCase(get(), get<AppDispatchers>().io) }
    // Recherche et filtres de la liste, faits sur l'appareil.
    factory { ObserveFilteredTodosUseCase(get(), get<AppDispatchers>().io) }
    // AnalyticsTracker vient de core:analytics : il ne garde rien sans le consentement Analytics.
    factory { AddTodoUseCase(get(), get(), get<AppDispatchers>().io) }
    factory { ToggleTodoUseCase(get(), get(), get<AppDispatchers>().io) }
    factory { DeleteTodoUseCase(get(), get<AppDispatchers>().io) }
    factory { FindTodoUseCase(get(), get<AppDispatchers>().io) }
    factory { SetTodoReminderUseCase(get(), systemNow, get<AppDispatchers>().io) }
    // NotificationsPreference vient de feature:settings, SessionExpiry de feature:auth : sans dépendance directe.
    factory { ObserveRemindersEnabledUseCase(get(), get<AppDispatchers>().io) }
    factory { SyncTodoRemindersUseCase(get(), get(), get(), get(), systemNow, get<AppDispatchers>().io) }
    // Widget de l'écran d'accueil : AppLockPreference vient de feature:settings (ADR 0011).
    factory { ObserveTodoWidgetUseCase(get(), get(), get(), systemNow, get<AppDispatchers>().io) }
    // Mode hors ligne (ADR 0012) : StartTodoSyncUseCase est lancé une fois par chaque app.
    factory { StartTodoSyncUseCase(get(), get(), get(), get<AppDispatchers>().io) }
    factory { SyncTodosUseCase(get(), get(), get<AppDispatchers>().io) }
    factory { ObserveTodoSyncStatusUseCase(get(), get(), get<AppDispatchers>().io) }
    // Notes et pièces jointes (ADR 0014).
    factory { UpdateTodoNoteUseCase(get(), get<AppDispatchers>().io) }
    factory { ObserveTodoAttachmentsUseCase(get(), get<AppDispatchers>().io) }
    factory { AddTodoAttachmentUseCase(get(), get<AppDispatchers>().io) }
    factory { DeleteTodoAttachmentUseCase(get(), get<AppDispatchers>().io) }
    // Partage d'une tâche par la feuille de partage du système (ADR 0017).
    factory { ShareTodoUseCase(get(), get<AppDispatchers>().io) }
}

/** Dossier des fichiers des pièces jointes, effacé à la déconnexion. */
private const val ATTACHMENTS_FOLDER = "todo-attachments"

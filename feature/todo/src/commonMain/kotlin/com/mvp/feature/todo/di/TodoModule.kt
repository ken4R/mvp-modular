package com.mvp.feature.todo.di

import com.mvp.core.common.dispatcher.AppDispatchers
import com.mvp.core.common.domain.session.UserSessionObserver
import com.mvp.core.database.DatabaseDriverFactory
import com.mvp.core.files.PrivateFileStoreFactory
import com.mvp.feature.todo.data.calendar.SystemDayCalendar
import com.mvp.feature.todo.data.db.TodoDatabase
import com.mvp.feature.todo.data.reminder.NotificationLocationReminderScheduler
import com.mvp.feature.todo.data.reminder.NotificationReminderScheduler
import com.mvp.feature.todo.data.repository.TodoRepositoryImpl
import com.mvp.feature.todo.data.repository.TodoTrashRepositoryImpl
import com.mvp.feature.todo.data.session.TodoUserSessionObserver
import com.mvp.feature.todo.data.source.SqlDelightTodoLocalDataSource
import com.mvp.feature.todo.data.stats.SystemWeekCalendar
import com.mvp.feature.todo.data.stats.TodoStatsLocalDataSource
import com.mvp.feature.todo.data.stats.TodoStatsRepositoryImpl
import com.mvp.feature.todo.data.source.TodoLocalDataSource
import com.mvp.feature.todo.data.sync.FakeTodoRemoteDataSource
import com.mvp.feature.todo.data.sync.TodoRemoteDataSource
import com.mvp.feature.todo.data.sync.TodoSyncLocalDataSource
import com.mvp.feature.todo.data.sync.TodoSyncRepositoryImpl
import com.mvp.feature.todo.domain.calendar.DayCalendar
import com.mvp.feature.todo.domain.calendar.WeekCalendar
import com.mvp.feature.todo.domain.reminder.LocationReminderScheduler
import com.mvp.feature.todo.domain.reminder.ReminderScheduler
import com.mvp.feature.todo.domain.repository.TodoRepository
import com.mvp.feature.todo.domain.repository.TodoStatsRepository
import com.mvp.feature.todo.domain.repository.TodoSyncRepository
import com.mvp.feature.todo.domain.repository.TodoTrashRepository
import com.mvp.feature.todo.domain.usecase.AddTodoAttachmentUseCase
import com.mvp.feature.todo.domain.usecase.AddTodoUseCase
import com.mvp.feature.todo.domain.usecase.DeleteTodoAttachmentUseCase
import com.mvp.feature.todo.domain.usecase.DeleteTodoPermanentlyUseCase
import com.mvp.feature.todo.domain.usecase.DeleteTodoUseCase
import com.mvp.feature.todo.domain.usecase.EmptyTrashUseCase
import com.mvp.feature.todo.domain.usecase.ExportTodoToCalendarUseCase
import com.mvp.feature.todo.domain.usecase.FindTodoUseCase
import com.mvp.feature.todo.domain.usecase.MoveTodoUseCase
import com.mvp.feature.todo.domain.usecase.ObserveCompletionStatsUseCase
import com.mvp.feature.todo.domain.usecase.ObserveFilteredTodosUseCase
import com.mvp.feature.todo.domain.usecase.ObserveLocationRemindersAvailableUseCase
import com.mvp.feature.todo.domain.usecase.ObserveRemindersEnabledUseCase
import com.mvp.feature.todo.domain.usecase.ObserveTodoAttachmentsUseCase
import com.mvp.feature.todo.domain.usecase.ObserveTodoCalendarUseCase
import com.mvp.feature.todo.domain.usecase.ObserveTodoSyncStatusUseCase
import com.mvp.feature.todo.domain.usecase.ObserveTodoWidgetUseCase
import com.mvp.feature.todo.domain.usecase.ObserveTodosUseCase
import com.mvp.feature.todo.domain.usecase.ObserveTrashUseCase
import com.mvp.feature.todo.domain.usecase.PurgeExpiredTrashUseCase
import com.mvp.feature.todo.domain.usecase.RefreshLocationRemindersAvailabilityUseCase
import com.mvp.feature.todo.domain.usecase.RemoveLocationReminderUseCase
import com.mvp.feature.todo.domain.usecase.RestoreTodoUseCase
import com.mvp.feature.todo.domain.usecase.SetLocationReminderHereUseCase
import com.mvp.feature.todo.domain.usecase.SetTodoDueDateUseCase
import com.mvp.feature.todo.domain.usecase.SetTodoPriorityUseCase
import com.mvp.feature.todo.domain.usecase.SetTodoReminderUseCase
import com.mvp.feature.todo.domain.usecase.ShareTodoUseCase
import com.mvp.feature.todo.domain.usecase.StartTodoDraftUseCase
import com.mvp.feature.todo.domain.usecase.StartTodoSyncUseCase
import com.mvp.feature.todo.domain.usecase.SyncTodoLocationRemindersUseCase
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
    // Corbeille (ADR 0025) : même base, effacée avec les tâches à la déconnexion.
    single<TodoTrashRepository> { TodoTrashRepositoryImpl(get()) }
    // Statistiques (ADR 0019) : heures de fin gardées dans la même base, effacées avec les tâches à la déconnexion.
    single<TodoStatsLocalDataSource> { get<SqlDelightTodoLocalDataSource>() }
    single<TodoStatsRepository> { TodoStatsRepositoryImpl(get()) }
    single<WeekCalendar> { SystemWeekCalendar() }
    // Calendrier des échéances (ADR 0022) : jours dans le fuseau de l'appareil.
    single<DayCalendar> { SystemDayCalendar() }
    single<ReminderScheduler> { NotificationReminderScheduler(get()) }
    // Rappels par lieu (ADR 0023) : LocationNotificationScheduler vient de core:notifications.
    single<LocationReminderScheduler> { NotificationLocationReminderScheduler(get()) }
    // Pas encore de backend : faux serveur en mémoire. ConnectivityObserver vient de core:connectivity.
    single<TodoRemoteDataSource> { FakeTodoRemoteDataSource(get()) }
    single { TodoSyncRepositoryImpl(get(), get()) }
    single<TodoSyncRepository> { get<TodoSyncRepositoryImpl>() }
    // Prévenu par feature:auth : les tâches et leurs modifications en attente sont effacées, leurs rappels
    // annulés à la déconnexion, après la synchronisation en cours.
    single { TodoUserSessionObserver(get(), get(), get(), get()) } bind UserSessionObserver::class

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
    // Ajout à la voix (Siri, Google Assistant ; ADR 0018) : PendingDeepLinkRepository vient de core:deeplink.
    factory { StartTodoDraftUseCase(get(), get<AppDispatchers>().default) }
    // Statistiques : tâches terminées par semaine, semaines du lundi dans le fuseau de l'appareil (ADR 0019).
    factory { ObserveCompletionStatsUseCase(get(), get(), systemNow, get<AppDispatchers>().default) }
    // Calendrier : échéance synchronisée, mois des tâches par jour, export vers le calendrier du téléphone (ADR 0022).
    factory { SetTodoDueDateUseCase(get(), get<AppDispatchers>().io) }
    factory { ObserveTodoCalendarUseCase(get(), get(), systemNow, get<AppDispatchers>().default) }
    factory { ExportTodoToCalendarUseCase(get(), get<AppDispatchers>().io) }
    // Rappels par lieu (ADR 0023) : LocationProvider vient de core:location ; le lieu reste sur l'appareil.
    factory { SetLocationReminderHereUseCase(get(), get(), get<AppDispatchers>().io) }
    factory { RemoveLocationReminderUseCase(get(), get<AppDispatchers>().io) }
    factory { SyncTodoLocationRemindersUseCase(get(), get(), get(), get(), get<AppDispatchers>().io) }
    factory { ObserveLocationRemindersAvailableUseCase(get(), get<AppDispatchers>().io) }
    factory { RefreshLocationRemindersAvailabilityUseCase(get(), get<AppDispatchers>().default) }
    // Priorité et ordre manuel (glisser-déposer), synchronisés avec la tâche (ADR 0024).
    factory { SetTodoPriorityUseCase(get(), get<AppDispatchers>().io) }
    factory { MoveTodoUseCase(get(), get<AppDispatchers>().io) }
    // Corbeille avec annulation, 30 jours avant la suppression définitive (ADR 0025). PurgeExpiredTrashUseCase est
    // lancé par chaque app au démarrage.
    factory { RestoreTodoUseCase(get(), get<AppDispatchers>().io) }
    factory { ObserveTrashUseCase(get(), systemNow, get<AppDispatchers>().io) }
    factory { DeleteTodoPermanentlyUseCase(get(), get<AppDispatchers>().io) }
    factory { EmptyTrashUseCase(get(), get<AppDispatchers>().io) }
    factory { PurgeExpiredTrashUseCase(get(), systemNow, get<AppDispatchers>().io) }
}

/** Dossier des fichiers des pièces jointes, effacé à la déconnexion. */
private const val ATTACHMENTS_FOLDER = "todo-attachments"

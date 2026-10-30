package com.mvp.feature.todo.ios

import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.core.common.ios.Subscription
import com.mvp.core.common.ios.SwiftUseCases
import com.mvp.feature.todo.domain.model.CalendarMonth
import com.mvp.feature.todo.domain.model.CalendarMonthRequest
import com.mvp.feature.todo.domain.model.CompletionStats
import com.mvp.feature.todo.domain.model.FilteredTodos
import com.mvp.feature.todo.domain.model.Todo
import com.mvp.feature.todo.domain.model.TodoAttachment
import com.mvp.feature.todo.domain.model.TodoFilter
import com.mvp.feature.todo.domain.model.TodoSyncStatus
import com.mvp.feature.todo.domain.model.TodoWidgetState
import com.mvp.feature.todo.domain.model.TrashItem
import com.mvp.feature.todo.domain.usecase.AddTodoAttachmentUseCase
import com.mvp.feature.todo.domain.usecase.AddTodoUseCase
import com.mvp.feature.todo.domain.usecase.CompleteTodoFromWidgetUseCase
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
import kotlinx.coroutines.launch
import org.koin.core.component.get

/**
 * Point d'accès Swift aux use cases des tâches.
 * Koin n'est pas utilisable directement depuis Swift. Les use cases suspend s'appellent en `async` côté Swift ;
 * les Flow passent par des callbacks (voir SwiftUseCases).
 */
object TodoUseCases : SwiftUseCases() {
    fun addTodo(): AddTodoUseCase = get()
    fun toggleTodo(): ToggleTodoUseCase = get()
    fun deleteTodo(): DeleteTodoUseCase = get()
    fun restoreTodo(): RestoreTodoUseCase = get()
    fun deleteTodoPermanently(): DeleteTodoPermanentlyUseCase = get()
    fun emptyTrash(): EmptyTrashUseCase = get()
    fun purgeExpiredTrash(): PurgeExpiredTrashUseCase = get()
    fun findTodo(): FindTodoUseCase = get()
    fun setTodoReminder(): SetTodoReminderUseCase = get()
    fun setTodoDueDate(): SetTodoDueDateUseCase = get()
    fun setTodoPriority(): SetTodoPriorityUseCase = get()
    fun moveTodo(): MoveTodoUseCase = get()
    fun setLocationReminderHere(): SetLocationReminderHereUseCase = get()
    fun removeLocationReminder(): RemoveLocationReminderUseCase = get()
    fun refreshLocationRemindersAvailability(): RefreshLocationRemindersAvailabilityUseCase = get()
    fun exportTodoToCalendar(): ExportTodoToCalendarUseCase = get()
    fun updateTodoNote(): UpdateTodoNoteUseCase = get()
    fun addTodoAttachment(): AddTodoAttachmentUseCase = get()
    fun deleteTodoAttachment(): DeleteTodoAttachmentUseCase = get()
    fun shareTodo(): ShareTodoUseCase = get()
    fun syncTodos(): SyncTodosUseCase = get()

    fun startTodoDraft(): StartTodoDraftUseCase = get()

    /** Les callbacks sont appelés sur le main thread. */
    fun observeTodos(onTodos: (List<Todo>) -> Unit, onError: (AppError) -> Unit): Subscription =
        get<ObserveTodosUseCase>()(NoParams).subscribe(onTodos, onError)

    /**
     * Tâches qui correspondent à [filter] (recherche et filtres), et toutes les tâches. Un nouveau filtre demande un
     * nouvel abonnement, l'ancien étant annulé. Les callbacks sont appelés sur le main thread.
     */
    fun observeFilteredTodos(
        filter: TodoFilter,
        onTodos: (FilteredTodos) -> Unit,
        onError: (AppError) -> Unit,
    ): Subscription =
        get<ObserveFilteredTodosUseCase>()(filter).subscribe(onTodos, onError)

    /**
     * Corbeille (ADR 0025) : tâches jetées et jours restants avant leur suppression définitive. Les callbacks sont
     * appelés sur le main thread.
     */
    fun observeTrash(onItems: (List<TrashItem>) -> Unit, onError: (AppError) -> Unit): Subscription =
        get<ObserveTrashUseCase>()(NoParams).subscribe(onItems, onError)

    /**
     * Statistiques : tâches terminées par semaine, semaines du lundi dans le fuseau de l'appareil (ADR 0019).
     * Les callbacks sont appelés sur le main thread.
     */
    fun observeCompletionStats(onStats: (CompletionStats) -> Unit, onError: (AppError) -> Unit): Subscription =
        get<ObserveCompletionStatsUseCase>()(NoParams).subscribe(onStats, onError)

    /**
     * Calendrier des tâches par échéance pour le mois [request] (ADR 0022), recalculé à minuit. Un autre mois demande
     * un nouvel abonnement, l'ancien étant annulé. Les callbacks sont appelés sur le main thread.
     */
    fun observeTodoCalendar(
        request: CalendarMonthRequest,
        onMonth: (CalendarMonth) -> Unit,
        onError: (AppError) -> Unit,
    ): Subscription =
        get<ObserveTodoCalendarUseCase>()(request).subscribe(onMonth, onError)

    /** Pièces jointes de la tâche [todoId] (ADR 0014). Les callbacks sont appelés sur le main thread. */
    fun observeTodoAttachments(
        todoId: String,
        onAttachments: (List<TodoAttachment>) -> Unit,
        onError: (AppError) -> Unit,
    ): Subscription =
        get<ObserveTodoAttachmentsUseCase>()(todoId).subscribe(onAttachments, onError)

    /**
     * Contenu du widget Tâches de l'écran d'accueil (ADR 0011), à recopier dans le fichier partagé avec
     * l'extension WidgetKit. En cas d'erreur, n'y écrire aucune tâche. Les callbacks sont appelés sur le main thread.
     */
    fun observeTodoWidget(onState: (TodoWidgetState) -> Unit, onError: (AppError) -> Unit): Subscription =
        get<ObserveTodoWidgetUseCase>()(NoParams).subscribe(onState, onError)

    /**
     * Cases cochées dans le widget Tâches depuis la dernière ouverture de l'app (ADR 0026), appliquées une à une par
     * le use case partagé (session, verrou, corbeille vérifiés ; la modification part par la file d'envoi).
     * [onDone] est appelé sur le main thread une fois toutes traitées : les demandes refusées (déconnecté, verrou
     * activé, tâche supprimée) sont abandonnées, comme celles qui ont réussi.
     */
    fun completeTodosFromWidget(todoIds: List<String>, onDone: () -> Unit) {
        scope.launch {
            val complete = get<CompleteTodoFromWidgetUseCase>()
            todoIds.distinct().forEach { complete(it) }
            onDone()
        }
    }

    /**
     * Synchronisation automatique des tâches (mode hors ligne, ADR 0012). À appeler une seule fois au lancement
     * de l'app, et à garder pendant toute sa vie.
     */
    fun startTodoSync(): Subscription = get<StartTodoSyncUseCase>()(NoParams).launchSubscription()

    /** Bandeau de l'écran Tâches : hors ligne, modifications en attente, échec. Les callbacks sont appelés sur le main thread. */
    fun observeTodoSyncStatus(onStatus: (TodoSyncStatus) -> Unit, onError: (AppError) -> Unit): Subscription =
        get<ObserveTodoSyncStatusUseCase>()(NoParams).subscribe(onStatus, onError)

    /** `true` si les rappels peuvent partir (réglage Notifications activé). Les callbacks sont appelés sur le main thread. */
    fun observeRemindersEnabled(onEnabled: (Boolean) -> Unit, onError: (AppError) -> Unit): Subscription =
        get<ObserveRemindersEnabledUseCase>()(NoParams).subscribe(onEnabled, onError)

    /**
     * Garde les notifications de rappel alignées sur les tâches et le réglage Notifications.
     * À appeler une seule fois au lancement de l'app, et à garder pendant toute sa vie.
     */
    fun startTodoReminders(): Subscription = get<SyncTodoRemindersUseCase>()(NoParams).launchSubscription()

    /**
     * Garde les rappels par lieu alignés sur les tâches, le réglage Notifications et les permissions (ADR 0023).
     * À appeler une seule fois au lancement de l'app, et à garder pendant toute sa vie.
     */
    fun startTodoLocationReminders(): Subscription =
        get<SyncTodoLocationRemindersUseCase>()(NoParams).launchSubscription()

    /** `true` si les rappels par lieu peuvent partir (position exacte autorisée). Les callbacks sont appelés sur le main thread. */
    fun observeLocationRemindersAvailable(onAvailable: (Boolean) -> Unit, onError: (AppError) -> Unit): Subscription =
        get<ObserveLocationRemindersAvailableUseCase>()(NoParams).subscribe(onAvailable, onError)
}

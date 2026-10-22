package com.mvp.shared

import com.mvp.core.analytics.domain.model.AnalyticsJournal
import com.mvp.core.analytics.domain.usecase.FlushAnalyticsUseCase
import com.mvp.core.analytics.domain.usecase.ObserveAnalyticsJournalUseCase
import com.mvp.core.analytics.domain.usecase.TrackEventUseCase
import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.session.UserAccount
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.core.deeplink.domain.model.DeepLink
import com.mvp.core.deeplink.domain.usecase.ConsumeDeepLinkUseCase
import com.mvp.core.deeplink.domain.usecase.ObservePendingDeepLinkUseCase
import com.mvp.core.deeplink.domain.usecase.OpenDeepLinkUseCase
import com.mvp.feature.auth.domain.usecase.LoginUseCase
import com.mvp.feature.auth.domain.usecase.LogoutUseCase
import com.mvp.feature.auth.domain.usecase.ObserveSessionUseCase
import com.mvp.feature.catalog.domain.model.Catalog
import com.mvp.feature.catalog.domain.usecase.GetProductUseCase
import com.mvp.feature.catalog.domain.usecase.LoadMoreProductsUseCase
import com.mvp.feature.catalog.domain.usecase.ObserveCatalogUseCase
import com.mvp.feature.catalog.domain.usecase.RefreshCatalogUseCase
import com.mvp.feature.settings.domain.model.UserSettings
import com.mvp.feature.settings.domain.usecase.LockAppUseCase
import com.mvp.feature.settings.domain.usecase.ObserveAppLockUseCase
import com.mvp.feature.settings.domain.usecase.ObserveSettingsUseCase
import com.mvp.feature.settings.domain.usecase.SetAppLockEnabledUseCase
import com.mvp.feature.settings.domain.usecase.SetConsentUseCase
import com.mvp.feature.settings.domain.usecase.SetNotificationsEnabledUseCase
import com.mvp.feature.settings.domain.usecase.UnlockAppUseCase
import com.mvp.feature.settings.domain.usecase.UpdateProfileUseCase
import com.mvp.feature.todo.domain.model.Todo
import com.mvp.feature.todo.domain.usecase.AddTodoUseCase
import com.mvp.feature.todo.domain.usecase.DeleteTodoUseCase
import com.mvp.feature.todo.domain.usecase.FindTodoUseCase
import com.mvp.feature.todo.domain.usecase.ObserveRemindersEnabledUseCase
import com.mvp.feature.todo.domain.usecase.ObserveTodosUseCase
import com.mvp.feature.todo.domain.usecase.SetTodoReminderUseCase
import com.mvp.feature.todo.domain.usecase.SyncTodoRemindersUseCase
import com.mvp.feature.todo.domain.usecase.ToggleTodoUseCase
import com.mvp.feature.weather.domain.usecase.GetCurrentWeatherUseCase
import com.mvp.feature.weather.domain.usecase.GetLocalWeatherUseCase
import com.mvp.feature.weather.domain.usecase.SearchCitiesUseCase
import com.mvp.shared.di.initKoin
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/** Appelé une seule fois au lancement de l'app iOS. */
fun doInitKoin() {
    initKoin()
}

/** Abonnement à un Flow côté Swift : appeler `cancel()` quand la vue disparaît. */
class Subscription internal constructor(private val job: Job) {
    fun cancel() = job.cancel()
}

/**
 * Point d'accès Swift aux use cases (Koin n'est pas utilisable directement depuis Swift).
 * Les use cases suspend s'appellent en `async` côté Swift ; les Flow passent par des callbacks.
 */
object UseCases : KoinComponent {
    private val scope = MainScope()

    fun addTodo(): AddTodoUseCase = get()
    fun toggleTodo(): ToggleTodoUseCase = get()
    fun deleteTodo(): DeleteTodoUseCase = get()
    fun findTodo(): FindTodoUseCase = get()
    fun setTodoReminder(): SetTodoReminderUseCase = get()

    fun searchCities(): SearchCitiesUseCase = get()
    fun getCurrentWeather(): GetCurrentWeatherUseCase = get()
    fun getLocalWeather(): GetLocalWeatherUseCase = get()

    fun updateProfile(): UpdateProfileUseCase = get()
    fun setNotificationsEnabled(): SetNotificationsEnabledUseCase = get()
    fun setConsent(): SetConsentUseCase = get()
    fun setAppLockEnabled(): SetAppLockEnabledUseCase = get()
    fun lockApp(): LockAppUseCase = get()
    fun unlockApp(): UnlockAppUseCase = get()

    fun login(): LoginUseCase = get()
    fun logout(): LogoutUseCase = get()

    fun refreshCatalog(): RefreshCatalogUseCase = get()
    fun loadMoreProducts(): LoadMoreProductsUseCase = get()
    fun getProduct(): GetProductUseCase = get()

    fun openDeepLink(): OpenDeepLinkUseCase = get()
    fun consumeDeepLink(): ConsumeDeepLinkUseCase = get()

    fun trackEvent(): TrackEventUseCase = get()
    fun flushAnalytics(): FlushAnalyticsUseCase = get()

    /** Journal de mesure (builds de debug). Les callbacks sont appelés sur le main thread. */
    fun observeAnalyticsJournal(onJournal: (AnalyticsJournal) -> Unit, onError: (AppError) -> Unit): Subscription {
        val job = get<ObserveAnalyticsJournalUseCase>()(NoParams)
            .onEach { result ->
                when (result) {
                    is AppResult.Success -> onJournal(result.data)
                    is AppResult.Failure -> onError(result.error)
                }
            }
            .launchIn(scope)
        return Subscription(job)
    }

    /** Lien profond en attente d'affichage, ou `null`. Les callbacks sont appelés sur le main thread. */
    fun observePendingDeepLink(onLink: (DeepLink?) -> Unit, onError: (AppError) -> Unit): Subscription {
        val job = get<ObservePendingDeepLinkUseCase>()(NoParams)
            .onEach { result ->
                when (result) {
                    is AppResult.Success -> onLink(result.data)
                    is AppResult.Failure -> onError(result.error)
                }
            }
            .launchIn(scope)
        return Subscription(job)
    }

    /** Les callbacks sont appelés sur le main thread. */
    fun observeTodos(onTodos: (List<Todo>) -> Unit, onError: (AppError) -> Unit): Subscription {
        val job = get<ObserveTodosUseCase>()(NoParams)
            .onEach { result ->
                when (result) {
                    is AppResult.Success -> onTodos(result.data)
                    is AppResult.Failure -> onError(result.error)
                }
            }
            .launchIn(scope)
        return Subscription(job)
    }

    /** `true` si les rappels peuvent partir (réglage Notifications activé). Les callbacks sont appelés sur le main thread. */
    fun observeRemindersEnabled(onEnabled: (Boolean) -> Unit, onError: (AppError) -> Unit): Subscription {
        val job = get<ObserveRemindersEnabledUseCase>()(NoParams)
            .onEach { result ->
                when (result) {
                    is AppResult.Success -> onEnabled(result.data)
                    is AppResult.Failure -> onError(result.error)
                }
            }
            .launchIn(scope)
        return Subscription(job)
    }

    /**
     * Garde les notifications de rappel alignées sur les tâches et le réglage Notifications.
     * À appeler une seule fois au lancement de l'app, et à garder pendant toute sa vie.
     */
    fun startTodoReminders(): Subscription = Subscription(get<SyncTodoRemindersUseCase>()(NoParams).launchIn(scope))

    /** Les callbacks sont appelés sur le main thread. */
    fun observeSettings(onSettings: (UserSettings) -> Unit, onError: (AppError) -> Unit): Subscription {
        val job = get<ObserveSettingsUseCase>()(NoParams)
            .onEach { result ->
                when (result) {
                    is AppResult.Success -> onSettings(result.data)
                    is AppResult.Failure -> onError(result.error)
                }
            }
            .launchIn(scope)
        return Subscription(job)
    }

    /** `true` : afficher l'écran de déverrouillage (Face ID / Touch ID). Les callbacks sont appelés sur le main thread. */
    fun observeAppLock(onLocked: (Boolean) -> Unit, onError: (AppError) -> Unit): Subscription {
        val job = get<ObserveAppLockUseCase>()(NoParams)
            .onEach { result ->
                when (result) {
                    is AppResult.Success -> onLocked(result.data)
                    is AppResult.Failure -> onError(result.error)
                }
            }
            .launchIn(scope)
        return Subscription(job)
    }

    /** Compte connecté, ou `null` (écran de connexion). Les callbacks sont appelés sur le main thread. */
    fun observeSession(onAccount: (UserAccount?) -> Unit, onError: (AppError) -> Unit): Subscription {
        val job = get<ObserveSessionUseCase>()(NoParams)
            .onEach { result ->
                when (result) {
                    is AppResult.Success -> onAccount(result.data)
                    is AppResult.Failure -> onError(result.error)
                }
            }
            .launchIn(scope)
        return Subscription(job)
    }

    /** Catalogue en cache (disponible hors ligne). Les callbacks sont appelés sur le main thread. */
    fun observeCatalog(onCatalog: (Catalog) -> Unit, onError: (AppError) -> Unit): Subscription {
        val job = get<ObserveCatalogUseCase>()(NoParams)
            .onEach { result ->
                when (result) {
                    is AppResult.Success -> onCatalog(result.data)
                    is AppResult.Failure -> onError(result.error)
                }
            }
            .launchIn(scope)
        return Subscription(job)
    }
}

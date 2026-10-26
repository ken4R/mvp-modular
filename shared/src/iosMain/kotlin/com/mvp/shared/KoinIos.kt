package com.mvp.shared

import com.mvp.core.analytics.domain.model.AnalyticsJournal
import com.mvp.core.analytics.domain.usecase.FlushAnalyticsUseCase
import com.mvp.core.analytics.domain.usecase.ObserveAnalyticsJournalUseCase
import com.mvp.core.analytics.domain.usecase.TrackEventUseCase
import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.session.UserAccount
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.core.connectivity.domain.usecase.ObserveConnectivityUseCase
import com.mvp.core.crashreporting.domain.model.CrashReport
import com.mvp.core.crashreporting.domain.model.TestCrashException
import com.mvp.core.crashreporting.domain.usecase.ObserveCrashJournalUseCase
import com.mvp.core.crashreporting.domain.usecase.StartCrashReportingUseCase
import com.mvp.core.deeplink.domain.model.DeepLink
import com.mvp.core.deeplink.domain.usecase.ConsumeDeepLinkUseCase
import com.mvp.core.deeplink.domain.usecase.ObservePendingDeepLinkUseCase
import com.mvp.core.deeplink.domain.usecase.OpenDeepLinkUseCase
import com.mvp.core.designsystem.Appearance
import com.mvp.core.featureflags.domain.model.FeatureFlag
import com.mvp.core.featureflags.domain.model.FeatureFlagState
import com.mvp.core.featureflags.domain.usecase.ClearFeatureFlagOverridesUseCase
import com.mvp.core.featureflags.domain.usecase.ObserveFeatureFlagUseCase
import com.mvp.core.featureflags.domain.usecase.ObserveFeatureFlagsUseCase
import com.mvp.core.featureflags.domain.usecase.RefreshRemoteFlagsUseCase
import com.mvp.core.featureflags.domain.usecase.SetFeatureFlagOverrideUseCase
import com.mvp.feature.auth.domain.usecase.LoginUseCase
import com.mvp.feature.auth.domain.usecase.LogoutUseCase
import com.mvp.feature.auth.domain.usecase.ObserveSessionUseCase
import com.mvp.feature.catalog.domain.model.Catalog
import com.mvp.feature.catalog.domain.model.CatalogFilter
import com.mvp.feature.catalog.domain.model.CatalogSort
import com.mvp.feature.catalog.domain.model.FilteredCatalog
import com.mvp.feature.catalog.domain.usecase.GetProductUseCase
import com.mvp.feature.catalog.domain.usecase.LoadMoreProductsUseCase
import com.mvp.feature.catalog.domain.usecase.ObserveCatalogUseCase
import com.mvp.feature.catalog.domain.usecase.ObserveFilteredCatalogUseCase
import com.mvp.feature.catalog.domain.usecase.RefreshCatalogUseCase
import com.mvp.feature.onboarding.domain.usecase.CompleteOnboardingUseCase
import com.mvp.feature.onboarding.domain.usecase.GetOnboardingStepsUseCase
import com.mvp.feature.onboarding.domain.usecase.ObserveOnboardingRequiredUseCase
import com.mvp.feature.onboarding.domain.usecase.RestartOnboardingUseCase
import com.mvp.feature.settings.domain.model.ConsentType
import com.mvp.feature.settings.domain.model.UserSettings
import com.mvp.feature.settings.domain.usecase.LockAppUseCase
import com.mvp.feature.settings.domain.usecase.ObserveAppLockUseCase
import com.mvp.feature.settings.domain.usecase.ObserveAppearanceUseCase
import com.mvp.feature.settings.domain.usecase.ObserveSettingsUseCase
import com.mvp.feature.settings.domain.usecase.SetAccentColorUseCase
import com.mvp.feature.settings.domain.usecase.SetAppLockEnabledUseCase
import com.mvp.feature.settings.domain.usecase.SetConsentUseCase
import com.mvp.feature.settings.domain.usecase.SetNotificationsEnabledUseCase
import com.mvp.feature.settings.domain.usecase.SetThemeModeUseCase
import com.mvp.feature.settings.domain.usecase.UnlockAppUseCase
import com.mvp.feature.settings.domain.usecase.UpdateProfileUseCase
import com.mvp.feature.todo.domain.model.CalendarMonth
import com.mvp.feature.todo.domain.model.CalendarMonthRequest
import com.mvp.feature.todo.domain.model.CompletionStats
import com.mvp.feature.todo.domain.model.FilteredTodos
import com.mvp.feature.todo.domain.model.Todo
import com.mvp.feature.todo.domain.model.TodoAttachment
import com.mvp.feature.todo.domain.model.TodoFilter
import com.mvp.feature.todo.domain.model.TodoStatusFilter
import com.mvp.feature.todo.domain.model.TodoSyncStatus
import com.mvp.feature.todo.domain.model.TodoWidgetState
import com.mvp.feature.todo.domain.usecase.AddTodoAttachmentUseCase
import com.mvp.feature.todo.domain.usecase.AddTodoUseCase
import com.mvp.feature.todo.domain.usecase.DeleteTodoAttachmentUseCase
import com.mvp.feature.todo.domain.usecase.DeleteTodoUseCase
import com.mvp.feature.todo.domain.usecase.ExportTodoToCalendarUseCase
import com.mvp.feature.todo.domain.usecase.FindTodoUseCase
import com.mvp.feature.todo.domain.usecase.ObserveCompletionStatsUseCase
import com.mvp.feature.todo.domain.usecase.ObserveFilteredTodosUseCase
import com.mvp.feature.todo.domain.usecase.ObserveLocationRemindersAvailableUseCase
import com.mvp.feature.todo.domain.usecase.ObserveRemindersEnabledUseCase
import com.mvp.feature.todo.domain.usecase.ObserveTodoAttachmentsUseCase
import com.mvp.feature.todo.domain.usecase.ObserveTodoCalendarUseCase
import com.mvp.feature.todo.domain.usecase.ObserveTodoSyncStatusUseCase
import com.mvp.feature.todo.domain.usecase.ObserveTodoWidgetUseCase
import com.mvp.feature.todo.domain.usecase.ObserveTodosUseCase
import com.mvp.feature.todo.domain.usecase.RefreshLocationRemindersAvailabilityUseCase
import com.mvp.feature.todo.domain.usecase.RemoveLocationReminderUseCase
import com.mvp.feature.todo.domain.usecase.SetLocationReminderHereUseCase
import com.mvp.feature.todo.domain.usecase.SetTodoDueDateUseCase
import com.mvp.feature.todo.domain.usecase.SetTodoReminderUseCase
import com.mvp.feature.todo.domain.usecase.ShareTodoUseCase
import com.mvp.feature.todo.domain.usecase.StartTodoDraftUseCase
import com.mvp.feature.todo.domain.usecase.StartTodoSyncUseCase
import com.mvp.feature.todo.domain.usecase.SyncTodoLocationRemindersUseCase
import com.mvp.feature.todo.domain.usecase.SyncTodoRemindersUseCase
import com.mvp.feature.todo.domain.usecase.SyncTodosUseCase
import com.mvp.feature.todo.domain.usecase.ToggleTodoUseCase
import com.mvp.feature.todo.domain.usecase.UpdateTodoNoteUseCase
import com.mvp.feature.weather.domain.usecase.GetCurrentWeatherUseCase
import com.mvp.feature.weather.domain.usecase.GetLocalWeatherUseCase
import com.mvp.feature.weather.domain.usecase.SearchCitiesUseCase
import com.mvp.shared.di.initKoin
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import platform.Foundation.NSData
import platform.posix.memcpy

/**
 * Filtre des tâches construit depuis Swift, qui renomme les entrées des enum Kotlin : [status] est le nom Kotlin
 * de l'entrée de `TodoStatusFilter` (« All », « Pending », « Done ») ; un nom inconnu vaut « All ».
 */
fun todoFilter(query: String, status: String, withReminder: Boolean, withAttachments: Boolean): TodoFilter = TodoFilter(
    query = query,
    status = TodoStatusFilter.entries.firstOrNull { it.name == status } ?: TodoStatusFilter.All,
    withReminder = withReminder,
    withAttachments = withAttachments,
)

/**
 * Filtre du catalogue construit depuis Swift : [sort] est le nom Kotlin de l'entrée de `CatalogSort`
 * (« ServerOrder », « PriceAscending », « PriceDescending », « BestRated ») ; un nom inconnu vaut « ServerOrder ».
 */
fun catalogFilter(query: String, topRatedOnly: Boolean, sort: String): CatalogFilter = CatalogFilter(
    query = query,
    topRatedOnly = topRatedOnly,
    sort = CatalogSort.entries.firstOrNull { it.name == sort } ?: CatalogSort.ServerOrder,
)

/**
 * Consentement par son nom Kotlin (« Analytics », « CrashReports »...), pour Swift qui renomme les entrées des enum
 * Kotlin ; `null` pour un nom inconnu.
 */
fun consentType(name: String): ConsentType? = ConsentType.entries.firstOrNull { it.name == name }

/**
 * Feature flag par sa clé du JSON distant (« catalog », « preview_banner »), pour Swift qui renomme les entrées des
 * enum Kotlin ; `null` pour une clé inconnue (ADR 0021).
 */
fun featureFlag(key: String): FeatureFlag? = FeatureFlag.fromKey(key)

/** Appelé une seule fois au lancement de l'app iOS. */
fun doInitKoin() {
    initKoin()
}

/**
 * Contenu d'un fichier choisi en Swift (`Data`), pour `NewTodoAttachment` : une seule copie mémoire, au lieu
 * d'un octet à la fois depuis Swift.
 */
@OptIn(ExperimentalForeignApi::class)
fun bytesOf(data: NSData): ByteArray {
    val size = data.length.toInt()
    val bytes = ByteArray(size)
    if (size > 0) bytes.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
    return bytes
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
    fun setTodoDueDate(): SetTodoDueDateUseCase = get()
    fun setLocationReminderHere(): SetLocationReminderHereUseCase = get()
    fun removeLocationReminder(): RemoveLocationReminderUseCase = get()
    fun refreshLocationRemindersAvailability(): RefreshLocationRemindersAvailabilityUseCase = get()
    fun exportTodoToCalendar(): ExportTodoToCalendarUseCase = get()
    fun updateTodoNote(): UpdateTodoNoteUseCase = get()
    fun addTodoAttachment(): AddTodoAttachmentUseCase = get()
    fun deleteTodoAttachment(): DeleteTodoAttachmentUseCase = get()
    fun shareTodo(): ShareTodoUseCase = get()
    fun syncTodos(): SyncTodosUseCase = get()

    fun searchCities(): SearchCitiesUseCase = get()
    fun getCurrentWeather(): GetCurrentWeatherUseCase = get()
    fun getLocalWeather(): GetLocalWeatherUseCase = get()

    fun updateProfile(): UpdateProfileUseCase = get()
    fun setNotificationsEnabled(): SetNotificationsEnabledUseCase = get()
    fun setConsent(): SetConsentUseCase = get()
    fun setAppLockEnabled(): SetAppLockEnabledUseCase = get()
    fun setThemeMode(): SetThemeModeUseCase = get()
    fun setAccentColor(): SetAccentColorUseCase = get()
    fun lockApp(): LockAppUseCase = get()
    fun unlockApp(): UnlockAppUseCase = get()

    fun login(): LoginUseCase = get()
    fun logout(): LogoutUseCase = get()

    fun refreshCatalog(): RefreshCatalogUseCase = get()
    fun loadMoreProducts(): LoadMoreProductsUseCase = get()
    fun getProduct(): GetProductUseCase = get()

    fun openDeepLink(): OpenDeepLinkUseCase = get()
    fun consumeDeepLink(): ConsumeDeepLinkUseCase = get()
    fun startTodoDraft(): StartTodoDraftUseCase = get()

    fun trackEvent(): TrackEventUseCase = get()
    fun flushAnalytics(): FlushAnalyticsUseCase = get()

    fun startCrashReporting(): StartCrashReportingUseCase = get()

    fun refreshRemoteFlags(): RefreshRemoteFlagsUseCase = get()
    fun setFeatureFlagOverride(): SetFeatureFlagOverrideUseCase = get()
    fun clearFeatureFlagOverrides(): ClearFeatureFlagOverridesUseCase = get()

    /** `true` si la feature doit être affichée (ADR 0021). Les callbacks sont appelés sur le main thread. */
    fun observeFeatureFlag(flag: FeatureFlag, onEnabled: (Boolean) -> Unit, onError: (AppError) -> Unit): Subscription {
        val job = get<ObserveFeatureFlagUseCase>()(flag)
            .onEach { result ->
                when (result) {
                    is AppResult.Success -> onEnabled(result.data)
                    is AppResult.Failure -> onError(result.error)
                }
            }
            .launchIn(scope)
        return Subscription(job)
    }

    /** Tous les flags pour le menu debug. Les callbacks sont appelés sur le main thread. */
    fun observeFeatureFlags(onFlags: (List<FeatureFlagState>) -> Unit, onError: (AppError) -> Unit): Subscription {
        val job = get<ObserveFeatureFlagsUseCase>()(NoParams)
            .onEach { result ->
                when (result) {
                    is AppResult.Success -> onFlags(result.data)
                    is AppResult.Failure -> onError(result.error)
                }
            }
            .launchIn(scope)
        return Subscription(job)
    }

    /**
     * « Provoquer un plantage » des builds de debug : exception Kotlin levée vers Swift sans `@Throws`, donc non
     * rattrapable, comme un vrai plantage KMP. L'app s'arrête ; le rapport part au lancement suivant (ADR 0020).
     */
    fun crashForTest(): Unit = throw TestCrashException()

    fun getOnboardingSteps(): GetOnboardingStepsUseCase = get()
    fun completeOnboarding(): CompleteOnboardingUseCase = get()
    fun restartOnboarding(): RestartOnboardingUseCase = get()

    /** `true` : afficher la présentation avant les onglets (ADR 0016). Les callbacks sont appelés sur le main thread. */
    fun observeOnboardingRequired(onRequired: (Boolean) -> Unit, onError: (AppError) -> Unit): Subscription {
        val job = get<ObserveOnboardingRequiredUseCase>()(NoParams)
            .onEach { result ->
                when (result) {
                    is AppResult.Success -> onRequired(result.data)
                    is AppResult.Failure -> onError(result.error)
                }
            }
            .launchIn(scope)
        return Subscription(job)
    }

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

    /** Journal des rapports de plantage (builds de debug). Les callbacks sont appelés sur le main thread. */
    fun observeCrashJournal(onJournal: (List<CrashReport>) -> Unit, onError: (AppError) -> Unit): Subscription {
        val job = get<ObserveCrashJournalUseCase>()(NoParams)
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

    /**
     * Tâches qui correspondent à [filter] (recherche et filtres), et toutes les tâches. Un nouveau filtre demande un
     * nouvel abonnement, l'ancien étant annulé. Les callbacks sont appelés sur le main thread.
     */
    fun observeFilteredTodos(
        filter: TodoFilter,
        onTodos: (FilteredTodos) -> Unit,
        onError: (AppError) -> Unit,
    ): Subscription {
        val job = get<ObserveFilteredTodosUseCase>()(filter)
            .onEach { result ->
                when (result) {
                    is AppResult.Success -> onTodos(result.data)
                    is AppResult.Failure -> onError(result.error)
                }
            }
            .launchIn(scope)
        return Subscription(job)
    }

    /**
     * Statistiques : tâches terminées par semaine, semaines du lundi dans le fuseau de l'appareil (ADR 0019).
     * Les callbacks sont appelés sur le main thread.
     */
    fun observeCompletionStats(onStats: (CompletionStats) -> Unit, onError: (AppError) -> Unit): Subscription {
        val job = get<ObserveCompletionStatsUseCase>()(NoParams)
            .onEach { result ->
                when (result) {
                    is AppResult.Success -> onStats(result.data)
                    is AppResult.Failure -> onError(result.error)
                }
            }
            .launchIn(scope)
        return Subscription(job)
    }

    /**
     * Calendrier des tâches par échéance pour le mois [request] (ADR 0022), recalculé à minuit. Un autre mois demande
     * un nouvel abonnement, l'ancien étant annulé. Les callbacks sont appelés sur le main thread.
     */
    fun observeTodoCalendar(
        request: CalendarMonthRequest,
        onMonth: (CalendarMonth) -> Unit,
        onError: (AppError) -> Unit,
    ): Subscription {
        val job = get<ObserveTodoCalendarUseCase>()(request)
            .onEach { result ->
                when (result) {
                    is AppResult.Success -> onMonth(result.data)
                    is AppResult.Failure -> onError(result.error)
                }
            }
            .launchIn(scope)
        return Subscription(job)
    }

    /** Pièces jointes de la tâche [todoId] (ADR 0014). Les callbacks sont appelés sur le main thread. */
    fun observeTodoAttachments(
        todoId: String,
        onAttachments: (List<TodoAttachment>) -> Unit,
        onError: (AppError) -> Unit,
    ): Subscription {
        val job = get<ObserveTodoAttachmentsUseCase>()(todoId)
            .onEach { result ->
                when (result) {
                    is AppResult.Success -> onAttachments(result.data)
                    is AppResult.Failure -> onError(result.error)
                }
            }
            .launchIn(scope)
        return Subscription(job)
    }

    /**
     * Contenu du widget Tâches de l'écran d'accueil (ADR 0011), à recopier dans le fichier partagé avec
     * l'extension WidgetKit. En cas d'erreur, n'y écrire aucune tâche. Les callbacks sont appelés sur le main thread.
     */
    fun observeTodoWidget(onState: (TodoWidgetState) -> Unit, onError: (AppError) -> Unit): Subscription {
        val job = get<ObserveTodoWidgetUseCase>()(NoParams)
            .onEach { result ->
                when (result) {
                    is AppResult.Success -> onState(result.data)
                    is AppResult.Failure -> onError(result.error)
                }
            }
            .launchIn(scope)
        return Subscription(job)
    }

    /**
     * Synchronisation automatique des tâches (mode hors ligne, ADR 0012). À appeler une seule fois au lancement
     * de l'app, et à garder pendant toute sa vie.
     */
    fun startTodoSync(): Subscription = Subscription(get<StartTodoSyncUseCase>()(NoParams).launchIn(scope))

    /** Bandeau de l'écran Tâches : hors ligne, modifications en attente, échec. Les callbacks sont appelés sur le main thread. */
    fun observeTodoSyncStatus(onStatus: (TodoSyncStatus) -> Unit, onError: (AppError) -> Unit): Subscription {
        val job = get<ObserveTodoSyncStatusUseCase>()(NoParams)
            .onEach { result ->
                when (result) {
                    is AppResult.Success -> onStatus(result.data)
                    is AppResult.Failure -> onError(result.error)
                }
            }
            .launchIn(scope)
        return Subscription(job)
    }

    /** `true` tant que l'appareil a accès à internet (bandeau « Hors ligne »). Les callbacks sont appelés sur le main thread. */
    fun observeConnectivity(onOnline: (Boolean) -> Unit, onError: (AppError) -> Unit): Subscription {
        val job = get<ObserveConnectivityUseCase>()(NoParams)
            .onEach { result ->
                when (result) {
                    is AppResult.Success -> onOnline(result.data)
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

    /**
     * Garde les rappels par lieu alignés sur les tâches, le réglage Notifications et les permissions (ADR 0023).
     * À appeler une seule fois au lancement de l'app, et à garder pendant toute sa vie.
     */
    fun startTodoLocationReminders(): Subscription =
        Subscription(get<SyncTodoLocationRemindersUseCase>()(NoParams).launchIn(scope))

    /** `true` si les rappels par lieu peuvent partir (position exacte autorisée). Les callbacks sont appelés sur le main thread. */
    fun observeLocationRemindersAvailable(onAvailable: (Boolean) -> Unit, onError: (AppError) -> Unit): Subscription {
        val job = get<ObserveLocationRemindersAvailableUseCase>()(NoParams)
            .onEach { result ->
                when (result) {
                    is AppResult.Success -> onAvailable(result.data)
                    is AppResult.Failure -> onError(result.error)
                }
            }
            .launchIn(scope)
        return Subscription(job)
    }

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

    /** Thème et accent choisis dans Réglages (ADR 0015). Les callbacks sont appelés sur le main thread. */
    fun observeAppearance(onAppearance: (Appearance) -> Unit, onError: (AppError) -> Unit): Subscription {
        val job = get<ObserveAppearanceUseCase>()(NoParams)
            .onEach { result ->
                when (result) {
                    is AppResult.Success -> onAppearance(result.data)
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

    /**
     * Catalogue en cache filtré et trié selon [filter] (recherche sur les pages déjà chargées). Un nouveau filtre
     * demande un nouvel abonnement, l'ancien étant annulé. Les callbacks sont appelés sur le main thread.
     */
    fun observeFilteredCatalog(
        filter: CatalogFilter,
        onCatalog: (FilteredCatalog) -> Unit,
        onError: (AppError) -> Unit,
    ): Subscription {
        val job = get<ObserveFilteredCatalogUseCase>()(filter)
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

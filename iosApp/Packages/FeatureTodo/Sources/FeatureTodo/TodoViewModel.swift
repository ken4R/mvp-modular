import Foundation
import Shared

/// État filtré ; la valeur brute est le nom Kotlin de l'entrée de `TodoStatusFilter`, que l'export Swift renomme.
enum TodoStatusOption: String, CaseIterable {
    case all = "All"
    case pending = "Pending"
    case done = "Done"
}

/// Bandeau au-dessus de la liste (mode hors ligne, ADR 0012). `canRetry` : bouton « Réessayer ».
struct SyncBanner: Equatable {
    let message: String
    let canRetry: Bool
}

@MainActor
final class TodoViewModel: ObservableObject {
    /// Tâches qui correspondent à la recherche et aux filtres.
    @Published private(set) var todos: [Todo] = []
    /// Toutes les tâches, sans filtre.
    @Published private(set) var allTodos: [Todo] = []
    /// Recherche dans le titre et la note ; appliquée après une courte pause de saisie.
    @Published var query = "" { didSet { if query != oldValue { observeFiltered(debounced: true) } } }
    @Published var status: TodoStatusOption = .all { didSet { if status != oldValue { observeFiltered() } } }
    @Published var withReminder = false { didSet { if withReminder != oldValue { observeFiltered() } } }
    @Published var withAttachments = false { didSet { if withAttachments != oldValue { observeFiltered() } } }
    @Published var input: String = ""
    @Published private(set) var error: String?
    @Published private(set) var remindersEnabled = true
    /// Tâche ouverte par un lien : la liste défile jusqu'à elle et la met en évidence quelques secondes.
    @Published private(set) var focusedTodoId: String?
    /// Hors ligne, ou synchronisation en échec ; `nil` : rien à signaler.
    @Published private(set) var syncBanner: SyncBanner?

    private var subscription: Subscription?
    private var debounceTask: Task<Void, Never>?
    /// Entre `start()` et `stop()` ; `false` aussi pendant `clearFilter()`, qui ne réobserve qu'une fois.
    private var isObserving = false
    private var remindersSubscription: Subscription?
    private var syncSubscription: Subscription?
    private var focusTask: Task<Void, Never>?

    /// Des rappels à venir sont posés mais ne partiront pas : la vue invite à activer les notifications.
    var showRemindersDisabledHint: Bool {
        let now = Date()
        return !remindersEnabled && allTodos.contains { todo in
            !todo.isDone && (todo.reminderDate.map { $0 > now } ?? false)
        }
    }

    func start() {
        guard subscription == nil else { return }
        isObserving = true
        subscribe()
        remindersSubscription = UseCases.shared.observeRemindersEnabled(
            onEnabled: { [weak self] enabled in self?.remindersEnabled = enabled.boolValue },
            onError: { _ in }
        )
        syncSubscription = UseCases.shared.observeTodoSyncStatus(
            onStatus: { [weak self] status in self?.syncBanner = Self.banner(for: status) },
            onError: { _ in }
        )
    }

    func stop() {
        isObserving = false
        debounceTask?.cancel()
        debounceTask = nil
        subscription?.cancel()
        subscription = nil
        remindersSubscription?.cancel()
        remindersSubscription = nil
        syncSubscription?.cancel()
        syncSubscription = nil
    }

    /// Filtre courant ; `isActive` (logique KMP) dit s'il retire des tâches.
    private var filter: TodoFilter {
        KoinIosKt.todoFilter(query: query, status: status.rawValue, withReminder: withReminder, withAttachments: withAttachments)
    }

    /// Retire la recherche et les filtres : toute la liste est affichée.
    func clearFilter() {
        let wasObserving = isObserving
        isObserving = false
        query = ""
        status = .all
        withReminder = false
        withAttachments = false
        isObserving = wasObserving
        debounceTask?.cancel()
        if wasObserving { subscribe() }
    }

    /// Remplace l'observation par celle du filtre courant (filtrage fait par la logique KMP).
    private func observeFiltered(debounced: Bool = false) {
        guard isObserving else { return }
        debounceTask?.cancel()
        if debounced {
            debounceTask = Task { [weak self] in
                try? await Task.sleep(nanoseconds: Self.searchDebounce)
                if !Task.isCancelled { self?.subscribe() }
            }
        } else {
            subscribe()
        }
    }

    private func subscribe() {
        subscription?.cancel()
        subscription = UseCases.shared.observeFilteredTodos(
            filter: filter,
            onTodos: { [weak self] result in
                self?.todos = result.todos
                self?.allTodos = result.all
            },
            onError: { [weak self] error in self?.error = error.userMessage }
        )
    }

    /// Pause de saisie avant d'appliquer la recherche, en nanosecondes (0,25 s).
    private static let searchDebounce: UInt64 = 250_000_000

    /// Bouton « Réessayer » du bandeau ; sinon la synchronisation réessaie seule, de plus en plus espacée.
    func retrySync() async {
        _ = try? await UseCases.shared.syncTodos().invoke(params: NoParams.shared)
    }

    nonisolated private static func banner(for status: TodoSyncStatus) -> SyncBanner? {
        let count = Int(status.pendingChanges)
        let pending = count == 1 ? "1 modification en attente" : "\(count) modifications en attente"
        if !status.isOnline {
            return SyncBanner(message: count > 0 ? "Hors ligne · \(pending)" : "Hors ligne · vos tâches restent modifiables", canRetry: false)
        }
        if status.lastSyncFailed && count > 0 {
            return SyncBanner(message: "Synchronisation impossible · \(pending)", canRetry: !status.isSyncing)
        }
        return nil
    }

    /// Un lien demande la tâche `id` : mise en évidence si elle existe ; sinon l'écran reste sur la liste et le dit.
    func focus(_ id: String) async {
        let result = try? await UseCases.shared.findTodo().invoke(params: id as NSString)
        if let failure = result as? AppResultFailure {
            error = failure.error.userMessage
            return
        }
        guard result != nil else { return }
        // Les filtres sont retirés pour que la tâche soit dans la liste.
        if filter.isActive { clearFilter() }
        focusedTodoId = id
        focusTask?.cancel()
        focusTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: Self.focusDuration)
            if !Task.isCancelled { self?.focusedTodoId = nil }
        }
    }

    /// Durée de la mise en évidence, en nanosecondes (3 s).
    private static let focusDuration: UInt64 = 3_000_000_000

    func add() async {
        let result = try? await UseCases.shared.addTodo().invoke(params: input as NSString)
        if let failure = result as? AppResultFailure {
            error = failure.error.userMessage
        } else {
            input = ""
            error = nil
        }
    }

    func toggle(_ todo: Todo) async {
        let result = try? await UseCases.shared.toggleTodo().invoke(params: todo.id as NSString)
        if let failure = result as? AppResultFailure { error = failure.error.userMessage }
    }

    func delete(_ todo: Todo) async {
        let result = try? await UseCases.shared.deleteTodo().invoke(params: todo.id as NSString)
        if let failure = result as? AppResultFailure { error = failure.error.userMessage }
    }

    func clearError() {
        error = nil
    }

    /// `date == nil` retire le rappel. Renvoie `true` si le rappel est enregistré.
    @discardableResult
    func setReminder(_ todo: Todo, at date: Date?) async -> Bool {
        let at = date.map { KotlinLong(value: Int64(($0.timeIntervalSince1970 * 1000).rounded())) }
        let result = try? await UseCases.shared.setTodoReminder().invoke(params: TodoReminder(todoId: todo.id, at: at))
        if let failure = result as? AppResultFailure {
            error = failure.error.userMessage
            return false
        }
        error = nil
        return result != nil
    }
}

extension Todo {
    /// Heure du rappel, `nil` sans rappel.
    var reminderDate: Date? {
        reminderAt.map { Date(timeIntervalSince1970: Double($0.int64Value) / 1000) }
    }
}

private extension AppError {
    var userMessage: String {
        switch self {
        case let validation as AppErrorValidation: return validation.message
        case is AppErrorNotFound: return "Tâche introuvable"
        case is AppErrorNetwork: return "Problème de connexion"
        case is AppErrorUnauthorized: return "Accès refusé"
        case let unknown as AppErrorUnknown: return unknown.message ?? "Erreur inattendue"
        default: return "Erreur inattendue"
        }
    }
}

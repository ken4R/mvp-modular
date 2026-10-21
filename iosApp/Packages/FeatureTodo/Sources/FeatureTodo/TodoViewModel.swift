import Foundation
import Shared

@MainActor
final class TodoViewModel: ObservableObject {
    @Published private(set) var todos: [Todo] = []
    @Published var input: String = ""
    @Published private(set) var error: String?
    @Published private(set) var remindersEnabled = true

    private var subscription: Subscription?
    private var remindersSubscription: Subscription?

    /// Des rappels à venir sont posés mais ne partiront pas : la vue invite à activer les notifications.
    var showRemindersDisabledHint: Bool {
        let now = Date()
        return !remindersEnabled && todos.contains { todo in
            !todo.isDone && (todo.reminderDate.map { $0 > now } ?? false)
        }
    }

    func start() {
        guard subscription == nil else { return }
        subscription = UseCases.shared.observeTodos(
            onTodos: { [weak self] todos in self?.todos = todos },
            onError: { [weak self] error in self?.error = error.userMessage }
        )
        remindersSubscription = UseCases.shared.observeRemindersEnabled(
            onEnabled: { [weak self] enabled in self?.remindersEnabled = enabled.boolValue },
            onError: { _ in }
        )
    }

    func stop() {
        subscription?.cancel()
        subscription = nil
        remindersSubscription?.cancel()
        remindersSubscription = nil
    }

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

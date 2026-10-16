import Foundation
import Shared

@MainActor
final class TodoViewModel: ObservableObject {
    @Published private(set) var todos: [Todo] = []
    @Published var input: String = ""
    @Published private(set) var error: String?

    private var subscription: Subscription?

    func start() {
        guard subscription == nil else { return }
        subscription = UseCases.shared.observeTodos(
            onTodos: { [weak self] todos in self?.todos = todos },
            onError: { [weak self] error in self?.error = error.userMessage }
        )
    }

    func stop() {
        subscription?.cancel()
        subscription = nil
    }

    func add() async {
        let result = try? await UseCases.shared.addTodo().invoke(params: input)
        if let failure = result as? AppResultFailure {
            error = failure.error.userMessage
        } else {
            input = ""
            error = nil
        }
    }

    func toggle(_ todo: Todo) async {
        let result = try? await UseCases.shared.toggleTodo().invoke(params: todo.id)
        if let failure = result as? AppResultFailure { error = failure.error.userMessage }
    }

    func delete(_ todo: Todo) async {
        let result = try? await UseCases.shared.deleteTodo().invoke(params: todo.id)
        if let failure = result as? AppResultFailure { error = failure.error.userMessage }
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

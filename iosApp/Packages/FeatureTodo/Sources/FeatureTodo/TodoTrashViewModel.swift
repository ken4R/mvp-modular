import Foundation
import Shared

/// Une ligne de la corbeille. `daysLeft` : jours avant la suppression définitive (calcul KMP).
struct TrashRow: Identifiable, Equatable {
    let id: String
    let title: String
    let isDone: Bool
    let daysLeft: Int
}

/// Corbeille (ADR 0025) : les règles (30 jours, restauration, suppression définitive) sont dans KMP, ici seulement
/// l'état de l'écran.
@MainActor
final class TodoTrashViewModel: ObservableObject {
    /// `nil` tant que la première lecture n'est pas arrivée.
    @Published private(set) var rows: [TrashRow]?
    @Published private(set) var failed = false
    /// Résultat de la dernière action, affiché sous le titre.
    @Published private(set) var message: String?

    private var subscription: Subscription?

    func start() {
        guard subscription == nil else { return }
        // Une app restée ouverte longtemps supprime aussi ce qui a fait son temps.
        Task { _ = try? await TodoUseCases.shared.purgeExpiredTrash().invoke(params: NoParams.shared) }
        subscription = TodoUseCases.shared.observeTrash(
            onItems: { [weak self] items in
                self?.rows = items.map { item in
                    TrashRow(id: item.todo.id, title: item.todo.title, isDone: item.todo.isDone, daysLeft: Int(item.daysLeft))
                }
                self?.failed = false
            },
            onError: { [weak self] _ in self?.failed = true }
        )
    }

    func stop() {
        subscription?.cancel()
        subscription = nil
    }

    func restore(_ row: TrashRow) async {
        let result = try? await TodoUseCases.shared.restoreTodo().invoke(params: row.id as NSString)
        message = Self.succeeded(result) ? "« \(row.title) » est revenue dans la liste" : "Tâche introuvable"
    }

    func deletePermanently(_ row: TrashRow) async {
        let result = try? await TodoUseCases.shared.deleteTodoPermanently().invoke(params: row.id as NSString)
        message = Self.succeeded(result) ? "« \(row.title) » est supprimée définitivement" : "Suppression impossible"
    }

    func emptyTrash() async {
        let result = try? await TodoUseCases.shared.emptyTrash().invoke(params: NoParams.shared)
        message = Self.succeeded(result) ? "Corbeille vidée" : "Suppression impossible"
    }

    /// `nil` (appel interrompu) ou `AppResultFailure` : échec.
    private static func succeeded<T>(_ result: T?) -> Bool {
        guard let result else { return false }
        return !(result is AppResultFailure)
    }
}

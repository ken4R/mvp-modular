import Foundation
import Shared

/// Une semaine du graphique. `start` : lundi 00:00 dans le fuseau de l'appareil (celui du calcul KMP).
struct StatsWeek: Identifiable, Equatable {
    let start: Date
    let count: Int
    let isCurrent: Bool
    var id: Date { start }
}

/// Statistiques des tâches terminées (ADR 0019) : le calcul est dans KMP, ici seulement l'état de l'écran.
@MainActor
final class TodoStatsViewModel: ObservableObject {
    /// `nil` tant que le premier calcul n'est pas arrivé.
    @Published private(set) var weeks: [StatsWeek]?
    @Published private(set) var total = 0
    @Published private(set) var best = 0
    @Published private(set) var failed = false

    private var subscription: Subscription?

    func start() {
        guard subscription == nil else { return }
        subscription = UseCases.shared.observeCompletionStats(
            onStats: { [weak self] stats in self?.apply(stats) },
            onError: { [weak self] _ in self?.failed = true }
        )
    }

    func stop() {
        subscription?.cancel()
        subscription = nil
    }

    private func apply(_ stats: CompletionStats) {
        let last = stats.weeks.count - 1
        weeks = stats.weeks.enumerated().map { index, week in
            StatsWeek(
                start: Date(timeIntervalSince1970: TimeInterval(week.weekStart) / 1000),
                count: Int(week.count),
                isCurrent: index == last
            )
        }
        total = Int(stats.total)
        best = Int(stats.best)
        failed = false
    }
}

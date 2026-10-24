import Charts
import DesignSystemUI
import Shared
import SwiftUI

/// Statistiques (ADR 0019) : tâches terminées par semaine sur les dernières semaines, en barres (Swift Charts).
/// Affiché à la place de la liste, dans l'onglet Tâches : il reste sous le verrou d'AppLockGate. VoiceOver lit
/// chaque barre (« Semaine du 20 octobre, 3 tâches terminées ») et propose la vue d'ensemble audio du graphique.
struct TodoStatsView: View {
    @StateObject private var viewModel = TodoStatsViewModel()
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.dsAccent) private var accent
    let onClose: () -> Void

    var body: some View {
        let colors = DS.colors(colorScheme, accent)
        VStack(alignment: .leading, spacing: DS.Spacing.md) {
            HStack(spacing: DS.Spacing.sm) {
                Button("Retour", action: onClose)
                    .font(DS.Typography.label)
                    .tint(Color(argb: colors.primary))
                Text("Statistiques")
                    .font(DS.Typography.titleLarge)
                    .foregroundColor(Color(argb: colors.onBackground))
                    .accessibilityAddTraits(.isHeader)
                Spacer(minLength: 0)
            }
            if let weeks = viewModel.weeks {
                content(weeks, colors: colors)
            } else if viewModel.failed {
                Text("Statistiques indisponibles")
                    .font(DS.Typography.body)
                    .foregroundColor(Color(argb: colors.error))
            }
            Spacer(minLength: 0)
        }
        .padding(DS.Spacing.md)
        .background(Color(argb: colors.background))
        .onAppear { viewModel.start() }
        .onDisappear { viewModel.stop() }
    }

    @ViewBuilder
    private func content(_ weeks: [StatsWeek], colors: ColorTokens) -> some View {
        let shortDate = Self.formatter("d/MM")
        let longDate = Self.formatter("d MMMM")
        Text("Tâches terminées par semaine")
            .font(DS.Typography.titleMedium)
            .foregroundColor(Color(argb: colors.onBackground))
        Text(summary(weeks: weeks.count))
            .font(DS.Typography.body)
            .foregroundColor(Color(argb: colors.onBackground))
        Chart(weeks) { week in
            BarMark(
                x: .value("Semaine", shortDate.string(from: week.start)),
                y: .value("Tâches terminées", week.count)
            )
            .foregroundStyle(Color(argb: week.isCurrent ? colors.primary : colors.secondary))
            .annotation(position: .top) {
                Text("\(week.count)")
                    .font(DS.Typography.caption)
                    .foregroundColor(Color(argb: colors.onBackground))
            }
            .accessibilityLabel(week.isCurrent ? "Cette semaine" : "Semaine du \(longDate.string(from: week.start))")
            .accessibilityValue(Self.tasks(week.count))
        }
        // Graduations entières seulement (au plus 5) : pas de « 0,5 tâche ».
        .chartYScale(domain: 0...max(viewModel.best, 1))
        .chartYAxis {
            AxisMarks(values: Self.ticks(best: viewModel.best)) { value in
                AxisGridLine()
                if let count = value.as(Int.self) { AxisValueLabel("\(count)") }
            }
        }
        .frame(height: DS.Size.chart)
        if viewModel.total == 0 {
            Text("Cochez une tâche pour la voir apparaître ici")
                .font(DS.Typography.caption)
                .foregroundColor(Color(argb: colors.primary))
        }
    }

    private func summary(weeks: Int) -> String {
        switch viewModel.total {
        case 0: return "Aucune tâche terminée sur les \(weeks) dernières semaines"
        case 1: return "1 tâche terminée sur les \(weeks) dernières semaines"
        default:
            return "\(viewModel.total) tâches terminées sur les \(weeks) dernières semaines · meilleure semaine : \(viewModel.best)"
        }
    }

    private static func tasks(_ count: Int) -> String {
        switch count {
        case 0: return "aucune tâche terminée"
        case 1: return "1 tâche terminée"
        default: return "\(count) tâches terminées"
        }
    }

    private static func ticks(best: Int) -> [Int] {
        let top = max(best, 1)
        return Array(stride(from: 0, through: top, by: max(1, (top + 3) / 4)))
    }

    /// Recréé à chaque affichage : le fuseau courant de l'appareil, comme le calcul des semaines (KMP).
    private static func formatter(_ format: String) -> DateFormatter {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "fr_FR")
        formatter.timeZone = .current
        formatter.dateFormat = format
        return formatter
    }
}

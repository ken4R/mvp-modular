import SwiftUI
import Shared
import DesignSystemUI

/// Mode focus (ADR 0028) : compte à rebours de la phase sur une tâche. Affiché par l'app à la place de la liste dans
/// l'onglet Tâches (derrière le verrou de l'app, pas en feuille) ; « Réduire » y revient sans arrêter la session,
/// qui continue dans la Live Activity.
public struct FocusView: View {
    @ObservedObject private var viewModel: FocusViewModel
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.dsAccent) private var accent
    private let onClose: () -> Void

    public init(viewModel: FocusViewModel, onClose: @escaping () -> Void) {
        self.viewModel = viewModel
        self.onClose = onClose
    }

    public var body: some View {
        let colors = DS.colors(colorScheme, accent)
        VStack(alignment: .leading, spacing: DS.Spacing.md) {
            HStack(spacing: DS.Spacing.sm) {
                Button("Réduire", action: onClose)
                    .font(DS.Typography.label)
                    .tint(Color(argb: colors.primary))
                Text("Mode focus")
                    .font(DS.Typography.titleLarge)
                    .foregroundColor(Color(argb: colors.onBackground))
                    .accessibilityAddTraits(.isHeader)
            }
            if let timer = viewModel.timer {
                content(timer, colors: colors)
            }
            if let error = viewModel.error {
                StatusBanner(error)
            }
            Spacer()
        }
        .padding(DS.Spacing.md)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(argb: colors.background))
    }

    @ViewBuilder
    private func content(_ timer: FocusTimer, colors: ColorTokens) -> some View {
        let focus = timer.state
        Text(focus.title)
            .font(DS.Typography.titleMedium)
            .foregroundColor(Color(argb: colors.onBackground))
        Text(Self.phaseLine(focus))
            .font(DS.Typography.body)
            .foregroundColor(Color(argb: colors.onSurface))
        Text(timer.remainingLabel)
            .font(DS.Typography.display.monospacedDigit())
            .foregroundColor(Color(argb: colors.onBackground))
            .frame(maxWidth: .infinity)
            // VoiceOver : la durée en minutes, pas une annonce à chaque seconde.
            .accessibilityLabel(Self.spokenRemaining(timer))
        ProgressView(value: Double(timer.progress))
            .tint(Color(argb: colors.primary))
        if focus.isFinished {
            Text(focus.isBreak ? "Pause terminée" : "Concentration terminée, bravo !")
                .font(DS.Typography.titleMedium)
                .foregroundColor(Color(argb: colors.onBackground))
        }
        HStack(spacing: DS.Spacing.sm) {
            if focus.isFinished {
                Button(Self.nextLabel(focus)) { viewModel.next() }
                    .buttonStyle(.borderedProminent)
            } else if focus.isPaused {
                Button("Reprendre") { viewModel.resume() }
                    .buttonStyle(.borderedProminent)
            } else {
                Button("Pause") { viewModel.pause() }
                    .buttonStyle(.borderedProminent)
            }
            if !focus.isFinished {
                Button("Passer") { viewModel.next() }
                    .buttonStyle(.bordered)
            }
            Button("Arrêter") { viewModel.stopFocus() }
                .buttonStyle(.borderless)
        }
        .font(DS.Typography.label)
        .tint(Color(argb: colors.primary))
    }

    private static func phaseLine(_ focus: FocusSnapshot) -> String {
        let done = Int(focus.completedWorkPhases)
        let count = done == 0 ? "" : " · \(done) concentration\(done > 1 ? "s" : "") terminée\(done > 1 ? "s" : "")"
        return focus.phaseLabel + (focus.isPaused ? " (en pause)" : "") + count
    }

    private static func nextLabel(_ focus: FocusSnapshot) -> String {
        focus.isBreak ? "Reprendre la concentration" : "Commencer la \(focus.nextPhaseLabel.lowercased())"
    }

    private static func spokenRemaining(_ timer: FocusTimer) -> String {
        let minutes = Int((timer.remainingMillis + 59_999) / 60_000)
        let unit = "minute\(minutes > 1 ? "s" : "")"
        if timer.state.isFinished { return "Phase terminée" }
        if timer.state.isPaused { return "En pause, \(minutes) \(unit) restantes" }
        return "Environ \(minutes) \(unit) restantes"
    }
}

/// Bandeau de la liste des tâches pendant une session, pour revenir à l'écran Focus.
public struct FocusBanner: View {
    private let timer: FocusTimer
    private let onOpen: () -> Void

    public init(timer: FocusTimer, onOpen: @escaping () -> Void) {
        self.timer = timer
        self.onOpen = onOpen
    }

    public var body: some View {
        let focus = timer.state
        let status = focus.isFinished ? "terminée" : (focus.isPaused ? "en pause · \(timer.remainingLabel)" : timer.remainingLabel)
        StatusBanner("\(focus.phaseLabel) \(status) · \(focus.title)", actionTitle: "Ouvrir", action: onOpen)
            .padding(.horizontal, DS.Spacing.md)
    }
}

import ActivityKit
import SwiftUI
import WidgetKit
import FocusActivityData

/// Live Activity du mode focus (ADR 0028), compilée dans l'extension TodoWidgetExtension : écran verrouillé et
/// Dynamic Island. Le compte à rebours et la barre de progression sont ceux du système, jusqu'à `phaseEnd` ; une
/// phase finie pendant que l'app était suspendue est affichée « terminée » grâce à la date de péremption.
@available(iOS 16.2, *)
struct FocusLiveActivityWidget: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: FocusActivityAttributes.self) { context in
            FocusLockScreenView(attributes: context.attributes, state: context.state, isStale: context.isStale)
                .activityBackgroundTint(nil)
        } dynamicIsland: { context in
            let state = context.state
            let finished = context.isStale || state.status == .finished
            return DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    Label(state.phaseLabel, systemImage: state.isBreak ? "cup.and.saucer" : "timer")
                        .font(.caption)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    FocusRemainingText(state: state, finished: finished)
                        .font(.title3.monospacedDigit())
                }
                DynamicIslandExpandedRegion(.bottom) {
                    Text(displayedTitle(state))
                        .font(.caption)
                        .lineLimit(1)
                        .privacySensitive()
                }
            } compactLeading: {
                Image(systemName: state.isBreak ? "cup.and.saucer" : "timer")
            } compactTrailing: {
                FocusRemainingText(state: state, finished: finished)
                    .monospacedDigit()
                    .frame(maxWidth: 48)
            } minimal: {
                Image(systemName: "timer")
            }
        }
    }
}

@available(iOS 16.2, *)
private struct FocusLockScreenView: View {
    @Environment(\.colorScheme) private var colorScheme
    let attributes: FocusActivityAttributes
    let state: FocusActivityAttributes.ContentState
    let isStale: Bool

    var body: some View {
        let finished = isStale || state.status == .finished
        let primary = attributes.primary(colorScheme)
        VStack(alignment: .leading) {
            HStack {
                Label(state.phaseLabel + (state.status == .paused ? " · en pause" : ""), systemImage: state.isBreak ? "cup.and.saucer" : "timer")
                    .font(.subheadline)
                    .foregroundStyle(primary)
                Spacer()
                FocusRemainingText(state: state, finished: finished)
                    .font(.title2.monospacedDigit())
            }
            Text(displayedTitle(state))
                .font(.headline)
                .lineLimit(1)
                // Masqué sur l'écran verrouillé si l'utilisateur l'a demandé pour les données sensibles.
                .privacySensitive()
            if finished {
                Text("Terminée · ensuite : \(state.nextPhaseLabel.lowercased())")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            } else if state.status == .running, let start = state.phaseStart, let end = state.phaseEnd, start < end {
                ProgressView(timerInterval: start...end, countsDown: false) { EmptyView() } currentValueLabel: { EmptyView() }
                    .tint(primary)
            }
        }
        .padding()
    }
}

/// Titre de la tâche, remplacé après la fin de la session de connexion (l'app ne tourne peut-être plus pour terminer
/// la Live Activity).
@available(iOS 16.2, *)
private func displayedTitle(_ state: FocusActivityAttributes.ContentState) -> String {
    Date() < state.visibleUntil ? state.title : "Session de concentration"
}

/// Temps restant : compte à rebours du système en cours, valeur figée en pause.
@available(iOS 16.2, *)
private struct FocusRemainingText: View {
    let state: FocusActivityAttributes.ContentState
    let finished: Bool

    var body: some View {
        if finished {
            Text("00:00")
        } else if state.status == .running, let end = state.phaseEnd, end > Date() {
            Text(timerInterval: Date()...end, countsDown: true)
                .multilineTextAlignment(.trailing)
        } else {
            Text(state.remainingLabel)
        }
    }
}

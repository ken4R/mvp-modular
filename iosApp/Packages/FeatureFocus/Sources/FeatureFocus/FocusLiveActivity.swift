import ActivityKit
import Foundation
import Shared
import DesignSystemUI
import FocusActivityData

/// Live Activity du mode focus (ADR 0028) : à démarrer une fois au lancement de l'app. KMP décide de ce qui est
/// montré (`FocusUseCases.observeFocus` : session, titre masqué avec le verrou, rien sans connexion) ; ici,
/// seulement la recopie dans ActivityKit. iOS 16.2 et plus ; ailleurs, l'écran Focus et l'alerte de fin suffisent.
public enum FocusLiveActivity {
    private static var subscription: Subscription?

    public static func start() {
        guard subscription == nil else { return }
        subscription = FocusUseCases.shared.observeFocus(
            onState: { state in submit(state) },
            // Session illisible : la Live Activity ne doit pas rester sur un état périmé.
            onError: { _ in submit(nil) }
        )
    }

    /// Appelé sur le main thread (callbacks de SwiftUseCases).
    private static func submit(_ state: FocusState?) {
        guard #available(iOS 16.2, *) else { return }
        FocusActivityUpdater.shared.submit(state.map(content(of:)))
    }

    @available(iOS 16.2, *)
    private static func content(of state: FocusState) -> FocusActivityAttributes.ContentState {
        let status: FocusActivityAttributes.Status = state.isRunning ? .running : (state.isPaused ? .paused : .finished)
        return FocusActivityAttributes.ContentState(
            title: state.lockScreenTitle ?? "Session de concentration",
            phaseLabel: state.phaseLabel,
            status: status,
            phaseStart: state.phaseStartedAt.map { Date(timeIntervalSince1970: Double($0.int64Value) / 1000) },
            phaseEnd: state.endsAt.map { Date(timeIntervalSince1970: Double($0.int64Value) / 1000) },
            remainingLabel: FocusFormat.shared.remaining(millis: state.remainingMillis),
            nextPhaseLabel: state.nextPhaseLabel,
            isBreak: state.isBreak
        )
    }
}

/// Applique les états un par un, le dernier reçu gagnant : deux demandes simultanées créeraient deux Live Activities.
/// Tout se passe sur le main thread (callbacks KMP et tâche @MainActor).
@available(iOS 16.2, *)
private final class FocusActivityUpdater {
    static let shared = FocusActivityUpdater()

    /// `.some(nil)` : terminer la Live Activity.
    private var pending: FocusActivityAttributes.ContentState??
    private var running = false

    func submit(_ content: FocusActivityAttributes.ContentState?) {
        pending = .some(content)
        guard !running else { return }
        running = true
        Task { @MainActor [self] in
            while let next = pending {
                pending = nil
                await apply(next)
            }
            running = false
        }
    }

    @MainActor
    private func apply(_ content: FocusActivityAttributes.ContentState?) async {
        let activities = Activity<FocusActivityAttributes>.activities
        guard let content else {
            for activity in activities { await activity.end(nil, dismissalPolicy: .immediate) }
            return
        }
        // Après la fin de la phase, le système affiche l'état comme périmé (l'app peut être suspendue à ce moment-là).
        let update = ActivityContent(state: content, staleDate: content.phaseEnd)
        if let current = activities.first {
            await current.update(update)
            for extra in activities.dropFirst() { await extra.end(nil, dismissalPolicy: .immediate) }
            return
        }
        // Désactivées par l'utilisateur (Réglages > MVP Modular) : rien à faire, l'alerte de fin reste.
        guard ActivityAuthorizationInfo().areActivitiesEnabled, content.status != .finished else { return }
        let attributes = FocusActivityAttributes(primaryLight: DS.colors(.light).primary, primaryDark: DS.colors(.dark).primary)
        // Échoue si l'app n'est pas au premier plan : la prochaine mise à jour (au retour dans l'app) réessaie.
        _ = try? Activity.request(attributes: attributes, content: update, pushType: nil)
    }
}

import Foundation
import Shared

/// Mode focus (ADR 0028) : compte à rebours et règles dans KMP (`FocusUseCases`), ici seulement l'état de l'écran.
@MainActor
public final class FocusViewModel: ObservableObject {
    /// `nil` sans session de concentration.
    @Published public private(set) var timer: FocusTimer?
    /// Première valeur reçue.
    @Published public private(set) var loaded = false
    @Published public private(set) var error: String?
    private var subscription: Subscription?

    public init() {}

    public func start() {
        guard subscription == nil else { return }
        subscription = FocusUseCases.shared.observeFocusTimer(
            onTimer: { [weak self] timer in
                self?.timer = timer
                self?.loaded = true
            },
            onError: { [weak self] _ in
                self?.loaded = true
                self?.error = "Mode focus indisponible"
            }
        )
    }

    public func stop() {
        subscription?.cancel()
        subscription = nil
    }

    public func startFocus(todoId: String, title: String) {
        run { try await FocusUseCases.shared.startFocus().invoke(params: StartFocus(todoId: todoId, title: title)) }
    }

    func pause() { run { try await FocusUseCases.shared.pauseFocus().invoke(params: NoParams.shared) } }
    func resume() { run { try await FocusUseCases.shared.resumeFocus().invoke(params: NoParams.shared) } }
    func next() { run { try await FocusUseCases.shared.startNextFocusPhase().invoke(params: NoParams.shared) } }
    func stopFocus() { run { try await FocusUseCases.shared.stopFocus().invoke(params: NoParams.shared) } }

    private func run(_ action: @escaping () async throws -> Any?) {
        Task {
            let result = try? await action()
            if let failure = result as? AppResultFailure {
                error = (failure.error as? AppErrorValidation)?.message ?? "Action impossible"
            } else {
                error = nil
            }
        }
    }
}

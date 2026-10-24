import SwiftUI
import Shared
import DesignSystemUI

/// Présentation du premier lancement (ADR 0016) : affichée à la place de [content] tant qu'elle n'a été ni
/// terminée ni passée sur cet appareil. À placer après la connexion et le verrou : les choix sont enregistrés
/// pour le compte connecté.
public struct OnboardingGate<Content: View>: View {
    @StateObject private var gate = OnboardingGateModel()
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.dsAccent) private var accent
    private let content: () -> Content

    public init(@ViewBuilder content: @escaping () -> Content) {
        self.content = content
    }

    public var body: some View {
        Group {
            switch gate.required {
            case .none:
                Color(argb: DS.colors(colorScheme, accent).background).ignoresSafeArea()
            case .some(true):
                // Nouvelle vue (et nouveau ViewModel) à chaque affichage : « Revoir » repart de la première étape.
                OnboardingView()
            case .some(false):
                content()
            }
        }
        .onAppear { gate.start() }
        .onDisappear { gate.stop() }
    }
}

@MainActor
private final class OnboardingGateModel: ObservableObject {
    /// `nil` tant que l'état n'est pas lu.
    @Published private(set) var required: Bool?
    private var subscription: Subscription?

    func start() {
        guard subscription == nil else { return }
        subscription = UseCases.shared.observeOnboardingRequired(
            onRequired: { [weak self] required in self?.required = required.boolValue },
            // Illisible : la présentation n'est pas imposée, l'app reste utilisable (elle se revoit depuis Réglages).
            onError: { [weak self] _ in self?.required = false }
        )
    }

    func stop() {
        subscription?.cancel()
        subscription = nil
    }
}

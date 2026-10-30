import SwiftUI
import Shared
import DesignSystemUI

/// Vue racine : l'écran de connexion tant qu'il n'y a pas de session, le contenu de l'app sinon.
/// Le contenu est recréé à chaque connexion, donc aucun écran ne garde l'état de l'utilisateur précédent.
public struct SessionGate<Content: View>: View {
    @StateObject private var viewModel = SessionViewModel()
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.dsAccent) private var accent
    private let content: () -> Content

    public init(@ViewBuilder content: @escaping () -> Content) {
        self.content = content
    }

    public var body: some View {
        Group {
            switch viewModel.state {
            case .loading:
                // Session pas encore lue : écran vide, pour ne pas faire clignoter la connexion.
                Color(argb: DS.colors(colorScheme, accent).background).ignoresSafeArea()
            case .loggedOut:
                LoginView()
            case .loggedIn:
                content()
            }
        }
        .onAppear { viewModel.start() }
    }
}

@MainActor
final class SessionViewModel: ObservableObject {
    enum State {
        case loading
        case loggedOut
        case loggedIn(UserAccount)
    }

    @Published private(set) var state: State = .loading
    private var subscription: Subscription?

    func start() {
        guard subscription == nil else { return }
        subscription = AuthUseCases.shared.observeSession(
            onAccount: { [weak self] account in
                if let account { self?.state = .loggedIn(account) } else { self?.state = .loggedOut }
            },
            // Session illisible : on redemande la connexion plutôt que d'ouvrir l'app.
            onError: { [weak self] _ in self?.state = .loggedOut }
        )
    }
}

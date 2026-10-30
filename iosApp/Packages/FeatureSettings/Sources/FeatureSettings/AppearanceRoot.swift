import SwiftUI
import Shared
import DesignSystemUI

/// Thème et accent enregistrés dans Réglages (KMP), suivis pendant toute la vie de l'app.
@MainActor
final class AppearanceModel: ObservableObject {
    @Published private(set) var themeMode: DSThemeMode = .System
    @Published private(set) var accent: DSAccent = .Blue

    private var subscription: Subscription?

    func start() {
        guard subscription == nil else { return }
        // Une erreur de lecture garde l'apparence précédente (par défaut : thème de l'appareil, accent bleu).
        subscription = SettingsUseCases.shared.observeAppearance(
            onAppearance: { [weak self] appearance in
                self?.themeMode = DSThemeMode(appearance.themeMode)
                self?.accent = DSAccent(appearance.accent)
            },
            onError: { _ in }
        )
    }
}

/// Racine de l'app : applique à tout son contenu (connexion et verrou compris) le thème et l'accent
/// choisis dans Réglages (ADR 0015).
public struct AppearanceRoot<Content: View>: View {
    @StateObject private var model = AppearanceModel()
    private let content: Content

    public init(@ViewBuilder content: () -> Content) {
        self.content = content()
    }

    public var body: some View {
        content
            .dsAppearance(themeMode: model.themeMode, accent: model.accent)
            .onAppear { model.start() }
    }
}

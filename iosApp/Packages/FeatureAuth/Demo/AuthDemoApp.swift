import SwiftUI
import Shared
import DesignSystemUI
import FeatureAuth

/// App de démo : l'écran de connexion seul, puis un écran « connecté » avec la déconnexion,
/// pour tester le parcours sans passer par l'app complète.
@main
struct AuthDemoApp: App {
    init() {
        KoinIosKt.doInitKoin()
    }

    var body: some Scene {
        WindowGroup {
            SessionGate {
                LoggedInDemoView()
            }
        }
    }
}

private struct LoggedInDemoView: View {
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.dsAccent) private var accent

    var body: some View {
        let colors = DS.colors(colorScheme, accent)
        VStack(spacing: DS.Spacing.md) {
            Text("Connecté")
                .font(DS.Typography.titleLarge)
                .foregroundColor(Color(argb: colors.onBackground))
            Button("Se déconnecter") {
                Task { _ = try? await AuthUseCases.shared.logout().invoke(params: NoParams.shared) }
            }
            .tint(Color(argb: colors.primary))
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(argb: colors.background))
    }
}

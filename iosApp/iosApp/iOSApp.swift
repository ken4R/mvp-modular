import SwiftUI
import Shared
import FeatureAuth
import FeatureTodo
import FeatureTodoWidget
import FeatureSettings

/// App complète : initialise la logique KMP et assemble les features, derrière l'écran de connexion.
@main
struct iOSApp: App {
    @Environment(\.scenePhase) private var scenePhase

    init() {
        KoinIosKt.doInitKoin()
        TodoReminders.start()
        // Mode hors ligne : les modifications des tâches partent dès que le réseau revient (ADR 0012).
        TodoSync.start()
        // Widget Tâches de l'écran d'accueil : recopié à chaque changement, vidé à la déconnexion ou avec le verrou.
        TodoWidgetPublisher.start()
    }

    var body: some Scene {
        WindowGroup {
            SessionGate {
                // Face ID / Touch ID, si activé dans Réglages : rien n'est affiché avant.
                AppLockGate {
                    AppTabsView()
                }
            }
            // Liens mvpmodular://… (schéma déclaré dans project.yml) : reçus même déconnecté ou verrouillé,
            // ils attendent dans KMP que les onglets soient affichés.
            .onOpenURL { url in
                Task { @MainActor in
                    _ = try? await UseCases.shared.openDeepLink().invoke(params: url.absoluteString as NSString)
                }
            }
        }
        // Mesure d'audience : ce qui attend part quand l'app passe en arrière-plan (rien sans consentement).
        .onChange(of: scenePhase) { phase in
            guard phase == .background else { return }
            Task { @MainActor in _ = try? await UseCases.shared.flushAnalytics().invoke(params: NoParams.shared) }
        }
    }
}

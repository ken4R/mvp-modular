import SwiftUI
import Shared
import FeatureAuth
import FeatureTodo
import FeatureTodoWidget
import FeatureWeather
import FeatureSettings
import FeatureOnboarding
import FeatureFocus

/// App complète : initialise la logique KMP et assemble les features, derrière l'écran de connexion.
@main
struct iOSApp: App {
    @Environment(\.scenePhase) private var scenePhase

    init() {
        KoinIosKt.doInitKoin()
        // Plantages : enregistrés seulement avec le consentement Rapports de plantage, ceux du lancement précédent
        // partent maintenant (ADR 0020).
        Task { @MainActor in _ = try? await CrashReportingUseCases.shared.startCrashReporting().invoke(params: NoParams.shared) }
        // Feature flags distants (ADR 0021) : partent du cache, relus au plus une fois toutes les 15 minutes.
        Self.refreshFeatureFlags()
        TodoReminders.start()
        // Mode hors ligne : les modifications des tâches partent dès que le réseau revient (ADR 0012).
        TodoSync.start()
        // Widget Tâches de l'écran d'accueil : recopié à chaque changement, vidé à la déconnexion ou avec le verrou.
        // Les cases cochées dans le widget depuis la dernière ouverture sont appliquées (ADR 0026).
        TodoWidgetPublisher.start()
        // Widget Météo : dernière météo « Ma position », actualisée au lancement et au retour au premier plan.
        WeatherWidgetPublisher.start()
        // Mode focus (ADR 0028) : Live Activity tenue à jour tant qu'une session existe, terminée à l'arrêt ou à la déconnexion.
        FocusLiveActivity.start()
    }

    var body: some Scene {
        WindowGroup {
            // Thème et accent de Réglages, aussi sur l'écran de connexion et l'écran verrouillé.
            AppearanceRoot {
                SessionGate {
                    // Face ID / Touch ID, si activé dans Réglages : rien n'est affiché avant.
                    AppLockGate {
                        // Présentation du premier lancement, une fois par appareil (ADR 0016) : avant les onglets,
                        // un lien reçu entre-temps attend dans KMP.
                        OnboardingGate {
                            AppTabsView()
                        }
                    }
                }
            }
            // Liens mvpmodular://… (schéma déclaré dans project.yml) : reçus même déconnecté ou verrouillé,
            // ils attendent dans KMP que les onglets soient affichés.
            .onOpenURL { url in
                Task { @MainActor in
                    _ = try? await DeepLinkUseCases.shared.openDeepLink().invoke(params: url.absoluteString as NSString)
                }
            }
        }
        // Mesure d'audience : ce qui attend part quand l'app passe en arrière-plan (rien sans consentement).
        .onChange(of: scenePhase) { phase in
            if phase == .active {
                Self.refreshFeatureFlags()
                TodoWidgetPublisher.applyWidgetCompletions()
                WeatherWidgetPublisher.refresh()
            }
            guard phase == .background else { return }
            Task { @MainActor in _ = try? await AnalyticsUseCases.shared.flushAnalytics().invoke(params: NoParams.shared) }
        }
    }

    private static func refreshFeatureFlags() {
        Task { @MainActor in
            _ = try? await FeatureFlagsUseCases.shared.refreshRemoteFlags().invoke(params: RemoteFlagsRefresh(force: false))
        }
    }
}

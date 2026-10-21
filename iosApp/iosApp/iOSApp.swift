import SwiftUI
import Shared
import FeatureAuth
import FeatureTodo
import FeatureWeather
import FeatureCatalog
import FeatureSettings

/// App complète : initialise la logique KMP et assemble les features, derrière l'écran de connexion.
@main
struct iOSApp: App {
    init() {
        KoinIosKt.doInitKoin()
        TodoReminders.start()
    }

    var body: some Scene {
        WindowGroup {
            SessionGate {
                // Face ID / Touch ID, si activé dans Réglages : rien n'est affiché avant.
                AppLockGate {
                    TabView {
                        TodoListView()
                            .tabItem { Label("Tâches", systemImage: "checklist") }
                        WeatherView()
                            .tabItem { Label("Météo", systemImage: "cloud.sun") }
                        CatalogView()
                            .tabItem { Label("Catalogue", systemImage: "bag") }
                        SettingsView()
                            .tabItem { Label("Réglages", systemImage: "gearshape") }
                    }
                }
            }
        }
    }
}

import SwiftUI
import Shared
import FeatureAuth
import FeatureTodo
import FeatureWeather
import FeatureSettings

/// App complète : initialise la logique KMP et assemble les features, derrière l'écran de connexion.
@main
struct iOSApp: App {
    init() {
        KoinIosKt.doInitKoin()
    }

    var body: some Scene {
        WindowGroup {
            SessionGate {
                TabView {
                    TodoListView()
                        .tabItem { Label("Tâches", systemImage: "checklist") }
                    WeatherView()
                        .tabItem { Label("Météo", systemImage: "cloud.sun") }
                    SettingsView()
                        .tabItem { Label("Réglages", systemImage: "gearshape") }
                }
            }
        }
    }
}

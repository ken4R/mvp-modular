import SwiftUI
import Shared
import FeatureTodo
import FeatureWeather
import FeatureSettings

/// App complète : initialise la logique KMP et assemble les features.
@main
struct iOSApp: App {
    init() {
        KoinIosKt.doInitKoin()
    }

    var body: some Scene {
        WindowGroup {
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

import SwiftUI
import Shared
import FeatureWeather

/// App de démo : lance uniquement la feature météo, avec une recherche d'exemple déjà faite,
/// pour la développer et la tester sans passer par l'app complète.
@main
struct WeatherDemoApp: App {
    init() {
        KoinIosKt.doInitKoin()
    }

    var body: some Scene {
        WindowGroup {
            WeatherView(initialQuery: "Paris")
        }
    }
}

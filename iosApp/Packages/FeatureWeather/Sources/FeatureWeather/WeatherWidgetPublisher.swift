import Foundation
import SwiftUI
import WidgetKit
import Shared
import DesignSystemUI
import WeatherWidgetData

/// Widget Météo (ADR 0026) : à démarrer une fois au lancement de l'app. KMP décide de ce que le widget peut montrer
/// (`UseCases.observeWeatherWidget`) ; ici, seulement la recopie dans le fichier de l'App Group (sans la position)
/// et la demande de redessin à WidgetKit. Déconnexion : le fichier ne contient plus aucune météo.
public enum WeatherWidgetPublisher {
    private static var subscription: Subscription?
    private static var lastPublished: WeatherWidgetSnapshot?

    public static func start() {
        guard subscription == nil else { return }
        subscription = UseCases.shared.observeWeatherWidget(
            onState: { state in publish(snapshot(of: state)) },
            onError: { _ in publish(WeatherWidgetSnapshot(content: .unavailable, palette: palette)) }
        )
        refresh()
    }

    /// Actualise la météo de la position déjà gardée, sans demander la localisation (au plus tous les quarts d'heure,
    /// décidé par KMP). À appeler au lancement (fait par `start()`) et à chaque retour au premier plan.
    public static func refresh() {
        Task { @MainActor in
            _ = try? await UseCases.shared.refreshWeatherWidget().invoke(params: NoParams.shared)
        }
    }

    private static func publish(_ snapshot: WeatherWidgetSnapshot) {
        guard snapshot != lastPublished else { return }
        do {
            try WeatherWidgetStore.write(snapshot)
            lastPublished = snapshot
        } catch {
            // Écriture impossible : ne pas laisser une ancienne météo derrière soi.
            WeatherWidgetStore.remove()
            lastPublished = nil
        }
        WidgetCenter.shared.reloadTimelines(ofKind: WeatherWidgetStore.widgetKind)
    }

    private static func snapshot(of state: WeatherWidgetState) -> WeatherWidgetSnapshot {
        guard let weather = state as? WeatherWidgetStateWeather else {
            let content: WeatherWidgetSnapshot.Content = state is WeatherWidgetStateSignedOut ? .signedOut : .noData
            return WeatherWidgetSnapshot(content: content, palette: palette)
        }
        let appearance = ConditionAppearance(weather.condition, isDay: weather.isDay)
        return WeatherWidgetSnapshot(
            content: .weather,
            temperatureCelsius: Int(weather.temperatureCelsius.rounded()),
            conditionLabel: appearance.label,
            symbolName: appearance.symbol,
            updatedAt: Date(timeIntervalSince1970: Double(weather.updatedAt) / 1000),
            visibleUntil: Date(timeIntervalSince1970: Double(weather.visibleUntil) / 1000),
            link: URL(string: weather.link),
            palette: palette
        )
    }

    private static var palette: WeatherWidgetSnapshot.Palette {
        WeatherWidgetSnapshot.Palette(primaryLight: DS.colors(.light).primary, primaryDark: DS.colors(.dark).primary)
    }
}

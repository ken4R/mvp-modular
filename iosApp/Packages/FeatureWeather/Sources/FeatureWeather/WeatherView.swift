import SwiftUI
import UIKit
import Shared
import DesignSystemUI

public struct WeatherView: View {
    @StateObject private var viewModel: WeatherViewModel
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.dsAccent) private var accent
    @FocusState private var searchFocused: Bool

    /// - Parameter initialQuery: ville recherchée à l'ouverture (utilisé par la démo).
    public init(initialQuery: String? = nil) {
        _viewModel = StateObject(wrappedValue: WeatherViewModel(initialQuery: initialQuery))
    }

    public var body: some View {
        let colors = DS.colors(colorScheme, accent)
        VStack(alignment: .leading, spacing: DS.Spacing.md) {
            Text("Météo")
                .font(DS.Typography.titleLarge)
                .foregroundColor(Color(argb: colors.onBackground))
            if !viewModel.isOnline {
                StatusBanner("Hors ligne · la météo s'affichera au retour du réseau")
            }

            HStack(spacing: DS.Spacing.sm) {
                TextField("Rechercher une ville", text: $viewModel.query)
                    .textFieldStyle(.roundedBorder)
                    .autocorrectionDisabled()
                    .submitLabel(.search)
                    .focused($searchFocused)
                    .onSubmit(search)
                Button("Rechercher", action: search)
                    .tint(Color(argb: colors.primary))
            }

            Button(action: locate) {
                Label("Météo à ma position", systemImage: "location.fill")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered)
            .tint(Color(argb: colors.primary))

            content(colors)
        }
        .padding(DS.Spacing.md)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(Color(argb: colors.background))
        .task { await viewModel.start() }
        .onAppear { viewModel.observeConnectivity() }
        .onDisappear { viewModel.stopObservingConnectivity() }
    }

    @ViewBuilder
    private func content(_ colors: ColorTokens) -> some View {
        switch viewModel.state {
        case .idle:
            message("Cherchez une ville, ou utilisez votre position, pour voir la météo actuelle.", color: colors.onBackground)
        case .loading:
            ProgressView()
                .frame(maxWidth: .infinity)
        case let .results(cities) where cities.isEmpty:
            message("Aucune ville trouvée", color: colors.onBackground)
        case let .results(cities):
            List(cities, id: \.id) { city in
                Button { select(city) } label: { CityRow(city: city, colors: colors) }
                    .buttonStyle(.plain)
            }
            .listStyle(.plain)
            .scrollDismissesKeyboard(.immediately)
        case let .weather(place, weather):
            WeatherCard(place: place, weather: weather, colors: colors) {
                Task { await viewModel.refresh() }
            }
        case let .error(text, canOpenSettings):
            VStack(alignment: .leading, spacing: DS.Spacing.sm) {
                message(text, color: colors.error)
                if canOpenSettings {
                    Button("Ouvrir Réglages", action: openSettings)
                        .tint(Color(argb: colors.primary))
                }
            }
        }
    }

    private func message(_ text: String, color: Int64) -> some View {
        Text(text)
            .font(DS.Typography.body)
            .foregroundColor(Color(argb: color))
    }

    /// Ferme le clavier avant de charger, pour que le tap suivant atteigne la liste.
    private func search() {
        searchFocused = false
        Task { await viewModel.search() }
    }

    private func select(_ city: City) {
        searchFocused = false
        Task { await viewModel.select(city) }
    }

    private func locate() {
        searchFocused = false
        Task { await viewModel.locate() }
    }

    /// Une fois la localisation refusée, iOS ne redemande plus : seule la page de l'app dans Réglages permet d'autoriser.
    private func openSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
        UIApplication.shared.open(url)
    }
}

private struct CityRow: View {
    let city: City
    let colors: ColorTokens

    var body: some View {
        VStack(alignment: .leading, spacing: DS.Spacing.xs) {
            Text(city.name)
                .font(DS.Typography.body)
                .foregroundColor(Color(argb: colors.onSurface))
            if let subtitle = city.subtitle {
                Text(subtitle)
                    .font(DS.Typography.caption)
                    .foregroundColor(Color(argb: colors.onSurface))
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .contentShape(Rectangle())
    }
}

private struct WeatherCard: View {
    let place: WeatherPlace
    let weather: CurrentWeather
    let colors: ColorTokens
    let onRefresh: () -> Void

    var body: some View {
        let appearance = ConditionAppearance(weather.condition, isDay: weather.isDay)
        VStack(alignment: .leading, spacing: DS.Spacing.sm) {
            Text(title)
                .font(DS.Typography.titleMedium)
                .foregroundColor(Color(argb: colors.onSurface))
            if let subtitle {
                Text(subtitle)
                    .font(DS.Typography.caption)
                    .foregroundColor(Color(argb: colors.onSurface))
            }

            HStack(spacing: DS.Spacing.md) {
                Image(systemName: appearance.symbol)
                    .font(DS.Typography.titleLarge)
                    .foregroundColor(Color(argb: colors.primary))
                    .accessibilityHidden(true)
                Text(celsius(weather.temperatureCelsius))
                    .font(DS.Typography.titleLarge)
                    .foregroundColor(Color(argb: colors.onSurface))
            }
            Text(appearance.label)
                .font(DS.Typography.body)
                .foregroundColor(Color(argb: colors.onSurface))

            detail("Ressenti", celsius(weather.feelsLikeCelsius))
            detail("Humidité", "\(weather.humidityPercent) %")
            detail("Vent", "\(Int(weather.windSpeedKmh.rounded())) km/h")

            Button("Actualiser", action: onRefresh)
                .tint(Color(argb: colors.primary))
        }
        .padding(DS.Spacing.md)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: DS.Radius.lg)
                .fill(Color(argb: colors.surface))
        )
    }

    private var title: String {
        switch place {
        case let .city(city): return city.name
        case .myPosition: return "Ma position"
        }
    }

    private var subtitle: String? {
        switch place {
        case let .city(city): return city.subtitle
        case .myPosition: return "Position approximative (environ 1 km)"
        }
    }

    private func detail(_ title: String, _ value: String) -> some View {
        HStack {
            Text(title)
            Spacer()
            Text(value)
        }
        .font(DS.Typography.label)
        .foregroundColor(Color(argb: colors.onSurface))
    }

    private func celsius(_ value: Double) -> String {
        "\(Int(value.rounded())) °C"
    }
}

/// Icône SF Symbols et libellé d'une condition météo KMP.
private struct ConditionAppearance {
    let symbol: String
    let label: String

    init(_ condition: WeatherCondition, isDay: Bool) {
        let appearance: (symbol: String, label: String)
        // Comparaison sur le nom Kotlin de l'entrée : l'export Swift renomme les cas de l'enum.
        switch condition.name {
        case "Clear": appearance = (isDay ? "sun.max.fill" : "moon.stars.fill", "Ciel dégagé")
        case "PartlyCloudy": appearance = (isDay ? "cloud.sun.fill" : "cloud.moon.fill", "Partiellement nuageux")
        case "Cloudy": appearance = ("cloud.fill", "Couvert")
        case "Fog": appearance = ("cloud.fog.fill", "Brouillard")
        case "Drizzle": appearance = ("cloud.drizzle.fill", "Bruine")
        case "Rain": appearance = ("cloud.rain.fill", "Pluie")
        case "Snow": appearance = ("cloud.snow.fill", "Neige")
        case "Thunderstorm": appearance = ("cloud.bolt.rain.fill", "Orage")
        default: appearance = ("questionmark.circle", "Conditions inconnues")
        }
        symbol = appearance.symbol
        label = appearance.label
    }
}

private extension City {
    /// « Région, Pays », ou nil si aucun des deux n'est connu.
    var subtitle: String? {
        let parts = [region, country].compactMap { $0 }
        return parts.isEmpty ? nil : parts.joined(separator: ", ")
    }
}

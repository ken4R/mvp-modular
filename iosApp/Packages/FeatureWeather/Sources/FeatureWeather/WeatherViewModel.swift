import Foundation
import Shared

/// Lieu dont on affiche la météo : une ville choisie, ou la position de l'appareil.
enum WeatherPlace {
    case city(City)
    case myPosition
}

@MainActor
final class WeatherViewModel: ObservableObject {
    enum State {
        case idle
        case loading
        case results([City])
        case weather(WeatherPlace, CurrentWeather)
        /// `canOpenSettings` : la localisation est refusée, l'écran propose d'ouvrir les Réglages iOS.
        case error(String, canOpenSettings: Bool = false)
    }

    @Published var query: String = ""
    @Published private(set) var state: State = .idle
    /// Hors ligne : bandeau en haut de l'écran, la recherche et la météo attendent le réseau (ADR 0012).
    @Published private(set) var isOnline = true
    private var requestID = 0
    private var connectivitySubscription: Subscription?

    private let initialQuery: String?
    private let searchCities: SearchCitiesUseCase
    private let getCurrentWeather: GetCurrentWeatherUseCase
    private let getLocalWeather: GetLocalWeatherUseCase

    init(
        initialQuery: String? = nil,
        searchCities: SearchCitiesUseCase = WeatherUseCases.shared.searchCities(),
        getCurrentWeather: GetCurrentWeatherUseCase = WeatherUseCases.shared.getCurrentWeather(),
        getLocalWeather: GetLocalWeatherUseCase = WeatherUseCases.shared.getLocalWeather()
    ) {
        self.initialQuery = initialQuery
        self.searchCities = searchCities
        self.getCurrentWeather = getCurrentWeather
        self.getLocalWeather = getLocalWeather
    }

    func observeConnectivity() {
        guard connectivitySubscription == nil else { return }
        connectivitySubscription = ConnectivityUseCases.shared.observeConnectivity(
            onOnline: { [weak self] online in self?.isOnline = online.boolValue },
            onError: { _ in }
        )
    }

    func stopObservingConnectivity() {
        connectivitySubscription?.cancel()
        connectivitySubscription = nil
    }

    /// Lance la recherche initiale éventuelle, une seule fois.
    func start() async {
        guard case .idle = state, let initialQuery else { return }
        query = initialQuery
        await search()
    }

    func search() async {
        await load {
            let result = try await self.searchCities.invoke(params: self.query as NSString)
            guard let cities = (result as? AppResultSuccess<AnyObject>)?.data as? [City] else { return Self.failure(result) }
            return .results(cities)
        }
    }

    func select(_ city: City) async {
        await load {
            let result = try await self.getCurrentWeather.invoke(params: city)
            guard let weather = (result as? AppResultSuccess<AnyObject>)?.data as? CurrentWeather else { return Self.failure(result) }
            return .weather(.city(city), weather)
        }
    }

    /// La première fois, iOS demande l'autorisation de localisation (gérée par la logique KMP).
    func locate() async {
        await load {
            let result = try await self.getLocalWeather.invoke(params: NoParams.shared)
            guard let local = (result as? AppResultSuccess<AnyObject>)?.data as? LocalWeather else { return Self.failure(result) }
            return .weather(.myPosition, local.weather)
        }
    }

    func refresh() async {
        guard case let .weather(place, _) = state else { return }
        switch place {
        case let .city(city): await select(city)
        case .myPosition: await locate()
        }
    }

    /// Affiche le chargement puis le résultat de `request`, sauf si une action plus récente a été lancée entre-temps
    /// (« Ma position » peut prendre plusieurs secondes) : la dernière action de l'utilisateur l'emporte.
    private func load(_ request: () async throws -> State) async {
        requestID += 1
        let id = requestID
        state = .loading
        let newState = (try? await request()) ?? .error("Erreur inattendue")
        guard id == requestID else { return }
        state = newState
    }

    private static func failure(_ result: Any?) -> State {
        let error = (result as? AppResultFailure)?.error
        return .error(error?.userMessage ?? "Erreur inattendue", canOpenSettings: error is AppErrorPermissionDenied)
    }
}

private extension AppError {
    var userMessage: String {
        switch self {
        case let validation as AppErrorValidation: return validation.message
        case is AppErrorNotFound: return "Ville introuvable"
        case is AppErrorNetwork: return "Problème de connexion"
        case is AppErrorUnauthorized: return "Accès refusé"
        case is AppErrorPermissionDenied:
            return "La localisation est refusée ou coupée. Autorisez-la dans Réglages pour voir la météo à votre position."
        case is AppErrorUnavailable: return "Position indisponible. Réessayez dans un endroit mieux dégagé."
        case let unknown as AppErrorUnknown: return unknown.message ?? "Erreur inattendue"
        default: return "Erreur inattendue"
        }
    }
}

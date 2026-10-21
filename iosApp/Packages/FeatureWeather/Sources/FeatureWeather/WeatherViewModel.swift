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

    private let initialQuery: String?
    private let searchCities: SearchCitiesUseCase
    private let getCurrentWeather: GetCurrentWeatherUseCase
    private let getLocalWeather: GetLocalWeatherUseCase

    init(
        initialQuery: String? = nil,
        searchCities: SearchCitiesUseCase = UseCases.shared.searchCities(),
        getCurrentWeather: GetCurrentWeatherUseCase = UseCases.shared.getCurrentWeather(),
        getLocalWeather: GetLocalWeatherUseCase = UseCases.shared.getLocalWeather()
    ) {
        self.initialQuery = initialQuery
        self.searchCities = searchCities
        self.getCurrentWeather = getCurrentWeather
        self.getLocalWeather = getLocalWeather
    }

    /// Lance la recherche initiale éventuelle, une seule fois.
    func start() async {
        guard case .idle = state, let initialQuery else { return }
        query = initialQuery
        await search()
    }

    func search() async {
        state = .loading
        do {
            let result = try await searchCities.invoke(params: query as NSString)
            if let cities = (result as? AppResultSuccess<AnyObject>)?.data as? [City] {
                state = .results(cities)
            } else {
                showFailure(result)
            }
        } catch {
            state = .error("Erreur inattendue")
        }
    }

    func select(_ city: City) async {
        state = .loading
        do {
            let result = try await getCurrentWeather.invoke(params: city)
            if let weather = (result as? AppResultSuccess<AnyObject>)?.data as? CurrentWeather {
                state = .weather(.city(city), weather)
            } else {
                showFailure(result)
            }
        } catch {
            state = .error("Erreur inattendue")
        }
    }

    /// La première fois, iOS demande l'autorisation de localisation (gérée par la logique KMP).
    func locate() async {
        state = .loading
        do {
            let result = try await getLocalWeather.invoke(params: NoParams.shared)
            if let local = (result as? AppResultSuccess<AnyObject>)?.data as? LocalWeather {
                state = .weather(.myPosition, local.weather)
            } else {
                showFailure(result)
            }
        } catch {
            state = .error("Erreur inattendue")
        }
    }

    func refresh() async {
        guard case let .weather(place, _) = state else { return }
        switch place {
        case let .city(city): await select(city)
        case .myPosition: await locate()
        }
    }

    private func showFailure(_ result: Any?) {
        let error = (result as? AppResultFailure)?.error
        state = .error(error?.userMessage ?? "Erreur inattendue", canOpenSettings: error is AppErrorPermissionDenied)
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
            return "La localisation est refusée. Autorisez-la dans Réglages pour voir la météo à votre position."
        case is AppErrorUnavailable: return "Position indisponible. Vérifiez que le service de localisation est activé, puis réessayez."
        case let unknown as AppErrorUnknown: return unknown.message ?? "Erreur inattendue"
        default: return "Erreur inattendue"
        }
    }
}

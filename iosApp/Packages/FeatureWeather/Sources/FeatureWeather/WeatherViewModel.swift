import Foundation
import Shared

@MainActor
final class WeatherViewModel: ObservableObject {
    enum State {
        case idle
        case loading
        case results([City])
        case weather(City, CurrentWeather)
        case error(String)
    }

    @Published var query: String = ""
    @Published private(set) var state: State = .idle

    private let initialQuery: String?
    private let searchCities: SearchCitiesUseCase
    private let getCurrentWeather: GetCurrentWeatherUseCase

    init(
        initialQuery: String? = nil,
        searchCities: SearchCitiesUseCase = UseCases.shared.searchCities(),
        getCurrentWeather: GetCurrentWeatherUseCase = UseCases.shared.getCurrentWeather()
    ) {
        self.initialQuery = initialQuery
        self.searchCities = searchCities
        self.getCurrentWeather = getCurrentWeather
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
                state = .error((result as? AppResultFailure)?.error.userMessage ?? "Erreur inattendue")
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
                state = .weather(city, weather)
            } else {
                state = .error((result as? AppResultFailure)?.error.userMessage ?? "Erreur inattendue")
            }
        } catch {
            state = .error("Erreur inattendue")
        }
    }

    func refresh() async {
        guard case let .weather(city, _) = state else { return }
        await select(city)
    }
}

private extension AppError {
    var userMessage: String {
        switch self {
        case let validation as AppErrorValidation: return validation.message
        case is AppErrorNotFound: return "Ville introuvable"
        case is AppErrorNetwork: return "Problème de connexion"
        case is AppErrorUnauthorized: return "Accès refusé"
        case let unknown as AppErrorUnknown: return unknown.message ?? "Erreur inattendue"
        default: return "Erreur inattendue"
        }
    }
}

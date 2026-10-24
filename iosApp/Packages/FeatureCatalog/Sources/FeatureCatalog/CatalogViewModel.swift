import Foundation
import Shared

/// Tri choisi ; la valeur brute est le nom Kotlin de l'entrée de `CatalogSort`, que l'export Swift renomme.
enum CatalogSortOption: String, CaseIterable {
    case serverOrder = "ServerOrder"
    case priceAscending = "PriceAscending"
    case priceDescending = "PriceDescending"
    case bestRated = "BestRated"
}

@MainActor
final class CatalogViewModel: ObservableObject {
    /// Produits en cache qui correspondent à la recherche et aux filtres : affichés même hors ligne.
    @Published private(set) var products: [ProductSummary] = []
    /// Nombre de produits en cache, sans filtre : la pagination le suit.
    @Published private(set) var cachedCount = 0
    /// Produits déjà parcourus côté serveur : le pied de liste le suit (une page de doublons le fait aussi avancer).
    @Published private(set) var loadedCount = 0
    /// Recherche dans le nom et la marque, sur les pages déjà chargées ; appliquée après une courte pause de saisie.
    @Published var query = "" { didSet { if query != oldValue { observeFiltered(debounced: true) } } }
    /// Note d'au moins 4 sur 5.
    @Published var topRatedOnly = false { didSet { if topRatedOnly != oldValue { observeFiltered() } } }
    @Published var sort: CatalogSortOption = .serverOrder { didSet { if sort != oldValue { observeFiltered() } } }
    @Published private(set) var hasMore = true
    /// Date du dernier chargement réussi, nil si le catalogue n'a jamais été chargé.
    @Published private(set) var updatedAt: Date?
    @Published private(set) var isRefreshing = false
    @Published private(set) var isLoadingMore = false
    /// Échec de l'actualisation : affiché au-dessus de la liste en cache, ou à sa place si elle est vide.
    @Published private(set) var refreshError: String?
    /// Échec du chargement de la page suivante : la pagination s'arrête jusqu'à « Réessayer ».
    @Published private(set) var loadMoreError: String?
    /// Hors ligne : bandeau au-dessus de la liste en cache (ADR 0012).
    @Published private(set) var isOnline = true

    private var subscription: Subscription?
    private var debounceTask: Task<Void, Never>?
    /// Entre `start()` et `stop()`.
    private var isObserving = false
    private var connectivitySubscription: Subscription?
    private var didRefreshOnce = false

    /// Affiche le cache tout de suite, puis recharge la première page en arrière-plan (au premier affichage seulement).
    func start() {
        guard subscription == nil else { return }
        isObserving = true
        subscribe()
        connectivitySubscription = UseCases.shared.observeConnectivity(
            onOnline: { [weak self] online in self?.connectivityChanged(online.boolValue) },
            onError: { _ in }
        )
        guard !didRefreshOnce else { return }
        didRefreshOnce = true
        Task { await refresh() }
    }

    func stop() {
        isObserving = false
        debounceTask?.cancel()
        debounceTask = nil
        subscription?.cancel()
        subscription = nil
        connectivitySubscription?.cancel()
        connectivitySubscription = nil
    }

    /// Remplace l'observation par celle du filtre courant (filtrage et tri faits par la logique KMP).
    private func observeFiltered(debounced: Bool = false) {
        guard isObserving else { return }
        debounceTask?.cancel()
        if debounced {
            debounceTask = Task { [weak self] in
                try? await Task.sleep(nanoseconds: Self.searchDebounce)
                if !Task.isCancelled { self?.subscribe() }
            }
        } else {
            subscribe()
        }
    }

    private func subscribe() {
        let filter = KoinIosKt.catalogFilter(query: query, topRatedOnly: topRatedOnly, sort: sort.rawValue)
        subscription?.cancel()
        subscription = UseCases.shared.observeFilteredCatalog(
            filter: filter,
            onCatalog: { [weak self] filtered in
                let catalog = filtered.catalog
                self?.products = filtered.products
                self?.cachedCount = catalog.products.count
                self?.loadedCount = Int(catalog.loadedCount)
                self?.hasMore = catalog.hasMore
                self?.updatedAt = catalog.updatedAt.map { Date(timeIntervalSince1970: Double($0.int64Value) / 1000) }
            },
            onError: { _ in }
        )
    }

    /// Pause de saisie avant d'appliquer la recherche, en nanosecondes (0,25 s).
    private static let searchDebounce: UInt64 = 250_000_000

    /// Retour du réseau : ce qui a échoué hors ligne est relancé sans attendre l'utilisateur.
    private func connectivityChanged(_ online: Bool) {
        let wasOffline = !isOnline
        isOnline = online
        guard online, wasOffline else { return }
        if refreshError != nil {
            Task { await refresh() }
        } else if loadMoreError != nil {
            loadMoreError = nil
            Task { await loadMore() }
        }
    }

    func refresh() async {
        guard !isRefreshing else { return }
        isRefreshing = true
        refreshError = nil
        loadMoreError = nil
        let result = try? await UseCases.shared.refreshCatalog().invoke(params: NoParams.shared)
        if !(result is AppResultSuccess<AnyObject>) {
            refreshError = (result as? AppResultFailure)?.error.catalogMessage ?? unexpectedMessage
        }
        isRefreshing = false
    }

    /// Appelé quand le bas de la liste devient visible.
    func loadMore() async {
        guard !isLoadingMore, !isRefreshing, hasMore, loadMoreError == nil else { return }
        isLoadingMore = true
        let result = try? await UseCases.shared.loadMoreProducts().invoke(params: NoParams.shared)
        if !(result is AppResultSuccess<AnyObject>) {
            loadMoreError = (result as? AppResultFailure)?.error.catalogMessage ?? unexpectedMessage
        }
        isLoadingMore = false
    }

    func retryLoadMore() async {
        loadMoreError = nil
        await loadMore()
    }
}

@MainActor
final class ProductDetailViewModel: ObservableObject {
    enum State {
        case loading
        case loaded(Product)
        case error(String)
    }

    @Published private(set) var state: State = .loading
    let productId: Int64

    init(productId: Int64) {
        self.productId = productId
    }

    /// Le cache d'abord (la fiche s'ouvre hors ligne), le serveur sinon : règle portée par la logique KMP.
    func load() async {
        state = .loading
        let result = try? await UseCases.shared.getProduct().invoke(params: KotlinLong(value: productId))
        if let product = (result as? AppResultSuccess<AnyObject>)?.data as? Product {
            state = .loaded(product)
        } else {
            state = .error((result as? AppResultFailure)?.error.catalogMessage ?? unexpectedMessage)
        }
    }
}

private let unexpectedMessage = "Le catalogue est indisponible pour le moment."

private extension AppError {
    /// Mêmes messages que sur Android. Le détail technique d'une erreur n'est jamais montré.
    var catalogMessage: String {
        switch self {
        case is AppErrorNetwork: return "Pas de connexion internet."
        case is AppErrorNotFound: return "Produit introuvable."
        default: return unexpectedMessage
        }
    }
}

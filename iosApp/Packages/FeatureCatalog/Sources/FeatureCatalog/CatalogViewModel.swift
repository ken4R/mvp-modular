import Foundation
import Shared

@MainActor
final class CatalogViewModel: ObservableObject {
    /// Produits en cache : affichés même hors ligne.
    @Published private(set) var products: [ProductSummary] = []
    @Published private(set) var hasMore = true
    /// Date du dernier chargement réussi, nil si le catalogue n'a jamais été chargé.
    @Published private(set) var updatedAt: Date?
    @Published private(set) var isRefreshing = false
    @Published private(set) var isLoadingMore = false
    /// Échec de l'actualisation : affiché au-dessus de la liste en cache, ou à sa place si elle est vide.
    @Published private(set) var refreshError: String?
    /// Échec du chargement de la page suivante : la pagination s'arrête jusqu'à « Réessayer ».
    @Published private(set) var loadMoreError: String?

    private var subscription: Subscription?

    /// Affiche le cache tout de suite, puis recharge la première page en arrière-plan (une seule fois).
    func start() {
        guard subscription == nil else { return }
        subscription = UseCases.shared.observeCatalog(
            onCatalog: { [weak self] catalog in
                self?.products = catalog.products
                self?.hasMore = catalog.hasMore
                self?.updatedAt = catalog.updatedAt.map { Date(timeIntervalSince1970: Double($0.int64Value) / 1000) }
            },
            onError: { _ in }
        )
        Task { await refresh() }
    }

    deinit {
        subscription?.cancel()
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

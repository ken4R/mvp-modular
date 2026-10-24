package com.mvp.android.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.result.onFailure
import com.mvp.core.common.domain.result.onSuccess
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.core.connectivity.domain.usecase.ObserveConnectivityUseCase
import com.mvp.feature.catalog.domain.model.CatalogFilter
import com.mvp.feature.catalog.domain.model.CatalogSort
import com.mvp.feature.catalog.domain.model.Product
import com.mvp.feature.catalog.domain.model.ProductSummary
import com.mvp.feature.catalog.domain.usecase.GetProductUseCase
import com.mvp.feature.catalog.domain.usecase.LoadMoreProductsUseCase
import com.mvp.feature.catalog.domain.usecase.ObserveFilteredCatalogUseCase
import com.mvp.feature.catalog.domain.usecase.RefreshCatalogUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Fiche ouverte : chargement ([product] et [error] nuls), produit affiché, ou erreur. */
data class ProductDetailState(
    val productId: Long,
    val product: Product? = null,
    val error: String? = null,
)

data class CatalogUiState(
    /** Produits en cache qui correspondent à la recherche et aux filtres : affichés même hors ligne. */
    val products: List<ProductSummary> = emptyList(),
    /** Nombre de produits en cache, sans filtre : la pagination le suit. */
    val cachedCount: Int = 0,
    /** Produits déjà parcourus côté serveur : la pagination le suit (une page de doublons le fait aussi avancer). */
    val loadedCount: Int = 0,
    /** Texte du champ de recherche, appliqué à [filter] après une courte pause de saisie. */
    val query: String = "",
    val filter: CatalogFilter = CatalogFilter(),
    val hasMore: Boolean = true,
    /** Date du dernier chargement réussi (epoch ms), `null` si le catalogue n'a jamais été chargé. */
    val updatedAt: Long? = null,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    /** Échec de l'actualisation : affiché au-dessus de la liste en cache, ou à sa place si elle est vide. */
    val refreshError: String? = null,
    /** Échec du chargement de la page suivante : la pagination s'arrête jusqu'à « Réessayer ». */
    val loadMoreError: String? = null,
    /** Fiche produit ouverte par-dessus la liste, ou `null`. */
    val detail: ProductDetailState? = null,
    /** Hors ligne : bandeau au-dessus de la liste en cache (ADR 0012). */
    val isOnline: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogViewModel(
    observeFilteredCatalog: ObserveFilteredCatalogUseCase,
    private val refreshCatalog: RefreshCatalogUseCase,
    private val loadMoreProducts: LoadMoreProductsUseCase,
    private val getProduct: GetProductUseCase,
    observeConnectivity: ObserveConnectivityUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(CatalogUiState())
    val state: StateFlow<CatalogUiState> = _state.asStateFlow()

    private var detailRequest: Job? = null
    private var queryJob: Job? = null

    init {
        // Chaque nouveau filtre remplace l'observation précédente.
        _state.map { it.filter }
            .distinctUntilChanged()
            .flatMapLatest { filter -> observeFilteredCatalog(filter) }
            .onEach { result ->
                result.onSuccess { filtered ->
                    val catalog = filtered.catalog
                    _state.update {
                        it.copy(
                            products = filtered.products,
                            cachedCount = catalog.products.size,
                            loadedCount = catalog.loadedCount,
                            hasMore = catalog.hasMore,
                            updatedAt = catalog.updatedAt,
                        )
                    }
                }
            }
            .launchIn(viewModelScope)
        // Le cache s'affiche tout de suite ; la première page est rechargée en arrière-plan.
        refresh()
        observeConnectivity(NoParams)
            .onEach { result ->
                if (result !is AppResult.Success) return@onEach
                val online = result.data
                val wasOffline = !_state.value.isOnline
                _state.update { it.copy(isOnline = online) }
                // Retour du réseau : ce qui a échoué hors ligne est relancé sans attendre l'utilisateur.
                if (online && wasOffline) {
                    if (_state.value.refreshError != null) refresh()
                    if (_state.value.loadMoreError != null) retryLoadMore()
                }
            }
            .launchIn(viewModelScope)
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
        queryJob?.cancel()
        queryJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MILLIS)
            updateFilter { it.copy(query = query) }
        }
    }

    fun onToggleTopRated() = updateFilter { it.copy(topRatedOnly = !it.topRatedOnly) }

    fun onSortChange(sort: CatalogSort) = updateFilter { it.copy(sort = sort) }

    /** « Tout afficher » : retire la recherche et le filtre, garde le tri. */
    fun onClearFilter() {
        queryJob?.cancel()
        _state.update { it.copy(query = "", filter = it.filter.copy(query = "", topRatedOnly = false)) }
    }

    private fun updateFilter(transform: (CatalogFilter) -> CatalogFilter) = _state.update { it.copy(filter = transform(it.filter)) }

    fun refresh() {
        if (_state.value.isRefreshing) return
        _state.update { it.copy(isRefreshing = true, refreshError = null, loadMoreError = null) }
        viewModelScope.launch {
            val result = refreshCatalog(NoParams)
            _state.update { it.copy(isRefreshing = false, refreshError = (result as? AppResult.Failure)?.error?.toMessage()) }
        }
    }

    /** Appelé quand le bas de la liste devient visible. */
    fun loadMore() {
        val current = _state.value
        if (current.isLoadingMore || current.isRefreshing || !current.hasMore || current.loadMoreError != null) return
        _state.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            loadMoreProducts(NoParams).onFailure { error -> _state.update { it.copy(loadMoreError = error.toMessage()) } }
            _state.update { it.copy(isLoadingMore = false) }
        }
    }

    fun retryLoadMore() {
        _state.update { it.copy(loadMoreError = null) }
        loadMore()
    }

    fun openProduct(id: Long) {
        detailRequest?.cancel()
        _state.update { it.copy(detail = ProductDetailState(id)) }
        detailRequest = viewModelScope.launch {
            getProduct(id)
                .onSuccess { product -> updateDetail(id) { it.copy(product = product) } }
                .onFailure { error -> updateDetail(id) { it.copy(error = error.toMessage()) } }
        }
    }

    fun retryProduct() {
        _state.value.detail?.let { openProduct(it.productId) }
    }

    fun closeProduct() {
        detailRequest?.cancel()
        _state.update { it.copy(detail = null) }
    }

    /** Ignore une réponse arrivée après la fermeture de la fiche ou l'ouverture d'une autre. */
    private fun updateDetail(id: Long, transform: (ProductDetailState) -> ProductDetailState) = _state.update { state ->
        val detail = state.detail?.takeIf { it.productId == id } ?: return@update state
        state.copy(detail = transform(detail))
    }
}

/** Pause de saisie avant d'appliquer la recherche. */
private const val SEARCH_DEBOUNCE_MILLIS = 250L

private fun AppError.toMessage(): String = when (this) {
    AppError.Network -> "Pas de connexion internet."
    AppError.NotFound -> "Produit introuvable."
    // Le détail technique d'une exception n'est jamais montré à l'utilisateur.
    else -> "Le catalogue est indisponible pour le moment."
}

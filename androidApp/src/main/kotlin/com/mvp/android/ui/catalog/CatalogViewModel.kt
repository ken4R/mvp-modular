package com.mvp.android.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.result.onFailure
import com.mvp.core.common.domain.result.onSuccess
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.core.connectivity.domain.usecase.ObserveConnectivityUseCase
import com.mvp.feature.catalog.domain.model.Product
import com.mvp.feature.catalog.domain.model.ProductSummary
import com.mvp.feature.catalog.domain.usecase.GetProductUseCase
import com.mvp.feature.catalog.domain.usecase.LoadMoreProductsUseCase
import com.mvp.feature.catalog.domain.usecase.ObserveCatalogUseCase
import com.mvp.feature.catalog.domain.usecase.RefreshCatalogUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
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
    /** Produits en cache : affichés même hors ligne. */
    val products: List<ProductSummary> = emptyList(),
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

class CatalogViewModel(
    observeCatalog: ObserveCatalogUseCase,
    private val refreshCatalog: RefreshCatalogUseCase,
    private val loadMoreProducts: LoadMoreProductsUseCase,
    private val getProduct: GetProductUseCase,
    observeConnectivity: ObserveConnectivityUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(CatalogUiState())
    val state: StateFlow<CatalogUiState> = _state.asStateFlow()

    private var detailRequest: Job? = null

    init {
        observeCatalog(NoParams)
            .onEach { result ->
                result.onSuccess { catalog ->
                    _state.update {
                        it.copy(products = catalog.products, hasMore = catalog.hasMore, updatedAt = catalog.updatedAt)
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

private fun AppError.toMessage(): String = when (this) {
    AppError.Network -> "Pas de connexion internet."
    AppError.NotFound -> "Produit introuvable."
    // Le détail technique d'une exception n'est jamais montré à l'utilisateur.
    else -> "Le catalogue est indisponible pour le moment."
}

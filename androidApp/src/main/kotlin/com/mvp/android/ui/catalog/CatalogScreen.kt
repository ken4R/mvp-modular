package com.mvp.android.ui.catalog

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.mvp.android.ui.common.StatusBanner
import com.mvp.android.ui.theme.Radius
import com.mvp.android.ui.theme.Sizes
import com.mvp.android.ui.theme.Spacing
import com.mvp.feature.catalog.domain.model.Product
import com.mvp.feature.catalog.domain.model.ProductSummary
import org.koin.androidx.compose.koinViewModel
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Currency
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** [productRequest] : produit demandé par un lien, dont la fiche s'ouvre ; [onProductRequestHandled] une fois ouverte. */
@Composable
fun CatalogScreen(
    productRequest: Long? = null,
    onProductRequestHandled: () -> Unit = {},
    viewModel: CatalogViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(productRequest) {
        // Produit inconnu : la fiche affiche « Produit introuvable. », le retour mène à la liste.
        productRequest?.let(viewModel::openProduct)
        onProductRequestHandled()
    }
    val detail = state.detail
    if (detail != null) {
        // Retour système : revient à la liste, à la même position.
        BackHandler(onBack = viewModel::closeProduct)
        ProductDetailView(detail, onBack = viewModel::closeProduct, onRetry = viewModel::retryProduct)
    } else {
        CatalogListView(
            state = state,
            onRefresh = viewModel::refresh,
            onLoadMore = viewModel::loadMore,
            onRetryLoadMore = viewModel::retryLoadMore,
            onOpen = viewModel::openProduct,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CatalogListView(
    state: CatalogUiState,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onRetryLoadMore: () -> Unit,
    onOpen: (Long) -> Unit,
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text("Catalogue", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = Spacing.md))
            if (!state.isOnline) StatusBanner("Hors ligne · catalogue enregistré, actualisé au retour du réseau")
            // Liste en cache affichée malgré l'échec : on dit d'où elle vient.
            if (state.refreshError != null && state.products.isNotEmpty()) {
                Text(
                    "${state.refreshError} Liste enregistrée${state.updatedAt?.let { " le ${dateTime(it)}" }.orEmpty()}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            PullToRefreshBox(
                isRefreshing = state.isRefreshing && state.products.isNotEmpty(),
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    state.products.isNotEmpty() -> ProductList(state, onLoadMore, onRetryLoadMore, onOpen)
                    state.isRefreshing -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    state.refreshError != null -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text(state.refreshError, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = onRefresh) { Text("Réessayer") }
                    }
                    else -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text("Aucun produit pour le moment.", style = MaterialTheme.typography.bodyLarge)
                        TextButton(onClick = onRefresh) { Text("Actualiser") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductList(
    state: CatalogUiState,
    onLoadMore: () -> Unit,
    onRetryLoadMore: () -> Unit,
    onOpen: (Long) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(state.products, key = { it.id }) { product ->
            ProductRow(product, onClick = { onOpen(product.id) })
            HorizontalDivider()
        }
        if (state.hasMore) {
            item(key = "footer") {
                Box(Modifier.fillMaxWidth().padding(Spacing.md), contentAlignment = Alignment.Center) {
                    if (state.loadMoreError != null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(state.loadMoreError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = onRetryLoadMore) { Text("Réessayer") }
                        }
                    } else {
                        // Le pied de liste n'est composé qu'en arrivant en bas : c'est lui qui demande la page suivante,
                        // et la redemande après chaque page ou actualisation s'il reste visible.
                        LaunchedEffect(state.products.size, state.isRefreshing) { onLoadMore() }
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductRow(product: ProductSummary, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProductImage(product.thumbnailUrl, Modifier.size(Sizes.thumbnail))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(product.title, style = MaterialTheme.typography.bodyLarge)
            product.brand?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(price(product.price, product.currencyCode), style = MaterialTheme.typography.labelLarge)
                discount(product.discountPercent)?.let {
                    Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Text(rating(product.rating), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ProductDetailView(detail: ProductDetailState, onBack: () -> Unit, onRetry: () -> Unit) {
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            TextButton(onClick = onBack) { Text("‹ Catalogue") }
            val product = detail.product
            when {
                product != null -> ProductDetailContent(product)
                detail.error != null -> {
                    Text(detail.error, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetry) { Text("Réessayer") }
                }
                else -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
        }
    }
}

@Composable
private fun ProductDetailContent(product: Product) {
    val images = product.imageUrls.ifEmpty { listOfNotNull(product.thumbnailUrl) }
    if (images.isNotEmpty()) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            items(images) { url ->
                ProductImage(url, Modifier.fillParentMaxWidth(if (images.size > 1) 0.85f else 1f).height(Sizes.heroImage))
            }
        }
    }
    Text(product.title, style = MaterialTheme.typography.headlineSmall)
    Text(
        listOfNotNull(product.brand, product.category.takeIf { it.isNotBlank() }).joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Text(price(product.price, product.currencyCode), style = MaterialTheme.typography.titleMedium)
        discount(product.discountPercent)?.let {
            Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
    Detail("Note", rating(product.rating))
    Detail("Stock", if (product.stock > 0) "${product.stock} disponibles" else "Rupture de stock")
    if (product.description.isNotBlank()) {
        Text(product.description, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Image distante, gardée en cache mémoire et disque par Coil : elle reste visible hors ligne une fois chargée. */
@Composable
private fun ProductImage(url: String?, modifier: Modifier) {
    AsyncImage(
        model = url,
        // Image décorative : le titre du produit est lu juste à côté.
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .clip(RoundedCornerShape(Radius.md))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

@Composable
private fun Detail(title: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        Text(value, style = MaterialTheme.typography.labelLarge)
    }
}

private fun price(value: Double, currencyCode: String): String =
    NumberFormat.getCurrencyInstance(Locale.FRANCE)
        .apply { runCatching { currency = Currency.getInstance(currencyCode) } }
        .format(value)

/** « -10 % », ou null sans remise notable. */
private fun discount(percent: Double): String? = percent.roundToInt().takeIf { it > 0 }?.let { "-$it %" }

private fun rating(value: Double) = "★ ${String.format(Locale.FRANCE, "%.1f", value)}"

private fun dateTime(epochMillis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.FRANCE).format(Date(epochMillis))

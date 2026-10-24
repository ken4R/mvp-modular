import SwiftUI
import Shared
import DesignSystemUI

/// Fiche produit, ouverte par son identifiant (depuis la liste, ou depuis un lien profond).
struct ProductDetailView: View {
    @StateObject private var viewModel: ProductDetailViewModel
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.dsAccent) private var accent

    init(productId: Int64) {
        _viewModel = StateObject(wrappedValue: ProductDetailViewModel(productId: productId))
    }

    var body: some View {
        let colors = DS.colors(colorScheme, accent)
        ScrollView {
            VStack(alignment: .leading, spacing: DS.Spacing.sm) {
                switch viewModel.state {
                case .loading:
                    ProgressView()
                        .frame(maxWidth: .infinity)
                case let .error(text):
                    Text(text)
                        .font(DS.Typography.body)
                        .foregroundColor(Color(argb: colors.error))
                    Button("Réessayer") { Task { await viewModel.load() } }
                case let .loaded(product):
                    ProductDetailContent(product: product, colors: colors)
                }
            }
            .padding(DS.Spacing.md)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .background(Color(argb: colors.background))
        .navigationBarTitleDisplayMode(.inline)
        .task { await viewModel.load() }
    }
}

private struct ProductDetailContent: View {
    let product: Product
    let colors: ColorTokens

    var body: some View {
        let images = product.imageUrls.isEmpty ? [product.thumbnailUrl].compactMap { $0 } : product.imageUrls
        if !images.isEmpty {
            // Galerie : une image par page, balayage horizontal.
            TabView {
                ForEach(images, id: \.self) { url in
                    RemoteImage(url: url)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: images.count > 1 ? .always : .never))
            .indexViewStyle(.page(backgroundDisplayMode: .always))
            .frame(height: DS.Size.heroImage)
        }

        Text(product.title)
            .font(DS.Typography.titleLarge)
            .foregroundColor(Color(argb: colors.onBackground))
        if let subtitle {
            Text(subtitle)
                .font(DS.Typography.caption)
                .foregroundColor(Color(argb: colors.onBackground))
        }
        HStack(spacing: DS.Spacing.sm) {
            Text(CatalogFormat.price(product.price, currencyCode: product.currencyCode))
                .foregroundColor(Color(argb: colors.onBackground))
            if let discount = CatalogFormat.discount(product.discountPercent) {
                Text(discount)
                    .foregroundColor(Color(argb: colors.primary))
            }
        }
        .font(DS.Typography.titleMedium)

        detail("Note", CatalogFormat.rating(product.rating))
        detail("Stock", product.stock > 0 ? "\(product.stock) disponibles" : "Rupture de stock")

        if !product.description_.isEmpty {
            Text(product.description_)
                .font(DS.Typography.body)
                .foregroundColor(Color(argb: colors.onBackground))
        }
    }

    /// « Marque · catégorie », ou nil si aucune des deux n'est connue.
    private var subtitle: String? {
        let parts = [product.brand, product.category.isEmpty ? nil : product.category].compactMap { $0 }
        return parts.isEmpty ? nil : parts.joined(separator: " · ")
    }

    private func detail(_ title: String, _ value: String) -> some View {
        HStack {
            Text(title)
            Spacer()
            Text(value)
        }
        .font(DS.Typography.label)
        .foregroundColor(Color(argb: colors.onBackground))
    }
}

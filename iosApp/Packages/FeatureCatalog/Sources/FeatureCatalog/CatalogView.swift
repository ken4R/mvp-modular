import SwiftUI
import Shared
import DesignSystemUI

/// Catalogue : liste paginée (pagination infinie, tirer pour actualiser), consultable hors ligne, et fiche produit.
public struct CatalogView: View {
    @StateObject private var viewModel = CatalogViewModel()
    @Environment(\.colorScheme) private var colorScheme

    public init() {}

    public var body: some View {
        let colors = DS.colors(colorScheme)
        NavigationStack {
            content(colors)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                .background(Color(argb: colors.background))
                .navigationTitle("Catalogue")
                .navigationDestination(for: Int64.self) { id in
                    ProductDetailView(productId: id)
                }
        }
        .tint(Color(argb: colors.primary))
        .onAppear { viewModel.start() }
        .onDisappear { viewModel.stop() }
    }

    @ViewBuilder
    private func content(_ colors: ColorTokens) -> some View {
        if !viewModel.products.isEmpty {
            productList(colors)
        } else if viewModel.isRefreshing {
            ProgressView()
                .frame(maxWidth: .infinity)
                .padding(DS.Spacing.md)
        } else if let error = viewModel.refreshError {
            VStack(alignment: .leading, spacing: DS.Spacing.sm) {
                message(error, color: colors.error)
                Button("Réessayer") { Task { await viewModel.refresh() } }
            }
            .padding(DS.Spacing.md)
        } else {
            VStack(alignment: .leading, spacing: DS.Spacing.sm) {
                message("Aucun produit pour le moment.", color: colors.onBackground)
                Button("Actualiser") { Task { await viewModel.refresh() } }
            }
            .padding(DS.Spacing.md)
        }
    }

    private func productList(_ colors: ColorTokens) -> some View {
        List {
            // Liste en cache affichée malgré l'échec : on dit d'où elle vient.
            if let error = viewModel.refreshError {
                Text(offlineBanner(error))
                    .font(DS.Typography.caption)
                    .foregroundColor(Color(argb: colors.error))
            }
            ForEach(viewModel.products, id: \.id) { product in
                NavigationLink(value: product.id) {
                    ProductRow(product: product, colors: colors)
                }
            }
            if viewModel.hasMore {
                footer(colors)
                    // Nouvel identifiant à chaque page ou actualisation : le pied réapparaît, et redemande la suite, s'il reste visible.
                    .id("\(viewModel.products.count)-\(viewModel.isRefreshing)")
            }
        }
        .listStyle(.plain)
        .refreshable { await viewModel.refresh() }
    }

    @ViewBuilder
    private func footer(_ colors: ColorTokens) -> some View {
        if let error = viewModel.loadMoreError {
            VStack(spacing: DS.Spacing.xs) {
                Text(error)
                    .font(DS.Typography.caption)
                    .foregroundColor(Color(argb: colors.error))
                Button("Réessayer") { Task { await viewModel.retryLoadMore() } }
                    .buttonStyle(.borderless)
            }
            .frame(maxWidth: .infinity)
        } else {
            // Le pied de liste n'apparaît qu'en arrivant en bas : c'est lui qui demande la page suivante.
            ProgressView()
                .frame(maxWidth: .infinity)
                .onAppear { Task { await viewModel.loadMore() } }
        }
    }

    private func offlineBanner(_ error: String) -> String {
        guard let updatedAt = viewModel.updatedAt else { return error }
        return "\(error) Liste enregistrée le \(updatedAt.formatted(date: .numeric, time: .shortened))."
    }

    private func message(_ text: String, color: Int64) -> some View {
        Text(text)
            .font(DS.Typography.body)
            .foregroundColor(Color(argb: color))
    }
}

private struct ProductRow: View {
    let product: ProductSummary
    let colors: ColorTokens

    var body: some View {
        HStack(spacing: DS.Spacing.md) {
            RemoteImage(url: product.thumbnailUrl)
                .frame(width: DS.Size.thumbnail, height: DS.Size.thumbnail)
            VStack(alignment: .leading, spacing: DS.Spacing.xs) {
                Text(product.title)
                    .font(DS.Typography.body)
                    .foregroundColor(Color(argb: colors.onSurface))
                if let brand = product.brand {
                    Text(brand)
                        .font(DS.Typography.caption)
                        .foregroundColor(Color(argb: colors.onSurface))
                }
                HStack(spacing: DS.Spacing.sm) {
                    Text(CatalogFormat.price(product.price, currencyCode: product.currencyCode))
                        .foregroundColor(Color(argb: colors.onSurface))
                    if let discount = CatalogFormat.discount(product.discountPercent) {
                        Text(discount)
                            .foregroundColor(Color(argb: colors.primary))
                    }
                }
                .font(DS.Typography.label)
            }
            Spacer(minLength: DS.Spacing.sm)
            Text(CatalogFormat.rating(product.rating))
                .font(DS.Typography.caption)
                .foregroundColor(Color(argb: colors.onSurface))
        }
        .padding(.vertical, DS.Spacing.xs)
    }
}

/// Formats partagés par la liste et la fiche (mêmes rendus que sur Android).
enum CatalogFormat {
    private static let locale = Locale(identifier: "fr_FR")

    static func price(_ value: Double, currencyCode: String) -> String {
        value.formatted(.currency(code: currencyCode).locale(locale))
    }

    /// « -10 % », ou nil sans remise notable.
    static func discount(_ percent: Double) -> String? {
        let rounded = Int(percent.rounded())
        return rounded > 0 ? "-\(rounded) %" : nil
    }

    static func rating(_ value: Double) -> String {
        "★ " + value.formatted(.number.precision(.fractionLength(1)).locale(locale))
    }
}

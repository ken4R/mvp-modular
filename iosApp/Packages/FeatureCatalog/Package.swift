// swift-tools-version: 5.9
import PackageDescription

// Feature "catalogue" côté iOS : uniquement l'UI (vues + ViewModels) et le cache d'images.
// La logique (pagination, cache hors ligne de la liste) vient du module KMP feature:catalog, via le framework Shared.
// L'app de démo (Demo/) est une cible Xcode déclarée dans iosApp/project.yml.
let package = Package(
    name: "FeatureCatalog",
    platforms: [.iOS(.v16)],
    products: [
        .library(name: "FeatureCatalog", targets: ["FeatureCatalog"]),
    ],
    dependencies: [
        .package(path: "../SharedKMP"),
        .package(path: "../DesignSystemUI"),
    ],
    targets: [
        .target(
            name: "FeatureCatalog",
            dependencies: [
                .product(name: "Shared", package: "SharedKMP"),
                .product(name: "DesignSystemUI", package: "DesignSystemUI"),
            ]
        ),
    ]
)

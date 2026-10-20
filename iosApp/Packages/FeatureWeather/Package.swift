// swift-tools-version: 5.9
import PackageDescription

// Feature "météo" côté iOS : uniquement l'UI (vues + ViewModel).
// La logique vient du module KMP feature:weather, via le framework Shared.
// L'app de démo (Demo/) est une cible Xcode déclarée dans iosApp/project.yml.
let package = Package(
    name: "FeatureWeather",
    platforms: [.iOS(.v16)],
    products: [
        .library(name: "FeatureWeather", targets: ["FeatureWeather"]),
    ],
    dependencies: [
        .package(path: "../SharedKMP"),
        .package(path: "../DesignSystemUI"),
    ],
    targets: [
        .target(
            name: "FeatureWeather",
            dependencies: [
                .product(name: "Shared", package: "SharedKMP"),
                .product(name: "DesignSystemUI", package: "DesignSystemUI"),
            ]
        ),
    ]
)

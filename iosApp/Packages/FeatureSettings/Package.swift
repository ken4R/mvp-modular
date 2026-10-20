// swift-tools-version: 5.9
import PackageDescription

// Feature "réglages" côté iOS : uniquement l'UI (vues + ViewModel).
// La logique vient du module KMP feature:settings, via le framework Shared.
// L'app de démo (Demo/) est une cible Xcode déclarée dans iosApp/project.yml.
let package = Package(
    name: "FeatureSettings",
    platforms: [.iOS(.v16)],
    products: [
        .library(name: "FeatureSettings", targets: ["FeatureSettings"]),
    ],
    dependencies: [
        .package(path: "../SharedKMP"),
        .package(path: "../DesignSystemUI"),
    ],
    targets: [
        .target(
            name: "FeatureSettings",
            dependencies: [
                .product(name: "Shared", package: "SharedKMP"),
                .product(name: "DesignSystemUI", package: "DesignSystemUI"),
            ]
        ),
    ]
)

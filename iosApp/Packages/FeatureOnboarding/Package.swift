// swift-tools-version: 5.9
import PackageDescription

// Feature "présentation du premier lancement" côté iOS : uniquement l'UI (vues + ViewModel).
// La logique vient du module KMP feature:onboarding (et feature:settings pour les choix), via le framework Shared.
// L'app de démo (Demo/) est une cible Xcode déclarée dans iosApp/project.yml.
let package = Package(
    name: "FeatureOnboarding",
    platforms: [.iOS(.v16)],
    products: [
        .library(name: "FeatureOnboarding", targets: ["FeatureOnboarding"]),
    ],
    dependencies: [
        .package(path: "../SharedKMP"),
        .package(path: "../DesignSystemUI"),
    ],
    targets: [
        .target(
            name: "FeatureOnboarding",
            dependencies: [
                .product(name: "Shared", package: "SharedKMP"),
                .product(name: "DesignSystemUI", package: "DesignSystemUI"),
            ]
        ),
    ]
)

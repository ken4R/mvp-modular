// swift-tools-version: 5.9
import PackageDescription

// Feature "connexion" côté iOS : uniquement l'UI (écran de connexion, aiguillage selon la session).
// La logique vient du module KMP feature:auth, via le framework Shared.
// L'app de démo (Demo/) est une cible Xcode déclarée dans iosApp/project.yml.
let package = Package(
    name: "FeatureAuth",
    platforms: [.iOS(.v16)],
    products: [
        .library(name: "FeatureAuth", targets: ["FeatureAuth"]),
    ],
    dependencies: [
        .package(path: "../SharedKMP"),
        .package(path: "../DesignSystemUI"),
    ],
    targets: [
        .target(
            name: "FeatureAuth",
            dependencies: [
                .product(name: "Shared", package: "SharedKMP"),
                .product(name: "DesignSystemUI", package: "DesignSystemUI"),
            ]
        ),
    ]
)

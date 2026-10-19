// swift-tools-version: 5.9
import PackageDescription

// Feature "todo" côté iOS : uniquement l'UI (vues + ViewModel).
// La logique vient du module KMP feature:todo, via le framework Shared.
// L'app de démo (Demo/) est une cible Xcode déclarée dans iosApp/project.yml.
let package = Package(
    name: "FeatureTodo",
    platforms: [.iOS(.v16)],
    products: [
        .library(name: "FeatureTodo", targets: ["FeatureTodo"]),
    ],
    dependencies: [
        .package(path: "../SharedKMP"),
        .package(path: "../DesignSystemUI"),
    ],
    targets: [
        .target(
            name: "FeatureTodo",
            dependencies: [
                .product(name: "Shared", package: "SharedKMP"),
                .product(name: "DesignSystemUI", package: "DesignSystemUI"),
            ]
        ),
    ]
)

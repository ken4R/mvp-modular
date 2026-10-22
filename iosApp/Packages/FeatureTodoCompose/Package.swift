// swift-tools-version: 5.9
import PackageDescription

// Écran Tâches en Compose Multiplatform côté iOS : uniquement l'enveloppe SwiftUI.
// L'écran (UI comprise) vient du module KMP feature:todo-compose, via le framework Shared (ADR 0010).
// L'app de démo (Demo/) est une cible Xcode déclarée dans iosApp/project.yml.
let package = Package(
    name: "FeatureTodoCompose",
    platforms: [.iOS(.v16)],
    products: [
        .library(name: "FeatureTodoCompose", targets: ["FeatureTodoCompose"]),
    ],
    dependencies: [
        .package(path: "../SharedKMP"),
    ],
    targets: [
        .target(
            name: "FeatureTodoCompose",
            dependencies: [
                .product(name: "Shared", package: "SharedKMP"),
            ]
        ),
    ]
)

// swift-tools-version: 5.9
import PackageDescription

// Pont SwiftUI vers les tokens du module KMP core:designsystem, partagé par toutes les features.
let package = Package(
    name: "DesignSystemUI",
    platforms: [.iOS(.v16)],
    products: [
        .library(name: "DesignSystemUI", targets: ["DesignSystemUI"]),
    ],
    dependencies: [
        .package(path: "../SharedKMP"),
    ],
    targets: [
        .target(
            name: "DesignSystemUI",
            dependencies: [.product(name: "Shared", package: "SharedKMP")]
        ),
    ]
)

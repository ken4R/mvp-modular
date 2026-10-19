// swift-tools-version: 5.9
import PackageDescription

// Framework Kotlin Multiplatform "Shared" (toute la logique métier), exposé aux packages Swift.
// Le XCFramework est produit par `scripts/build-ios-framework.sh` et n'est pas versionné.
let package = Package(
    name: "SharedKMP",
    platforms: [.iOS(.v16)],
    products: [
        .library(name: "Shared", targets: ["Shared"]),
    ],
    targets: [
        .binaryTarget(name: "Shared", path: "Shared.xcframework"),
    ]
)

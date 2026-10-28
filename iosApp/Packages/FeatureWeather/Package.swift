// swift-tools-version: 5.9
import PackageDescription

// Feature "météo" côté iOS : uniquement l'UI (vues + ViewModel).
// La logique vient du module KMP feature:weather, via le framework Shared.
// L'app de démo (Demo/) est une cible Xcode déclarée dans iosApp/project.yml.
// Widget Météo (ADR 0026) : FeatureWeather recopie l'état calculé par KMP dans un fichier de l'App Group
// (WeatherWidgetPublisher) ; WeatherWidgetData (app + extension, sans dépendance) en définit le format. La vue du
// widget (Widget/) est compilée dans l'extension TodoWidgetExtension (project.yml), qui ne charge pas le framework Kotlin.
let package = Package(
    name: "FeatureWeather",
    platforms: [.iOS(.v16)],
    products: [
        .library(name: "FeatureWeather", targets: ["FeatureWeather"]),
        .library(name: "WeatherWidgetData", targets: ["WeatherWidgetData"]),
    ],
    dependencies: [
        .package(path: "../SharedKMP"),
        .package(path: "../DesignSystemUI"),
    ],
    targets: [
        .target(name: "WeatherWidgetData"),
        .target(
            name: "FeatureWeather",
            dependencies: [
                "WeatherWidgetData",
                .product(name: "Shared", package: "SharedKMP"),
                .product(name: "DesignSystemUI", package: "DesignSystemUI"),
            ]
        ),
    ]
)

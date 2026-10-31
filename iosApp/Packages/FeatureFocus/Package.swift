// swift-tools-version: 5.9
import PackageDescription

// Mode focus (Pomodoro) sur une tâche, côté iOS (ADR 0028) : uniquement l'UI. Le compte à rebours et les règles
// viennent du module KMP feature:focus, via le framework Shared.
// - FeatureFocus (app) : écran Focus, bandeau de la liste, et recopie de la session dans la Live Activity.
// - FocusActivityData (app + extension, sans dépendance) : attributs ActivityKit de la Live Activity. L'extension
//   ne charge pas le framework Kotlin.
// La vue de la Live Activity (Widget/) est compilée dans l'extension TodoWidgetExtension (project.yml).
// Pas d'app de démo : la feature s'ouvre depuis la fiche d'une tâche (FeatureTodo), dans l'app complète.
let package = Package(
    name: "FeatureFocus",
    platforms: [.iOS(.v16)],
    products: [
        .library(name: "FeatureFocus", targets: ["FeatureFocus"]),
        .library(name: "FocusActivityData", targets: ["FocusActivityData"]),
    ],
    dependencies: [
        .package(path: "../SharedKMP"),
        .package(path: "../DesignSystemUI"),
    ],
    targets: [
        .target(name: "FocusActivityData"),
        .target(
            name: "FeatureFocus",
            dependencies: [
                "FocusActivityData",
                .product(name: "Shared", package: "SharedKMP"),
                .product(name: "DesignSystemUI", package: "DesignSystemUI"),
            ]
        ),
    ]
)

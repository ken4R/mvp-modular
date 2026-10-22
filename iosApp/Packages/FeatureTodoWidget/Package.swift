// swift-tools-version: 5.9
import PackageDescription

// Widget Tâches de l'écran d'accueil (WidgetKit, ADR 0011).
// - FeatureTodoWidget (app) : recopie l'état calculé par KMP (feature:todo) dans le fichier partagé
//   avec l'extension, et demande à WidgetKit de redessiner le widget.
// - TodoWidgetData (app + extension) : format et emplacement de ce fichier, sans dépendance. L'extension
//   ne charge pas le framework Kotlin : un widget est limité à environ 30 Mo de mémoire, et il n'a besoin
//   que de ce fichier.
// L'extension elle-même (Widget/) est une cible Xcode déclarée dans iosApp/project.yml.
let package = Package(
    name: "FeatureTodoWidget",
    platforms: [.iOS(.v16)],
    products: [
        .library(name: "FeatureTodoWidget", targets: ["FeatureTodoWidget"]),
        .library(name: "TodoWidgetData", targets: ["TodoWidgetData"]),
    ],
    dependencies: [
        .package(path: "../SharedKMP"),
        .package(path: "../DesignSystemUI"),
    ],
    targets: [
        .target(name: "TodoWidgetData"),
        .target(
            name: "FeatureTodoWidget",
            dependencies: [
                "TodoWidgetData",
                .product(name: "Shared", package: "SharedKMP"),
                .product(name: "DesignSystemUI", package: "DesignSystemUI"),
            ]
        ),
    ]
)

import SwiftUI
import WidgetKit

/// Extension WidgetKit de l'app : widgets Tâches (ADR 0011, cases à cocher ADR 0026) et Météo (ADR 0026). Chaque vue
/// vient du package de sa feature (Packages/<Feature>/Widget) et ne lit que le fichier que l'app lui écrit dans
/// l'App Group : l'extension ne charge pas le framework Kotlin.
@main
struct MvpWidgetsBundle: WidgetBundle {
    var body: some Widget {
        TodoWidget()
        WeatherWidget()
    }
}

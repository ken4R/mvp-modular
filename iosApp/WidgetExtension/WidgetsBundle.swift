import SwiftUI
import WidgetKit

/// Extension WidgetKit de l'app : widgets Tâches (ADR 0011, cases à cocher ADR 0026) et Météo (ADR 0026), et Live
/// Activity du mode focus (ADR 0028, iOS 16.2 et plus). Chaque vue vient du package de sa feature
/// (Packages/<Feature>/Widget) et ne lit que ce que l'app lui donne (fichier de l'App Group, état ActivityKit) :
/// l'extension ne charge pas le framework Kotlin.
@main
struct MvpWidgetsBundle: WidgetBundle {
    var body: some Widget {
        TodoWidget()
        WeatherWidget()
        if #available(iOSApplicationExtension 16.2, *) {
            FocusLiveActivityWidget()
        }
    }
}

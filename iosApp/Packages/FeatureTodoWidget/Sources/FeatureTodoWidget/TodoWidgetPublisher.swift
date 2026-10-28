import Foundation
import SwiftUI
import WidgetKit
import Shared
import DesignSystemUI
import TodoWidgetData

/// Widget Tâches : à démarrer une fois au lancement de l'app. KMP décide de ce que le widget peut montrer
/// (`UseCases.observeTodoWidget`) ; ici, seulement la recopie dans le fichier de l'App Group et la demande
/// de redessin à WidgetKit. Déconnexion ou verrou activé : le fichier ne contient plus aucune tâche, et les cases
/// cochées dans le widget pas encore appliquées sont oubliées.
public enum TodoWidgetPublisher {
    private static var subscription: Subscription?
    private static var lastPublished: TodoWidgetSnapshot?
    private static var applying = false

    public static func start() {
        guard subscription == nil else { return }
        subscription = UseCases.shared.observeTodoWidget(
            onState: { state in
                if !(state is TodoWidgetStateTasks) { TodoWidgetCompletions.clear() }
                publish(snapshot(of: state))
            },
            onError: { _ in publish(TodoWidgetSnapshot(content: .unavailable, palette: palette)) }
        )
        applyWidgetCompletions()
    }

    /// Termine les tâches cochées dans le widget depuis la dernière ouverture (ADR 0026), par le use case partagé :
    /// session, verrou et corbeille y sont vérifiés, la modification part par la file d'envoi. À appeler au lancement
    /// (fait par `start()`) et à chaque retour au premier plan.
    public static func applyWidgetCompletions() {
        let ids = TodoWidgetCompletions.pending()
        guard !applying, !ids.isEmpty else { return }
        applying = true
        UseCases.shared.completeTodosFromWidget(todoIds: ids) {
            // Traitées, réussies ou refusées : oubliées. Une case cochée entre-temps reste pour la prochaine fois.
            TodoWidgetCompletions.remove(ids)
            applying = false
            WidgetCenter.shared.reloadTimelines(ofKind: TodoWidgetStore.widgetKind)
        }
    }

    private static func publish(_ snapshot: TodoWidgetSnapshot) {
        guard snapshot != lastPublished else { return }
        do {
            try TodoWidgetStore.write(snapshot)
            lastPublished = snapshot
        } catch {
            // Écriture impossible : ne pas laisser d'anciennes tâches derrière soi.
            TodoWidgetStore.remove()
            lastPublished = nil
        }
        WidgetCenter.shared.reloadTimelines(ofKind: TodoWidgetStore.widgetKind)
    }

    private static func snapshot(of state: TodoWidgetState) -> TodoWidgetSnapshot {
        guard let tasks = state as? TodoWidgetStateTasks else {
            let content: TodoWidgetSnapshot.Content = state is TodoWidgetStateLocked ? .locked : .signedOut
            return TodoWidgetSnapshot(content: content, palette: palette)
        }
        return TodoWidgetSnapshot(
            content: .tasks,
            remainingCount: Int(tasks.remainingCount),
            items: tasks.items.map { item in
                TodoWidgetSnapshot.Item(
                    id: item.id,
                    title: item.title,
                    reminderAt: item.reminderAt.map { Date(timeIntervalSince1970: Double($0.int64Value) / 1000) },
                    link: URL(string: item.link)
                )
            },
            visibleUntil: Date(timeIntervalSince1970: Double(tasks.visibleUntil) / 1000),
            link: URL(string: tasks.link),
            palette: palette
        )
    }

    private static var palette: TodoWidgetSnapshot.Palette {
        TodoWidgetSnapshot.Palette(primaryLight: DS.colors(.light).primary, primaryDark: DS.colors(.dark).primary)
    }
}

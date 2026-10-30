import SwiftUI
import UserNotifications
import Shared
import FeatureTodo

/// App de démo : lance uniquement la feature todo, avec quelques tâches d'exemple (deux avec une échéance, dont une
/// en retard, pour le calendrier), pour la développer et la tester sans passer par l'app complète.
/// Les notifications y sont activées d'office (après la permission iOS) pour essayer les rappels.
@main
struct TodoDemoApp: App {
    init() {
        KoinIosKt.doInitKoin()
        TodoReminders.start()
        TodoSync.start()
        Task { @MainActor in
            let parts = Calendar(identifier: .gregorian).dateComponents([.year, .month, .day], from: Date())
            let today = CalendarDate(
                year: Int32(parts.year ?? 2026), month: Int32(parts.month ?? 1), day: Int32(parts.day ?? 1)
            ).epochDay
            let samples: [(title: String, dueIn: Int32?)] = [
                ("Acheter du pain", nil), ("Préparer la démo", 2), ("Rendre le rapport", -1),
            ]
            for sample in samples {
                let result = try? await TodoUseCases.shared.addTodo().invoke(params: sample.title as NSString)
                guard let dueIn = sample.dueIn, let todo = (result as? AppResultSuccess<AnyObject>)?.data as? Todo else { continue }
                let dueDate = TodoDueDate(todoId: todo.id, dueDate: KotlinInt(value: today + dueIn))
                _ = try? await TodoUseCases.shared.setTodoDueDate().invoke(params: dueDate)
            }
            let granted = (try? await UNUserNotificationCenter.current()
                .requestAuthorization(options: [.alert, .badge, .sound])) ?? false
            _ = try? await SettingsUseCases.shared.setNotificationsEnabled().invoke(params: KotlinBoolean(bool: granted))
        }
    }

    var body: some Scene {
        WindowGroup {
            TodoListView()
        }
    }
}

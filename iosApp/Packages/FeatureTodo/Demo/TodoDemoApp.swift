import SwiftUI
import UserNotifications
import Shared
import FeatureTodo

/// App de démo : lance uniquement la feature todo, avec quelques tâches d'exemple,
/// pour la développer et la tester sans passer par l'app complète.
/// Les notifications y sont activées d'office (après la permission iOS) pour essayer les rappels.
@main
struct TodoDemoApp: App {
    init() {
        KoinIosKt.doInitKoin()
        TodoReminders.start()
        Task { @MainActor in
            for title in ["Acheter du pain", "Préparer la démo"] {
                _ = try? await UseCases.shared.addTodo().invoke(params: title as NSString)
            }
            let granted = (try? await UNUserNotificationCenter.current()
                .requestAuthorization(options: [.alert, .badge, .sound])) ?? false
            _ = try? await UseCases.shared.setNotificationsEnabled().invoke(params: KotlinBoolean(bool: granted))
        }
    }

    var body: some Scene {
        WindowGroup {
            TodoListView()
        }
    }
}

import SwiftUI
import Shared
import FeatureTodo

/// App de démo : lance uniquement la feature todo, avec quelques tâches d'exemple,
/// pour la développer et la tester sans passer par l'app complète.
@main
struct TodoDemoApp: App {
    init() {
        KoinIosKt.doInitKoin()
        Task { @MainActor in
            for title in ["Acheter du pain", "Préparer la démo"] {
                _ = try? await UseCases.shared.addTodo().invoke(params: title as NSString)
            }
        }
    }

    var body: some Scene {
        WindowGroup {
            TodoListView()
        }
    }
}

import SwiftUI
import Shared
import FeatureTodoCompose

/// App de démo : uniquement l'écran Tâches en Compose Multiplatform, avec quelques tâches d'exemple,
/// à comparer à TodoDemo (le même écran en SwiftUI).
@main
struct TodoComposeDemoApp: App {
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
            TodoComposeView()
        }
    }
}

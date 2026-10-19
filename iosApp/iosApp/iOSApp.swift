import SwiftUI
import Shared
import FeatureTodo

/// App complète : initialise la logique KMP et assemble les features.
@main
struct iOSApp: App {
    init() {
        KoinIosKt.doInitKoin()
    }

    var body: some Scene {
        WindowGroup {
            TodoListView()
        }
    }
}

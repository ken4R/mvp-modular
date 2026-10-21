import SwiftUI
import Shared
import FeatureCatalog

/// App de démo : lance uniquement la feature catalogue (liste paginée, détail, cache hors ligne),
/// pour la développer et la tester sans passer par l'app complète.
@main
struct CatalogDemoApp: App {
    init() {
        KoinIosKt.doInitKoin()
    }

    var body: some Scene {
        WindowGroup {
            CatalogView()
        }
    }
}

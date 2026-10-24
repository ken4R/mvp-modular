import SwiftUI
import Shared
import FeatureSettings

/// App de démo : lance uniquement la feature réglages, pour la développer et la tester
/// sans passer par l'app complète.
@main
struct SettingsDemoApp: App {
    init() {
        KoinIosKt.doInitKoin()
    }

    var body: some Scene {
        WindowGroup {
            // Thème et accent : se changent depuis la section Apparence.
            AppearanceRoot {
                // Verrou Face ID / Touch ID : s'active depuis la section Sécurité.
                AppLockGate {
                    SettingsView()
                }
            }
        }
    }
}

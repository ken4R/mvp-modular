import SwiftUI
import Shared
import DesignSystemUI
import FeatureOnboarding

/// App de démo : lance uniquement la présentation du premier lancement, remontrée à chaque ouverture.
/// Sans connexion : les choix sont écrits dans les réglages de l'appareil, comme pour un compte.
@main
struct OnboardingDemoApp: App {
    init() {
        KoinIosKt.doInitKoin()
    }

    var body: some Scene {
        WindowGroup {
            OnboardingGate {
                OnboardingDemoDone()
            }
            .task { await OnboardingDemoDone.restart() }
        }
    }
}

private struct OnboardingDemoDone: View {
    var body: some View {
        VStack(spacing: DS.Spacing.md) {
            Text("Présentation terminée").font(DS.Typography.titleMedium)
            Button("Revoir la présentation") { Task { await Self.restart() } }
        }
    }

    static func restart() async {
        _ = try? await UseCases.shared.restartOnboarding().invoke(params: NoParams.shared)
    }
}

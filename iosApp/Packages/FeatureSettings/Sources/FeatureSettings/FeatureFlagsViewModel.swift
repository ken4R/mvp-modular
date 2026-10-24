import Foundation
import Shared

/// Feature flags dans Réglages (ADR 0021) : carte de la feature en développement `preview_banner`, et menu
/// « Feature flags (debug) ». Les valeurs et la règle de priorité viennent de KMP.
@MainActor
final class FeatureFlagsViewModel: ObservableObject {
    @Published private(set) var flags: [FeatureFlagState] = []
    @Published private(set) var message: String?
    @Published private(set) var refreshing = false

    private var subscription: Subscription?

    func start() {
        guard subscription == nil else { return }
        subscription = UseCases.shared.observeFeatureFlags(
            onFlags: { [weak self] flags in self?.flags = flags },
            onError: { _ in }
        )
    }

    func stop() {
        subscription?.cancel()
        subscription = nil
    }

    /// Clé du JSON distant plutôt que l'entrée d'enum Kotlin, que Swift renomme.
    func isEnabled(_ key: String) -> Bool {
        flags.first { $0.flag.key == key }?.enabled ?? false
    }

    func setOverride(_ state: FeatureFlagState, enabled: Bool) async {
        let params = SetFeatureFlagOverrideUseCaseParams(flag: state.flag, enabled: KotlinBoolean(bool: enabled))
        let result = try? await UseCases.shared.setFeatureFlagOverride().invoke(params: params)
        message = result is AppResultFailure ? "Réglage réservé aux builds de debug" : nil
    }

    func reset() async {
        _ = try? await UseCases.shared.clearFeatureFlagOverrides().invoke(params: NoParams.shared)
        message = nil
    }

    /// « Rafraîchir le distant » : passe outre le délai de 15 minutes.
    func forceRefresh() async {
        refreshing = true
        let result = try? await UseCases.shared.refreshRemoteFlags().invoke(params: RefreshRemoteFlagsUseCaseParams(force: true))
        message = result is AppResultSuccess
            ? "Flags distants à jour"
            : "Flags distants injoignables ou refusés : dernières valeurs connues gardées"
        refreshing = false
    }

    static func stageLabel(_ state: FeatureFlagState) -> String {
        state.flag.stage.name == "InDevelopment" ? "en développement" : "livrée"
    }

    static func originLabel(_ state: FeatureFlagState) -> String {
        switch state.origin.name {
        case "Remote": return "distant"
        case "Override": return "local"
        case "ReleaseLocked": return "coupée en release"
        default: return "défaut"
        }
    }
}

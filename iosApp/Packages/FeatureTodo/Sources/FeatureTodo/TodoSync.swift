import Foundation
import Shared

/// Mode hors ligne (ADR 0012) et purge de la corbeille (ADR 0025) : à démarrer une fois au lancement de l'app (ou de la démo). La logique (quand
/// envoyer les modifications, réessayer, départager les conflits) est dans KMP ; ici, seulement le démarrage.
public enum TodoSync {
    private static var subscription: Subscription?

    public static func start() {
        guard subscription == nil else { return }
        subscription = UseCases.shared.startTodoSync()
        // Corbeille (ADR 0025) : les tâches jetées depuis plus de 30 jours sont supprimées définitivement.
        Task { @MainActor in _ = try? await UseCases.shared.purgeExpiredTrash().invoke(params: NoParams.shared) }
    }
}

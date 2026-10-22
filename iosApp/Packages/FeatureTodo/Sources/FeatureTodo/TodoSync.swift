import Foundation
import Shared

/// Mode hors ligne (ADR 0012) : à démarrer une fois au lancement de l'app (ou de la démo). La logique (quand
/// envoyer les modifications, réessayer, départager les conflits) est dans KMP ; ici, seulement le démarrage.
public enum TodoSync {
    private static var subscription: Subscription?

    public static func start() {
        guard subscription == nil else { return }
        subscription = UseCases.shared.startTodoSync()
    }
}

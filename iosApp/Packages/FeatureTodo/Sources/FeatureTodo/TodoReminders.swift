import Foundation
import UserNotifications
import Shared

/// Rappels de tâches : à démarrer une fois au lancement de l'app (ou de la démo).
/// La logique (quels rappels programmer, quand les annuler) est dans KMP ; ici, seulement
/// le démarrage et l'affichage des notifications quand l'app est au premier plan.
public enum TodoReminders {
    private static var subscription: Subscription?
    private static let presenter = ForegroundPresenter()

    public static func start() {
        guard subscription == nil else { return }
        // Le centre de notifications ne garde qu'une référence faible vers son délégué.
        UNUserNotificationCenter.current().delegate = presenter
        subscription = UseCases.shared.startTodoReminders()
    }
}

/// Sans délégué, iOS n'affiche pas les notifications quand l'app est ouverte.
private final class ForegroundPresenter: NSObject, UNUserNotificationCenterDelegate {
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .list, .sound])
    }
}

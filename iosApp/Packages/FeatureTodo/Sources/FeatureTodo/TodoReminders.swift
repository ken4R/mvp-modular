import Foundation
import UserNotifications
import Shared

/// Rappels de tâches, à l'heure et par lieu : à démarrer une fois au lancement de l'app (ou de la démo).
/// La logique (quels rappels programmer, quand les annuler) est dans KMP ; ici, seulement
/// le démarrage, l'affichage des notifications quand l'app est au premier plan, et le tap.
public enum TodoReminders {
    private static var subscription: Subscription?
    private static var locationSubscription: Subscription?
    private static let delegate = ReminderNotificationDelegate()

    public static func start() {
        guard subscription == nil else { return }
        // Le centre de notifications ne garde qu'une référence faible vers son délégué.
        // Posé dès le lancement : un tap qui lance l'app est remis à ce délégué.
        UNUserNotificationCenter.current().delegate = delegate
        subscription = TodoUseCases.shared.startTodoReminders()
        // Rappels par lieu (ADR 0023) : iOS déclenche lui-même la notification à l'arrivée, ce délégué gère le tap.
        locationSubscription = TodoUseCases.shared.startTodoLocationReminders()
    }

    /// Relit les permissions de localisation (retour au premier plan) : les rappels par lieu sont reposés ou retirés.
    public static func refreshLocationAvailability() {
        Task { @MainActor in
            _ = try? await TodoUseCases.shared.refreshLocationRemindersAvailability().invoke(params: NoParams.shared)
        }
    }
}

private final class ReminderNotificationDelegate: NSObject, UNUserNotificationCenterDelegate {
    /// Clé posée par core:notifications (iosMain) dans `userInfo`.
    private let deepLinkKey = "deepLink"

    /// Sans délégué, iOS n'affiche pas les notifications quand l'app est ouverte.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .list, .sound])
    }

    /// Tap sur un rappel : son lien (la tâche) est mis en attente dans KMP ; l'app l'affiche
    /// après la connexion et le déverrouillage.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        if response.actionIdentifier == UNNotificationDefaultActionIdentifier,
           let link = response.notification.request.content.userInfo[deepLinkKey] as? String {
            Task { @MainActor in
                _ = try? await DeepLinkUseCases.shared.openDeepLink().invoke(params: link as NSString)
            }
        }
        completionHandler()
    }
}

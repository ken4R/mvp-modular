import EventKit
import EventKitUI
import Shared
import UIKit

/// Export d'une tâche vers le calendrier de l'iPhone (ADR 0022) : l'écran d'ajout d'événement du système, prérempli
/// (titre, journée entière, lien de la tâche). Rien n'est écrit sans la confirmation de l'utilisateur, qui choisit
/// aussi le calendrier. Depuis iOS 17, cet écran tourne hors de l'app et ne demande aucun accès au calendrier ; sur
/// iOS 16, il exige l'accès complet, demandé seulement à ce moment-là.
/// Présenté par UIKit au-dessus de l'app : `PresentedSheets.dismissAll()` le ferme au passage en arrière-plan.
@MainActor
enum CalendarExport {
    private static let store = EKEventStore()
    private static let delegate = EventEditorDelegate()

    /// `nil` si l'écran d'ajout est affiché, sinon le message à afficher.
    static func present(_ event: TodoCalendarEvent) async -> String? {
        if #unavailable(iOS 17.0) {
            let granted = (try? await store.requestAccess(to: .event)) ?? false
            guard granted else { return "Autorisez l'accès au calendrier dans Réglages > Confidentialité" }
        }
        guard let day = event.date.localDate, let presenter = topViewController() else {
            return "Calendrier indisponible"
        }
        let calendarEvent = EKEvent(eventStore: store)
        calendarEvent.title = event.title
        calendarEvent.isAllDay = true
        calendarEvent.startDate = day
        calendarEvent.endDate = day
        calendarEvent.url = URL(string: event.link)
        let editor = EKEventEditViewController()
        editor.eventStore = store
        editor.event = calendarEvent
        editor.editViewDelegate = delegate
        presenter.present(editor, animated: true)
        return nil
    }

    private static func topViewController() -> UIViewController? {
        let window = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first(where: \.isKeyWindow)
        var top = window?.rootViewController
        while let presented = top?.presentedViewController { top = presented }
        return top
    }
}

private final class EventEditorDelegate: NSObject, EKEventEditViewDelegate {
    /// Ajouté ou annulé : l'écran se ferme ; l'app ne lit pas l'événement créé.
    func eventEditViewController(_ controller: EKEventEditViewController, didCompleteWith action: EKEventEditViewAction) {
        controller.dismiss(animated: true)
    }
}

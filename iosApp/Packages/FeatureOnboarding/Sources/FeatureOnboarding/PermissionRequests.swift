import CoreLocation
import Foundation
import LocalAuthentication
import UserNotifications

/// Demandes système de la présentation. Chacune est précédée de son explication (OnboardingTexts, KMP).
enum PermissionRequests {

    /// `true` si iOS autorise les notifications (alerte affichée la première fois seulement).
    static func notifications() async -> Bool {
        (try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .badge, .sound])) ?? false
    }

    /// Face ID / Touch ID ou un code configuré : sans quoi le verrou ne peut pas être activé.
    static var deviceAuthenticationAvailable: Bool {
        LAContext().canEvaluatePolicy(.deviceOwnerAuthentication, error: nil)
    }

    /// Même vérification que l'activation du verrou dans Réglages (FeatureSettings) : la biométrie, avec repli sur
    /// le code, est vérifiée par iOS ; l'app ne reçoit que le résultat.
    @MainActor
    static func authenticate(reason: String) async -> Bool {
        let context = LAContext()
        guard context.canEvaluatePolicy(.deviceOwnerAuthentication, error: nil) else { return false }
        return (try? await context.evaluatePolicy(.deviceOwnerAuthentication, localizedReason: reason)) ?? false
    }
}

/// Autorisation de la position « pendant l'utilisation » (approximative par défaut, NSLocationDefaultAccuracyReduced).
/// Attend la réponse à l'alerte ; ne demande rien si l'utilisateur a déjà répondu.
@MainActor
final class LocationAuthorization: NSObject, CLLocationManagerDelegate {
    private let manager = CLLocationManager()
    private var continuation: CheckedContinuation<Void, Never>?

    func request() async {
        guard manager.authorizationStatus == .notDetermined, continuation == nil else { return }
        await withCheckedContinuation { continuation in
            self.continuation = continuation
            manager.delegate = self
            manager.requestWhenInUseAuthorization()
        }
    }

    // Appelé sur le main thread (gestionnaire créé dessus), aussi dès l'affectation du délégué avec l'état
    // « pas encore demandé » : seule une réponse termine l'attente.
    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        guard manager.authorizationStatus != .notDetermined else { return }
        Task { @MainActor in self.finish() }
    }

    private func finish() {
        continuation?.resume()
        continuation = nil
    }
}

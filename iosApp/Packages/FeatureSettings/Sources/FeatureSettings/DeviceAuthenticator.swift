import Foundation
import LocalAuthentication

enum DeviceAuthResult {
    case success
    case failed
    case unavailable
}

/// Authentification par le système : Face ID / Touch ID, avec repli sur le code de l'iPhone.
/// La vérification est faite par iOS (LocalAuthentication) ; l'app ne reçoit que le résultat.
enum DeviceAuthenticator {

    /// Nom à afficher, selon le capteur de l'appareil.
    static var methodName: String {
        let context = LAContext()
        _ = context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: nil)
        switch context.biometryType {
        case .faceID: return "Face ID"
        case .touchID: return "Touch ID"
        default: return "le code de l'iPhone"
        }
    }

    @MainActor
    static func authenticate(reason: String) async -> DeviceAuthResult {
        let context = LAContext()
        var error: NSError?
        // Aucun code configuré sur l'appareil : ni biométrie ni repli possibles.
        guard context.canEvaluatePolicy(.deviceOwnerAuthentication, error: &error) else { return .unavailable }
        do {
            return try await context.evaluatePolicy(.deviceOwnerAuthentication, localizedReason: reason) ? .success : .failed
        } catch {
            // Annulation, trop d'essais, app passée en arrière-plan : l'écran reste verrouillé.
            return .failed
        }
    }
}

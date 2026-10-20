import Foundation
import UserNotifications
import Shared

@MainActor
final class SettingsViewModel: ObservableObject {
    // Toute modification du formulaire efface le « Profil enregistré » précédent.
    @Published var name: String = "" { didSet { profileMessage = nil } }
    @Published var email: String = "" { didSet { profileMessage = nil } }
    @Published private(set) var profileMessage: String?
    @Published private(set) var notificationsEnabled = false
    @Published private(set) var consents: [Consent] = []
    @Published private(set) var error: String?

    private var subscription: Subscription?
    /// Le formulaire de profil n'est pré-rempli qu'une fois, pour ne pas écraser la saisie en cours.
    private var profileLoaded = false

    func start() {
        guard subscription == nil else { return }
        subscription = UseCases.shared.observeSettings(
            onSettings: { [weak self] settings in self?.apply(settings) },
            onError: { [weak self] error in self?.error = error.userMessage }
        )
    }

    func stop() {
        subscription?.cancel()
        subscription = nil
    }

    func saveProfile() async {
        let profile = UserProfile(displayName: name, email: email)
        let result = try? await UseCases.shared.updateProfile().invoke(params: profile)
        if let saved = (result as? AppResultSuccess<AnyObject>)?.data as? UserProfile {
            name = saved.displayName
            email = saved.email
            profileMessage = "Profil enregistré"
            error = nil
        } else {
            profileMessage = nil
            error = (result as? AppResultFailure)?.error.userMessage ?? "Erreur inattendue"
        }
    }

    /// Activer demande d'abord la permission iOS ; la couper ne change que la préférence de l'app.
    func setNotifications(_ enabled: Bool) async {
        if enabled {
            let granted = (try? await UNUserNotificationCenter.current()
                .requestAuthorization(options: [.alert, .badge, .sound])) ?? false
            guard granted else {
                error = "Autorisez les notifications dans Réglages iOS"
                return
            }
        }
        let result = try? await UseCases.shared.setNotificationsEnabled().invoke(params: KotlinBoolean(bool: enabled))
        if let failure = result as? AppResultFailure { error = failure.error.userMessage }
    }

    func setConsent(_ type: ConsentType, granted: Bool) async {
        let result = try? await UseCases.shared.setConsent().invoke(params: Consent(type: type, granted: granted))
        if let failure = result as? AppResultFailure { error = failure.error.userMessage }
    }

    private func apply(_ settings: UserSettings) {
        if !profileLoaded {
            name = settings.profile.displayName
            email = settings.profile.email
            profileLoaded = true
        }
        notificationsEnabled = settings.notificationsEnabled
        consents = settings.consents
    }
}

private extension AppError {
    var userMessage: String {
        switch self {
        case let validation as AppErrorValidation: return validation.message
        case is AppErrorNotFound: return "Réglage introuvable"
        case is AppErrorNetwork: return "Problème de connexion"
        case is AppErrorUnauthorized: return "Accès refusé"
        case let unknown as AppErrorUnknown: return unknown.message ?? "Erreur inattendue"
        default: return "Erreur inattendue"
        }
    }
}

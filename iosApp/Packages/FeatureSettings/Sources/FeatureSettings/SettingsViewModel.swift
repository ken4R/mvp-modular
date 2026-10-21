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
    @Published private(set) var appLockEnabled = false
    @Published private(set) var consents: [Consent] = []
    @Published private(set) var accountEmail: String?
    @Published private(set) var isLoggingOut = false
    @Published private(set) var error: String?

    private var subscription: Subscription?
    private var sessionSubscription: Subscription?
    /// Le formulaire de profil n'est pré-rempli qu'une fois, pour ne pas écraser la saisie en cours.
    private var profileLoaded = false

    func start() {
        guard subscription == nil else { return }
        subscription = UseCases.shared.observeSettings(
            onSettings: { [weak self] settings in self?.apply(settings) },
            onError: { [weak self] error in self?.error = error.userMessage }
        )
        sessionSubscription = UseCases.shared.observeSession(
            onAccount: { [weak self] account in self?.accountEmail = account?.email },
            onError: { _ in }
        )
    }

    func stop() {
        subscription?.cancel()
        subscription = nil
        sessionSubscription?.cancel()
        sessionSubscription = nil
    }

    /// Efface les données locales puis la session ; l'app revient alors sur l'écran de connexion.
    func logout() async {
        guard !isLoggingOut else { return }
        isLoggingOut = true
        defer { isLoggingOut = false }
        guard let result = try? await UseCases.shared.logout().invoke(params: NoParams.shared) else {
            error = "Erreur inattendue"
            return
        }
        if let failure = result as? AppResultFailure { error = failure.error.userMessage } else { error = nil }
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
        await saveNotifications(enabled)
    }

    /// Au retour dans l'app : si les notifications ont été refusées dans Réglages iOS, on aligne le réglage.
    func syncNotificationPermission() async {
        let status = await UNUserNotificationCenter.current().notificationSettings().authorizationStatus
        if status == .denied && notificationsEnabled {
            await saveNotifications(false)
        }
    }

    private func saveNotifications(_ enabled: Bool) async {
        let result = try? await UseCases.shared.setNotificationsEnabled().invoke(params: KotlinBoolean(bool: enabled))
        if let failure = result as? AppResultFailure { error = failure.error.userMessage } else { error = nil }
    }

    /// Activer comme couper demande Face ID / Touch ID (ou le code) : quelqu'un qui trouve l'app ouverte
    /// ne peut pas retirer le verrou, et à l'activation on vérifie que ça marche sur l'appareil.
    func setAppLock(_ enabled: Bool) async {
        let reason = enabled
            ? "Activer le déverrouillage de l'app par \(DeviceAuthenticator.methodName)"
            : "Couper le déverrouillage de l'app"
        switch await DeviceAuthenticator.authenticate(reason: reason) {
        case .success: break
        case .failed: return
        // Plus aucun code sur l'iPhone : le retirer demandait déjà l'ancien code, on laisse couper le verrou.
        case .unavailable where !enabled: break
        case .unavailable:
            error = "Configurez Face ID, Touch ID ou un code dans Réglages iOS"
            return
        }
        let result = try? await UseCases.shared.setAppLockEnabled().invoke(params: KotlinBoolean(bool: enabled))
        if let failure = result as? AppResultFailure { error = failure.error.userMessage } else { error = nil }
    }

    func setConsent(_ type: ConsentType, granted: Bool) async {
        let result = try? await UseCases.shared.setConsent().invoke(params: Consent(type: type, granted: granted))
        if let failure = result as? AppResultFailure { error = failure.error.userMessage } else { error = nil }
    }

    private func apply(_ settings: UserSettings) {
        if !profileLoaded {
            name = settings.profile.displayName
            email = settings.profile.email
            profileLoaded = true
        }
        notificationsEnabled = settings.notificationsEnabled
        appLockEnabled = settings.appLockEnabled
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
        default: return "Erreur inattendue"
        }
    }
}

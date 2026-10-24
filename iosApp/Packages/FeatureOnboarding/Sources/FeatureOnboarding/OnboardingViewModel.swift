import Foundation
import Shared

/// Étapes de la présentation (OnboardingStep, KMP). L'export renomme les entrées des enums Kotlin :
/// les cas Swift portent les noms Kotlin en `rawValue`.
enum OnboardingPage: String {
    case Welcome, Notifications, Location, AppLock, Analytics, CrashReports

    /// Consentement demandé par cette étape (même nom Kotlin que l'étape), `nil` pour les autres.
    var consentType: ConsentType? {
        switch self {
        case .Analytics, .CrashReports: return KoinIosKt.consentType(name: rawValue)
        case .Welcome, .Notifications, .Location, .AppLock: return nil
        }
    }
}

@MainActor
final class OnboardingViewModel: ObservableObject {
    @Published private(set) var steps: [OnboardingStep] = []
    @Published private(set) var index = 0
    /// Une demande ou un enregistrement est en cours : les boutons sont désactivés.
    @Published private(set) var busy = false
    @Published private(set) var error: String?
    /// Terminée et enregistrée : OnboardingGate va afficher l'app, plus rien ne doit être enregistré d'ici là.
    @Published private(set) var finished = false

    var enabled: Bool { !busy && !finished }

    private let location = LocationAuthorization()

    var step: OnboardingStep? { steps.indices.contains(index) ? steps[index] : nil }
    var page: OnboardingPage? { step.flatMap { OnboardingPage(rawValue: $0.name) } }

    /// Le verrou n'est proposé que si l'appareil a Face ID, Touch ID ou un code.
    func load() async {
        guard steps.isEmpty else { return }
        let device = OnboardingDevice(appLockAvailable: PermissionRequests.deviceAuthenticationAvailable)
        let result = try? await UseCases.shared.getOnboardingSteps().invoke(params: device)
        steps = ((result as? AppResultSuccess<AnyObject>)?.data as? [OnboardingStep]) ?? []
        // Illisibles (ne devrait pas arriver) : rien à montrer, la présentation se termine.
        if steps.isEmpty { await finish() }
    }

    func accept() async {
        guard let page, enabled else { return }
        busy = true
        defer { busy = false }
        error = nil
        switch page {
        case .Welcome:
            await advance()
        case .Notifications:
            // Refusées : on passe à la suite, elles s'activent plus tard depuis Réglages.
            if await PermissionRequests.notifications() {
                await save(try? await UseCases.shared.setNotificationsEnabled().invoke(params: KotlinBoolean(bool: true)))
            } else {
                await advance()
            }
        case .Location:
            // Refusée, la météo reste disponible par la recherche d'une ville.
            await location.request()
            await advance()
        case .AppLock:
            // Plus de code sur l'iPhone (retiré entre-temps) : rien à activer, on passe à la suite.
            guard PermissionRequests.deviceAuthenticationAvailable else { return await advance() }
            // Annulé : l'utilisateur reste sur l'étape, il peut réessayer ou choisir « Plus tard ».
            guard await PermissionRequests.authenticate(reason: "Activer le déverrouillage de l'app") else { return }
            await save(try? await UseCases.shared.setAppLockEnabled().invoke(params: KotlinBoolean(bool: true)))
        case .Analytics, .CrashReports:
            await saveConsent(page, granted: true)
        }
    }

    func decline() async {
        guard let page, enabled else { return }
        busy = true
        defer { busy = false }
        error = nil
        if page.consentType != nil { await saveConsent(page, granted: false) } else { await advance() }
    }

    /// « Passer » : la présentation n'est plus montrée sur cet appareil. Possible aussi pendant une demande système
    /// qui ne répond pas (localisation), pour ne jamais rester bloqué sur une étape.
    func skip() async {
        guard !finished else { return }
        error = nil
        await finish()
    }

    /// Étapes vides et enregistrement raté : seul « Réessayer » est affiché.
    func retry() async {
        guard enabled else { return }
        busy = true
        defer { busy = false }
        error = nil
        if steps.isEmpty { await load() } else { await finish() }
    }

    /// Accepté ou refusé explicitement : le choix est daté avec la version du texte affiché (ConsentTexts, KMP).
    private func saveConsent(_ page: OnboardingPage, granted: Bool) async {
        guard let type = page.consentType else { return await advance() }
        let choice = ConsentChoice(type: type, granted: granted)
        await save(try? await UseCases.shared.setConsent().invoke(params: choice))
    }

    private func save(_ result: AnyObject?) async {
        if result == nil || result is AppResultFailure {
            error = "Impossible d'enregistrer, réessayez"
        } else {
            await advance()
        }
    }

    private func advance() async {
        if index >= steps.count - 1 { await finish() } else { index += 1 }
    }

    /// Une fois enregistré, OnboardingGate affiche l'app.
    private func finish() async {
        let result = try? await UseCases.shared.completeOnboarding().invoke(params: NoParams.shared)
        if result == nil || result is AppResultFailure {
            error = "Impossible d'enregistrer, réessayez"
        } else {
            finished = true
        }
    }
}

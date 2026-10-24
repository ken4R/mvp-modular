import SwiftUI
import Shared
import DesignSystemUI

/// Verrou Face ID / Touch ID : tant que l'app est verrouillée, le contenu n'est pas construit du tout.
/// Verrouillée à chaque lancement et à chaque passage en arrière-plan, si le réglage est activé.
public struct AppLockGate<Content: View>: View {
    @StateObject private var viewModel = AppLockViewModel()
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.dsAccent) private var accent
    private let content: () -> Content

    public init(@ViewBuilder content: @escaping () -> Content) {
        self.content = content
    }

    public var body: some View {
        let colors = DS.colors(colorScheme, accent)
        ZStack {
            switch viewModel.state {
            case .unknown:
                Color(argb: colors.background).ignoresSafeArea()
            case .locked:
                LockView(viewModel: viewModel)
            case .unlocked:
                content()
            }
            // Aperçu du sélecteur d'apps : capturé quand la scène n'est plus active, il ne montre rien.
            if viewModel.enabled && scenePhase != .active {
                Color(argb: colors.background).ignoresSafeArea()
            }
        }
        .onAppear {
            viewModel.start()
            viewModel.setSceneActive(scenePhase == .active)
        }
        .onChange(of: scenePhase) { phase in
            if phase == .background { viewModel.lock() }
            viewModel.setSceneActive(phase == .active)
        }
    }
}

private struct LockView: View {
    @ObservedObject var viewModel: AppLockViewModel
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.dsAccent) private var accent

    var body: some View {
        let colors = DS.colors(colorScheme, accent)
        VStack(spacing: DS.Spacing.md) {
            Spacer()
            Text("App verrouillée")
                .font(DS.Typography.titleLarge)
                .foregroundColor(Color(argb: colors.onBackground))
            Text("Utilisez \(DeviceAuthenticator.methodName) pour continuer.")
                .font(DS.Typography.body)
                .foregroundColor(Color(argb: colors.onBackground))
                .multilineTextAlignment(.center)
            if let message = viewModel.message {
                Text(message)
                    .font(DS.Typography.caption)
                    .foregroundColor(Color(argb: colors.error))
                    .multilineTextAlignment(.center)
            }
            Button {
                Task { await viewModel.unlock() }
            } label: {
                Text("Déverrouiller").font(DS.Typography.label)
            }
            .buttonStyle(.borderedProminent)
            .tint(Color(argb: colors.primary))
            .disabled(viewModel.isAuthenticating)
            Button("Se déconnecter") { Task { await viewModel.logout() } }
                .font(DS.Typography.label)
                .foregroundColor(Color(argb: colors.error))
                .disabled(viewModel.isLoggingOut)
            Spacer()
        }
        .padding(DS.Spacing.lg)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(argb: colors.background))
    }
}

@MainActor
final class AppLockViewModel: ObservableObject {
    enum State {
        /// Pas encore lu : rien n'est affiché.
        case unknown
        case locked
        case unlocked
    }

    @Published private(set) var state: State = .unknown
    @Published private(set) var enabled = false
    @Published private(set) var message: String?
    @Published private(set) var isAuthenticating = false
    @Published private(set) var isLoggingOut = false

    private var lockSubscription: Subscription?
    private var settingsSubscription: Subscription?
    private var sceneActive = false
    /// Une seule ouverture automatique de Face ID par verrouillage : après une annulation, c'est le bouton.
    private var autoPromptPending = true

    func start() {
        guard lockSubscription == nil else { return }
        lockSubscription = UseCases.shared.observeAppLock(
            onLocked: { [weak self] locked in self?.apply(locked: locked.boolValue) },
            // Verrou illisible : l'app reste verrouillée, la déconnexion reste possible.
            onError: { [weak self] _ in self?.apply(locked: true) }
        )
        settingsSubscription = UseCases.shared.observeSettings(
            onSettings: { [weak self] settings in self?.enabled = settings.appLockEnabled },
            onError: { _ in }
        )
    }

    func setSceneActive(_ active: Bool) {
        sceneActive = active
        promptIfNeeded()
    }

    /// L'app passe en arrière-plan : il faudra se ré-authentifier au retour.
    func lock() {
        autoPromptPending = true
        // Masqué tout de suite, sans attendre le use case : rien ne réapparaît si l'app est suspendue entre-temps.
        if enabled { state = .locked }
        Task { _ = try? await UseCases.shared.lockApp().invoke(params: NoParams.shared) }
    }

    func unlock() async {
        guard !isAuthenticating else { return }
        isAuthenticating = true
        defer { isAuthenticating = false }
        switch await DeviceAuthenticator.authenticate(reason: "Déverrouiller l'app pour accéder à vos données") {
        case .success:
            let result = try? await UseCases.shared.unlockApp().invoke(params: NoParams.shared)
            if result == nil || result is AppResultFailure {
                message = "Déverrouillage impossible, réessayez"
            } else {
                apply(locked: false)
            }
        case .failed:
            message = nil
        case .unavailable:
            message = "Aucun code n'est configuré sur cet iPhone. Déconnectez-vous puis reconnectez-vous."
        }
    }

    /// Repli si l'authentification est impossible : efface les données de l'appareil puis revient à la connexion.
    func logout() async {
        guard !isLoggingOut else { return }
        isLoggingOut = true
        defer { isLoggingOut = false }
        let result = try? await UseCases.shared.logout().invoke(params: NoParams.shared)
        if result == nil || result is AppResultFailure { message = "Déconnexion impossible, réessayez" }
    }

    private func apply(locked: Bool) {
        state = locked ? .locked : .unlocked
        if !locked { message = nil }
        promptIfNeeded()
    }

    private func promptIfNeeded() {
        guard state == .locked, sceneActive, autoPromptPending, !isAuthenticating else { return }
        autoPromptPending = false
        Task { await unlock() }
    }
}

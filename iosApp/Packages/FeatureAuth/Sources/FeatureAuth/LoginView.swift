import SwiftUI
import Shared
import DesignSystemUI

struct LoginView: View {
    private enum Field { case email, password }

    @StateObject private var viewModel = LoginViewModel()
    @Environment(\.colorScheme) private var colorScheme
    @FocusState private var focusedField: Field?

    var body: some View {
        let colors = DS.colors(colorScheme)
        ScrollView {
            VStack(alignment: .leading, spacing: DS.Spacing.md) {
                Text("Connexion")
                    .font(DS.Typography.titleLarge)
                    .foregroundColor(Color(argb: colors.onBackground))
                Text("Prototype sans serveur : n'importe quel e-mail, mot de passe « demo1234 ».")
                    .font(DS.Typography.caption)
                    .foregroundColor(Color(argb: colors.onBackground))

                TextField("E-mail", text: $viewModel.email)
                    .textContentType(.username)
                    .keyboardType(.emailAddress)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .textFieldStyle(.roundedBorder)
                    .focused($focusedField, equals: .email)
                    .submitLabel(.next)
                    .onSubmit { focusedField = .password }
                SecureField("Mot de passe", text: $viewModel.password)
                    .textContentType(.password)
                    .textFieldStyle(.roundedBorder)
                    .focused($focusedField, equals: .password)
                    .submitLabel(.go)
                    .onSubmit(login)

                if let error = viewModel.error {
                    Text(error)
                        .font(DS.Typography.caption)
                        .foregroundColor(Color(argb: colors.error))
                }

                Button(action: login) {
                    HStack(spacing: DS.Spacing.sm) {
                        if viewModel.isLoading {
                            ProgressView().tint(Color(argb: colors.onPrimary))
                        }
                        Text("Se connecter").font(DS.Typography.label)
                    }
                    .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .tint(Color(argb: colors.primary))
                .disabled(viewModel.isLoading)
            }
            .padding(DS.Spacing.lg)
        }
        .scrollDismissesKeyboard(.immediately)
        .background(Color(argb: colors.background))
    }

    /// Ferme le clavier avant d'envoyer, pour que le tap suivant atteigne le formulaire.
    private func login() {
        focusedField = nil
        Task { await viewModel.login() }
    }
}

@MainActor
final class LoginViewModel: ObservableObject {
    // Toute nouvelle saisie efface l'erreur précédente.
    @Published var email = "" { didSet { error = nil } }
    @Published var password = "" { didSet { error = nil } }
    @Published private(set) var isLoading = false
    @Published private(set) var error: String?

    func login() async {
        guard !isLoading else { return }
        isLoading = true
        defer { isLoading = false }
        let result = try? await UseCases.shared.login().invoke(params: Credentials(email: email, password: password))
        if result is AppResultSuccess<AnyObject> {
            // La session prend le relais (SessionGate) ; le mot de passe ne reste pas en mémoire.
            password = ""
            return
        }
        let failure = (result as? AppResultFailure)?.error
        if failure is AppErrorUnauthorized { password = "" }
        error = failure?.loginMessage ?? "Erreur inattendue"
    }
}

private extension AppError {
    var loginMessage: String {
        switch self {
        case let validation as AppErrorValidation: return validation.message
        case is AppErrorUnauthorized: return "E-mail ou mot de passe incorrect"
        case is AppErrorNetwork: return "Problème de connexion"
        case is AppErrorNotFound: return "Compte introuvable"
        default: return "Erreur inattendue"
        }
    }
}

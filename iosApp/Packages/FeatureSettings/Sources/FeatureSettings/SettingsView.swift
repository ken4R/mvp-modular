import SwiftUI
import UIKit
import Shared
import DesignSystemUI

public struct SettingsView: View {
    @StateObject private var viewModel = SettingsViewModel()
    @Environment(\.colorScheme) private var colorScheme
    @FocusState private var profileFocused: Bool

    public init() {}

    public var body: some View {
        let colors = DS.colors(colorScheme)
        Form {
            Section {
                Text("Réglages")
                    .font(DS.Typography.titleLarge)
                    .foregroundColor(Color(argb: colors.onBackground))
                    .listRowBackground(Color(argb: colors.background))
                if let error = viewModel.error {
                    Text(error)
                        .font(DS.Typography.caption)
                        .foregroundColor(Color(argb: colors.error))
                        .listRowBackground(Color(argb: colors.background))
                }
            }

            Section(header: header("Profil", colors)) {
                TextField("Nom", text: $viewModel.name)
                    .textContentType(.name)
                    .focused($profileFocused)
                TextField("E-mail (facultatif)", text: $viewModel.email)
                    .textContentType(.emailAddress)
                    .keyboardType(.emailAddress)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .focused($profileFocused)
                HStack(spacing: DS.Spacing.sm) {
                    Button("Enregistrer", action: saveProfile)
                        .tint(Color(argb: colors.primary))
                    if let message = viewModel.profileMessage {
                        Text(message)
                            .font(DS.Typography.caption)
                            .foregroundColor(Color(argb: colors.onSurface))
                    }
                }
            }
            .listRowBackground(Color(argb: colors.surface))

            Section(header: header("Notifications", colors)) {
                Toggle(isOn: Binding(
                    get: { viewModel.notificationsEnabled },
                    set: { enabled in Task { await viewModel.setNotifications(enabled) } }
                )) {
                    rowText("Recevoir des notifications", subtitle: nil, colors)
                }
                .tint(Color(argb: colors.primary))
            }
            .listRowBackground(Color(argb: colors.surface))

            Section(header: header("Consentements", colors)) {
                ForEach(viewModel.consents, id: \.type.name) { consent in
                    let texts = ConsentTexts(consent.type)
                    Toggle(isOn: Binding(
                        get: { consent.granted },
                        set: { granted in Task { await viewModel.setConsent(consent.type, granted: granted) } }
                    )) {
                        rowText(texts.title, subtitle: texts.subtitle, colors)
                    }
                    .tint(Color(argb: colors.primary))
                }
            }
            .listRowBackground(Color(argb: colors.surface))

            Section(header: header("Compte", colors)) {
                if let email = viewModel.accountEmail {
                    Text("Connecté avec \(email)")
                        .font(DS.Typography.body)
                        .foregroundColor(Color(argb: colors.onSurface))
                }
                Text("La déconnexion efface les tâches, le profil et les réglages de cet appareil.")
                    .font(DS.Typography.caption)
                    .foregroundColor(Color(argb: colors.onSurface))
                Button("Se déconnecter") { Task { await viewModel.logout() } }
                    .foregroundColor(Color(argb: colors.error))
                    .disabled(viewModel.isLoggingOut)
            }
            .listRowBackground(Color(argb: colors.surface))

            Section(header: header("À propos", colors)) {
                HStack {
                    Text("Version")
                    Spacer()
                    Text(Self.appVersion)
                }
                .font(DS.Typography.body)
                .foregroundColor(Color(argb: colors.onSurface))
            }
            .listRowBackground(Color(argb: colors.surface))
        }
        .scrollContentBackground(.hidden)
        .scrollDismissesKeyboard(.immediately)
        .background(Color(argb: colors.background))
        .onAppear { viewModel.start() }
        .task { await viewModel.syncNotificationPermission() }
        // L'utilisateur peut couper les notifications dans Réglages iOS pendant que l'app est en arrière-plan.
        .onReceive(NotificationCenter.default.publisher(for: UIApplication.willEnterForegroundNotification)) { _ in
            Task { await viewModel.syncNotificationPermission() }
        }
        .onDisappear { viewModel.stop() }
    }

    /// Ferme le clavier avant d'enregistrer, pour que le tap suivant atteigne le formulaire.
    private func saveProfile() {
        profileFocused = false
        Task { await viewModel.saveProfile() }
    }

    private func header(_ title: String, _ colors: ColorTokens) -> some View {
        Text(title)
            .font(DS.Typography.label)
            .foregroundColor(Color(argb: colors.primary))
    }

    private func rowText(_ title: String, subtitle: String?, _ colors: ColorTokens) -> some View {
        VStack(alignment: .leading, spacing: DS.Spacing.xs) {
            Text(title)
                .font(DS.Typography.body)
                .foregroundColor(Color(argb: colors.onSurface))
            if let subtitle {
                Text(subtitle)
                    .font(DS.Typography.caption)
                    .foregroundColor(Color(argb: colors.onSurface))
            }
        }
    }

    private static var appVersion: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "-"
    }
}

/// Titre et explication d'un consentement KMP.
private struct ConsentTexts {
    let title: String
    let subtitle: String

    init(_ type: ConsentType) {
        let texts: (title: String, subtitle: String)
        // Comparaison sur le nom Kotlin de l'entrée : l'export Swift renomme les cas de l'enum.
        switch type.name {
        case "Analytics": texts = ("Mesure d'audience", "Statistiques anonymes pour améliorer l'app")
        case "Marketing": texts = ("Communications marketing", "Offres et nouveautés par e-mail ou notification")
        case "Personalization": texts = ("Personnalisation", "Contenus adaptés à votre utilisation")
        default: texts = (type.name, "")
        }
        title = texts.title
        subtitle = texts.subtitle
    }
}

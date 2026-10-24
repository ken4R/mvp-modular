import SwiftUI
import UIKit
import Shared
import DesignSystemUI

public struct SettingsView: View {
    @StateObject private var viewModel = SettingsViewModel()
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.dsAccent) private var accent
    @FocusState private var profileFocused: Bool

    public init() {}

    public var body: some View {
        let colors = DS.colors(colorScheme, accent)
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

            Section(header: header("Apparence", colors)) {
                VStack(alignment: .leading, spacing: DS.Spacing.sm) {
                    rowText("Thème", subtitle: nil, colors)
                    Picker("Thème", selection: Binding(
                        get: { viewModel.themeMode },
                        set: { mode in Task { await viewModel.setThemeMode(mode) } }
                    )) {
                        ForEach(DSThemeMode.allCases) { mode in
                            Text(mode.label).tag(mode)
                        }
                    }
                    .pickerStyle(.segmented)
                }
                VStack(alignment: .leading, spacing: DS.Spacing.sm) {
                    rowText("Couleur d'accent", subtitle: nil, colors)
                    HStack(spacing: DS.Spacing.sm) {
                        ForEach(DSAccent.allCases) { option in
                            accentSwatch(option, selected: option == viewModel.accent, colors)
                        }
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

            Section(header: header("Sécurité", colors)) {
                Toggle(isOn: Binding(
                    get: { viewModel.appLockEnabled },
                    set: { enabled in Task { await viewModel.setAppLock(enabled) } }
                )) {
                    rowText(
                        "Déverrouiller avec \(DeviceAuthenticator.methodName)",
                        subtitle: "Demandé à l'ouverture et au retour dans l'app",
                        colors
                    )
                }
                .tint(Color(argb: colors.primary))
            }
            .listRowBackground(Color(argb: colors.surface))

            Section(header: header("Consentements", colors)) {
                ForEach(viewModel.consents, id: \.type.name) { consent in
                    // Textes et versions communs aux deux apps (KMP) : l'accord porte sur ce texte précis.
                    let text = ConsentTexts.shared.of(type: consent.type)
                    Toggle(isOn: Binding(
                        get: { consent.granted },
                        set: { granted in Task { await viewModel.setConsent(consent.type, granted: granted) } }
                    )) {
                        rowText(text.title, subtitle: "\(text.summary)\n\(Self.decisionLabel(consent))", colors)
                    }
                    .tint(Color(argb: colors.primary))
                }
            }
            .listRowBackground(Color(argb: colors.surface))

            #if DEBUG
            if let journal = viewModel.analyticsJournal {
                analyticsJournalSection(journal, colors)
            }
            #endif

            Section(header: header("Compte", colors)) {
                if let email = viewModel.accountEmail {
                    Text("Connecté avec \(email)")
                        .font(DS.Typography.body)
                        .foregroundColor(Color(argb: colors.onSurface))
                }
                Text("La déconnexion efface les tâches, leurs rappels, le profil et les réglages de cet appareil (sauf l'apparence).")
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

    /// Pastille d'un accent, dans sa couleur pour le thème affiché ; l'accent choisi est entouré.
    private func accentSwatch(_ option: DSAccent, selected: Bool, _ colors: ColorTokens) -> some View {
        Button {
            Task { await viewModel.setAccent(option) }
        } label: {
            Circle()
                .fill(Color(argb: DS.colors(colorScheme, option).primary))
                .padding(DS.Size.selectionBorder + DS.Spacing.xs)
                .overlay(
                    Circle().strokeBorder(
                        Color(argb: selected ? colors.onSurface : colors.surface),
                        lineWidth: DS.Size.selectionBorder
                    )
                )
                .frame(width: DS.Size.accentSwatch, height: DS.Size.accentSwatch)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(option.label)
        .accessibilityAddTraits(selected ? [.isButton, .isSelected] : .isButton)
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

    /// Date du dernier choix, ou rien de choisi : le consentement est alors refusé.
    private static func decisionLabel(_ consent: Consent) -> String {
        guard let decidedAt = consent.decidedAt?.int64Value else { return "Pas encore choisi : refusé par défaut" }
        let date = Date(timeIntervalSince1970: TimeInterval(decidedAt) / 1000)
        let version = consent.textVersion.map { " (texte v\($0.intValue))" } ?? ""
        return "\(consent.granted ? "Accepté" : "Refusé") le \(date.formatted(date: .abbreviated, time: .shortened))\(version)"
    }

    /// Journal de mesure (builds de debug) : ce qui attend sur l'appareil et ce qui a été « envoyé ».
    /// Pas encore d'outil de mesure : les envois restent en mémoire sur l'appareil.
    private func analyticsJournalSection(_ journal: AnalyticsJournal, _ colors: ColorTokens) -> some View {
        Section(header: header("Journal de mesure (debug)", colors)) {
            Text("Rien n'est gardé sans le consentement Mesure d'audience. Envoyé par lots, ou quand l'app passe en arrière-plan.")
                .font(DS.Typography.caption)
                .foregroundColor(Color(argb: colors.onSurface))
            Button("Événement test") { Task { await viewModel.sendTestEvent() } }
                .tint(Color(argb: colors.primary))
            Button("Envoyer maintenant") { Task { await viewModel.flushAnalytics() } }
                .tint(Color(argb: colors.primary))
                .disabled(journal.pending.isEmpty)
            journalRows("En attente", journal.pending, colors)
            journalRows("Envoyés", journal.sent, colors)
        }
        .listRowBackground(Color(argb: colors.surface))
    }

    @ViewBuilder
    private func journalRows(_ title: String, _ events: [TrackedEvent], _ colors: ColorTokens) -> some View {
        Text("\(title) : \(events.count)")
            .font(DS.Typography.body)
            .foregroundColor(Color(argb: colors.onSurface))
        ForEach(events, id: \.id) { event in
            Text(Self.describe(event))
                .font(DS.Typography.caption)
                .foregroundColor(Color(argb: colors.onSurface))
        }
    }

    private static func describe(_ event: TrackedEvent) -> String {
        let date = Date(timeIntervalSince1970: TimeInterval(event.timestamp) / 1000)
        let properties = event.properties.sorted { $0.key < $1.key }.map { "  \($0.key)=\($0.value)" }.joined()
        return "\(date.formatted(date: .omitted, time: .standard))  \(event.name)\(properties)"
    }

    private static var appVersion: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "-"
    }
}

import SwiftUI
import UIKit
import Shared
import DesignSystemUI

public struct SettingsView: View {
    @StateObject private var viewModel = SettingsViewModel()
    @StateObject private var flags = FeatureFlagsViewModel()
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

            #if DEBUG
            // Feature en développement (ADR 0021) : absente du code d'un build release.
            if flags.isEnabled("preview_banner") {
                Section {
                    VStack(alignment: .leading, spacing: DS.Spacing.xs) {
                        Text("Bientôt")
                            .font(DS.Typography.titleMedium)
                            .foregroundColor(Color(argb: colors.onSurface))
                        Text("Une nouvelle fonction en préparation, visible seulement dans les builds de debug.")
                            .font(DS.Typography.caption)
                            .foregroundColor(Color(argb: colors.onSurface))
                    }
                }
                .listRowBackground(Color(argb: colors.surface))
            }
            #endif

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
            crashJournalSection(viewModel.crashJournal, colors)
            featureFlagsSection(colors)
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
                Button("Revoir la présentation") { Task { await viewModel.restartOnboarding() } }
                    .tint(Color(argb: colors.primary))
            }
            .listRowBackground(Color(argb: colors.surface))
        }
        .scrollContentBackground(.hidden)
        .scrollDismissesKeyboard(.immediately)
        .background(Color(argb: colors.background))
        .onAppear {
            viewModel.start()
            flags.start()
        }
        .task { await viewModel.syncNotificationPermission() }
        // L'utilisateur peut couper les notifications dans Réglages iOS pendant que l'app est en arrière-plan.
        .onReceive(NotificationCenter.default.publisher(for: UIApplication.willEnterForegroundNotification)) { _ in
            Task { await viewModel.syncNotificationPermission() }
        }
        .onDisappear {
            viewModel.stop()
            flags.stop()
        }
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

    /// Menu « Feature flags (debug) » : priorité réglage local, puis distant, puis valeur par défaut (ADR 0021).
    private func featureFlagsSection(_ colors: ColorTokens) -> some View {
        Section(header: header("Feature flags (debug)", colors)) {
            Text("Priorité : réglage local, puis valeur distante, puis valeur par défaut. Une feature en développement n'est jamais visible en release.")
                .font(DS.Typography.caption)
                .foregroundColor(Color(argb: colors.onSurface))
            ForEach(flags.flags, id: \.flag.key) { state in
                Toggle(isOn: Binding(
                    get: { state.enabled },
                    set: { enabled in Task { await flags.setOverride(state, enabled: enabled) } }
                )) {
                    rowText(
                        state.flag.label,
                        subtitle: "\(state.flag.key) · \(FeatureFlagsViewModel.stageLabel(state)) · \(FeatureFlagsViewModel.originLabel(state))",
                        colors
                    )
                }
                .tint(Color(argb: colors.primary))
            }
            if let message = flags.message {
                Text(message)
                    .font(DS.Typography.caption)
                    .foregroundColor(Color(argb: colors.onSurface))
            }
            Button("Réinitialiser") { Task { await flags.reset() } }
                .tint(Color(argb: colors.primary))
            Button("Rafraîchir le distant") { Task { await flags.forceRefresh() } }
                .tint(Color(argb: colors.primary))
                .disabled(flags.refreshing)
        }
        .listRowBackground(Color(argb: colors.surface))
    }

    /// Rapports de plantage (builds de debug). Pas encore d'outil de suivi : les rapports « envoyés » restent en
    /// mémoire sur l'appareil. Le bouton fait vraiment planter l'app (ADR 0020).
    private func crashJournalSection(_ reports: [CrashReport], _ colors: ColorTokens) -> some View {
        Section(header: header("Rapports de plantage (debug)", colors)) {
            Text("Rien n'est enregistré sans le consentement Rapports de plantage. Le rapport part au lancement suivant.")
                .font(DS.Typography.caption)
                .foregroundColor(Color(argb: colors.onSurface))
            Button("Provoquer un plantage") { viewModel.crashForTest() }
                .tint(Color(argb: colors.error))
            Text("Envoyés : \(reports.count)")
                .font(DS.Typography.body)
                .foregroundColor(Color(argb: colors.onSurface))
            ForEach(reports, id: \.id) { report in
                Text(Self.describe(report))
                    .font(DS.Typography.caption)
                    .foregroundColor(Color(argb: colors.onSurface))
            }
        }
        .listRowBackground(Color(argb: colors.surface))
    }

    private static func describe(_ report: CrashReport) -> String {
        let date = Date(timeIntervalSince1970: TimeInterval(report.occurredAt) / 1000)
        let first = report.exceptions.first
        let frame = first?.frames.first.map { "\n\($0)" } ?? ""
        return "\(date.formatted(date: .abbreviated, time: .standard))  \(first?.type ?? "?")\(frame)"
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

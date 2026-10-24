import SwiftUI
import Shared
import DesignSystemUI

/// Une étape par écran, chaque autorisation expliquée avant que iOS ne la demande (textes communs, KMP).
/// Toutes les étapes se remettent à plus tard, et « Passer » termine tout de suite.
struct OnboardingView: View {
    @StateObject private var viewModel = OnboardingViewModel()
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.dsAccent) private var accent

    var body: some View {
        let colors = DS.colors(colorScheme, accent)
        Group {
            if let step = viewModel.step, let page = viewModel.page {
                content(step: step, page: page, colors)
            } else if let error = viewModel.error {
                // Rien à montrer et l'enregistrement a échoué : sans ce bouton, l'écran resterait vide.
                VStack(spacing: DS.Spacing.md) {
                    Text(error)
                        .font(DS.Typography.body)
                        .foregroundColor(Color(argb: colors.error))
                    Button("Réessayer") { Task { await viewModel.retry() } }
                        .tint(Color(argb: colors.primary))
                        .disabled(!viewModel.enabled)
                }
                .padding(DS.Spacing.lg)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Color(argb: colors.background))
            } else {
                Color(argb: colors.background).ignoresSafeArea()
            }
        }
        .task { await viewModel.load() }
    }

    private func content(step: OnboardingStep, page: OnboardingPage, _ colors: ColorTokens) -> some View {
        let text = OnboardingTexts.shared.of(step: step)
        return VStack(alignment: .leading, spacing: DS.Spacing.md) {
            HStack {
                Text("Étape \(viewModel.index + 1) sur \(viewModel.steps.count)")
                    .font(DS.Typography.caption)
                    .foregroundColor(Color(argb: colors.onBackground))
                Spacer()
                Button(OnboardingTexts.shared.skipLabel) { Task { await viewModel.skip() } }
                    .font(DS.Typography.label)
                    .foregroundColor(Color(argb: colors.primary))
                    // Actif pendant une demande système : on ne reste jamais bloqué sur une étape.
                    .disabled(viewModel.finished)
            }
            ScrollView {
                VStack(alignment: .leading, spacing: DS.Spacing.md) {
                    Text(text.title)
                        .font(DS.Typography.titleLarge)
                        .foregroundColor(Color(argb: colors.primary))
                        .accessibilityAddTraits(.isHeader)
                    Text(text.body)
                        .font(DS.Typography.body)
                        .foregroundColor(Color(argb: colors.onBackground))
                    // L'accord porte sur ce texte précis (versionné), le même que dans Réglages > Consentements.
                    if let type = page.consentType {
                        Text(ConsentTexts.shared.of(type: type).summary)
                            .font(DS.Typography.caption)
                            .foregroundColor(Color(argb: colors.onBackground))
                    }
                    if let error = viewModel.error {
                        Text(error)
                            .font(DS.Typography.caption)
                            .foregroundColor(Color(argb: colors.error))
                    }
                }
                .padding(.top, DS.Spacing.lg)
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            // Consentement : accepter et refuser ont le même poids visuel (refuser doit être aussi simple qu'accepter).
            if page.consentType != nil {
                actionButton(text.acceptLabel, prominent: false, colors) { await viewModel.accept() }
            } else {
                actionButton(text.acceptLabel, prominent: true, colors) { await viewModel.accept() }
            }
            if let decline = text.declineLabel {
                actionButton(decline, prominent: false, colors) { await viewModel.decline() }
            }
        }
        .padding(DS.Spacing.lg)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(argb: colors.background))
    }

    @ViewBuilder
    private func actionButton(
        _ label: String,
        prominent: Bool,
        _ colors: ColorTokens,
        action: @escaping () async -> Void
    ) -> some View {
        let button = Button { Task { await action() } } label: {
            Text(label).font(DS.Typography.label).frame(maxWidth: .infinity)
        }
        .controlSize(.large)
        .tint(Color(argb: colors.primary))
        .disabled(!viewModel.enabled)
        if prominent {
            button.buttonStyle(.borderedProminent)
        } else {
            button.buttonStyle(.bordered)
        }
    }
}

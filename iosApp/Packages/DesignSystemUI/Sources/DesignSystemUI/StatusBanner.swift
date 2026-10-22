import SwiftUI
import Shared

/// Bandeau d'état du réseau ou de la synchronisation (mode hors ligne, ADR 0012), avec une action facultative.
public struct StatusBanner: View {
    @Environment(\.colorScheme) private var colorScheme
    private let message: String
    private let actionTitle: String?
    private let action: () -> Void

    public init(_ message: String, actionTitle: String? = nil, action: @escaping () -> Void = {}) {
        self.message = message
        self.actionTitle = actionTitle
        self.action = action
    }

    public var body: some View {
        let colors = DS.colors(colorScheme)
        HStack(spacing: DS.Spacing.sm) {
            Text(message)
                .font(DS.Typography.caption)
                .foregroundColor(Color(argb: colors.onSecondary))
                .frame(maxWidth: .infinity, alignment: .leading)
            if let actionTitle {
                Button(actionTitle, action: action)
                    .font(DS.Typography.label)
                    .foregroundColor(Color(argb: colors.onSecondary))
                    .buttonStyle(.borderless)
            }
        }
        .padding(.horizontal, DS.Spacing.md)
        .padding(.vertical, DS.Spacing.sm)
        .background(Color(argb: colors.secondary), in: RoundedRectangle(cornerRadius: DS.Radius.md))
    }
}

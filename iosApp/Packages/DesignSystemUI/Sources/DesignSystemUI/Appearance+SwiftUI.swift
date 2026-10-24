import SwiftUI
import UIKit
import Shared

/// Couleur d'accent choisie dans Réglages (ADR 0015). Les noms sont ceux des entrées de l'enum Kotlin
/// `AccentColor` (rawValue) : l'export vers Swift renomme les entrées, on passe donc par le nom.
public enum DSAccent: String, CaseIterable, Identifiable {
    case Blue, Teal, Violet, Orange, Pink

    public var id: String { rawValue }

    /// Accent Kotlin de même nom (bleu si le nom n'existe plus).
    public var kotlin: AccentColor { AppearanceIosKt.accentColor(name: rawValue) }

    public init(_ accent: AccentColor) {
        self = DSAccent(rawValue: accent.name) ?? .Blue
    }

    public var label: String {
        switch self {
        case .Blue: return "Bleu"
        case .Teal: return "Sarcelle"
        case .Violet: return "Violet"
        case .Orange: return "Orange"
        case .Pink: return "Rose"
        }
    }
}

/// Thème choisi dans Réglages, par le nom de l'entrée de l'enum Kotlin `ThemeMode`.
public enum DSThemeMode: String, CaseIterable, Identifiable {
    case System, Light, Dark

    public var id: String { rawValue }

    public var kotlin: ThemeMode { AppearanceIosKt.themeMode(name: rawValue) }

    public init(_ mode: ThemeMode) {
        self = DSThemeMode(rawValue: mode.name) ?? .System
    }

    /// Style imposé aux fenêtres : `.unspecified` suit le thème de l'appareil.
    var interfaceStyle: UIUserInterfaceStyle {
        switch self {
        case .System: return .unspecified
        case .Light: return .light
        case .Dark: return .dark
        }
    }

    public var label: String {
        switch self {
        case .System: return "Appareil"
        case .Light: return "Clair"
        case .Dark: return "Sombre"
        }
    }
}

private struct DSAccentKey: EnvironmentKey {
    static let defaultValue: DSAccent = .Blue
}

extension EnvironmentValues {
    /// Accent courant, posé à la racine de l'app : les vues le passent à `DS.colors(colorScheme, accent)`.
    public var dsAccent: DSAccent {
        get { self[DSAccentKey.self] }
        set { self[DSAccentKey.self] = newValue }
    }
}

extension View {
    /// Applique le thème et l'accent à toute l'app : style imposé aux fenêtres (feuilles et écran Compose
    /// compris), accent dans l'environnement, et teinte des contrôles système (onglets, boutons).
    public func dsAppearance(themeMode: DSThemeMode, accent: DSAccent) -> some View {
        modifier(DSAppearanceModifier(themeMode: themeMode, accent: accent))
    }
}

private struct DSAppearanceModifier: ViewModifier {
    let themeMode: DSThemeMode
    let accent: DSAccent
    /// Thème effectif : celui des fenêtres, donc déjà le thème forcé s'il y en a un.
    @Environment(\.colorScheme) private var colorScheme

    func body(content: Content) -> some View {
        content
            .environment(\.dsAccent, accent)
            .tint(Color(argb: DS.colors(colorScheme, accent).primary))
            .onAppear { apply(themeMode) }
            .onChange(of: themeMode) { mode in apply(mode) }
    }

    /// Plutôt que `.preferredColorScheme` : revenir au thème de l'appareil (`nil`) n'y est pas fiable,
    /// et le style des fenêtres vaut aussi pour les feuilles et les écrans UIKit / Compose.
    private func apply(_ mode: DSThemeMode) {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .forEach { $0.overrideUserInterfaceStyle = mode.interfaceStyle }
    }
}

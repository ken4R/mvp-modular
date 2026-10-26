import SwiftUI
import Shared

/// Pont SwiftUI vers les tokens du module KMP core:designsystem.
/// Les vues iOS n'utilisent aucune valeur visuelle en dur.
public enum DS {
    private static let tokens = DesignSystem.shared

    /// Palette du thème [scheme] avec l'accent choisi dans Réglages (`@Environment(\.dsAccent)`).
    public static func colors(_ scheme: ColorScheme, _ accent: DSAccent = .Blue) -> ColorTokens {
        tokens.colors(darkTheme: scheme == .dark, accent: accent.kotlin)
    }

    public enum Spacing {
        public static var xs: CGFloat { CGFloat(tokens.spacing.xs) }
        public static var sm: CGFloat { CGFloat(tokens.spacing.sm) }
        public static var md: CGFloat { CGFloat(tokens.spacing.md) }
        public static var lg: CGFloat { CGFloat(tokens.spacing.lg) }
        public static var xl: CGFloat { CGFloat(tokens.spacing.xl) }
    }

    public enum Radius {
        public static var sm: CGFloat { CGFloat(tokens.radius.sm) }
        public static var md: CGFloat { CGFloat(tokens.radius.md) }
        public static var lg: CGFloat { CGFloat(tokens.radius.lg) }
    }

    public enum Size {
        public static var thumbnail: CGFloat { CGFloat(tokens.sizes.thumbnail) }
        public static var heroImage: CGFloat { CGFloat(tokens.sizes.heroImage) }
        public static var accentSwatch: CGFloat { CGFloat(tokens.sizes.accentSwatch) }
        public static var selectionBorder: CGFloat { CGFloat(tokens.sizes.selectionBorder) }
        public static var chart: CGFloat { CGFloat(tokens.sizes.chart) }
        public static var calendarDay: CGFloat { CGFloat(tokens.sizes.calendarDay) }
    }

    public enum Typography {
        public static var titleLarge: Font { tokens.typography.titleLarge.font }
        public static var titleMedium: Font { tokens.typography.titleMedium.font }
        public static var body: Font { tokens.typography.body.font }
        public static var label: Font { tokens.typography.label.font }
        public static var caption: Font { tokens.typography.caption.font }
    }
}

extension Color {
    /// Couleur à partir d'un token ARGB 0xAARRGGBB.
    public init(argb: Int64) {
        self.init(
            .sRGB,
            red: Double((argb >> 16) & 0xFF) / 255,
            green: Double((argb >> 8) & 0xFF) / 255,
            blue: Double(argb & 0xFF) / 255,
            opacity: Double((argb >> 24) & 0xFF) / 255
        )
    }
}

extension TextStyleToken {
    public var font: Font {
        // On s'appuie sur la valeur numérique (400, 500...) plutôt que sur les noms
        // des cas de l'enum, que l'export Kotlin vers Swift renomme.
        let weight: Font.Weight
        switch self.weight.value {
        case 700...: weight = .bold
        case 600..<700: weight = .semibold
        case 500..<600: weight = .medium
        default: weight = .regular
        }
        return .system(size: CGFloat(size), weight: weight)
    }
}

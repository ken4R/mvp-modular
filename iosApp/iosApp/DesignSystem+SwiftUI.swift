import SwiftUI
import Shared

/// Pont SwiftUI vers les tokens du module KMP core:designsystem.
/// Les vues iOS n'utilisent aucune valeur visuelle en dur.
enum DS {
    private static let tokens = DesignSystem.shared

    static func colors(_ scheme: ColorScheme) -> ColorTokens {
        tokens.colors(darkTheme: scheme == .dark)
    }

    enum Spacing {
        static var xs: CGFloat { CGFloat(tokens.spacing.xs) }
        static var sm: CGFloat { CGFloat(tokens.spacing.sm) }
        static var md: CGFloat { CGFloat(tokens.spacing.md) }
        static var lg: CGFloat { CGFloat(tokens.spacing.lg) }
        static var xl: CGFloat { CGFloat(tokens.spacing.xl) }
    }

    enum Typography {
        static var titleLarge: Font { tokens.typography.titleLarge.font }
        static var titleMedium: Font { tokens.typography.titleMedium.font }
        static var body: Font { tokens.typography.body.font }
        static var label: Font { tokens.typography.label.font }
        static var caption: Font { tokens.typography.caption.font }
    }
}

extension Color {
    /// Couleur à partir d'un token ARGB 0xAARRGGBB.
    init(argb: Int64) {
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
    var font: Font {
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

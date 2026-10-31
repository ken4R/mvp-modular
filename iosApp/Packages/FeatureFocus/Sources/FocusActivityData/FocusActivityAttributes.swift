import ActivityKit
import Foundation
import SwiftUI

/// Live Activity du mode focus (ADR 0028) : écran verrouillé et Dynamic Island. L'app la tient à jour depuis la
/// session KMP (FocusLiveActivity) ; l'extension l'affiche sans charger le framework Kotlin. Le compte à rebours
/// est celui du système (`Text(timerInterval:)`) : rien à mettre à jour chaque seconde.
@available(iOS 16.1, *)
public struct FocusActivityAttributes: ActivityAttributes {
    public enum Status: String, Codable, Hashable {
        case running, paused, finished
    }

    public struct ContentState: Codable, Hashable {
        /// Titre de la tâche, ou un titre neutre si le verrou Face ID est activé (décidé par KMP).
        public var title: String
        public var phaseLabel: String
        public var status: Status
        /// Début (pauses déduites) et fin de la phase, seulement en cours.
        public var phaseStart: Date?
        public var phaseEnd: Date?
        /// Temps restant figé (« 12:34 »), affiché en pause.
        public var remainingLabel: String
        public var nextPhaseLabel: String
        public var isBreak: Bool

        public init(
            title: String,
            phaseLabel: String,
            status: Status,
            phaseStart: Date?,
            phaseEnd: Date?,
            remainingLabel: String,
            nextPhaseLabel: String,
            isBreak: Bool
        ) {
            self.title = title
            self.phaseLabel = phaseLabel
            self.status = status
            self.phaseStart = phaseStart
            self.phaseEnd = phaseEnd
            self.remainingLabel = remainingLabel
            self.nextPhaseLabel = nextPhaseLabel
            self.isBreak = isBreak
        }
    }

    /// Couleur principale du design system (ARGB), clair et sombre : DesignSystemUI n'est pas dans l'extension.
    public var primaryLight: Int64
    public var primaryDark: Int64

    public init(primaryLight: Int64, primaryDark: Int64) {
        self.primaryLight = primaryLight
        self.primaryDark = primaryDark
    }

    public func primary(_ scheme: ColorScheme) -> Color {
        let argb = scheme == .dark ? primaryDark : primaryLight
        // Même conversion que DesignSystemUI (Color(argb:)).
        return Color(
            .sRGB,
            red: Double((argb >> 16) & 0xFF) / 255,
            green: Double((argb >> 8) & 0xFF) / 255,
            blue: Double(argb & 0xFF) / 255,
            opacity: Double((argb >> 24) & 0xFF) / 255
        )
    }
}

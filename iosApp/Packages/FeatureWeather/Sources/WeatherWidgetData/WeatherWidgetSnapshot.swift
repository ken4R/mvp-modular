import Foundation
import SwiftUI

/// Ce que le widget Météo affiche, écrit par l'app et lu par l'extension WidgetKit (ADR 0026). Le contenu vient de
/// KMP (`WeatherWidgetState`) : sans session valide, ou avec une météo de plus de 3 h, aucune météo n'y est écrite.
/// La position n'y figure jamais.
public struct WeatherWidgetSnapshot: Codable, Equatable {

    public enum Content: String, Codable {
        /// Pas de session valide.
        case signedOut
        /// Pas de météo récente : ouvrir l'onglet Météo.
        case noData
        /// Erreur de lecture côté app : rien à montrer.
        case unavailable
        /// Météo à montrer.
        case weather
    }

    /// Couleurs du design system (ARGB 0xAARRGGBB, core:designsystem), recopiées par l'app :
    /// l'extension ne charge pas le framework Kotlin qui les contient.
    public struct Palette: Codable, Equatable {
        public let primaryLight: Int64
        public let primaryDark: Int64

        public init(primaryLight: Int64, primaryDark: Int64) {
            self.primaryLight = primaryLight
            self.primaryDark = primaryDark
        }

        public func primary(_ scheme: ColorScheme) -> Color {
            let argb = scheme == .dark ? primaryDark : primaryLight
            // Même conversion que DesignSystemUI (Color(argb:)), qui n'est pas disponible dans l'extension.
            return Color(
                .sRGB,
                red: Double((argb >> 16) & 0xFF) / 255,
                green: Double((argb >> 8) & 0xFF) / 255,
                blue: Double(argb & 0xFF) / 255,
                opacity: Double((argb >> 24) & 0xFF) / 255
            )
        }
    }

    /// Format du fichier : un fichier d'une autre version est ignoré (le widget invite à ouvrir l'app).
    public static let currentVersion = 1

    public var version: Int
    public var content: Content
    /// Température arrondie au degré.
    public var temperatureCelsius: Int
    /// Libellé et icône SF Symbols de la condition, choisis par l'app (mêmes que l'écran Météo).
    public var conditionLabel: String
    public var symbolName: String
    /// Heure de la mesure.
    public var updatedAt: Date?
    /// Au-delà, plus aucune météo affichée, même si l'app n'a pas été rouverte (météo trop vieille, fin de session).
    public var visibleUntil: Date?
    /// Lien profond vers l'onglet Météo.
    public var link: URL?
    public var palette: Palette?

    public init(
        content: Content,
        temperatureCelsius: Int = 0,
        conditionLabel: String = "",
        symbolName: String = "",
        updatedAt: Date? = nil,
        visibleUntil: Date? = nil,
        link: URL? = nil,
        palette: Palette? = nil
    ) {
        self.version = Self.currentVersion
        self.content = content
        self.temperatureCelsius = temperatureCelsius
        self.conditionLabel = conditionLabel
        self.symbolName = symbolName
        self.updatedAt = updatedAt
        self.visibleUntil = visibleUntil
        self.link = link
        self.palette = palette
    }

    /// Ce qui peut être montré à la date `now` : après `visibleUntil`, plus de météo.
    public func displayed(at now: Date) -> WeatherWidgetSnapshot {
        guard content == .weather else { return self }
        if let end = visibleUntil, now < end { return self }
        return WeatherWidgetSnapshot(content: .noData, palette: palette)
    }
}

/// Fichier partagé entre l'app et l'extension, dans le conteneur de l'App Group.
public enum WeatherWidgetStore {
    /// Déclaré dans les entitlements de l'app et de l'extension (iosApp/project.yml).
    public static let appGroup = "group.com.mvp.ios"
    /// Identifiant du widget, pour `WidgetCenter.reloadTimelines(ofKind:)`.
    public static let widgetKind = "WeatherWidget"

    /// `Library/Caches` du conteneur partagé : jamais sauvegardé. `nil` sans App Group (build non signé de la CI).
    private static var fileURL: URL? {
        FileManager.default
            .containerURL(forSecurityApplicationGroupIdentifier: appGroup)?
            .appendingPathComponent("Library/Caches", isDirectory: true)
            .appendingPathComponent("weather-widget.json")
    }

    public static func read() -> WeatherWidgetSnapshot? {
        guard let url = fileURL, let data = try? Data(contentsOf: url) else { return nil }
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .millisecondsSince1970
        guard let snapshot = try? decoder.decode(WeatherWidgetSnapshot.self, from: data),
              snapshot.version == WeatherWidgetSnapshot.currentVersion else { return nil }
        return snapshot
    }

    public static func write(_ snapshot: WeatherWidgetSnapshot) throws {
        guard var url = fileURL else { throw CocoaError(.fileNoSuchFile) }
        try FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .millisecondsSince1970
        // Même protection que le fichier du widget Tâches : lisible après le premier déverrouillage, écriture atomique.
        try encoder.encode(snapshot).write(to: url, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        try url.setResourceValues(values)
    }

    public static func remove() {
        guard let url = fileURL else { return }
        try? FileManager.default.removeItem(at: url)
    }
}

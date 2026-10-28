import Foundation
import SwiftUI

/// Ce que le widget Tâches affiche, écrit par l'app et lu par l'extension WidgetKit.
/// Le contenu vient de KMP (`TodoWidgetState`) : sans session valide ou avec le verrou Face ID activé,
/// aucune tâche n'y est écrite. Voir docs/adr/0011.
public struct TodoWidgetSnapshot: Codable, Equatable {

    public enum Content: String, Codable {
        /// Pas de session valide.
        case signedOut
        /// Déverrouillage Face ID / Touch ID activé dans Réglages.
        case locked
        /// Erreur de lecture côté app : rien à montrer.
        case unavailable
        /// Tâches à faire.
        case tasks
    }

    public struct Item: Codable, Equatable, Identifiable {
        public let id: String
        public let title: String
        public let reminderAt: Date?
        /// Lien profond vers la tâche (format de core:deeplink).
        public let link: URL?

        public init(id: String, title: String, reminderAt: Date?, link: URL?) {
            self.id = id
            self.title = title
            self.reminderAt = reminderAt
            self.link = link
        }
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
            Self.color(argb: scheme == .dark ? primaryDark : primaryLight)
        }

        // Même conversion que DesignSystemUI (Color(argb:)), qui n'est pas disponible dans l'extension.
        private static func color(argb: Int64) -> Color {
            Color(
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
    /// Nombre de tâches non terminées ; seules les premières sont dans `items`.
    public var remainingCount: Int
    public var items: [Item]
    /// Fin de la session : au-delà, le widget n'affiche plus aucune tâche, même si l'app n'a pas été rouverte.
    public var visibleUntil: Date?
    /// Lien profond vers l'onglet Tâches.
    public var link: URL?
    public var palette: Palette?

    public init(
        content: Content,
        remainingCount: Int = 0,
        items: [Item] = [],
        visibleUntil: Date? = nil,
        link: URL? = nil,
        palette: Palette? = nil
    ) {
        self.version = Self.currentVersion
        self.content = content
        self.remainingCount = remainingCount
        self.items = items
        self.visibleUntil = visibleUntil
        self.link = link
        self.palette = palette
    }

    /// Ce qui peut être montré à la date `now` : après la fin de session, plus aucune tâche.
    public func displayed(at now: Date) -> TodoWidgetSnapshot {
        guard content == .tasks else { return self }
        if let end = visibleUntil, now < end { return self }
        return TodoWidgetSnapshot(content: .signedOut, palette: palette)
    }
}

/// Fichier partagé entre l'app et l'extension, dans le conteneur de l'App Group.
public enum TodoWidgetStore {
    /// Déclaré dans les entitlements de l'app et de l'extension (iosApp/project.yml).
    public static let appGroup = "group.com.mvp.ios"
    /// Identifiant du widget, pour `WidgetCenter.reloadTimelines(ofKind:)`.
    public static let widgetKind = "TodoWidget"

    private static let fileName = "todo-widget.json"

    public static func read() -> TodoWidgetSnapshot? {
        guard let snapshot = SharedFile.read(TodoWidgetSnapshot.self, named: fileName),
              snapshot.version == TodoWidgetSnapshot.currentVersion else { return nil }
        return snapshot
    }

    public static func write(_ snapshot: TodoWidgetSnapshot) throws {
        try SharedFile.write(snapshot, named: fileName)
    }

    /// Efface le fichier (il ne reste alors rien des tâches hors de l'app).
    public static func remove() {
        SharedFile.remove(named: fileName)
    }
}

/// Cases cochées dans le widget (iOS 17+, ADR 0026), pas encore appliquées par l'app. L'extension n'a pas accès à la
/// base des tâches : elle note ici l'id de la tâche, et l'app la termine par le use case partagé à sa prochaine
/// ouverture (puis efface la note). Seulement des ids, jamais de titre.
public enum TodoWidgetCompletions {
    private static let fileName = "todo-widget-completions.json"

    private struct Pending: Codable {
        var ids: [String]
    }

    /// Ids cochés dans le widget, dans l'ordre où ils l'ont été.
    public static func pending() -> [String] {
        SharedFile.read(Pending.self, named: fileName)?.ids ?? []
    }

    /// Coche la tâche `id`, ou la décoche si elle l'était déjà (l'app ne l'a pas encore terminée).
    public static func toggle(_ id: String) throws {
        var ids = pending()
        if let index = ids.firstIndex(of: id) {
            ids.remove(at: index)
        } else {
            ids.append(id)
        }
        try save(ids)
    }

    /// Oublie les demandes `ids`, une fois traitées par l'app ; celles cochées entre-temps restent.
    public static func remove(_ ids: [String]) {
        let remaining = pending().filter { !ids.contains($0) }
        if (try? save(remaining)) == nil { clear() }
    }

    /// Oublie toutes les demandes (déconnexion, verrou activé).
    public static func clear() {
        SharedFile.remove(named: fileName)
    }

    private static func save(_ ids: [String]) throws {
        if ids.isEmpty {
            clear()
        } else {
            try SharedFile.write(Pending(ids: ids), named: fileName)
        }
    }
}

/// Fichiers JSON du conteneur de l'App Group, partagés entre l'app et l'extension.
enum SharedFile {
    /// `Library/Caches` du conteneur partagé : jamais sauvegardé (iCloud, Finder). `nil` sans App Group
    /// (build non signé de la CI, compte Apple sans App Groups) : le widget invite alors à ouvrir l'app.
    private static func url(named name: String) -> URL? {
        FileManager.default
            .containerURL(forSecurityApplicationGroupIdentifier: TodoWidgetStore.appGroup)?
            .appendingPathComponent("Library/Caches", isDirectory: true)
            .appendingPathComponent(name)
    }

    static func read<T: Decodable>(_ type: T.Type, named name: String) -> T? {
        guard let url = url(named: name), let data = try? Data(contentsOf: url) else { return nil }
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .millisecondsSince1970
        return try? decoder.decode(type, from: data)
    }

    static func write<T: Encodable>(_ value: T, named name: String) throws {
        guard var url = url(named: name) else { throw CocoaError(.fileNoSuchFile) }
        try FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .millisecondsSince1970
        // Lisible après le premier déverrouillage de l'appareil (pas avant), comme le Keychain de la session.
        // Écriture atomique : l'autre processus ne lit jamais un fichier à moitié écrit.
        try encoder.encode(value).write(to: url, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        try url.setResourceValues(values)
    }

    static func remove(named name: String) {
        guard let url = url(named: name) else { return }
        try? FileManager.default.removeItem(at: url)
    }
}

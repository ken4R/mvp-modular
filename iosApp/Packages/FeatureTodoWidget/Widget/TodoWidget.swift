import SwiftUI
import WidgetKit
import TodoWidgetData

/// Extension WidgetKit : lit le fichier écrit par l'app (TodoWidgetPublisher) et n'a accès à rien d'autre
/// (ni base de tâches, ni Keychain, ni framework Kotlin). Voir docs/adr/0011.
@main
struct TodoWidgetBundle: WidgetBundle {
    var body: some Widget {
        TodoWidget()
    }
}

struct TodoWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: TodoWidgetStore.widgetKind, provider: TodoWidgetProvider()) { entry in
            TodoWidgetView(entry: entry)
        }
        .configurationDisplayName("Tâches")
        .description("Vos tâches à faire, le rappel le plus proche en premier.")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

struct TodoWidgetEntry: TimelineEntry {
    let date: Date
    /// `nil` : l'app n'a encore rien écrit (jamais lancée, ou App Group indisponible).
    let snapshot: TodoWidgetSnapshot?
}

struct TodoWidgetProvider: TimelineProvider {

    func placeholder(in context: Context) -> TodoWidgetEntry {
        TodoWidgetEntry(date: Date(), snapshot: .example)
    }

    func getSnapshot(in context: Context, completion: @escaping (TodoWidgetEntry) -> Void) {
        // Galerie des widgets : un exemple, jamais les vraies tâches.
        completion(context.isPreview ? placeholder(in: context) : currentEntry(at: Date()).first!)
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<TodoWidgetEntry>) -> Void) {
        // Pas de rafraîchissement périodique : l'app demande un redessin à chaque changement.
        completion(Timeline(entries: currentEntry(at: Date()), policy: .never))
    }

    /// L'entrée de maintenant, et à la fin de la session une entrée sans tâche.
    private func currentEntry(at now: Date) -> [TodoWidgetEntry] {
        let snapshot = TodoWidgetStore.read()
        var entries = [TodoWidgetEntry(date: now, snapshot: snapshot?.displayed(at: now))]
        if let snapshot, snapshot.content == .tasks, let end = snapshot.visibleUntil, end > now {
            entries.append(TodoWidgetEntry(date: end, snapshot: snapshot.displayed(at: end)))
        }
        return entries
    }
}

struct TodoWidgetView: View {
    let entry: TodoWidgetEntry
    @Environment(\.widgetFamily) private var family
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        content
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            // Sans lien (déconnecté, verrouillé), un tap ouvre simplement l'app.
            .widgetURL(entry.snapshot?.content == .tasks ? entry.snapshot?.link : nil)
            .widgetBackground()
    }

    @ViewBuilder
    private var content: some View {
        switch entry.snapshot?.content {
        case .tasks?:
            if let snapshot = entry.snapshot { tasks(snapshot) }
        case .signedOut?:
            message("Tâches", "Connectez-vous pour voir vos tâches ici.")
        case .locked?:
            message("Tâches masquées", "Le déverrouillage Face ID est activé. Touchez pour ouvrir l'app.")
        case .unavailable?, nil:
            message("Tâches", "Ouvrez l'app pour voir vos tâches.")
        }
    }

    private var accent: Color {
        entry.snapshot?.palette?.primary(scheme) ?? .accentColor
    }

    private var maxRows: Int { family == .systemSmall ? 3 : 4 }

    private func tasks(_ snapshot: TodoWidgetSnapshot) -> some View {
        VStack(alignment: .leading) {
            Text(snapshot.remainingCount == 0 ? "Tout est fait" : "À faire · \(snapshot.remainingCount)")
                .font(.headline)
                .foregroundStyle(accent)
            ForEach(snapshot.items.prefix(maxRows)) { item in
                row(item)
            }
        }
    }

    @ViewBuilder
    private func row(_ item: TodoWidgetSnapshot.Item) -> some View {
        let label = VStack(alignment: .leading) {
            Text(item.title)
                .font(.subheadline)
                .lineLimit(1)
                // Masqué sur l'écran verrouillé (mode En veille) tant que l'iPhone n'est pas déverrouillé.
                .privacySensitive()
            if family != .systemSmall, let at = item.reminderAt {
                Text(at, format: .dateTime.day().month().hour().minute())
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
        }
        // Petit widget : une seule zone de tap (widgetURL) ; moyen : chaque ligne ouvre sa tâche.
        if family != .systemSmall, let link = item.link {
            Link(destination: link) { label }
        } else {
            label
        }
    }

    private func message(_ title: String, _ text: String) -> some View {
        VStack(alignment: .leading) {
            Text(title)
                .font(.headline)
                .foregroundStyle(accent)
            Text(text)
                .font(.caption)
                .foregroundStyle(.secondary)
        }
    }
}

private extension TodoWidgetSnapshot {
    /// Exemple de la galerie des widgets et du chargement.
    static let example = TodoWidgetSnapshot(
        content: .tasks,
        remainingCount: 3,
        items: [
            Item(id: "1", title: "Appeler Paul", reminderAt: nil, link: nil),
            Item(id: "2", title: "Acheter du pain", reminderAt: nil, link: nil),
            Item(id: "3", title: "Préparer la réunion", reminderAt: nil, link: nil),
        ],
        visibleUntil: .distantFuture
    )
}

private extension View {
    /// iOS 17+ exige un fond de conteneur ; avant, la marge habituelle d'un widget.
    @ViewBuilder
    func widgetBackground() -> some View {
        if #available(iOSApplicationExtension 17.0, *) {
            containerBackground(.fill.tertiary, for: .widget)
        } else {
            padding()
        }
    }
}

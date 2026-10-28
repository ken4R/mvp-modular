import AppIntents
import SwiftUI
import WidgetKit
import TodoWidgetData

/// Widget Tâches : lit le fichier écrit par l'app (TodoWidgetPublisher) et n'a accès à rien d'autre (ni base de
/// tâches, ni Keychain, ni framework Kotlin). Voir docs/adr/0011. Depuis iOS 17, chaque ligne a une case qui note
/// la tâche à terminer ; l'app la termine à sa prochaine ouverture (ADR 0026).
struct TodoWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: TodoWidgetStore.widgetKind, provider: TodoWidgetProvider()) { entry in
            TodoWidgetView(entry: entry)
        }
        .configurationDisplayName("Tâches")
        .description("Vos tâches à faire, le rappel le plus proche en premier. Cochez-les sans ouvrir l'app.")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

struct TodoWidgetEntry: TimelineEntry {
    let date: Date
    /// `nil` : l'app n'a encore rien écrit (jamais lancée, ou App Group indisponible).
    let snapshot: TodoWidgetSnapshot?
    /// Tâches cochées dans le widget, pas encore terminées par l'app.
    var completed: Set<String> = []
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
        let completed = Set(TodoWidgetCompletions.pending())
        var entries = [TodoWidgetEntry(date: now, snapshot: snapshot?.displayed(at: now), completed: completed)]
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
        // Les cases cochées comptent déjà comme faites, même avant que l'app ne les ait terminées.
        let remaining = max(0, snapshot.remainingCount - snapshot.items.filter { entry.completed.contains($0.id) }.count)
        return VStack(alignment: .leading) {
            Text(remaining == 0 ? "Tout est fait" : "À faire · \(remaining)")
                .font(.headline)
                .foregroundStyle(accent)
            ForEach(snapshot.items.prefix(maxRows)) { item in
                row(item)
            }
        }
        // Compteur, titres et heures masqués sur l'écran verrouillé (mode En veille) tant que l'iPhone l'est.
        .privacySensitive()
    }

    @ViewBuilder
    private func row(_ item: TodoWidgetSnapshot.Item) -> some View {
        let done = entry.completed.contains(item.id)
        if #available(iOSApplicationExtension 17.0, *) {
            HStack(alignment: .firstTextBaseline) {
                Button(intent: CompleteTodoIntent(todoId: item.id)) {
                    Image(systemName: done ? "checkmark.circle.fill" : "circle")
                        .font(.title3)
                        .foregroundStyle(accent)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(done ? "Remettre « \(item.title) » à faire" : "Terminer « \(item.title) »")
                rowLabel(item, done: done)
            }
        } else {
            rowLabel(item, done: done)
        }
    }

    @ViewBuilder
    private func rowLabel(_ item: TodoWidgetSnapshot.Item, done: Bool) -> some View {
        let label = VStack(alignment: .leading) {
            Text(item.title)
                .font(.subheadline)
                .strikethrough(done)
                .foregroundStyle(done ? HierarchicalShapeStyle.secondary : HierarchicalShapeStyle.primary)
                .lineLimit(1)
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

/// Case d'une ligne du widget (iOS 17+). Tourne dans l'extension, sans accès à la base : elle note seulement l'id de
/// la tâche dans le conteneur partagé (re-toucher la case annule la note), et l'app termine la tâche par le use case
/// partagé à sa prochaine ouverture (ADR 0026). iPhone verrouillé (mode En veille), elle demande de le déverrouiller.
@available(iOS 17.0, *)
struct CompleteTodoIntent: AppIntent {
    static var title: LocalizedStringResource = "Terminer la tâche"
    /// Réservée au widget : absente de Raccourcis et de Spotlight.
    static var isDiscoverable = false
    static var authenticationPolicy: IntentAuthenticationPolicy = .requiresAuthentication

    @Parameter(title: "Tâche")
    var todoId: String

    init() {}

    init(todoId: String) {
        self.todoId = todoId
    }

    func perform() async throws -> some IntentResult {
        // Seulement une tâche que le widget peut montrer maintenant : rien après la fin de session, ni verrou activé.
        let visible = TodoWidgetStore.read()?.displayed(at: Date())
        guard visible?.content == .tasks, visible?.items.contains(where: { $0.id == todoId }) == true else {
            return .result()
        }
        try TodoWidgetCompletions.toggle(todoId)
        return .result()
    }
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

import SwiftUI
import WidgetKit
import WeatherWidgetData

/// Widget Météo (ADR 0026) : lit le fichier écrit par l'app (WeatherWidgetPublisher) et n'a accès à rien d'autre
/// (ni position, ni réseau, ni framework Kotlin). Il montre la dernière météo « Ma position » obtenue par l'app.
struct WeatherWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: WeatherWidgetStore.widgetKind, provider: WeatherWidgetProvider()) { entry in
            WeatherWidgetView(entry: entry)
        }
        .configurationDisplayName("Météo")
        .description("La météo à votre position, telle que l'app l'a obtenue en dernier.")
        .supportedFamilies([.systemSmall])
    }
}

struct WeatherWidgetEntry: TimelineEntry {
    let date: Date
    /// `nil` : l'app n'a encore rien écrit (jamais lancée, ou App Group indisponible).
    let snapshot: WeatherWidgetSnapshot?
}

struct WeatherWidgetProvider: TimelineProvider {

    func placeholder(in context: Context) -> WeatherWidgetEntry {
        WeatherWidgetEntry(date: Date(), snapshot: .example)
    }

    func getSnapshot(in context: Context, completion: @escaping (WeatherWidgetEntry) -> Void) {
        // Galerie des widgets : un exemple, jamais la vraie météo.
        completion(context.isPreview ? placeholder(in: context) : entries(at: Date()).first!)
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<WeatherWidgetEntry>) -> Void) {
        // Pas de rafraîchissement périodique : l'app demande un redessin à chaque nouvelle météo.
        completion(Timeline(entries: entries(at: Date()), policy: .never))
    }

    /// L'entrée de maintenant, et quand la météo est trop vieille (ou la session finie) une entrée sans météo.
    private func entries(at now: Date) -> [WeatherWidgetEntry] {
        let snapshot = WeatherWidgetStore.read()
        var entries = [WeatherWidgetEntry(date: now, snapshot: snapshot?.displayed(at: now))]
        if let snapshot, snapshot.content == .weather, let end = snapshot.visibleUntil, end > now {
            entries.append(WeatherWidgetEntry(date: end, snapshot: snapshot.displayed(at: end)))
        }
        return entries
    }
}

struct WeatherWidgetView: View {
    let entry: WeatherWidgetEntry
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        content
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .widgetURL(entry.snapshot?.content == .weather ? entry.snapshot?.link : nil)
            .weatherWidgetBackground()
    }

    @ViewBuilder
    private var content: some View {
        switch entry.snapshot?.content {
        case .weather?:
            if let snapshot = entry.snapshot { weather(snapshot) }
        case .signedOut?:
            message("Connectez-vous pour voir la météo ici.")
        case .noData?, .unavailable?, nil:
            message("Ouvrez Météo et touchez « Ma position » pour l'afficher ici.")
        }
    }

    private var accent: Color {
        entry.snapshot?.palette?.primary(scheme) ?? .accentColor
    }

    private func weather(_ snapshot: WeatherWidgetSnapshot) -> some View {
        VStack(alignment: .leading) {
            Text("Ma position")
                .font(.headline)
                .foregroundStyle(accent)
            Spacer(minLength: 0)
            Image(systemName: snapshot.symbolName)
                .symbolRenderingMode(.multicolor)
                .font(.title2)
                .accessibilityHidden(true)
            Text("\(snapshot.temperatureCelsius) °C")
                .font(.title)
                .bold()
            Text(snapshot.conditionLabel)
                .font(.caption)
                .lineLimit(1)
            if let updatedAt = snapshot.updatedAt {
                Text("Mise à jour \(updatedAt, format: .dateTime.hour().minute())")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
        }
    }

    private func message(_ text: String) -> some View {
        VStack(alignment: .leading) {
            Text("Météo")
                .font(.headline)
                .foregroundStyle(accent)
            Text(text)
                .font(.caption)
                .foregroundStyle(.secondary)
        }
    }
}

private extension WeatherWidgetSnapshot {
    /// Exemple de la galerie des widgets et du chargement.
    static let example = WeatherWidgetSnapshot(
        content: .weather,
        temperatureCelsius: 18,
        conditionLabel: "Ciel dégagé",
        symbolName: "sun.max.fill",
        updatedAt: Date(),
        visibleUntil: .distantFuture
    )
}

private extension View {
    /// iOS 17+ exige un fond de conteneur ; avant, la marge habituelle d'un widget.
    @ViewBuilder
    func weatherWidgetBackground() -> some View {
        if #available(iOSApplicationExtension 17.0, *) {
            containerBackground(.fill.tertiary, for: .widget)
        } else {
            padding()
        }
    }
}

import SwiftUI
import Shared
import FeatureTodo
import FeatureTodoCompose
import FeatureWeather
import FeatureCatalog
import FeatureSettings

/// Onglets de l'app complète, et navigation des liens profonds.
/// Construite seulement une fois connecté et déverrouillé (SessionGate, AppLockGate) :
/// un lien reçu avant attend dans KMP, puis s'affiche ici.
struct AppTabsView: View {
    private enum Tab: Hashable {
        case todo, weather, catalog, settings

        var analyticsScreen: AnalyticsScreen {
            switch self {
            case .todo: return .tasks
            case .weather: return .weather
            case .catalog: return .catalog
            case .settings: return .settings
            }
        }
    }

    @StateObject private var router = DeepLinkRouter()
    /// Onglet Catalogue coupé à distance (flag `catalog`, ADR 0021).
    @StateObject private var catalogFlag = FeatureFlagObserver(key: "catalog", defaultValue: true)
    @State private var tab: Tab = .todo
    /// Tâche ou produit demandé par le dernier lien, remis à `nil` par l'écran qui l'affiche.
    @State private var todoRequest: String?
    @State private var productRequest: Int64?
    /// Titre dicté par le raccourci Siri (ADR 0018), remis à `nil` par l'écran Tâches.
    @State private var draftRequest: String?
    /// Onglet Tâches : écran SwiftUI ou sa version Compose Multiplatform (ADR 0010). Affichée dans l'onglet,
    /// pas en feuille : une feuille passerait au-dessus du cache d'aperçu et du verrou d'AppLockGate.
    @State private var showComposeTodo = false

    var body: some View {
        TabView(selection: $tab) {
            Group {
                if showComposeTodo {
                    TodoComposeView(onClose: { showComposeTodo = false })
                } else {
                    TodoListView(
                        focusRequest: $todoRequest,
                        draftRequest: $draftRequest,
                        onOpenComposeMultiplatform: { showComposeTodo = true }
                    )
                }
            }
            .tabItem { Label("Tâches", systemImage: "checklist") }
            .tag(Tab.todo)
            WeatherView()
                .tabItem { Label("Météo", systemImage: "cloud.sun") }
                .tag(Tab.weather)
            if catalogFlag.enabled {
                CatalogView(productRequest: $productRequest)
                    .tabItem { Label("Catalogue", systemImage: "bag") }
                    .tag(Tab.catalog)
            }
            SettingsView()
                .tabItem { Label("Réglages", systemImage: "gearshape") }
                .tag(Tab.settings)
        }
        .onAppear {
            router.start()
            catalogFlag.start()
            track(tab)
        }
        .onDisappear {
            router.stop()
            catalogFlag.stop()
        }
        // Catalogue coupé pendant qu'il est affiché : retour sur Tâches.
        .onChange(of: catalogFlag.enabled) { enabled in
            if !enabled && tab == .catalog { tab = .todo }
        }
        // Mesuré seulement avec le consentement Analytics (vérifié dans KMP).
        .onChange(of: tab) { track($0) }
        .onReceive(router.$link) { link in
            if let link { show(link) }
        }
    }

    private func track(_ tab: Tab) {
        let event = AnalyticsEvent.ScreenViewed(screen: tab.analyticsScreen)
        Task { _ = try? await AnalyticsUseCases.shared.trackEvent().invoke(params: event) }
    }

    private func show(_ link: DeepLink) {
        // La mise en évidence d'une tâche n'existe que dans l'écran SwiftUI.
        showComposeTodo = false
        switch link {
        case let tasks as DeepLinkTasks:
            tab = .todo
            todoRequest = tasks.todoId
        case is DeepLinkWeather:
            tab = .weather
        case let catalog as DeepLinkCatalog:
            // Catalogue coupé : le lien est ignoré, l'utilisateur reste où il est.
            if catalogFlag.enabled {
                tab = .catalog
                productRequest = catalog.productId?.int64Value
            }
        case is DeepLinkSettings:
            tab = .settings
        case let draft as DeepLinkNewTask:
            tab = .todo
            draftRequest = draft.title
        default:
            tab = .todo
        }
        router.consume(link)
    }
}

/// Lien en attente (KMP), observé tant que les onglets sont affichés.
@MainActor
private final class DeepLinkRouter: ObservableObject {
    @Published private(set) var link: DeepLink?
    private var subscription: Subscription?

    func start() {
        guard subscription == nil else { return }
        subscription = DeepLinkUseCases.shared.observePendingDeepLink(
            onLink: { [weak self] link in self?.link = link },
            onError: { _ in }
        )
    }

    func stop() {
        subscription?.cancel()
        subscription = nil
    }

    func consume(_ link: DeepLink) {
        Task { _ = try? await DeepLinkUseCases.shared.consumeDeepLink().invoke(params: link) }
    }
}

/// Valeur d'un feature flag (KMP), observée tant que les onglets sont affichés. [defaultValue] sert avant la
/// première valeur, lue depuis le cache.
@MainActor
private final class FeatureFlagObserver: ObservableObject {
    @Published private(set) var enabled: Bool
    private let flag: FeatureFlag?
    private var subscription: Subscription?

    init(key: String, defaultValue: Bool) {
        // Clé du JSON distant plutôt que l'entrée d'enum Kotlin, que Swift renomme.
        flag = FeatureFlagsIosKt.featureFlag(key: key)
        enabled = defaultValue
    }

    func start() {
        guard subscription == nil, let flag else { return }
        subscription = FeatureFlagsUseCases.shared.observeFeatureFlag(
            flag: flag,
            onEnabled: { [weak self] enabled in self?.enabled = enabled.boolValue },
            onError: { _ in }
        )
    }

    func stop() {
        subscription?.cancel()
        subscription = nil
    }
}

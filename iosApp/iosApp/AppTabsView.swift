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
    @State private var tab: Tab = .todo
    /// Tâche ou produit demandé par le dernier lien, remis à `nil` par l'écran qui l'affiche.
    @State private var todoRequest: String?
    @State private var productRequest: Int64?
    /// Version Compose Multiplatform de l'écran Tâches, ouverte depuis l'écran SwiftUI (ADR 0010).
    @State private var showComposeTodo = false

    var body: some View {
        TabView(selection: $tab) {
            TodoListView(focusRequest: $todoRequest, onOpenComposeMultiplatform: { showComposeTodo = true })
                .sheet(isPresented: $showComposeTodo) {
                    TodoComposeView(onClose: { showComposeTodo = false })
                }
                .tabItem { Label("Tâches", systemImage: "checklist") }
                .tag(Tab.todo)
            WeatherView()
                .tabItem { Label("Météo", systemImage: "cloud.sun") }
                .tag(Tab.weather)
            CatalogView(productRequest: $productRequest)
                .tabItem { Label("Catalogue", systemImage: "bag") }
                .tag(Tab.catalog)
            SettingsView()
                .tabItem { Label("Réglages", systemImage: "gearshape") }
                .tag(Tab.settings)
        }
        .onAppear {
            router.start()
            track(tab)
        }
        .onDisappear { router.stop() }
        // Mesuré seulement avec le consentement Analytics (vérifié dans KMP).
        .onChange(of: tab) { track($0) }
        .onReceive(router.$link) { link in
            if let link { show(link) }
        }
    }

    private func track(_ tab: Tab) {
        let event = AnalyticsEvent.ScreenViewed(screen: tab.analyticsScreen)
        Task { _ = try? await UseCases.shared.trackEvent().invoke(params: event) }
    }

    private func show(_ link: DeepLink) {
        // La feuille Compose couvrirait l'écran demandé (et la mise en évidence n'existe que dans l'écran SwiftUI).
        showComposeTodo = false
        switch link {
        case let tasks as DeepLinkTasks:
            tab = .todo
            todoRequest = tasks.todoId
        case is DeepLinkWeather:
            tab = .weather
        case let catalog as DeepLinkCatalog:
            tab = .catalog
            productRequest = catalog.productId?.int64Value
        case is DeepLinkSettings:
            tab = .settings
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
        subscription = UseCases.shared.observePendingDeepLink(
            onLink: { [weak self] link in self?.link = link },
            onError: { _ in }
        )
    }

    func stop() {
        subscription?.cancel()
        subscription = nil
    }

    func consume(_ link: DeepLink) {
        Task { _ = try? await UseCases.shared.consumeDeepLink().invoke(params: link) }
    }
}

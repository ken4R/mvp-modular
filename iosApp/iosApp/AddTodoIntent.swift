import AppIntents
import Shared

/// « Dis Siri, ajoute une tâche dans MVP Modular » (ADR 0018) : Siri demande le titre, puis ouvre l'app avec
/// ce titre prérempli dans « Nouvelle tâche ». Rien n'est ajouté sans le bouton « Ajouter » : l'intent peut être
/// lancé app verrouillée, déconnectée, ou par une automatisation de Raccourcis. Le titre attend dans KMP comme
/// un lien profond, jusqu'à la connexion et au déverrouillage, et il est oublié à la déconnexion.
struct AddTodoIntent: AppIntent {
    static var title: LocalizedStringResource = "Ajouter une tâche"
    static var description = IntentDescription("Ouvre l'app avec la tâche dictée, prête à être ajoutée.")
    // Dans le processus de l'app (Koin déjà initialisé par iOSApp), au premier plan : l'utilisateur confirme.
    static var openAppWhenRun: Bool = true

    @Parameter(title: "Tâche", requestValueDialog: IntentDialog("Quelle tâche voulez-vous ajouter ?"))
    var taskTitle: String

    @MainActor
    func perform() async throws -> some IntentResult {
        _ = try? await TodoUseCases.shared.startTodoDraft().invoke(params: taskTitle as NSString)
        return .result()
    }
}

/// Phrases Siri, en français (langue de développement du projet, project.yml). `applicationName` est le nom
/// affiché de l'app (« MVP Modular »). Un paramètre texte ne peut pas figurer dans la phrase : Siri le demande ensuite.
struct MvpAppShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: AddTodoIntent(),
            phrases: [
                "Ajoute une tâche dans \(.applicationName)",
                "Ajouter une tâche dans \(.applicationName)",
                "Nouvelle tâche dans \(.applicationName)",
            ],
            shortTitle: "Ajouter une tâche",
            systemImageName: "plus.circle"
        )
    }
}

import Shared

/// Priorité d'une tâche (ADR 0024) ; la valeur brute est le nom Kotlin de l'entrée de `TodoPriority`, que l'export
/// Swift renomme.
enum TodoPriorityOption: String, CaseIterable {
    case noPriority = "None"
    case low = "Low"
    case medium = "Medium"
    case high = "High"

    /// Libellé du sélecteur de la fiche.
    var label: String {
        switch self {
        case .noPriority: return "Aucune"
        case .low: return "Basse"
        case .medium: return "Moyenne"
        case .high: return "Haute"
        }
    }

    /// « Priorité haute » sous le titre dans la liste ; `nil` sans priorité.
    var badge: String? {
        switch self {
        case .noPriority: return nil
        case .low: return "Priorité basse"
        case .medium: return "Priorité moyenne"
        case .high: return "Priorité haute"
        }
    }
}

extension Todo {
    var priorityOption: TodoPriorityOption {
        TodoPriorityOption(rawValue: priority.name) ?? .noPriority
    }
}

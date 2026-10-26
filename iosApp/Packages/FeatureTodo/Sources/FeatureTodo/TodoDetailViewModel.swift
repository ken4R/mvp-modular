import Foundation
import PhotosUI
import Shared
import SwiftUI

/// Fiche d'une tâche : échéance (ADR 0022), note et pièces jointes (ADR 0014). La logique (formats, tailles, stockage) est dans KMP ;
/// ici, la lecture des fichiers choisis et l'état de l'écran.
@MainActor
final class TodoDetailViewModel: ObservableObject {
    @Published private(set) var todo: Todo?
    /// La tâche a été supprimée (ici ou sur un autre appareil) pendant que la fiche était ouverte.
    @Published private(set) var isMissing = false
    @Published var noteDraft = ""
    @Published private(set) var attachments: [TodoAttachment] = []
    @Published private(set) var isAdding = false
    @Published private(set) var error: String?
    /// Erreur de l'échéance ou de l'ajout au calendrier, affichée dans sa section.
    @Published private(set) var dueDateError: String?
    /// Contenus prêts pour ShareLink (ADR 0017), recalculés à chaque changement de la tâche : sans la note, et
    /// avec la note enregistrée (`nil` si la tâche n'en a pas).
    @Published private(set) var share: TodoShare?
    @Published private(set) var shareWithNote: TodoShare?
    /// Rappel par lieu (ADR 0023) : nom et rayon choisis avant « Me le rappeler en arrivant ici ».
    @Published var placeLabel = ""
    @Published var placeRadius = Int(LocationReminder.Companion.shared.DEFAULT_RADIUS)
    @Published private(set) var isLocating = false
    @Published private(set) var placeError: String?
    /// La position exacte manque : la section propose d'ouvrir Réglages.
    @Published private(set) var placeNeedsSettings = false
    /// `false` : les rappels par lieu posés ne partent pas (position refusée ou approximative).
    @Published private(set) var locationRemindersAvailable = true
    @Published private(set) var remindersEnabled = true

    private let todoId: String
    private var noteLoaded = false
    private var todosSubscription: Subscription?
    private var attachmentsSubscription: Subscription?
    private var locationSubscription: Subscription?
    private var remindersEnabledSubscription: Subscription?
    private var shareTask: Task<Void, Never>?
    private var calendarTask: Task<Void, Never>?

    init(todoId: String) {
        self.todoId = todoId
    }

    var noteChanged: Bool {
        guard let todo else { return false }
        return noteDraft.trimmingCharacters(in: .whitespacesAndNewlines) != todo.note
    }

    var canAddAttachment: Bool {
        !isAdding && attachments.count < Int(AddTodoAttachmentUseCase.Companion.shared.MAX_ATTACHMENTS_PER_TODO)
    }

    func start() {
        guard todosSubscription == nil else { return }
        todosSubscription = UseCases.shared.observeTodos(
            onTodos: { [weak self] todos in self?.apply(todos.first { $0.id == self?.todoId }) },
            onError: { [weak self] error in self?.error = error.detailMessage }
        )
        attachmentsSubscription = UseCases.shared.observeTodoAttachments(
            todoId: todoId,
            onAttachments: { [weak self] attachments in self?.attachments = attachments },
            onError: { [weak self] error in self?.error = error.detailMessage }
        )
        locationSubscription = UseCases.shared.observeLocationRemindersAvailable(
            onAvailable: { [weak self] available in self?.locationRemindersAvailable = available.boolValue },
            onError: { _ in }
        )
        remindersEnabledSubscription = UseCases.shared.observeRemindersEnabled(
            onEnabled: { [weak self] enabled in self?.remindersEnabled = enabled.boolValue },
            onError: { _ in }
        )
        TodoReminders.refreshLocationAvailability()
    }

    /// Rayons proposés par KMP (mètres).
    var placeRadii: [Int] { LocationReminder.Companion.shared.RADII.map { Int($0.int32Value) } }

    /// « Me le rappeler en arrivant ici » : KMP demande l'autorisation la première fois, mesure la position exacte et
    /// la garde sur l'iPhone. Rien ne part ailleurs.
    func setPlaceHere() async {
        guard !isLocating else { return }
        isLocating = true
        defer { isLocating = false }
        let request = LocationReminderHere(todoId: todoId, radiusMeters: Int32(placeRadius), label: placeLabel)
        let result = try? await UseCases.shared.setLocationReminderHere().invoke(params: request)
        if let failure = result as? AppResultFailure {
            placeError = failure.error.placeMessage
            placeNeedsSettings = failure.error is AppErrorPermissionDenied
        } else {
            placeError = nil
            placeNeedsSettings = false
            placeLabel = ""
        }
        TodoReminders.refreshLocationAvailability()
    }

    func removePlace() async {
        let result = try? await UseCases.shared.removeLocationReminder().invoke(params: todoId as NSString)
        if let failure = result as? AppResultFailure { placeError = failure.error.detailMessage } else { placeError = nil }
    }

    /// La fiche n'est plus affichée : la note modifiée est enregistrée. Le brouillon est gardé si elle revient.
    func stop() {
        if noteChanged {
            let note = TodoNote(todoId: todoId, note: noteDraft)
            Task { _ = try? await UseCases.shared.updateTodoNote().invoke(params: note) }
        }
        todosSubscription?.cancel()
        todosSubscription = nil
        attachmentsSubscription?.cancel()
        attachmentsSubscription = nil
        locationSubscription?.cancel()
        locationSubscription = nil
        remindersEnabledSubscription?.cancel()
        remindersEnabledSubscription = nil
        shareTask?.cancel()
        shareTask = nil
        // Fiche quittée (verrou, arrière-plan) pendant la demande d'accès d'iOS 16 : l'écran d'ajout ne s'ouvre pas.
        calendarTask?.cancel()
        calendarTask = nil
    }

    private func apply(_ todo: Todo?) {
        self.todo = todo
        isMissing = todo == nil
        // Le brouillon part de la note enregistrée, une seule fois : une synchronisation ne l'écrase pas.
        if let todo, !noteLoaded {
            noteDraft = todo.note
            noteLoaded = true
        }
        prepareShare(todo)
    }

    /// ShareLink a besoin de son contenu avant le tap : les deux variantes sont préparées par KMP à l'avance.
    /// Les anciens textes sont retirés tout de suite : une note modifiée ne peut pas partir dans sa version d'avant.
    private func prepareShare(_ todo: Todo?) {
        shareTask?.cancel()
        share = nil
        shareWithNote = nil
        guard let todo else { return }
        let todoId = todo.id
        let hasNote = !todo.note.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        shareTask = Task { [weak self] in
            let plain = await Self.loadShare(todoId: todoId, includeNote: false)
            var withNote: TodoShare?
            if hasNote { withNote = await Self.loadShare(todoId: todoId, includeNote: true) }
            guard !Task.isCancelled, let self else { return }
            self.share = plain
            self.shareWithNote = withNote
        }
    }

    private static func loadShare(todoId: String, includeNote: Bool) async -> TodoShare? {
        let result = try? await UseCases.shared.shareTodo().invoke(params: TodoShareRequest(todoId: todoId, includeNote: includeNote))
        return (result as? AppResultSuccess<AnyObject>)?.data as? TodoShare
    }

    /// `date == nil` retire l'échéance ; seul le jour compte (calendrier grégorien, fuseau de l'appareil).
    func setDueDate(_ date: Date?) async {
        let dueDate = date.map { KotlinInt(value: CalendarDate.of($0).epochDay) }
        let result = try? await UseCases.shared.setTodoDueDate().invoke(params: TodoDueDate(todoId: todoId, dueDate: dueDate))
        if let failure = result as? AppResultFailure {
            dueDateError = failure.error.detailMessage
        } else {
            dueDateError = nil
        }
    }

    /// « Ajouter au calendrier » : l'écran d'ajout du système, prérempli par KMP (titre, date, lien ; jamais la note).
    func addToCalendar() {
        calendarTask?.cancel()
        let todoId = todoId
        calendarTask = Task { [weak self] in
            let result = try? await UseCases.shared.exportTodoToCalendar().invoke(params: todoId as NSString)
            guard !Task.isCancelled else { return }
            if let failure = result as? AppResultFailure {
                self?.dueDateError = failure.error.detailMessage
            } else if let event = (result as? AppResultSuccess<AnyObject>)?.data as? TodoCalendarEvent {
                let error = await CalendarExport.present(event)
                if !Task.isCancelled { self?.dueDateError = error }
            }
        }
    }

    func saveNote() async {
        let result = try? await UseCases.shared.updateTodoNote().invoke(params: TodoNote(todoId: todoId, note: noteDraft))
        if let failure = result as? AppResultFailure {
            error = failure.error.detailMessage
        } else if let saved = (result as? AppResultSuccess<AnyObject>)?.data as? Todo {
            noteDraft = saved.note
            error = nil
        }
    }

    /// Photo choisie dans la photothèque : réenregistrée en JPEG avant l'ajout.
    func addPhoto(_ item: PhotosPickerItem) async {
        await add {
            guard let data = try? await item.loadTransferable(type: Data.self),
                  let jpeg = await Task.detached(operation: { AttachmentFiles.jpeg(from: data) }).value
            else { return nil }
            return ("Photo.jpg", "image/jpeg", jpeg)
        }
    }

    /// Fichier choisi dans « Fichiers » (PDF, texte ou image).
    func addFile(_ url: URL) async {
        await add { await Task.detached(operation: { AttachmentFiles.read(url: url) }).value }
    }

    func delete(_ attachment: TodoAttachment) async {
        let result = try? await UseCases.shared.deleteTodoAttachment().invoke(params: attachment.id as NSString)
        if let failure = result as? AppResultFailure { error = failure.error.detailMessage }
    }

    private func add(_ load: () async -> (fileName: String, mimeType: String, data: Data)?) async {
        guard !isAdding else { return }
        isAdding = true
        error = nil
        defer { isAdding = false }
        guard let file = await load() else {
            error = "Impossible de lire ce fichier (10 Mo au plus)"
            return
        }
        let attachment = NewTodoAttachment(
            todoId: todoId,
            fileName: file.fileName,
            mimeType: file.mimeType,
            bytes: KoinIosKt.bytesOf(data: file.data)
        )
        let result = try? await UseCases.shared.addTodoAttachment().invoke(params: attachment)
        if let failure = result as? AppResultFailure { error = failure.error.detailMessage }
    }
}

private extension AppError {
    var detailMessage: String {
        switch self {
        case let validation as AppErrorValidation: return validation.message
        case is AppErrorNotFound: return "Tâche introuvable"
        case is AppErrorUnknown: return "Enregistrement impossible, vérifiez l'espace disponible"
        default: return "Erreur inattendue"
        }
    }

    /// Erreurs de « Me le rappeler en arrivant ici ».
    var placeMessage: String {
        switch self {
        case is AppErrorPermissionDenied: return "Autorisez la position exacte pour MVP Modular dans Réglages > Confidentialité > Service de localisation."
        case is AppErrorUnavailable: return "Position introuvable, réessayez à l'extérieur ou près d'une fenêtre."
        default: return detailMessage
        }
    }
}

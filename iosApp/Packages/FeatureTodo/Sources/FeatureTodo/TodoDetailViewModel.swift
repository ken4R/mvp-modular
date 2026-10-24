import Foundation
import PhotosUI
import Shared
import SwiftUI

/// Fiche d'une tâche : note et pièces jointes (ADR 0014). La logique (formats, tailles, stockage) est dans KMP ;
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
    /// Contenus prêts pour ShareLink (ADR 0017), recalculés à chaque changement de la tâche : sans la note, et
    /// avec la note enregistrée (`nil` si la tâche n'en a pas).
    @Published private(set) var share: TodoShare?
    @Published private(set) var shareWithNote: TodoShare?

    private let todoId: String
    private var noteLoaded = false
    private var todosSubscription: Subscription?
    private var attachmentsSubscription: Subscription?
    private var shareTask: Task<Void, Never>?

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
        shareTask?.cancel()
        shareTask = nil
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
    private func prepareShare(_ todo: Todo?) {
        shareTask?.cancel()
        guard let todo else {
            share = nil
            shareWithNote = nil
            return
        }
        let todoId = todo.id
        let hasNote = !todo.note.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        shareTask = Task { [weak self] in
            let plain = await Self.loadShare(todoId: todoId, includeNote: false)
            let withNote = hasNote ? await Self.loadShare(todoId: todoId, includeNote: true) : nil
            guard !Task.isCancelled, let self else { return }
            self.share = plain
            self.shareWithNote = withNote
        }
    }

    private static func loadShare(todoId: String, includeNote: Bool) async -> TodoShare? {
        let result = try? await UseCases.shared.shareTodo().invoke(params: TodoShareRequest(todoId: todoId, includeNote: includeNote))
        return (result as? AppResultSuccess<AnyObject>)?.data as? TodoShare
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
}

import PDFKit
import PhotosUI
import Shared
import SwiftUI
import DesignSystemUI
import UniformTypeIdentifiers

/// Fiche d'une tâche (ADR 0014) : échéance et export vers le calendrier (ADR 0022), note et pièces jointes. Affichée à la place de la liste, dans l'onglet, et
/// jamais en feuille : elle reste sous le verrou d'AppLockGate. Son contenu est masqué dès que la scène n'est
/// plus active, verrou activé ou non : l'aperçu du sélecteur d'apps ne montre aucune pièce jointe.
/// Aperçus dans l'app (photo, PDF, texte), sans confier le fichier à une autre app.
struct TodoDetailView: View {
    @StateObject private var viewModel: TodoDetailViewModel
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.dsAccent) private var accent
    @Environment(\.scenePhase) private var scenePhase
    @State private var photoItem: PhotosPickerItem?
    @State private var showFileImporter = false
    @State private var previewedId: String?
    @FocusState private var noteFocused: Bool
    private let onClose: () -> Void

    init(todoId: String, onClose: @escaping () -> Void) {
        _viewModel = StateObject(wrappedValue: TodoDetailViewModel(todoId: todoId))
        self.onClose = onClose
    }

    var body: some View {
        let colors = DS.colors(colorScheme, accent)
        VStack(alignment: .leading, spacing: DS.Spacing.md) {
            if let previewed = viewModel.attachments.first(where: { $0.id == previewedId }) {
                header(previewed.fileName, action: "Fermer", colors: colors) { previewedId = nil }
                AttachmentPreview(attachment: previewed, colors: colors)
            } else {
                // La note modifiée est enregistrée par onDisappear.
                header(viewModel.todo?.title ?? "", action: "Retour", colors: colors, showsShare: true, onAction: onClose)
                if viewModel.isMissing {
                    Text("Cette tâche a été supprimée")
                        .font(DS.Typography.body)
                        .foregroundColor(Color(argb: colors.onBackground))
                    Spacer()
                } else {
                    content(colors: colors)
                }
            }
        }
        .padding(DS.Spacing.md)
        .background(Color(argb: colors.background))
        .overlay {
            if scenePhase != .active {
                Color(argb: colors.background).ignoresSafeArea()
            }
        }
        .onAppear { viewModel.start() }
        // Feuille de partage ouverte (ADR 0017) : fermée au passage en arrière-plan, avant l'aperçu du sélecteur
        // d'apps et le verrou. C'est une fenêtre modale UIKit, au-dessus du masque et de l'écran de verrouillage.
        .onChange(of: scenePhase) { phase in
            if phase == .background { PresentedSheets.dismissAll() }
            // Position exacte accordée ou retirée dans Réglages pendant que l'app était en arrière-plan (ADR 0023).
            if phase == .active { TodoReminders.refreshLocationAvailability() }
        }
        // Retour, changement d'onglet, verrouillage de l'app : la note modifiée est enregistrée.
        .onDisappear { viewModel.stop() }
        .onChange(of: photoItem) { item in
            guard let item else { return }
            photoItem = nil
            Task { await viewModel.addPhoto(item) }
        }
        .fileImporter(isPresented: $showFileImporter, allowedContentTypes: [.pdf, .plainText, .image]) { result in
            if case let .success(url) = result { Task { await viewModel.addFile(url) } }
        }
    }

    private func header(
        _ title: String,
        action: String,
        colors: ColorTokens,
        showsShare: Bool = false,
        onAction: @escaping () -> Void
    ) -> some View {
        HStack(spacing: DS.Spacing.sm) {
            Button(action, action: onAction)
                .font(DS.Typography.label)
                .tint(Color(argb: colors.primary))
            Text(title)
                .font(DS.Typography.titleLarge)
                .foregroundColor(Color(argb: colors.onBackground))
                .lineLimit(1)
            Spacer(minLength: 0)
            if showsShare { shareButton(colors: colors) }
        }
    }

    /// Partage par la feuille du système (ADR 0017) : du texte et le lien de la tâche, jamais de pièce jointe.
    /// La feuille est fermée au passage en arrière-plan (voir `onChange(of: scenePhase)`). Avec une note, elle n'est ajoutée que choisie, et seulement une fois enregistrée.
    @ViewBuilder
    private func shareButton(colors: ColorTokens) -> some View {
        if let share = viewModel.share {
            Group {
                if let withNote = viewModel.shareWithNote {
                    Menu {
                        shareLink(share, label: "Sans la note")
                        shareLink(withNote, label: viewModel.noteChanged ? "Avec la note (enregistrez-la d'abord)" : "Avec la note")
                            .disabled(viewModel.noteChanged)
                    } label: {
                        Label("Partager", systemImage: "square.and.arrow.up")
                    }
                } else {
                    shareLink(share, label: "Partager")
                }
            }
            .font(DS.Typography.label)
            .tint(Color(argb: colors.primary))
        }
    }

    private func shareLink(_ share: TodoShare, label: String) -> some View {
        ShareLink(item: share.text, subject: Text(share.subject)) {
            Label(label, systemImage: "square.and.arrow.up")
        }
    }

    private func content(colors: ColorTokens) -> some View {
        List {
            prioritySection
            dueDateSection(colors: colors)
            locationSection(colors: colors)

            Section {
                TextEditor(text: $viewModel.noteDraft)
                    .font(DS.Typography.body)
                    .frame(minHeight: DS.Size.heroImage / 2)
                    .focused($noteFocused)
                HStack {
                    Text("\(viewModel.noteDraft.trimmingCharacters(in: .whitespacesAndNewlines).count) / \(UpdateTodoNoteUseCase.Companion.shared.MAX_NOTE_LENGTH)")
                        .font(DS.Typography.caption)
                        .foregroundColor(Color(argb: colors.onSurface))
                    Spacer()
                    Button("Enregistrer la note") {
                        noteFocused = false
                        Task { await viewModel.saveNote() }
                    }
                    .buttonStyle(.borderless)
                    .disabled(!viewModel.noteChanged)
                }
            } header: {
                Text("Note")
            }

            Section {
                PhotosPicker(selection: $photoItem, matching: .images) {
                    Label("Ajouter une photo", systemImage: "photo")
                }
                .disabled(!viewModel.canAddAttachment)
                Button {
                    showFileImporter = true
                } label: {
                    Label("Ajouter un fichier", systemImage: "doc")
                }
                .disabled(!viewModel.canAddAttachment)
                if viewModel.isAdding {
                    ProgressView()
                }
                if let error = viewModel.error {
                    Text(error)
                        .font(DS.Typography.caption)
                        .foregroundColor(Color(argb: colors.error))
                }
                ForEach(viewModel.attachments, id: \.id) { attachment in
                    Button {
                        noteFocused = false
                        previewedId = attachment.id
                    } label: {
                        AttachmentRow(attachment: attachment, colors: colors)
                    }
                    .buttonStyle(.plain)
                    .swipeActions {
                        Button("Retirer", role: .destructive) { Task { await viewModel.delete(attachment) } }
                    }
                }
            } header: {
                Text("Pièces jointes (\(viewModel.attachments.count) / \(AddTodoAttachmentUseCase.Companion.shared.MAX_ATTACHMENTS_PER_TODO))")
            } footer: {
                Text("Photos (sans leur position), PDF et fichiers texte, 10 Mo au plus. Ils restent sur cet iPhone.")
            }
        }
        .listStyle(.insetGrouped)
        .scrollDismissesKeyboard(.interactively)
        .tint(Color(argb: colors.primary))
    }
}

private extension TodoDetailView {
    /// Priorité (ADR 0024) : choisie d'un geste, enregistrée tout de suite.
    var prioritySection: some View {
        Section {
            Picker("Priorité", selection: Binding(
                get: { viewModel.todo?.priorityOption ?? .noPriority },
                set: { option in Task { await viewModel.setPriority(option) } }
            )) {
                ForEach(TodoPriorityOption.allCases, id: \.self) { option in
                    Text(option.label).tag(option)
                }
            }
            .pickerStyle(.segmented)
            .disabled(viewModel.todo == nil)
        } header: {
            Text("Priorité")
        }
    }

    /// Échéance : la choisir (sélecteur compact), la retirer, ou l'ajouter au calendrier de l'iPhone.
    @ViewBuilder
    func dueDateSection(colors: ColorTokens) -> some View {
        Section {
            if let todo = viewModel.todo, let due = todo.dueCalendarDate?.localDate {
                DatePicker(
                    "Date",
                    selection: Binding(get: { due }, set: { date in Task { await viewModel.setDueDate(date) } }),
                    in: DueDates.range,
                    displayedComponents: .date
                )
                .environment(\.calendar, DueDates.calendar)
                if todo.isOverdueToday {
                    Text("En retard")
                        .font(DS.Typography.caption)
                        .foregroundColor(Color(argb: colors.error))
                }
                Button {
                    noteFocused = false
                    viewModel.addToCalendar()
                } label: {
                    Label("Ajouter au calendrier", systemImage: "calendar.badge.plus")
                }
                Button("Retirer l'échéance", role: .destructive) { Task { await viewModel.setDueDate(nil) } }
            } else {
                Button {
                    Task { await viewModel.setDueDate(Date()) }
                } label: {
                    Label("Ajouter une échéance", systemImage: "calendar")
                }
                .disabled(viewModel.todo == nil)
            }
            if let error = viewModel.dueDateError {
                Text(error)
                    .font(DS.Typography.caption)
                    .foregroundColor(Color(argb: colors.error))
            }
        } header: {
            Text("Échéance")
        } footer: {
            if viewModel.todo?.dueDate != nil {
                Text("Le titre et la date seulement, jamais la note. L'événement reste dans le calendrier même après la déconnexion.")
            } else {
                Text("Aujourd'hui par défaut, puis modifiable.")
            }
        }
    }
}

private extension TodoDetailView {
    /// Rappel par lieu (ADR 0023) : à chaque arrivée dans la zone, tant que la tâche est à faire. La position reste sur l'iPhone.
    @ViewBuilder
    func locationSection(colors: ColorTokens) -> some View {
        Section {
            if let todo = viewModel.todo, let place = todo.locationReminder {
                Label("En arrivant à \(place.label) (\(place.radiusMeters) m)", systemImage: "location")
                    .font(DS.Typography.body)
                    .foregroundColor(Color(argb: colors.onSurface))
                Button("Retirer le rappel par lieu", role: .destructive) { Task { await viewModel.removePlace() } }
                if !viewModel.locationRemindersAvailable {
                    Text("Ce rappel ne part pas : autorisez la position exacte pour MVP Modular.")
                        .font(DS.Typography.caption)
                        .foregroundColor(Color(argb: colors.error))
                    Button("Ouvrir Réglages", action: openSettings)
                }
            } else {
                TextField("Nom du lieu (ex. Bureau)", text: $viewModel.placeLabel)
                    .font(DS.Typography.body)
                    .submitLabel(.done)
                Picker("Rayon", selection: $viewModel.placeRadius) {
                    ForEach(viewModel.placeRadii, id: \.self) { radius in
                        Text("\(radius) m").tag(radius)
                    }
                }
                .pickerStyle(.segmented)
                Button {
                    noteFocused = false
                    Task { await viewModel.setPlaceHere() }
                } label: {
                    Label("Me le rappeler en arrivant ici", systemImage: "location.circle")
                }
                .disabled(viewModel.todo == nil || viewModel.todo?.isDone == true || viewModel.isLocating)
                if viewModel.isLocating {
                    ProgressView()
                }
            }
            if let error = viewModel.placeError {
                Text(error)
                    .font(DS.Typography.caption)
                    .foregroundColor(Color(argb: colors.error))
                // Pas en double avec le bouton du bandeau « ce rappel ne part pas ».
                if viewModel.placeNeedsSettings, viewModel.todo?.locationReminder == nil {
                    Button("Ouvrir Réglages", action: openSettings)
                }
            }
            if !viewModel.remindersEnabled {
                Text("Activez les notifications dans Réglages de l'app pour recevoir les rappels.")
                    .font(DS.Typography.caption)
                    .foregroundColor(Color(argb: colors.onSurface))
            }
        } header: {
            Text("Rappel par lieu")
        } footer: {
            Text("La position actuelle, gardée sur cet iPhone et jamais envoyée. Le rappel part à chaque arrivée tant que la tâche est à faire.")
        }
    }

    func openSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
        UIApplication.shared.open(url)
    }
}

private struct AttachmentRow: View {
    let attachment: TodoAttachment
    let colors: ColorTokens
    @State private var thumbnail: UIImage?

    var body: some View {
        HStack(spacing: DS.Spacing.sm) {
            ZStack {
                RoundedRectangle(cornerRadius: DS.Radius.sm)
                    .fill(Color(argb: colors.secondary))
                if let thumbnail {
                    Image(uiImage: thumbnail)
                        .resizable()
                        .scaledToFill()
                } else if !attachment.isPhoto {
                    Text(attachment.isPdf ? "PDF" : "TXT")
                        .font(DS.Typography.label)
                        .foregroundColor(Color(argb: colors.onSecondary))
                }
            }
            .frame(width: DS.Size.thumbnail, height: DS.Size.thumbnail)
            .clipShape(RoundedRectangle(cornerRadius: DS.Radius.sm))
            VStack(alignment: .leading, spacing: DS.Spacing.xs) {
                Text(attachment.fileName)
                    .font(DS.Typography.body)
                    .foregroundColor(Color(argb: colors.onSurface))
                    .lineLimit(2)
                Text(formattedSize(attachment.sizeBytes))
                    .font(DS.Typography.caption)
                    .foregroundColor(Color(argb: colors.onSurface))
            }
            Spacer(minLength: 0)
        }
        .contentShape(Rectangle())
        .task(id: attachment.id) {
            guard attachment.isPhoto else { return }
            let path = attachment.localPath
            let side = Int(DS.Size.thumbnail * 3)
            thumbnail = await Task.detached { AttachmentFiles.thumbnail(path: path, maxPixelSize: side) }.value
        }
    }
}

/// Aperçu d'une pièce jointe dans l'app. Photo et texte sont lus hors du main thread, une fois par pièce jointe.
private struct AttachmentPreview: View {
    let attachment: TodoAttachment
    let colors: ColorTokens
    @State private var image: UIImage?
    @State private var text: String?
    @State private var loaded = false

    var body: some View {
        Group {
            if attachment.isPdf {
                PDFPreview(url: URL(fileURLWithPath: attachment.localPath))
            } else if !loaded {
                ProgressView()
            } else if attachment.isPhoto {
                if let image {
                    Image(uiImage: image)
                        .resizable()
                        .scaledToFit()
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .accessibilityLabel(attachment.fileName)
                } else {
                    unavailable
                }
            } else {
                ScrollView {
                    Text(text ?? "")
                        .font(DS.Typography.body)
                        .foregroundColor(Color(argb: colors.onSurface))
                        .textSelection(.enabled)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .task(id: attachment.id) {
            guard !attachment.isPdf else { return }
            loaded = false
            let path = attachment.localPath
            if attachment.isPhoto {
                image = await Task.detached { UIImage(contentsOfFile: path) }.value
            } else {
                text = await Task.detached { AttachmentFiles.textPreview(path: path) }.value
            }
            loaded = true
        }
    }

    private var unavailable: some View {
        Text("Aperçu impossible")
            .font(DS.Typography.body)
            .foregroundColor(Color(argb: colors.onBackground))
    }
}

/// PDFKit dans SwiftUI : pages défilantes, à la largeur de l'écran. Les liens du document ne s'ouvrent pas :
/// un PDF reçu ne peut pas lancer Safari vers une adresse de son choix.
private struct PDFPreview: UIViewRepresentable {
    let url: URL

    func makeCoordinator() -> LinkBlocker { LinkBlocker() }

    func makeUIView(context: Context) -> PDFView {
        let view = PDFView()
        view.delegate = context.coordinator
        view.autoScales = true
        view.displayMode = .singlePageContinuous
        view.document = PDFDocument(url: url)
        return view
    }

    func updateUIView(_ view: PDFView, context: Context) {
        if view.document?.documentURL != url { view.document = PDFDocument(url: url) }
    }
}

/// Ignore les liens des PDF joints (web, mail, téléphone).
final class LinkBlocker: NSObject, PDFViewDelegate {
    func pdfViewWillClick(onLink sender: PDFView, with url: URL) {}
}

/// Fenêtres modales UIKit présentées au-dessus de l'app (feuille de partage, et l'écran de rédaction ouvert
/// depuis elle) : SwiftUI ne les ferme pas quand la vue qui les a ouvertes disparaît.
enum PresentedSheets {
    @MainActor
    static func dismissAll() {
        for scene in UIApplication.shared.connectedScenes.compactMap({ $0 as? UIWindowScene }) {
            for window in scene.windows {
                window.rootViewController?.presentedViewController?.dismiss(animated: false)
            }
        }
    }
}

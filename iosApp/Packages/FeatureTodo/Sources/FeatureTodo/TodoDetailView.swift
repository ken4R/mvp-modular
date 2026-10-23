import PDFKit
import PhotosUI
import Shared
import SwiftUI
import DesignSystemUI
import UniformTypeIdentifiers

/// Fiche d'une tâche (ADR 0014) : note et pièces jointes. Affichée à la place de la liste, dans l'onglet, et
/// jamais en feuille : elle reste sous le verrou d'AppLockGate. Son contenu est masqué dès que la scène n'est
/// plus active, verrou activé ou non : l'aperçu du sélecteur d'apps ne montre aucune pièce jointe.
/// Aperçus dans l'app (photo, PDF, texte), sans confier le fichier à une autre app.
struct TodoDetailView: View {
    @StateObject private var viewModel: TodoDetailViewModel
    @Environment(\.colorScheme) private var colorScheme
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
        let colors = DS.colors(colorScheme)
        VStack(alignment: .leading, spacing: DS.Spacing.md) {
            if let previewed = viewModel.attachments.first(where: { $0.id == previewedId }) {
                header(previewed.fileName, action: "Fermer", colors: colors) { previewedId = nil }
                AttachmentPreview(attachment: previewed, colors: colors)
            } else {
                // La note modifiée est enregistrée par onDisappear.
                header(viewModel.todo?.title ?? "", action: "Retour", colors: colors, onAction: onClose)
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

    private func header(_ title: String, action: String, colors: ColorTokens, onAction: @escaping () -> Void) -> some View {
        HStack(spacing: DS.Spacing.sm) {
            Button(action, action: onAction)
                .font(DS.Typography.label)
                .tint(Color(argb: colors.primary))
            Text(title)
                .font(DS.Typography.titleLarge)
                .foregroundColor(Color(argb: colors.onBackground))
                .lineLimit(1)
            Spacer(minLength: 0)
        }
    }

    private func content(colors: ColorTokens) -> some View {
        List {
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

/// Aperçu d'une pièce jointe dans l'app.
private struct AttachmentPreview: View {
    let attachment: TodoAttachment
    let colors: ColorTokens

    var body: some View {
        Group {
            if attachment.isPhoto {
                if let image = UIImage(contentsOfFile: attachment.localPath) {
                    Image(uiImage: image)
                        .resizable()
                        .scaledToFit()
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .accessibilityLabel(attachment.fileName)
                } else {
                    unavailable
                }
            } else if attachment.isPdf {
                PDFPreview(url: URL(fileURLWithPath: attachment.localPath))
            } else {
                ScrollView {
                    Text(AttachmentFiles.textPreview(path: attachment.localPath) ?? "")
                        .font(DS.Typography.body)
                        .foregroundColor(Color(argb: colors.onSurface))
                        .textSelection(.enabled)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
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

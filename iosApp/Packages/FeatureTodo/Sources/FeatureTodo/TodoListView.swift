import SwiftUI
import Shared
import DesignSystemUI

public struct TodoListView: View {
    @StateObject private var viewModel = TodoViewModel()
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.dsAccent) private var accent
    @FocusState private var inputFocused: Bool
    @FocusState private var searchFocused: Bool
    /// Tâche dont on choisit le rappel (feuille ouverte).
    @State private var reminderTodo: Todo?
    /// Tâche dont la fiche (note et pièces jointes, ADR 0014) est affichée à la place de la liste.
    @State private var openedTodoId: String?
    /// Statistiques des tâches terminées (ADR 0019), affichées à la place de la liste.
    @State private var showStats = false
    /// Calendrier des tâches par échéance (ADR 0022), affiché à la place de la liste ; une fiche ouverte depuis le
    /// calendrier y revient.
    @State private var showCalendar = false
    /// Corbeille (ADR 0025), affichée à la place de la liste.
    @State private var showTrash = false
    /// Gardé par la liste : le mois parcouru survit à l'ouverture d'une fiche depuis le calendrier.
    @StateObject private var calendarViewModel = TodoCalendarViewModel()
    /// Tâche demandée par un lien profond : prise en compte puis remise à `nil`.
    @Binding private var focusRequest: String?
    /// Titre dicté par le raccourci Siri (ADR 0018) : prérempli dans « Nouvelle tâche », puis remis à `nil`.
    /// La tâche n'est ajoutée qu'avec le bouton « Ajouter ».
    @Binding private var draftRequest: String?
    /// Bouton vers la version Compose Multiplatform de l'écran (ADR 0010), si fourni.
    private let onOpenComposeMultiplatform: (() -> Void)?

    public init(
        focusRequest: Binding<String?> = .constant(nil),
        draftRequest: Binding<String?> = .constant(nil),
        onOpenComposeMultiplatform: (() -> Void)? = nil
    ) {
        _focusRequest = focusRequest
        _draftRequest = draftRequest
        self.onOpenComposeMultiplatform = onOpenComposeMultiplatform
    }

    public var body: some View {
        Group {
            if let openedTodoId {
                TodoDetailView(todoId: openedTodoId) { self.openedTodoId = nil }
                    .id(openedTodoId)
            } else if showStats {
                TodoStatsView { showStats = false }
            } else if showCalendar {
                TodoCalendarView(viewModel: calendarViewModel, onClose: { showCalendar = false }, onOpenTodo: { openedTodoId = $0 })
            } else if showTrash {
                TodoTrashView { showTrash = false }
            } else {
                list
            }
        }
        .onAppear {
            viewModel.start()
            takeFocusRequest()
            takeDraftRequest()
        }
        .onChange(of: focusRequest) { _ in takeFocusRequest() }
        .onChange(of: draftRequest) { _ in takeDraftRequest() }
        .onDisappear { viewModel.stop() }
    }

    private var list: some View {
        let colors = DS.colors(colorScheme, accent)
        return VStack(alignment: .leading, spacing: DS.Spacing.md) {
            Text("Mes tâches")
                .font(DS.Typography.titleLarge)
                .foregroundColor(Color(argb: colors.onBackground))
            HStack(spacing: DS.Spacing.md) {
                Button("Calendrier") {
                    inputFocused = false
                    searchFocused = false
                    showCalendar = true
                }
                .font(DS.Typography.label)
                .tint(Color(argb: colors.primary))
                Button("Statistiques") {
                    inputFocused = false
                    searchFocused = false
                    showStats = true
                }
                .font(DS.Typography.label)
                .tint(Color(argb: colors.primary))
                Button("Corbeille") {
                    inputFocused = false
                    searchFocused = false
                    showTrash = true
                }
                .font(DS.Typography.label)
                .tint(Color(argb: colors.primary))
                if let onOpenComposeMultiplatform {
                    Button("Version Compose MP", action: onOpenComposeMultiplatform)
                        .font(DS.Typography.label)
                        .tint(Color(argb: colors.primary))
                }
            }
            if let banner = viewModel.syncBanner {
                StatusBanner(banner.message, actionTitle: banner.canRetry ? "Réessayer" : nil) {
                    Task { await viewModel.retrySync() }
                }
            }

            HStack(spacing: DS.Spacing.sm) {
                TextField("Nouvelle tâche", text: $viewModel.input)
                    .textFieldStyle(.roundedBorder)
                    .focused($inputFocused)
                    .onSubmit(add)
                Button("Ajouter", action: add)
                    .tint(Color(argb: colors.primary))
            }
            if let error = viewModel.error {
                Text(error)
                    .font(DS.Typography.caption)
                    .foregroundColor(Color(argb: colors.error))
            }
            if viewModel.showRemindersDisabledHint {
                Text("Activez les notifications dans Réglages pour recevoir les rappels")
                    .font(DS.Typography.caption)
                    .foregroundColor(Color(argb: colors.primary))
            }

            if !viewModel.allTodos.isEmpty {
                filterBar(colors)
            }

            if viewModel.allTodos.isEmpty {
                Text("Aucune tâche pour le moment")
                    .font(DS.Typography.body)
                    .foregroundColor(Color(argb: colors.onBackground))
            } else if viewModel.todos.isEmpty {
                HStack(spacing: DS.Spacing.sm) {
                    Text("Aucune tâche ne correspond")
                        .font(DS.Typography.body)
                        .foregroundColor(Color(argb: colors.onBackground))
                    Button("Tout afficher") { viewModel.clearFilter() }
                        .font(DS.Typography.label)
                        .tint(Color(argb: colors.primary))
                }
            }
            ScrollViewReader { proxy in
                List {
                    ForEach(viewModel.todos, id: \.id) { todo in
                        TodoRow(
                            todo: todo,
                            colors: colors,
                            onToggle: { Task { await viewModel.toggle(todo) } },
                            onReminder: {
                                viewModel.clearError()
                                reminderTodo = todo
                            },
                            onOpen: {
                                inputFocused = false
                                searchFocused = false
                                openedTodoId = todo.id
                            }
                        )
                        .swipeActions {
                            Button("Supprimer", role: .destructive) { Task { await viewModel.delete(todo) } }
                        }
                        .listRowBackground(todo.id == viewModel.focusedTodoId ? Color(argb: colors.highlight) : nil)
                        // VoiceOver : monter ou descendre d'un cran, à la place du glisser-déposer.
                        .accessibilityActions {
                            if viewModel.allowsReordering {
                                Button("Monter") { viewModel.moveByOne(todo, up: true) }
                                Button("Descendre") { viewModel.moveByOne(todo, up: false) }
                            }
                        }
                    }
                    // Appui long puis glisser (ADR 0024) ; absent quand la liste est triée par priorité.
                    .onMove(perform: viewModel.allowsReordering ? move : nil)
                }
                .listStyle(.plain)
                .scrollDismissesKeyboard(.immediately)
                .onChange(of: viewModel.focusedTodoId) { id in
                    guard let id else { return }
                    // Au tour suivant : la ligne vient parfois d'arriver avec la liste.
                    DispatchQueue.main.async {
                        withAnimation { proxy.scrollTo(id, anchor: .center) }
                    }
                }
            }
        }
        .padding(DS.Spacing.md)
        .background(Color(argb: colors.background))
        // « Annuler » après une suppression (ADR 0025), par-dessus le bas de la liste.
        .overlay(alignment: .bottom) {
            if let notice = viewModel.trashedNotice {
                StatusBanner(notice.message, actionTitle: "Annuler") {
                    Task { await viewModel.undoDelete() }
                }
                .padding(DS.Spacing.md)
                .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .animation(.easeInOut, value: viewModel.trashedNotice)
        .sheet(item: Binding(
            get: { reminderTodo.map(ReminderTarget.init) },
            set: { reminderTodo = $0?.todo }
        )) { target in
            ReminderSheet(todo: target.todo, colors: colors, viewModel: viewModel) { reminderTodo = nil }
        }
    }

    /// Recherche (titre et note) et filtres cumulables.
    private func filterBar(_ colors: ColorTokens) -> some View {
        VStack(alignment: .leading, spacing: DS.Spacing.sm) {
            HStack(spacing: DS.Spacing.sm) {
                Image(systemName: "magnifyingglass")
                    .foregroundColor(Color(argb: colors.onSurface))
                    .accessibilityHidden(true)
                TextField("Rechercher dans les tâches et les notes", text: $viewModel.query)
                    .textFieldStyle(.roundedBorder)
                    .focused($searchFocused)
                    .submitLabel(.search)
                    .onSubmit { searchFocused = false }
                    .autocorrectionDisabled()
                if !viewModel.query.isEmpty {
                    Button { viewModel.query = "" } label: {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundColor(Color(argb: colors.onSurface))
                    }
                    .buttonStyle(.borderless)
                    .accessibilityLabel("Effacer la recherche")
                }
            }

            Picker("État", selection: $viewModel.status) {
                Text("Toutes").tag(TodoStatusOption.all)
                Text("À faire").tag(TodoStatusOption.pending)
                Text("Terminées").tag(TodoStatusOption.done)
            }
            .pickerStyle(.segmented)

            HStack(spacing: DS.Spacing.sm) {
                Toggle(isOn: $viewModel.withReminder) { Label("Avec rappel", systemImage: "bell") }
                Toggle(isOn: $viewModel.withAttachments) { Label("Avec pièce jointe", systemImage: "paperclip") }
                // Tri, pas filtre : il désactive le glisser-déposer tant qu'il est choisi.
                Toggle(isOn: $viewModel.byPriority) { Label("Par priorité", systemImage: "arrow.up.arrow.down") }
            }
            .toggleStyle(.button)
            .font(DS.Typography.label)
            .tint(Color(argb: colors.primary))
        }
    }

    private func takeFocusRequest() {
        guard let id = focusRequest else { return }
        focusRequest = nil
        reminderTodo = nil
        // Un lien vers une tâche revient à la liste, où elle est mise en évidence.
        openedTodoId = nil
        showStats = false
        showCalendar = false
        showTrash = false
        Task { await viewModel.focus(id) }
    }

    private func takeDraftRequest() {
        guard let title = draftRequest else { return }
        draftRequest = nil
        reminderTodo = nil
        // Le champ « Nouvelle tâche » est sur la liste. Un titre vide garde le texte déjà saisi.
        openedTodoId = nil
        showStats = false
        showCalendar = false
        showTrash = false
        if !title.isEmpty { viewModel.input = title }
    }

    private func move(_ source: IndexSet, _ destination: Int) {
        viewModel.move(from: source, to: destination)
    }

    /// Ferme le clavier après l'ajout : sinon la barre d'autocorrection peut
    /// encore intercepter le tap suivant sur une ligne de la liste.
    private func add() {
        inputFocused = false
        searchFocused = false
        Task { await viewModel.add() }
    }
}

/// `Todo` (classe Kotlin) n'est pas `Identifiable` : enveloppe pour `.sheet(item:)`.
private struct ReminderTarget: Identifiable {
    let todo: Todo
    var id: String { todo.id }
}

private struct TodoRow: View {
    let todo: Todo
    let colors: ColorTokens
    let onToggle: () -> Void
    let onReminder: () -> Void
    let onOpen: () -> Void

    /// Couleur des erreurs pour la priorité haute, l'accent pour la moyenne.
    private var priorityColor: Int64 {
        switch todo.priorityOption {
        case .high: return colors.error
        case .medium: return colors.primary
        case .low, .noPriority: return colors.onSurface
        }
    }

    var body: some View {
        HStack(spacing: DS.Spacing.sm) {
            Button(action: onToggle) {
                HStack(spacing: DS.Spacing.sm) {
                    Image(systemName: todo.isDone ? "checkmark.circle.fill" : "circle")
                        .foregroundColor(Color(argb: colors.primary))
                    VStack(alignment: .leading, spacing: DS.Spacing.xs) {
                        Text(todo.title)
                            .font(DS.Typography.body)
                            .strikethrough(todo.isDone)
                            .foregroundColor(Color(argb: colors.onSurface))
                        if let badge = todo.priorityOption.badge {
                            Text(badge)
                                .font(DS.Typography.caption)
                                .foregroundColor(Color(argb: priorityColor))
                        }
                        if let date = todo.reminderDate {
                            Text("Rappel le \(date.formatted(date: .abbreviated, time: .shortened))")
                                .font(DS.Typography.caption)
                                .foregroundColor(Color(argb: colors.onSurface))
                        }
                        if let place = todo.locationReminder {
                            Text("En arrivant : \(place.label)")
                                .font(DS.Typography.caption)
                                .foregroundColor(Color(argb: colors.onSurface))
                        }
                        if let due = todo.dueCalendarDate {
                            Text("Échéance le \(DueDates.short(due))" + (todo.isOverdueToday ? " · en retard" : ""))
                                .font(DS.Typography.caption)
                                .foregroundColor(Color(argb: todo.isOverdueToday ? colors.error : colors.onSurface))
                        }
                        if let summary = todo.detailsSummary {
                            Text(summary)
                                .font(DS.Typography.caption)
                                .foregroundColor(Color(argb: colors.onSurface))
                        }
                    }
                    Spacer(minLength: 0)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)

            // Bouton séparé : dans une ligne de List, deux boutons .borderless / .plain restent indépendants.
            Button(action: onReminder) {
                Image(systemName: todo.reminderAt != nil ? "bell.fill" : "bell")
                    .foregroundColor(Color(argb: colors.primary))
            }
            .buttonStyle(.borderless)
            .disabled(todo.isDone)
            .accessibilityLabel("Rappel")

            Button(action: onOpen) {
                Image(systemName: todo.attachmentCount > 0 ? "paperclip.circle.fill" : "info.circle")
                    .foregroundColor(Color(argb: colors.primary))
            }
            .buttonStyle(.borderless)
            .accessibilityLabel("Note et pièces jointes")
        }
    }
}

private extension Todo {
    /// « Note · 2 pièces jointes » sous le titre ; `nil` sans note ni pièce jointe.
    var detailsSummary: String? {
        var parts: [String] = []
        if !note.isEmpty { parts.append("Note") }
        switch attachmentCount {
        case 0: break
        case 1: parts.append("1 pièce jointe")
        default: parts.append("\(attachmentCount) pièces jointes")
        }
        return parts.isEmpty ? nil : parts.joined(separator: " · ")
    }
}

/// Choix de la date et de l'heure du rappel, ou retrait du rappel existant.
private struct ReminderSheet: View {
    let todo: Todo
    let colors: ColorTokens
    @ObservedObject var viewModel: TodoViewModel
    let onClose: () -> Void
    @State private var date: Date

    init(todo: Todo, colors: ColorTokens, viewModel: TodoViewModel, onClose: @escaping () -> Void) {
        self.todo = todo
        self.colors = colors
        self.viewModel = viewModel
        self.onClose = onClose
        // Part du rappel existant, sinon de l'heure suivante.
        let nextHour = Calendar.current.nextDate(
            after: Date(), matching: DateComponents(minute: 0), matchingPolicy: .nextTime
        ) ?? Date().addingTimeInterval(3600)
        _date = State(initialValue: todo.reminderDate ?? nextHour)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Text(todo.title)
                        .font(DS.Typography.body)
                        .foregroundColor(Color(argb: colors.onSurface))
                    DatePicker("Rappel", selection: $date, in: Date()..., displayedComponents: [.date, .hourAndMinute])
                        .datePickerStyle(.graphical)
                        .tint(Color(argb: colors.primary))
                    if let error = viewModel.error {
                        Text(error)
                            .font(DS.Typography.caption)
                            .foregroundColor(Color(argb: colors.error))
                    }
                }
                if todo.reminderAt != nil {
                    Section {
                        Button("Retirer le rappel") {
                            Task { if await viewModel.setReminder(todo, at: nil) { onClose() } }
                        }
                        .foregroundColor(Color(argb: colors.error))
                    }
                }
            }
            .navigationTitle("Rappel")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Annuler", action: onClose)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Enregistrer") {
                        Task { if await viewModel.setReminder(todo, at: date) { onClose() } }
                    }
                }
            }
        }
        .tint(Color(argb: colors.primary))
        .presentationDetents([.large])
    }
}

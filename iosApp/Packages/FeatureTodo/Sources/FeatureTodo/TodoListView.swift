import SwiftUI
import Shared
import DesignSystemUI

public struct TodoListView: View {
    @StateObject private var viewModel = TodoViewModel()
    @Environment(\.colorScheme) private var colorScheme
    @FocusState private var inputFocused: Bool
    /// Tâche dont on choisit le rappel (feuille ouverte).
    @State private var reminderTodo: Todo?
    /// Tâche demandée par un lien profond : prise en compte puis remise à `nil`.
    @Binding private var focusRequest: String?

    public init(focusRequest: Binding<String?> = .constant(nil)) {
        _focusRequest = focusRequest
    }

    public var body: some View {
        let colors = DS.colors(colorScheme)
        VStack(alignment: .leading, spacing: DS.Spacing.md) {
            Text("Mes tâches")
                .font(DS.Typography.titleLarge)
                .foregroundColor(Color(argb: colors.onBackground))

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

            if viewModel.todos.isEmpty {
                Text("Aucune tâche pour le moment")
                    .font(DS.Typography.body)
                    .foregroundColor(Color(argb: colors.onBackground))
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
                            }
                        )
                        .swipeActions {
                            Button("Supprimer", role: .destructive) { Task { await viewModel.delete(todo) } }
                        }
                        .listRowBackground(todo.id == viewModel.focusedTodoId ? Color(argb: colors.highlight) : nil)
                    }
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
        .sheet(item: Binding(
            get: { reminderTodo.map(ReminderTarget.init) },
            set: { reminderTodo = $0?.todo }
        )) { target in
            ReminderSheet(todo: target.todo, colors: colors, viewModel: viewModel) { reminderTodo = nil }
        }
        .onAppear {
            viewModel.start()
            takeFocusRequest()
        }
        .onChange(of: focusRequest) { _ in takeFocusRequest() }
        .onDisappear { viewModel.stop() }
    }

    private func takeFocusRequest() {
        guard let id = focusRequest else { return }
        focusRequest = nil
        reminderTodo = nil
        viewModel.focus(id)
    }

    /// Ferme le clavier après l'ajout : sinon la barre d'autocorrection peut
    /// encore intercepter le tap suivant sur une ligne de la liste.
    private func add() {
        inputFocused = false
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
                        if let date = todo.reminderDate {
                            Text("Rappel le \(date.formatted(date: .abbreviated, time: .shortened))")
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
        }
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

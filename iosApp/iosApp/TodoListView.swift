import SwiftUI
import Shared

struct TodoListView: View {
    @StateObject private var viewModel = TodoViewModel()
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        let colors = DS.colors(colorScheme)
        VStack(alignment: .leading, spacing: DS.Spacing.md) {
            Text("Mes tâches")
                .font(DS.Typography.titleLarge)
                .foregroundColor(Color(argb: colors.onBackground))

            HStack(spacing: DS.Spacing.sm) {
                TextField("Nouvelle tâche", text: $viewModel.input)
                    .textFieldStyle(.roundedBorder)
                    .onSubmit { Task { await viewModel.add() } }
                Button("Ajouter") { Task { await viewModel.add() } }
                    .tint(Color(argb: colors.primary))
            }
            if let error = viewModel.error {
                Text(error)
                    .font(DS.Typography.caption)
                    .foregroundColor(Color(argb: colors.error))
            }

            if viewModel.todos.isEmpty {
                Text("Aucune tâche pour le moment")
                    .font(DS.Typography.body)
                    .foregroundColor(Color(argb: colors.onBackground))
            }
            List {
                ForEach(viewModel.todos, id: \.id) { todo in
                    TodoRow(todo: todo, colors: colors) { Task { await viewModel.toggle(todo) } }
                        .swipeActions {
                            Button("Supprimer", role: .destructive) { Task { await viewModel.delete(todo) } }
                        }
                }
            }
            .listStyle(.plain)
        }
        .padding(DS.Spacing.md)
        .background(Color(argb: colors.background))
        .onAppear { viewModel.start() }
        .onDisappear { viewModel.stop() }
    }
}

private struct TodoRow: View {
    let todo: Todo
    let colors: ColorTokens
    let onToggle: () -> Void

    var body: some View {
        Button(action: onToggle) {
            HStack(spacing: DS.Spacing.sm) {
                Image(systemName: todo.isDone ? "checkmark.circle.fill" : "circle")
                    .foregroundColor(Color(argb: colors.primary))
                Text(todo.title)
                    .font(DS.Typography.body)
                    .strikethrough(todo.isDone)
                    .foregroundColor(Color(argb: colors.onSurface))
            }
        }
        .buttonStyle(.plain)
    }
}

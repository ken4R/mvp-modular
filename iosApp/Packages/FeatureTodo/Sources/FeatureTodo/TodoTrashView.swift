import DesignSystemUI
import Shared
import SwiftUI

/// Corbeille (ADR 0025) : tâches supprimées, restaurables pendant 30 jours. Affichée à la place de la liste, dans
/// l'onglet Tâches : elle reste sous le verrou d'AppLockGate. La suppression définitive (une tâche ou toute la
/// corbeille) demande une confirmation.
struct TodoTrashView: View {
    @StateObject private var viewModel = TodoTrashViewModel()
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.dsAccent) private var accent
    /// Tâche dont on confirme la suppression définitive.
    @State private var rowToDelete: TrashRow?
    @State private var confirmEmpty = false
    let onClose: () -> Void

    var body: some View {
        let colors = DS.colors(colorScheme, accent)
        VStack(alignment: .leading, spacing: DS.Spacing.md) {
            HStack(spacing: DS.Spacing.sm) {
                Button("Retour", action: onClose)
                    .font(DS.Typography.label)
                    .tint(Color(argb: colors.primary))
                Text("Corbeille")
                    .font(DS.Typography.titleLarge)
                    .foregroundColor(Color(argb: colors.onBackground))
                    .accessibilityAddTraits(.isHeader)
                Spacer(minLength: 0)
                if let rows = viewModel.rows, !rows.isEmpty {
                    Button("Vider", role: .destructive) { confirmEmpty = true }
                        .font(DS.Typography.label)
                        .tint(Color(argb: colors.error))
                }
            }
            Text("Les tâches supprimées restent ici \(TodoTrash.shared.RETENTION_DAYS) jours, puis sont supprimées définitivement.")
                .font(DS.Typography.body)
                .foregroundColor(Color(argb: colors.onBackground))
            if let message = viewModel.message {
                StatusBanner(message)
            }
            if viewModel.failed {
                Text("Corbeille indisponible")
                    .font(DS.Typography.body)
                    .foregroundColor(Color(argb: colors.error))
            } else if let rows = viewModel.rows {
                if rows.isEmpty {
                    Text("La corbeille est vide")
                        .font(DS.Typography.body)
                        .foregroundColor(Color(argb: colors.onBackground))
                } else {
                    List(rows) { row in
                        rowView(row, colors: colors)
                    }
                    .listStyle(.plain)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(DS.Spacing.md)
        .background(Color(argb: colors.background))
        .onAppear { viewModel.start() }
        .onDisappear { viewModel.stop() }
        .confirmationDialog(
            "Supprimer définitivement ?",
            isPresented: Binding(get: { rowToDelete != nil }, set: { if !$0 { rowToDelete = nil } }),
            titleVisibility: .visible,
            presenting: rowToDelete
        ) { row in
            Button("Supprimer", role: .destructive) { Task { await viewModel.deletePermanently(row) } }
            Button("Annuler", role: .cancel) {}
        } message: { row in
            Text("« \(row.title) » et ses pièces jointes seront supprimées. C'est irréversible.")
        }
        .confirmationDialog("Vider la corbeille ?", isPresented: $confirmEmpty, titleVisibility: .visible) {
            Button("Supprimer", role: .destructive) { Task { await viewModel.emptyTrash() } }
            Button("Annuler", role: .cancel) {}
        } message: {
            Text("Toutes les tâches de la corbeille et leurs pièces jointes seront supprimées. C'est irréversible.")
        }
    }

    private func rowView(_ row: TrashRow, colors: ColorTokens) -> some View {
        VStack(alignment: .leading, spacing: DS.Spacing.xs) {
            Text(row.title)
                .font(DS.Typography.body)
                .strikethrough(row.isDone)
                .foregroundColor(Color(argb: colors.onBackground))
            Text(Self.daysLeft(row.daysLeft))
                .font(DS.Typography.caption)
                .foregroundColor(Color(argb: colors.onSurface))
            HStack(spacing: DS.Spacing.md) {
                Button("Restaurer") { Task { await viewModel.restore(row) } }
                    .tint(Color(argb: colors.primary))
                Button("Supprimer définitivement", role: .destructive) { rowToDelete = row }
                    .tint(Color(argb: colors.error))
            }
            .font(DS.Typography.label)
            // Deux boutons dans une ligne de List : sans ce style, un tap n'importe où les déclencherait tous les deux.
            .buttonStyle(.borderless)
        }
        .listRowBackground(Color(argb: colors.background))
    }

    private static func daysLeft(_ days: Int) -> String {
        days <= 1 ? "Supprimée définitivement dans 1 jour" : "Supprimée définitivement dans \(days) jours"
    }
}

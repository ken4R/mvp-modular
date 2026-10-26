import DesignSystemUI
import Shared
import SwiftUI

/// Calendrier des tâches par date d'échéance (ADR 0022) : un mois en grille, du lundi au dimanche, et les tâches du
/// jour choisi en dessous. Affiché à la place de la liste, dans l'onglet Tâches : il reste sous le verrou
/// d'AppLockGate. Un tap sur une tâche ouvre sa fiche (`onOpenTodo`), d'où l'on revient au calendrier.
struct TodoCalendarView: View {
    @ObservedObject var viewModel: TodoCalendarViewModel
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.dsAccent) private var accent
    let onClose: () -> Void
    let onOpenTodo: (String) -> Void

    /// Initiales des jours, du lundi au dimanche ; VoiceOver lit le jour complet dans chaque case.
    private static let weekdays = ["L", "M", "M", "J", "V", "S", "D"]

    var body: some View {
        let colors = DS.colors(colorScheme, accent)
        VStack(alignment: .leading, spacing: DS.Spacing.sm) {
            HStack(spacing: DS.Spacing.sm) {
                Button("Retour", action: onClose)
                    .font(DS.Typography.label)
                    .tint(Color(argb: colors.primary))
                Text("Calendrier")
                    .font(DS.Typography.titleLarge)
                    .foregroundColor(Color(argb: colors.onBackground))
                    .accessibilityAddTraits(.isHeader)
                Spacer(minLength: 0)
                Button("Aujourd'hui") { viewModel.showToday() }
                    .font(DS.Typography.label)
                    .tint(Color(argb: colors.primary))
            }
            HStack {
                Button { viewModel.showPrevious() } label: { Image(systemName: "chevron.left") }
                    .disabled(!viewModel.canShowPrevious)
                    .accessibilityLabel("Mois précédent")
                Spacer()
                Text(Self.monthTitle(viewModel.request))
                    .font(DS.Typography.titleMedium)
                    .foregroundColor(Color(argb: colors.onBackground))
                    .accessibilityAddTraits(.isHeader)
                Spacer()
                Button { viewModel.showNext() } label: { Image(systemName: "chevron.right") }
                    .disabled(!viewModel.canShowNext)
                    .accessibilityLabel("Mois suivant")
            }
            .tint(Color(argb: colors.primary))

            if let month = viewModel.month {
                // La liste du jour occupe le reste de l'écran.
                content(month, colors: colors)
            } else {
                if viewModel.failed {
                    Text("Calendrier indisponible")
                        .font(DS.Typography.body)
                        .foregroundColor(Color(argb: colors.error))
                }
                Spacer(minLength: 0)
            }
        }
        .padding(DS.Spacing.md)
        .background(Color(argb: colors.background))
        .onAppear { viewModel.start() }
        .onDisappear { viewModel.stop() }
    }

    @ViewBuilder
    private func content(_ month: CalendarMonth, colors: ColorTokens) -> some View {
        if month.overdueCount > 0 {
            Text(month.overdueCount == 1 ? "1 tâche en retard" : "\(month.overdueCount) tâches en retard")
                .font(DS.Typography.caption)
                .foregroundColor(Color(argb: colors.error))
        }
        VStack(spacing: DS.Spacing.xs) {
            HStack(spacing: DS.Spacing.xs) {
                ForEach(Array(Self.weekdays.enumerated()), id: \.offset) { _, initial in
                    Text(initial)
                        .font(DS.Typography.caption)
                        .foregroundColor(Color(argb: colors.onBackground))
                        .frame(maxWidth: .infinity)
                }
            }
            .accessibilityHidden(true)
            ForEach(Array(stride(from: 0, to: month.days.count, by: 7)), id: \.self) { start in
                HStack(spacing: DS.Spacing.xs) {
                    ForEach(month.days[start..<min(start + 7, month.days.count)], id: \.epochDay) { day in
                        DayCell(day: day, selected: day.epochDay == viewModel.selectedDay, colors: colors) {
                            viewModel.select(day)
                        }
                    }
                }
            }
        }
        if let day = viewModel.selected {
            dayTodos(day, undatedCount: Int(month.undatedCount), colors: colors)
        }
    }

    private func dayTodos(_ day: CalendarDay, undatedCount: Int, colors: ColorTokens) -> some View {
        List {
            Section {
                if day.todos.isEmpty {
                    Text("Aucune tâche à cette date")
                        .font(DS.Typography.body)
                        .foregroundColor(Color(argb: colors.onSurface))
                }
                ForEach(day.todos, id: \.id) { todo in
                    HStack(spacing: DS.Spacing.sm) {
                        Button { Task { await viewModel.toggle(todo) } } label: {
                            Image(systemName: todo.isDone ? "checkmark.circle.fill" : "circle")
                                .foregroundColor(Color(argb: colors.primary))
                        }
                        .buttonStyle(.borderless)
                        .accessibilityLabel(todo.isDone ? "Rouvrir" : "Terminer")
                        Button { onOpenTodo(todo.id) } label: {
                            VStack(alignment: .leading, spacing: DS.Spacing.xs) {
                                Text(todo.title)
                                    .font(DS.Typography.body)
                                    .strikethrough(todo.isDone)
                                    .foregroundColor(Color(argb: colors.onSurface))
                                    .lineLimit(2)
                                if day.isOverdue && !todo.isDone {
                                    Text("En retard")
                                        .font(DS.Typography.caption)
                                        .foregroundColor(Color(argb: colors.error))
                                }
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityHint("Ouvre la fiche")
                    }
                }
            } header: {
                Text(DueDates.long(day.date, withYear: false))
            } footer: {
                if undatedCount > 0 {
                    Text(undatedCount == 1
                        ? "1 tâche sans échéance n'apparaît pas ici"
                        : "\(undatedCount) tâches sans échéance n'apparaissent pas ici")
                }
            }
        }
        .listStyle(.plain)
    }

    private static func monthTitle(_ request: CalendarMonthRequest) -> String {
        let date = CalendarDate(year: request.year, month: request.month, day: 1).localDate ?? Date()
        let text = DueDates.formatter("LLLL yyyy").string(from: date)
        return text.prefix(1).uppercased() + text.dropFirst()
    }
}

/// Case d'un jour : le numéro, puis le nombre de tâches à faire (en rouge si l'une est en retard, ✓ si toutes sont
/// terminées). Aujourd'hui sur le fond de mise en évidence, le jour choisi dans la couleur d'accent ; les jours du
/// mois voisin en petit.
private struct DayCell: View {
    let day: CalendarDay
    let selected: Bool
    let colors: ColorTokens
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            VStack(spacing: 0) {
                Text("\(day.date.day)")
                    .font(day.isInMonth ? DS.Typography.body : DS.Typography.caption)
                    .fontWeight(day.isToday ? .bold : .regular)
                    .foregroundColor(Color(argb: selected ? colors.onPrimary : colors.onSurface))
                if !day.todos.isEmpty {
                    Text(day.pendingCount == 0 ? "✓" : "\(day.pendingCount)")
                        .font(DS.Typography.caption)
                        .foregroundColor(Color(argb: markerColor))
                }
            }
            .frame(maxWidth: .infinity, minHeight: DS.Size.calendarDay)
            .background(
                RoundedRectangle(cornerRadius: DS.Radius.md)
                    .fill(background)
            )
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(spoken)
        .accessibilityAddTraits(selected ? [.isButton, .isSelected] : .isButton)
    }

    private var background: Color {
        if selected { return Color(argb: colors.primary) }
        if day.isToday { return Color(argb: colors.highlight) }
        return Color.clear
    }

    private var markerColor: Int64 {
        if selected { return colors.onPrimary }
        return day.isOverdue ? colors.error : colors.primary
    }

    /// « Mercredi 28 octobre, 2 tâches dont 1 à faire, en retard, aujourd'hui ».
    private var spoken: String {
        var parts = [DueDates.long(day.date, withYear: false)]
        switch day.todos.count {
        case 0: parts.append("aucune tâche")
        case 1: parts.append(day.pendingCount == 1 ? "1 tâche à faire" : "1 tâche terminée")
        default: parts.append("\(day.todos.count) tâches dont \(day.pendingCount) à faire")
        }
        if day.isOverdue { parts.append("en retard") }
        if day.isToday { parts.append("aujourd'hui") }
        return parts.joined(separator: ", ")
    }
}

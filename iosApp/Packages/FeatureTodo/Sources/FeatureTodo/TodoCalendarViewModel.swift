import Foundation
import Shared

/// Calendrier des tâches par échéance (ADR 0022) : le mois est calculé dans KMP, ici seulement l'état de l'écran.
@MainActor
final class TodoCalendarViewModel: ObservableObject {
    @Published private(set) var request: CalendarMonthRequest
    /// `nil` tant que le premier calcul du mois `request` n'est pas arrivé.
    @Published private(set) var month: CalendarMonth?
    /// Jour choisi (jours depuis le 1970-01-01), dont les tâches sont listées sous la grille.
    @Published private(set) var selectedDay: Int32
    @Published private(set) var failed = false

    private var subscription: Subscription?

    init() {
        let today = CalendarDate.today()
        request = CalendarMonthRequest(year: today.year, month: today.month)
        selectedDay = today.epochDay
    }

    var selected: CalendarDay? { month?.days.first { $0.epochDay == selectedDay } }
    var canShowPrevious: Bool { request.previous().year >= CalendarDate.Companion.shared.MIN_YEAR }
    var canShowNext: Bool { request.next().year <= CalendarDate.Companion.shared.MAX_YEAR }

    func start() {
        guard subscription == nil else { return }
        observe()
    }

    func stop() {
        subscription?.cancel()
        subscription = nil
    }

    func showPrevious() { show(request.previous()) }

    func showNext() { show(request.next()) }

    /// Revient au mois en cours et choisit aujourd'hui (celui du calcul KMP, recalculé à minuit).
    func showToday() {
        let today = month?.today ?? CalendarDate.today()
        show(CalendarMonthRequest(year: today.year, month: today.month), selecting: today.epochDay)
    }

    /// Un jour du mois précédent ou suivant (début ou fin de la grille) affiche ce mois-là.
    func select(_ day: CalendarDay) {
        if day.isInMonth {
            selectedDay = day.epochDay
        } else {
            show(CalendarMonthRequest(year: day.date.year, month: day.date.month), selecting: day.epochDay)
        }
    }

    func toggle(_ todo: Todo) async {
        _ = try? await TodoUseCases.shared.toggleTodo().invoke(params: todo.id as NSString)
    }

    /// Sans jour précisé : aujourd'hui pour le mois en cours, sinon le 1er du mois.
    private func show(_ next: CalendarMonthRequest, selecting day: Int32? = nil) {
        let dates = CalendarDate.Companion.shared
        guard next.year >= dates.MIN_YEAR, next.year <= dates.MAX_YEAR else { return }
        let today = month?.today
        if let day {
            selectedDay = day
        } else if let today, today.year == next.year, today.month == next.month {
            selectedDay = today.epochDay
        } else {
            selectedDay = CalendarDate(year: next.year, month: next.month, day: 1).epochDay
        }
        request = next
        // L'ancien mois est retiré : la grille n'affiche jamais un mois sous le titre d'un autre.
        month = nil
        failed = false
        subscription?.cancel()
        observe()
    }

    private func observe() {
        subscription = TodoUseCases.shared.observeTodoCalendar(
            request: request,
            onMonth: { [weak self] month in
                self?.month = month
                self?.failed = false
            },
            onError: { [weak self] _ in self?.failed = true }
        )
    }
}

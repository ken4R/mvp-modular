import Foundation
import Shared

/// Échéances (ADR 0022) : une date du calendrier grégorien, sans heure ni fuseau. Toujours le calendrier grégorien,
/// même si l'iPhone affiche un autre calendrier (bouddhiste, japonais…) : c'est celui du calcul KMP.
enum DueDates {
    static var calendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = .current
        calendar.locale = Locale(identifier: "fr_FR")
        return calendar
    }

    /// Recréé à chaque appel : le fuseau courant de l'appareil.
    static func formatter(_ format: String) -> DateFormatter {
        let formatter = DateFormatter()
        formatter.calendar = calendar
        formatter.locale = Locale(identifier: "fr_FR")
        formatter.timeZone = .current
        formatter.dateFormat = format
        return formatter
    }

    /// « mer. 28 oct. 2026 » sous le titre d'une tâche.
    static func short(_ date: CalendarDate) -> String {
        date.localDate.map { formatter("EEE d MMM yyyy").string(from: $0) } ?? ""
    }

    /// « Mercredi 28 octobre » (sans l'année avec `withYear: false`).
    static func long(_ date: CalendarDate, withYear: Bool = true) -> String {
        let text = date.localDate.map { formatter(withYear ? "EEEE d MMMM yyyy" : "EEEE d MMMM").string(from: $0) } ?? ""
        return text.prefix(1).uppercased() + text.dropFirst()
    }

    /// Bornes du sélecteur de date : celles de `SetTodoDueDateUseCase` (2000 à 2100).
    static var range: ClosedRange<Date> {
        let dates = CalendarDate.Companion.shared
        let min = dates.fromEpochDay(epochDay: dates.MIN_EPOCH_DAY).localDate ?? Date.distantPast
        let max = dates.fromEpochDay(epochDay: dates.MAX_EPOCH_DAY).localDate ?? Date.distantFuture
        return min...max
    }
}

extension CalendarDate {
    /// Minuit local de ce jour.
    var localDate: Date? {
        DueDates.calendar.date(from: DateComponents(year: Int(year), month: Int(month), day: Int(day)))
    }

    /// Le jour local de [date].
    static func of(_ date: Date) -> CalendarDate {
        let parts = DueDates.calendar.dateComponents([.year, .month, .day], from: date)
        return CalendarDate(year: Int32(parts.year ?? 1970), month: Int32(parts.month ?? 1), day: Int32(parts.day ?? 1))
    }

    static func today() -> CalendarDate { of(Date()) }
}

extension Todo {
    /// Jour de l'échéance, `nil` sans échéance.
    var dueCalendarDate: CalendarDate? {
        dueDate.map { CalendarDate.Companion.shared.fromEpochDay(epochDay: $0.int32Value) }
    }

    /// Échéance passée d'une tâche encore à faire, d'après la date du jour de l'appareil.
    var isOverdue: Bool {
        guard !isDone, let dueDate else { return false }
        return dueDate.int32Value < CalendarDate.today().epochDay
    }
}

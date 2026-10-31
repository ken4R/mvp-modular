package com.mvp.feature.focus.domain.model

/** Phase d'un cycle Pomodoro : concentration, puis pause courte, et une pause longue toutes les 4 concentrations. */
enum class FocusPhase(val label: String) {
    Work("Concentration"),
    ShortBreak("Pause courte"),
    LongBreak("Pause longue"),
    ;

    val isBreak: Boolean get() = this != Work
}

/** Durées du cycle, injectées (Koin) pour que les tests et une future préférence puissent les changer. */
data class FocusDurations(
    val workMillis: Long = 25 * MINUTE,
    val shortBreakMillis: Long = 5 * MINUTE,
    val longBreakMillis: Long = 15 * MINUTE,
    /** Une pause longue après ce nombre de concentrations menées à terme. */
    val workPhasesBeforeLongBreak: Int = 4,
) {
    fun of(phase: FocusPhase): Long = when (phase) {
        FocusPhase.Work -> workMillis
        FocusPhase.ShortBreak -> shortBreakMillis
        FocusPhase.LongBreak -> longBreakMillis
    }

    /** Phase qui suit [phase], [completedWork] comptant déjà la concentration qui vient de se terminer. */
    fun next(phase: FocusPhase, completedWork: Int): FocusPhase = when {
        phase.isBreak -> FocusPhase.Work
        completedWork > 0 && completedWork % workPhasesBeforeLongBreak == 0 -> FocusPhase.LongBreak
        else -> FocusPhase.ShortBreak
    }

    companion object {
        const val MINUTE = 60_000L
    }
}

/**
 * Session de concentration en cours, telle qu'enregistrée : une seule à la fois, sur une tâche.
 * En cours : [endsAt] est l'heure de fin de la phase, [pausedRemainingMillis] est `null`. En pause : l'inverse.
 * Le temps de pause n'est pas compté : à la reprise, [endsAt] est recalculé à partir du temps restant.
 */
data class FocusSession(
    val todoId: String,
    val title: String,
    val phase: FocusPhase,
    val phaseDurationMillis: Long,
    val endsAt: Long?,
    val pausedRemainingMillis: Long?,
    /** Concentrations menées à terme depuis le début de la session (une concentration écourtée ne compte pas). */
    val completedWorkPhases: Int,
) {
    val isPaused: Boolean get() = endsAt == null

    /** Temps restant à [now], jamais négatif. */
    fun remainingAt(now: Long): Long =
        endsAt?.let { (it - now).coerceAtLeast(0) } ?: (pausedRemainingMillis ?: 0).coerceAtLeast(0)

    fun isFinishedAt(now: Long): Boolean = endsAt != null && endsAt <= now
}

package com.mvp.feature.todo.domain.model

/**
 * Tâches terminées pendant la semaine qui commence à [weekStart] : lundi 00:00 dans le fuseau de l'appareil, en
 * millisecondes depuis l'epoch (UTC). Les apps en tirent le libellé (« 20 oct. ») dans le même fuseau.
 */
data class WeekCompletion(val weekStart: Long, val count: Int)

/**
 * Statistiques des tâches terminées (ADR 0019). [weeks] : les `ObserveCompletionStatsUseCase.WEEK_COUNT` dernières
 * semaines, de la plus ancienne à la semaine en cours (dernière), y compris celles sans tâche terminée.
 * [total] : tâches terminées sur ces semaines ; [best] : le plus grand nombre en une semaine (0 si aucune).
 */
data class CompletionStats(val weeks: List<WeekCompletion>, val total: Int, val best: Int)

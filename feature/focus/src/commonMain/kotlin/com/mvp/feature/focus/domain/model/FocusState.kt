package com.mvp.feature.focus.domain.model

enum class FocusStatus { Running, Paused, Finished }

/**
 * Ce que les apps affichent d'une session (écran Focus, notification en cours Android, Live Activity iOS).
 * Les booléens doublent [status] et [phase] pour Swift, qui renomme les entrées des enums Kotlin.
 */
data class FocusState(
    val todoId: String,
    /** Titre de la tâche, pour l'écran de l'app. */
    val title: String,
    /** Titre à montrer hors de l'app (écran verrouillé, Dynamic Island) : `null` si le verrou Face ID / empreinte est activé. */
    val lockScreenTitle: String?,
    val phase: FocusPhase,
    val status: FocusStatus,
    /** Fin de la phase (epoch ms), seulement en cours : les comptes à rebours du système s'en servent. */
    val endsAt: Long?,
    /** Début de la phase, pauses déduites (`endsAt` - durée), seulement en cours : pour une barre de progression. */
    val phaseStartedAt: Long?,
    /** Temps restant au moment du calcul : figé en pause, 0 une fois la phase terminée. */
    val remainingMillis: Long,
    val phaseDurationMillis: Long,
    val completedWorkPhases: Int,
    /** Phase lancée par « suivant » (StartNextFocusPhaseUseCase). */
    val nextPhase: FocusPhase,
    /** Lien vers la tâche (mvpmodular://tasks/<id>). */
    val link: String,
) {
    val phaseLabel: String get() = phase.label
    val nextPhaseLabel: String get() = nextPhase.label
    val isBreak: Boolean get() = phase.isBreak
    val isRunning: Boolean get() = status == FocusStatus.Running
    val isPaused: Boolean get() = status == FocusStatus.Paused
    val isFinished: Boolean get() = status == FocusStatus.Finished
}

/** État de l'écran Focus, recalculé chaque seconde tant que le compte à rebours tourne. */
data class FocusTimer(
    val state: FocusState,
    val remainingMillis: Long,
    /** « 24:59 » : secondes arrondies au-dessus, la phase affiche 25:00 à son départ et 00:00 à sa fin. */
    val remainingLabel: String,
    /** Part écoulée de la phase, de 0 à 1. */
    val progress: Float,
)

object FocusFormat {
    fun remaining(millis: Long): String {
        val seconds = (millis.coerceAtLeast(0) + 999) / 1000
        val minutes = seconds / 60
        val rest = seconds % 60
        return "${minutes.toString().padStart(2, '0')}:${rest.toString().padStart(2, '0')}"
    }
}

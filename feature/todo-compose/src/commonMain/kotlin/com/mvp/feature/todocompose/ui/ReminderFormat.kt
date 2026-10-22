package com.mvp.feature.todocompose.ui

/** Date et heure du rappel ([at] en epoch ms), au format court de la langue de l'appareil. */
internal expect fun formatReminder(at: Long): String

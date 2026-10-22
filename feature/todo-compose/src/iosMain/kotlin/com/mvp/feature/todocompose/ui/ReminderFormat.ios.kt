package com.mvp.feature.todocompose.ui

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.dateWithTimeIntervalSince1970

// Créé une fois : appelé pour chaque ligne, toujours depuis la composition (thread principal).
private val formatter = NSDateFormatter().apply {
    dateStyle = NSDateFormatterShortStyle
    timeStyle = NSDateFormatterShortStyle
}

internal actual fun formatReminder(at: Long): String =
    formatter.stringFromDate(NSDate.dateWithTimeIntervalSince1970(at / 1000.0))

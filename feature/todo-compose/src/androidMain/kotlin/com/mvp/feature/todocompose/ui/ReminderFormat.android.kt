package com.mvp.feature.todocompose.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal actual fun formatReminder(at: Long): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).format(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()))

package com.mvp.feature.settings.ios

import com.mvp.feature.settings.domain.model.ConsentType

/**
 * Consentement par son nom Kotlin (« Analytics », « CrashReports »...), pour Swift qui renomme les entrées des enum
 * Kotlin ; `null` pour un nom inconnu.
 */
fun consentType(name: String): ConsentType? = ConsentType.entries.firstOrNull { it.name == name }

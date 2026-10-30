package com.mvp.core.featureflags.ios

import com.mvp.core.featureflags.domain.model.FeatureFlag

/**
 * Feature flag par sa clé du JSON distant (« catalog », « preview_banner »), pour Swift qui renomme les entrées des
 * enum Kotlin ; `null` pour une clé inconnue (ADR 0021).
 */
fun featureFlag(key: String): FeatureFlag? = FeatureFlag.fromKey(key)

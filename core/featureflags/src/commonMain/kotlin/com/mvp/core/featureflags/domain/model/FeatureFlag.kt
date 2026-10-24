package com.mvp.core.featureflags.domain.model

/** Étape d'une feature : seule une feature livrée peut être pilotée à distance (ADR 0021). */
enum class FeatureStage {
    /** Pas finie : toujours coupée en release, réglable en debug seulement. */
    InDevelopment,

    /** Livrée : affichée ou masquée à distance, sans nouvelle version. */
    Released,
}

/**
 * Catalogue des feature flags. [key] est le nom stable utilisé dans le JSON distant (`snake_case`) : ne jamais
 * la renommer pour un flag déjà publié. Une clé distante absente de ce catalogue est ignorée.
 */
enum class FeatureFlag(val key: String, val stage: FeatureStage, val defaultEnabled: Boolean, val label: String) {
    Catalog("catalog", FeatureStage.Released, defaultEnabled = true, label = "Onglet Catalogue"),
    PreviewBanner("preview_banner", FeatureStage.InDevelopment, defaultEnabled = false, label = "Carte « Bientôt » dans Réglages"),
    ;

    companion object {
        fun fromKey(key: String): FeatureFlag? = entries.firstOrNull { it.key == key }
    }
}

/** D'où vient la valeur d'un flag, montré dans le menu debug. */
enum class FlagOrigin {
    Default,
    Remote,

    /** Réglage local du menu debug. */
    Override,

    /** Feature en développement dans un build release : coupée, rien ne peut l'allumer. */
    ReleaseLocked,
}

data class FeatureFlagState(val flag: FeatureFlag, val enabled: Boolean, val origin: FlagOrigin)

/** Valeurs connues à un instant : distantes (cache) et réglages locaux, par clé. */
data class FlagInputs(
    val remote: Map<String, Boolean> = emptyMap(),
    val overrides: Map<String, Boolean> = emptyMap(),
)

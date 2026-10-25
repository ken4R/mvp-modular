package com.mvp.core.featureflags.data

/** Adresse du JSON distant, la même sur Android et iOS ; pas modifiable à l'exécution (ADR 0021). */
internal object FeatureFlagsConfig {
    const val REMOTE_URL = "https://ken4r.github.io/mvp-modular/flags.json"
}

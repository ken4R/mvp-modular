package com.mvp.core.featureflags.domain.model

/**
 * Règle de priorité d'un flag (ADR 0021) :
 * - en développement : coupée en release quoi qu'il arrive ; en debug, réglage local sinon coupée ;
 * - livrée : réglage local (debug seulement), sinon valeur distante, sinon valeur par défaut.
 */
object FeatureFlagResolver {

    fun resolve(flag: FeatureFlag, inputs: FlagInputs, isDevBuild: Boolean): FeatureFlagState {
        val override = if (isDevBuild) inputs.overrides[flag.key] else null
        return when (flag.stage) {
            FeatureStage.InDevelopment -> when {
                !isDevBuild -> FeatureFlagState(flag, enabled = false, origin = FlagOrigin.ReleaseLocked)
                override != null -> FeatureFlagState(flag, override, FlagOrigin.Override)
                else -> FeatureFlagState(flag, enabled = false, origin = FlagOrigin.Default)
            }
            FeatureStage.Released -> {
                val remote = inputs.remote[flag.key]
                when {
                    override != null -> FeatureFlagState(flag, override, FlagOrigin.Override)
                    remote != null -> FeatureFlagState(flag, remote, FlagOrigin.Remote)
                    else -> FeatureFlagState(flag, flag.defaultEnabled, FlagOrigin.Default)
                }
            }
        }
    }

    fun resolveAll(inputs: FlagInputs, isDevBuild: Boolean): List<FeatureFlagState> =
        FeatureFlag.entries.map { resolve(it, inputs, isDevBuild) }
}

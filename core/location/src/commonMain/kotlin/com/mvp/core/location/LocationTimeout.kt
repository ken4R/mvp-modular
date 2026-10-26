package com.mvp.core.location

/** Au-delà, on renonce : l'utilisateur voit « position indisponible » plutôt qu'un chargement sans fin. */
internal const val LOCATION_TIMEOUT_MS = 15_000L

/** Précision exigée d'une position [com.mvp.core.common.domain.location.LocationAccuracy.Precise], en mètres (ADR 0023). */
internal const val PRECISE_MAX_ACCURACY_METERS = 150f

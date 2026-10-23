package com.mvp.feature.auth.data.source

/**
 * Vrai dans les builds de développement (debug), faux dans les builds distribués (release) :
 * le faux service [FakeAuthRemoteDataSource] n'est jamais utilisé hors développement.
 */
internal expect fun isDemoAuthEnabled(): Boolean

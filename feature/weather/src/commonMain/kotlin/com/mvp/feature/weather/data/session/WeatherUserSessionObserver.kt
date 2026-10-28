package com.mvp.feature.weather.data.session

import com.mvp.core.common.data.safeCall
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.session.UserSessionObserver
import com.mvp.feature.weather.domain.repository.LocalWeatherCache

/** La météo gardée pour le widget contient la position (arrondie) de l'utilisateur : effacée à la déconnexion. */
internal class WeatherUserSessionObserver(private val cache: LocalWeatherCache) : UserSessionObserver {

    override suspend fun clearUserData(): AppResult<Unit> = safeCall { cache.clear() }
}

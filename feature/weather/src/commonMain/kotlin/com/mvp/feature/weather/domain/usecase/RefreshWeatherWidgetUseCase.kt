package com.mvp.feature.weather.domain.usecase

import com.mvp.core.common.domain.location.LocationProvider
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.session.SessionExpiry
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.core.common.domain.usecase.UseCase
import com.mvp.feature.weather.domain.model.CachedLocalWeather
import com.mvp.feature.weather.domain.model.LocalWeather
import com.mvp.feature.weather.domain.repository.LocalWeatherCache
import com.mvp.feature.weather.domain.repository.WeatherRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first

/**
 * Actualise la météo du widget (ADR 0026) pour la position **déjà gardée** (arrondie, celle de la dernière
 * « Ma position ») : la localisation n'est jamais demandée, l'appel peut donc se faire en arrière-plan (mise à jour
 * périodique du widget Android, retour au premier plan sur iOS). Ne fait rien sans session valide, sans météo
 * gardée, ou si elle date de moins de [MIN_INTERVAL_MS]. Si la localisation n'est plus autorisée, la météo gardée
 * est effacée sans rien envoyer : la position ne part plus une fois l'autorisation retirée. En cas d'échec (hors ligne), l'ancienne météo reste
 * jusqu'à son expiration.
 */
class RefreshWeatherWidgetUseCase(
    private val repository: WeatherRepository,
    private val cache: LocalWeatherCache,
    private val session: SessionExpiry,
    private val location: LocationProvider,
    private val now: () -> Long,
    dispatcher: CoroutineDispatcher,
) : UseCase<NoParams, Unit>(dispatcher) {

    override suspend fun execute(params: NoParams): AppResult<Unit> {
        val expiresAt = session.observeExpiresAt().first()
        if (expiresAt == null || expiresAt <= now()) return AppResult.Success(Unit)
        val cached = cache.observe().first() ?: return AppResult.Success(Unit)
        if (!location.isAuthorized()) {
            cache.clear()
            return AppResult.Success(Unit)
        }
        if (now() - cached.fetchedAt < MIN_INTERVAL_MS) return AppResult.Success(Unit)
        val position = cached.local.position
        return when (val weather = repository.getCurrentWeather(position.latitude, position.longitude)) {
            is AppResult.Failure -> weather
            is AppResult.Success -> {
                // Pendant l'appel, une déconnexion a pu effacer la météo, ou « Ma position » en écrire une plus récente
                // (peut-être ailleurs) : on n'écrit que si rien n'a changé.
                if (cache.observe().first() == cached) cache.save(CachedLocalWeather(LocalWeather(position, weather.data), now()))
                AppResult.Success(Unit)
            }
        }
    }

    companion object {
        /** Open-Meteo actualise ses mesures tous les quarts d'heure ; Android rafraîchit le widget toutes les 30 min. */
        const val MIN_INTERVAL_MS = 15 * 60 * 1000L
    }
}

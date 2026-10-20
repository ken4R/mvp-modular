package com.mvp.feature.weather.domain.usecase

import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.UseCase
import com.mvp.feature.weather.domain.model.City
import com.mvp.feature.weather.domain.repository.WeatherRepository
import kotlinx.coroutines.CoroutineDispatcher

/** Recherche des villes par nom. Règle métier : au moins 2 caractères, sans compter les espaces autour. */
class SearchCitiesUseCase(
    private val repository: WeatherRepository,
    dispatcher: CoroutineDispatcher,
) : UseCase<String, List<City>>(dispatcher) {

    override suspend fun execute(params: String): AppResult<List<City>> {
        val query = params.trim()
        return if (query.length < MIN_QUERY_LENGTH) {
            AppResult.Failure(AppError.Validation("$MIN_QUERY_LENGTH caractères minimum"))
        } else {
            repository.searchCities(query)
        }
    }

    companion object {
        const val MIN_QUERY_LENGTH = 2
    }
}

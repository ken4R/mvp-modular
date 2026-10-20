package com.mvp.shared

import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.feature.todo.domain.model.Todo
import com.mvp.feature.todo.domain.usecase.AddTodoUseCase
import com.mvp.feature.todo.domain.usecase.DeleteTodoUseCase
import com.mvp.feature.todo.domain.usecase.ObserveTodosUseCase
import com.mvp.feature.todo.domain.usecase.ToggleTodoUseCase
import com.mvp.feature.weather.domain.usecase.GetCurrentWeatherUseCase
import com.mvp.feature.weather.domain.usecase.SearchCitiesUseCase
import com.mvp.shared.di.initKoin
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/** Appelé une seule fois au lancement de l'app iOS. */
fun doInitKoin() {
    initKoin()
}

/** Abonnement à un Flow côté Swift : appeler `cancel()` quand la vue disparaît. */
class Subscription internal constructor(private val job: Job) {
    fun cancel() = job.cancel()
}

/**
 * Point d'accès Swift aux use cases (Koin n'est pas utilisable directement depuis Swift).
 * Les use cases suspend s'appellent en `async` côté Swift ; les Flow passent par des callbacks.
 */
object UseCases : KoinComponent {
    private val scope = MainScope()

    fun addTodo(): AddTodoUseCase = get()
    fun toggleTodo(): ToggleTodoUseCase = get()
    fun deleteTodo(): DeleteTodoUseCase = get()

    fun searchCities(): SearchCitiesUseCase = get()
    fun getCurrentWeather(): GetCurrentWeatherUseCase = get()

    /** Les callbacks sont appelés sur le main thread. */
    fun observeTodos(onTodos: (List<Todo>) -> Unit, onError: (AppError) -> Unit): Subscription {
        val job = get<ObserveTodosUseCase>()(NoParams)
            .onEach { result ->
                when (result) {
                    is AppResult.Success -> onTodos(result.data)
                    is AppResult.Failure -> onError(result.error)
                }
            }
            .launchIn(scope)
        return Subscription(job)
    }
}

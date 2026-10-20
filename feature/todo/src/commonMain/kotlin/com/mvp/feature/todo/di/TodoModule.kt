package com.mvp.feature.todo.di

import com.mvp.core.common.dispatcher.AppDispatchers
import com.mvp.core.common.domain.session.UserSessionObserver
import com.mvp.core.database.DatabaseDriverFactory
import com.mvp.feature.todo.data.db.TodoDatabase
import com.mvp.feature.todo.data.repository.TodoRepositoryImpl
import com.mvp.feature.todo.data.session.TodoUserSessionObserver
import com.mvp.feature.todo.data.source.SqlDelightTodoLocalDataSource
import com.mvp.feature.todo.data.source.TodoLocalDataSource
import com.mvp.feature.todo.domain.repository.TodoRepository
import com.mvp.feature.todo.domain.usecase.AddTodoUseCase
import com.mvp.feature.todo.domain.usecase.DeleteTodoUseCase
import com.mvp.feature.todo.domain.usecase.ObserveTodosUseCase
import com.mvp.feature.todo.domain.usecase.ToggleTodoUseCase
import org.koin.dsl.bind
import org.koin.dsl.module

val todoModule = module {
    // Data
    single { TodoDatabase(get<DatabaseDriverFactory>().create(TodoDatabase.Schema, "todo.db")) }
    single<TodoLocalDataSource> { SqlDelightTodoLocalDataSource(get(), get<AppDispatchers>().io) }
    single<TodoRepository> { TodoRepositoryImpl(get()) }
    // Prévenu par feature:auth : les tâches sont effacées à la déconnexion.
    single { TodoUserSessionObserver(get()) } bind UserSessionObserver::class

    // Domain
    factory { ObserveTodosUseCase(get(), get<AppDispatchers>().io) }
    factory { AddTodoUseCase(get(), get<AppDispatchers>().io) }
    factory { ToggleTodoUseCase(get(), get<AppDispatchers>().io) }
    factory { DeleteTodoUseCase(get(), get<AppDispatchers>().io) }
}

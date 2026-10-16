package com.mvp.feature.todo.di

import com.mvp.core.common.dispatcher.AppDispatchers
import com.mvp.feature.todo.data.repository.TodoRepositoryImpl
import com.mvp.feature.todo.data.source.InMemoryTodoLocalDataSource
import com.mvp.feature.todo.data.source.TodoLocalDataSource
import com.mvp.feature.todo.domain.repository.TodoRepository
import com.mvp.feature.todo.domain.usecase.AddTodoUseCase
import com.mvp.feature.todo.domain.usecase.DeleteTodoUseCase
import com.mvp.feature.todo.domain.usecase.ObserveTodosUseCase
import com.mvp.feature.todo.domain.usecase.ToggleTodoUseCase
import org.koin.dsl.module

val todoModule = module {
    // Data
    single<TodoLocalDataSource> { InMemoryTodoLocalDataSource() }
    single<TodoRepository> { TodoRepositoryImpl(get()) }

    // Domain
    factory { ObserveTodosUseCase(get(), get<AppDispatchers>().io) }
    factory { AddTodoUseCase(get(), get<AppDispatchers>().io) }
    factory { ToggleTodoUseCase(get(), get<AppDispatchers>().io) }
    factory { DeleteTodoUseCase(get(), get<AppDispatchers>().io) }
}

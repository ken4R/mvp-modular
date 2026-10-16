package com.mvp.shared.di

import com.mvp.core.common.di.coreCommonModule
import com.mvp.feature.todo.di.todoModule
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration

/**
 * Point de coordination : liste de tous les modules KMP du projet.
 * Chaque nouveau module (feature, design system, network...) expose son module Koin
 * et l'ajoute ici, sans que les apps Android / iOS aient à changer.
 */
val sharedModules: List<Module> = listOf(
    coreCommonModule,
    todoModule,
)

fun initKoin(appDeclaration: KoinAppDeclaration = {}) = startKoin {
    appDeclaration()
    modules(sharedModules)
}

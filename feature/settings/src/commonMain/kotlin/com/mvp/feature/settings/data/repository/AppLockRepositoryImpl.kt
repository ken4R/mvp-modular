package com.mvp.feature.settings.data.repository

import com.mvp.feature.settings.data.source.SettingsLocalDataSource
import com.mvp.feature.settings.domain.repository.AppLockRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Verrouillé = réglage activé et pas d'authentification réussie depuis le lancement ou le dernier passage en arrière-plan. */
internal class AppLockRepositoryImpl(private val settings: SettingsLocalDataSource) : AppLockRepository {

    // En mémoire seulement : à chaque lancement de l'app, il faut s'authentifier de nouveau.
    private val unlocked = MutableStateFlow(false)

    override fun observeLocked(): Flow<Boolean> =
        combine(settings.observe().map { it.appLockEnabled }, unlocked) { enabled, unlocked -> enabled && !unlocked }
            .distinctUntilChanged()

    override fun lock() {
        unlocked.value = false
    }

    override fun unlock() {
        unlocked.value = true
    }
}

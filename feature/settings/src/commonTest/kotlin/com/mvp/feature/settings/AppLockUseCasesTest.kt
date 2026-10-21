package com.mvp.feature.settings

import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.result.getOrNull
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.feature.settings.data.repository.AppLockRepositoryImpl
import com.mvp.feature.settings.data.repository.SettingsRepositoryImpl
import com.mvp.feature.settings.data.source.InMemorySettingsLocalDataSource
import com.mvp.feature.settings.data.source.SecureProfileLocalDataSource
import com.mvp.feature.settings.domain.model.UserSettings
import com.mvp.feature.settings.domain.usecase.LockAppUseCase
import com.mvp.feature.settings.domain.usecase.ObserveAppLockUseCase
import com.mvp.feature.settings.domain.usecase.ObserveSettingsUseCase
import com.mvp.feature.settings.domain.usecase.SetAppLockEnabledUseCase
import com.mvp.feature.settings.domain.usecase.UnlockAppUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppLockUseCasesTest {

    private val scope = TestScope()
    private val dispatcher = StandardTestDispatcher(scope.testScheduler)
    private val local = InMemorySettingsLocalDataSource()
    private val settings = SettingsRepositoryImpl(local, SecureProfileLocalDataSource(InMemorySecureStorage(), legacy = local))

    // Un repository neuf = un nouveau lancement de l'app.
    private var appLock = AppLockRepositoryImpl(local)

    private fun relaunch() {
        appLock = AppLockRepositoryImpl(local)
    }

    private suspend fun locked(): Boolean = ObserveAppLockUseCase(appLock, dispatcher)(NoParams).first().getOrNull()!!
    private suspend fun setEnabled(enabled: Boolean) = SetAppLockEnabledUseCase(settings, appLock, dispatcher)(enabled)
    private suspend fun lock() = LockAppUseCase(appLock, dispatcher)(NoParams)
    private suspend fun unlock() = UnlockAppUseCase(appLock, dispatcher)(NoParams)

    @Test
    fun lockIsOffByDefault() = scope.runTest {
        assertFalse(UserSettings.initial().appLockEnabled)
        assertFalse(locked())

        lock()

        assertFalse(locked())
    }

    @Test
    fun enablingKeepsTheAppOpenAndIsSaved() = scope.runTest {
        assertEquals(AppResult.Success(Unit), setEnabled(true))

        assertFalse(locked())
        assertTrue(ObserveSettingsUseCase(settings, dispatcher)(NoParams).first().getOrNull()!!.appLockEnabled)
    }

    @Test
    fun appIsLockedAtLaunchWhenEnabled() = scope.runTest {
        setEnabled(true)

        relaunch()

        assertTrue(locked())
    }

    @Test
    fun goingToBackgroundLocksUntilUnlocked() = scope.runTest {
        setEnabled(true)

        lock()
        assertTrue(locked())

        unlock()
        assertFalse(locked())
    }

    @Test
    fun enablingAfterGoingToBackgroundKeepsTheAppOpen() = scope.runTest {
        // Android 8-10 : l'écran de confirmation du code fait passer l'app en arrière-plan avant l'activation.
        lock()

        setEnabled(true)

        assertFalse(locked())
    }

    @Test
    fun disablingRemovesTheLock() = scope.runTest {
        setEnabled(true)
        lock()

        setEnabled(false)

        assertFalse(locked())
        relaunch()
        assertFalse(locked())
    }

    @Test
    fun clearingUserDataAtLogoutRemovesTheLock() = scope.runTest {
        setEnabled(true)
        relaunch()

        local.clear()

        assertFalse(locked())
    }
}

package com.mvp.feature.settings.ios

import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.core.common.ios.Subscription
import com.mvp.core.common.ios.SwiftUseCases
import com.mvp.core.designsystem.Appearance
import com.mvp.feature.settings.domain.model.UserSettings
import com.mvp.feature.settings.domain.usecase.LockAppUseCase
import com.mvp.feature.settings.domain.usecase.ObserveAppLockUseCase
import com.mvp.feature.settings.domain.usecase.ObserveAppearanceUseCase
import com.mvp.feature.settings.domain.usecase.ObserveSettingsUseCase
import com.mvp.feature.settings.domain.usecase.SetAccentColorUseCase
import com.mvp.feature.settings.domain.usecase.SetAppLockEnabledUseCase
import com.mvp.feature.settings.domain.usecase.SetConsentUseCase
import com.mvp.feature.settings.domain.usecase.SetNotificationsEnabledUseCase
import com.mvp.feature.settings.domain.usecase.SetThemeModeUseCase
import com.mvp.feature.settings.domain.usecase.UnlockAppUseCase
import com.mvp.feature.settings.domain.usecase.UpdateProfileUseCase
import org.koin.core.component.get

/**
 * Point d'accès Swift aux use cases des réglages.
 * Koin n'est pas utilisable directement depuis Swift. Les use cases suspend s'appellent en `async` côté Swift ;
 * les Flow passent par des callbacks (voir SwiftUseCases).
 */
object SettingsUseCases : SwiftUseCases() {
    fun updateProfile(): UpdateProfileUseCase = get()
    fun setNotificationsEnabled(): SetNotificationsEnabledUseCase = get()
    fun setConsent(): SetConsentUseCase = get()
    fun setAppLockEnabled(): SetAppLockEnabledUseCase = get()
    fun setThemeMode(): SetThemeModeUseCase = get()
    fun setAccentColor(): SetAccentColorUseCase = get()
    fun lockApp(): LockAppUseCase = get()
    fun unlockApp(): UnlockAppUseCase = get()

    /** Les callbacks sont appelés sur le main thread. */
    fun observeSettings(onSettings: (UserSettings) -> Unit, onError: (AppError) -> Unit): Subscription =
        get<ObserveSettingsUseCase>()(NoParams).subscribe(onSettings, onError)

    /** Thème et accent choisis dans Réglages (ADR 0015). Les callbacks sont appelés sur le main thread. */
    fun observeAppearance(onAppearance: (Appearance) -> Unit, onError: (AppError) -> Unit): Subscription =
        get<ObserveAppearanceUseCase>()(NoParams).subscribe(onAppearance, onError)

    /** `true` : afficher l'écran de déverrouillage (Face ID / Touch ID). Les callbacks sont appelés sur le main thread. */
    fun observeAppLock(onLocked: (Boolean) -> Unit, onError: (AppError) -> Unit): Subscription =
        get<ObserveAppLockUseCase>()(NoParams).subscribe(onLocked, onError)
}

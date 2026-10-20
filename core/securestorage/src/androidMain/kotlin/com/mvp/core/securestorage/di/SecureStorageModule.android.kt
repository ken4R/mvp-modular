package com.mvp.core.securestorage.di

import android.content.Context
import com.mvp.core.securestorage.KeystoreSecureStorage
import com.mvp.core.securestorage.SecureStorage
import org.koin.core.module.Module
import org.koin.dsl.module

/** Le `Context` vient de `androidContext(...)`, passé à `initKoin` par l'application. */
actual val secureStorageModule: Module = module {
    single<SecureStorage> { KeystoreSecureStorage(get<Context>()) }
}

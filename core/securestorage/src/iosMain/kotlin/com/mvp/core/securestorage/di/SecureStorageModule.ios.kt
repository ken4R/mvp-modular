package com.mvp.core.securestorage.di

import com.mvp.core.securestorage.KeychainSecureStorage
import com.mvp.core.securestorage.SecureStorage
import org.koin.core.module.Module
import org.koin.dsl.module

actual val secureStorageModule: Module = module {
    single<SecureStorage> { KeychainSecureStorage() }
}

package com.mvp.core.files.di

import com.mvp.core.files.NativePrivateFileStore
import com.mvp.core.files.PrivateFileStoreFactory
import org.koin.core.module.Module
import org.koin.dsl.module

actual val filesModule: Module = module {
    single<PrivateFileStoreFactory> { PrivateFileStoreFactory(NativePrivateFileStore::inApplicationSupport) }
}

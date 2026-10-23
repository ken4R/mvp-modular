package com.mvp.core.files.di

import android.content.Context
import com.mvp.core.files.AndroidPrivateFileStore
import com.mvp.core.files.PrivateFileStore
import com.mvp.core.files.PrivateFileStoreFactory
import org.koin.core.module.Module
import org.koin.dsl.module
import java.io.File

/** Le `Context` vient de `androidContext(...)`, passé à `initKoin` par l'application. */
actual val filesModule: Module = module {
    single<PrivateFileStoreFactory> {
        val context = get<Context>()
        PrivateFileStoreFactory { folder ->
            require(PrivateFileStore.isValidName(folder)) { "Nom de dossier refusé" }
            AndroidPrivateFileStore(File(context.noBackupFilesDir, folder))
        }
    }
}

package com.mvp.core.files

import java.io.File
import java.nio.file.Files

actual fun createTestFileStore(): PrivateFileStore =
    AndroidPrivateFileStore(File(Files.createTempDirectory("files-test").toFile(), "store"))

package com.mvp.core.files

import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID

actual fun createTestFileStore(): PrivateFileStore =
    NativePrivateFileStore("${NSTemporaryDirectory()}files-test-${NSUUID().UUIDString}")

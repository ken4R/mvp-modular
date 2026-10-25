package com.mvp.core.featureflags.data.platform

/** Build de debug : vraie constante de compilation sur Android, binaire de debug sur iOS (ADR 0021). */
internal expect fun isDevBuild(): Boolean

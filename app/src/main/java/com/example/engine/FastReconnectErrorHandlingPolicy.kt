package com.example.engine

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy

@OptIn(UnstableApi::class)
class FastReconnectErrorHandlingPolicy(
    private val settings: PlayerSettings
) : DefaultLoadErrorHandlingPolicy() {

    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        if (!settings.fastReconnectEnabled) {
            return super.getRetryDelayMsFor(loadErrorInfo)
        }
        // Return tuned ultra-fast delay (e.g. 150ms) instead of standard exponential backoff
        return settings.reconnectDelayMs
    }

    override fun getMinimumLoadableRetryCount(dataType: Int): Int {
        if (!settings.fastReconnectEnabled) {
            return super.getMinimumLoadableRetryCount(dataType)
        }
        return settings.maxRetryAttempts
    }
}

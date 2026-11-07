package com.example.engine

data class PlayerSettings(
    val tsExtractorMode: TsExtractorMode = TsExtractorMode.MODE_SINGLE_PMT,
    val forceLiveTimeline: Boolean = true,
    val allowNonIdrKeyframes: Boolean = true,
    val detectAccessUnits: Boolean = true,
    val ignoreSpliceInfoStream: Boolean = true,
    val tsChunkSizeBytes: Int = 1024 * 1024, // 1 MB
    val minBufferMs: Int = 30_000,           // 30 seconds
    val maxBufferMs: Int = 120_000,          // 2 minutes (minutes long as requested)
    val bufferForPlaybackMs: Int = 1_000,    // 1 second for ultra fast start
    val bufferForPlaybackAfterRebufferMs: Int = 2_000, // 2 seconds
    val targetBufferSizeBytes: Int = -1,     // -1 for auto
    val fastReconnectEnabled: Boolean = true,
    val reconnectDelayMs: Long = 150L,       // 150ms for lightning fast reconnection
    val maxRetryAttempts: Int = 999,         // Continuous infinite retry
    val keepLastFrameOnReconnect: Boolean = true, // Stuck on last frame, no black screen
    val hideBufferingSpinner: Boolean = true,     // No spinning circle
    val userAgent: String = USER_AGENT_SMARTERS,
    val connectTimeoutMs: Int = 8_000,
    val readTimeoutMs: Int = 12_000,
    val useHardwareAcceleration: Boolean = true,
    // Seamless Dual-Player Crossfade Settings
    val seamlessPlaybackEnabled: Boolean = false,
    val seamlessSegmentDurationSec: Int = 30, // Provider stream segment duration (~30s)
    val seamlessCrossfadeDurationMs: Long = 500L // 500ms crossfade transition
) {
    enum class TsExtractorMode(val label: String, val modeValue: Int) {
        MODE_SINGLE_PMT("MODE_SINGLE_PMT (IPTV Recommended)", 1),
        MODE_MULTI_PMT("MODE_MULTI_PMT (Standard Broadcast)", 0),
        MODE_HLS("MODE_HLS (HLS Chunks)", 2)
    }

    companion object {
        const val USER_AGENT_SMARTERS = "IPTVSmartersPro/3.1.5.1 (Linux; Android)"
        const val USER_AGENT_VLC = "VLC/3.5.4 (Linux; Android)"
        const val USER_AGENT_TIVIMATE = "TiviMate/4.7.0 (Linux; Android)"
        const val USER_AGENT_EXOPLAYER = "ExoPlayerLib/2.19.1"
        const val USER_AGENT_RAW = "RawTS-Player/1.0"
    }
}

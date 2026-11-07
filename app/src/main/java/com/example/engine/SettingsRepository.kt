package com.example.engine

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "raw_player_settings")

class SettingsRepository(private val context: Context) {

    private object PreferencesKeys {
        val TS_EXTRACTOR_MODE = stringPreferencesKey("ts_extractor_mode")
        val FORCE_LIVE_TIMELINE = booleanPreferencesKey("force_live_timeline")
        val ALLOW_NON_IDR_KEYFRAMES = booleanPreferencesKey("allow_non_idr_keyframes")
        val DETECT_ACCESS_UNITS = booleanPreferencesKey("detect_access_units")
        val IGNORE_SPLICE_INFO = booleanPreferencesKey("ignore_splice_info")
        val TS_CHUNK_SIZE_BYTES = intPreferencesKey("ts_chunk_size_bytes")
        val MIN_BUFFER_MS = intPreferencesKey("min_buffer_ms")
        val MAX_BUFFER_MS = intPreferencesKey("max_buffer_ms")
        val BUFFER_FOR_PLAYBACK_MS = intPreferencesKey("buffer_for_playback_ms")
        val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = intPreferencesKey("buffer_for_playback_after_rebuffer_ms")
        val TARGET_BUFFER_SIZE_BYTES = intPreferencesKey("target_buffer_size_bytes")
        val FAST_RECONNECT_ENABLED = booleanPreferencesKey("fast_reconnect_enabled")
        val RECONNECT_DELAY_MS = longPreferencesKey("reconnect_delay_ms")
        val MAX_RETRY_ATTEMPTS = intPreferencesKey("max_retry_attempts")
        val KEEP_LAST_FRAME = booleanPreferencesKey("keep_last_frame")
        val HIDE_BUFFERING_SPINNER = booleanPreferencesKey("hide_buffering_spinner")
        val USER_AGENT = stringPreferencesKey("user_agent")
        val CONNECT_TIMEOUT_MS = intPreferencesKey("connect_timeout_ms")
        val READ_TIMEOUT_MS = intPreferencesKey("read_timeout_ms")
        val USE_HW_ACCELERATION = booleanPreferencesKey("use_hw_acceleration")
        val SEAMLESS_PLAYBACK_ENABLED = booleanPreferencesKey("seamless_playback_enabled")
        val SEAMLESS_SEGMENT_DURATION_SEC = intPreferencesKey("seamless_segment_duration_sec")
        val SEAMLESS_CROSSFADE_DURATION_MS = longPreferencesKey("seamless_crossfade_duration_ms")
    }

    val settingsFlow: Flow<PlayerSettings> = context.dataStore.data.map { preferences ->
        val modeStr = preferences[PreferencesKeys.TS_EXTRACTOR_MODE] ?: PlayerSettings.TsExtractorMode.MODE_SINGLE_PMT.name
        val mode = try {
            PlayerSettings.TsExtractorMode.valueOf(modeStr)
        } catch (_: Exception) {
            PlayerSettings.TsExtractorMode.MODE_SINGLE_PMT
        }

        PlayerSettings(
            tsExtractorMode = mode,
            forceLiveTimeline = preferences[PreferencesKeys.FORCE_LIVE_TIMELINE] ?: true,
            allowNonIdrKeyframes = preferences[PreferencesKeys.ALLOW_NON_IDR_KEYFRAMES] ?: true,
            detectAccessUnits = preferences[PreferencesKeys.DETECT_ACCESS_UNITS] ?: true,
            ignoreSpliceInfoStream = preferences[PreferencesKeys.IGNORE_SPLICE_INFO] ?: true,
            tsChunkSizeBytes = preferences[PreferencesKeys.TS_CHUNK_SIZE_BYTES] ?: (1024 * 1024),
            minBufferMs = preferences[PreferencesKeys.MIN_BUFFER_MS] ?: 30_000,
            maxBufferMs = preferences[PreferencesKeys.MAX_BUFFER_MS] ?: 120_000,
            bufferForPlaybackMs = preferences[PreferencesKeys.BUFFER_FOR_PLAYBACK_MS] ?: 1_000,
            bufferForPlaybackAfterRebufferMs = preferences[PreferencesKeys.BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS] ?: 2_000,
            targetBufferSizeBytes = preferences[PreferencesKeys.TARGET_BUFFER_SIZE_BYTES] ?: -1,
            fastReconnectEnabled = preferences[PreferencesKeys.FAST_RECONNECT_ENABLED] ?: true,
            reconnectDelayMs = preferences[PreferencesKeys.RECONNECT_DELAY_MS] ?: 150L,
            maxRetryAttempts = preferences[PreferencesKeys.MAX_RETRY_ATTEMPTS] ?: 999,
            keepLastFrameOnReconnect = preferences[PreferencesKeys.KEEP_LAST_FRAME] ?: true,
            hideBufferingSpinner = preferences[PreferencesKeys.HIDE_BUFFERING_SPINNER] ?: true,
            userAgent = preferences[PreferencesKeys.USER_AGENT] ?: PlayerSettings.USER_AGENT_SMARTERS,
            connectTimeoutMs = preferences[PreferencesKeys.CONNECT_TIMEOUT_MS] ?: 8_000,
            readTimeoutMs = preferences[PreferencesKeys.READ_TIMEOUT_MS] ?: 12_000,
            useHardwareAcceleration = preferences[PreferencesKeys.USE_HW_ACCELERATION] ?: true,
            seamlessPlaybackEnabled = preferences[PreferencesKeys.SEAMLESS_PLAYBACK_ENABLED] ?: false,
            seamlessSegmentDurationSec = preferences[PreferencesKeys.SEAMLESS_SEGMENT_DURATION_SEC] ?: 30,
            seamlessCrossfadeDurationMs = preferences[PreferencesKeys.SEAMLESS_CROSSFADE_DURATION_MS] ?: 500L
        )
    }

    suspend fun updateSettings(settings: PlayerSettings) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.TS_EXTRACTOR_MODE] = settings.tsExtractorMode.name
            preferences[PreferencesKeys.FORCE_LIVE_TIMELINE] = settings.forceLiveTimeline
            preferences[PreferencesKeys.ALLOW_NON_IDR_KEYFRAMES] = settings.allowNonIdrKeyframes
            preferences[PreferencesKeys.DETECT_ACCESS_UNITS] = settings.detectAccessUnits
            preferences[PreferencesKeys.IGNORE_SPLICE_INFO] = settings.ignoreSpliceInfoStream
            preferences[PreferencesKeys.TS_CHUNK_SIZE_BYTES] = settings.tsChunkSizeBytes
            preferences[PreferencesKeys.MIN_BUFFER_MS] = settings.minBufferMs
            preferences[PreferencesKeys.MAX_BUFFER_MS] = settings.maxBufferMs
            preferences[PreferencesKeys.BUFFER_FOR_PLAYBACK_MS] = settings.bufferForPlaybackMs
            preferences[PreferencesKeys.BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS] = settings.bufferForPlaybackAfterRebufferMs
            preferences[PreferencesKeys.TARGET_BUFFER_SIZE_BYTES] = settings.targetBufferSizeBytes
            preferences[PreferencesKeys.FAST_RECONNECT_ENABLED] = settings.fastReconnectEnabled
            preferences[PreferencesKeys.RECONNECT_DELAY_MS] = settings.reconnectDelayMs
            preferences[PreferencesKeys.MAX_RETRY_ATTEMPTS] = settings.maxRetryAttempts
            preferences[PreferencesKeys.KEEP_LAST_FRAME] = settings.keepLastFrameOnReconnect
            preferences[PreferencesKeys.HIDE_BUFFERING_SPINNER] = settings.hideBufferingSpinner
            preferences[PreferencesKeys.USER_AGENT] = settings.userAgent
            preferences[PreferencesKeys.CONNECT_TIMEOUT_MS] = settings.connectTimeoutMs
            preferences[PreferencesKeys.READ_TIMEOUT_MS] = settings.readTimeoutMs
            preferences[PreferencesKeys.USE_HW_ACCELERATION] = settings.useHardwareAcceleration
            preferences[PreferencesKeys.SEAMLESS_PLAYBACK_ENABLED] = settings.seamlessPlaybackEnabled
            preferences[PreferencesKeys.SEAMLESS_SEGMENT_DURATION_SEC] = settings.seamlessSegmentDurationSec
            preferences[PreferencesKeys.SEAMLESS_CROSSFADE_DURATION_MS] = settings.seamlessCrossfadeDurationMs
        }
    }

    suspend fun resetToDefaults() {
        updateSettings(PlayerSettings())
    }
}

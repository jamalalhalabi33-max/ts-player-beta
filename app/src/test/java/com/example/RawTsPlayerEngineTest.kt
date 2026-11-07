package com.example

import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import com.example.engine.CustomTsExtractorsFactory
import com.example.engine.FastReconnectErrorHandlingPolicy
import com.example.engine.PlayerSettings
import com.example.engine.SafeTsExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RawTsPlayerEngineTest {

    @Test
    fun defaultSettings_hasSinglePmtAndLiveTimelineEnabled() {
        val settings = PlayerSettings()
        assertEquals(PlayerSettings.TsExtractorMode.MODE_SINGLE_PMT, settings.tsExtractorMode)
        assertTrue(settings.forceLiveTimeline)
        assertTrue(settings.allowNonIdrKeyframes)
        assertTrue(settings.fastReconnectEnabled)
        assertTrue(settings.keepLastFrameOnReconnect)
        assertTrue(settings.hideBufferingSpinner)
        assertEquals(1024 * 1024, settings.tsChunkSizeBytes)
        assertEquals(120_000, settings.maxBufferMs)
        assertFalse(settings.seamlessPlaybackEnabled)
        assertEquals(30, settings.seamlessSegmentDurationSec)
        assertEquals(500L, settings.seamlessCrossfadeDurationMs)
    }

    @Test
    fun customTsExtractorsFactory_createsInstance() {
        val settings = PlayerSettings(
            tsExtractorMode = PlayerSettings.TsExtractorMode.MODE_SINGLE_PMT,
            allowNonIdrKeyframes = true,
            detectAccessUnits = true,
            ignoreSpliceInfoStream = true
        )
        val factory = CustomTsExtractorsFactory.create(settings)
        assertNotNull(factory)
    }

    @Test
    fun safeTsExtractor_swallowsSeekInHlsModeWithoutException() {
        var seekCalled = false
        val fakeDelegate = object : Extractor {
            override fun init(output: ExtractorOutput) {}
            override fun sniff(input: ExtractorInput): Boolean = true
            override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int = 0
            override fun seek(position: Long, timeUs: Long) {
                seekCalled = true
                throw IllegalStateException("checkState(mode != MODE_HLS)")
            }
            override fun release() {}
        }

        // Branch 1: In HLS mode, seek() is completely swallowed to avoid TsExtractor.java:384 checkState
        val safeExtractorHls = SafeTsExtractor(fakeDelegate, isHlsMode = true)
        safeExtractorHls.seek(0L, 0L)
        assertFalse(seekCalled)

        // Branch 2: In non-HLS mode, seek() delegates and catches any unexpected IllegalStateException
        val safeExtractorNonHls = SafeTsExtractor(fakeDelegate, isHlsMode = false)
        safeExtractorNonHls.seek(0L, 0L)
        assertTrue(seekCalled)
    }

    @Test
    fun fastReconnectPolicy_returnsConfiguredDelays() {
        val settings = PlayerSettings(
            fastReconnectEnabled = true,
            reconnectDelayMs = 150L,
            maxRetryAttempts = 500
        )
        val policy = FastReconnectErrorHandlingPolicy(settings)
        assertEquals(500, policy.getMinimumLoadableRetryCount(0))
    }

    @Test
    fun seamlessPlaybackSettings_canBeConfigured() {
        val settings = PlayerSettings(
            seamlessPlaybackEnabled = true,
            seamlessSegmentDurationSec = 25,
            seamlessCrossfadeDurationMs = 350L
        )
        assertTrue(settings.seamlessPlaybackEnabled)
        assertEquals(25, settings.seamlessSegmentDurationSec)
        assertEquals(350L, settings.seamlessCrossfadeDurationMs)
    }
}

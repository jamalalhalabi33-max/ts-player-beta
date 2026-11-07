package com.example.engine

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.SinglePeriodTimeline
import androidx.media3.exoplayer.source.WrappingMediaSource

/**
 * Wraps a MediaSource (such as ProgressiveMediaSource for raw HTTP MPEG-TS live feeds).
 * When forceLiveTimeline is true, it overrides the Timeline to report an unbounded LIVE window
 * (windowDurationUs = C.TIME_UNSET, isLive = true, isDynamic = true, isSeekable = false).
 *
 * This completely resolves the IPTV TS issue where ExoPlayer calculates a fake ~30s duration
 * from initial PCR timestamps and crashes or terminates playback after 30 seconds.
 */
@OptIn(UnstableApi::class)
class LiveTsWrappingMediaSource(
    childMediaSource: MediaSource,
    private val forceLiveTimeline: Boolean
) : WrappingMediaSource(childMediaSource) {

    override fun onChildSourceInfoRefreshed(newTimeline: Timeline) {
        if (forceLiveTimeline) {
            val liveTimeline = SinglePeriodTimeline(
                /* periodDurationUs = */ C.TIME_UNSET,
                /* windowDurationUs = */ C.TIME_UNSET,
                /* windowPositionInPeriodUs = */ 0L,
                /* windowDefaultStartPositionUs = */ 0L,
                /* isSeekable = */ false,
                /* isDynamic = */ true,
                /* useLiveConfiguration = */ true,
                /* manifest = */ null,
                /* mediaItem = */ mediaItem
            )
            refreshSourceInfo(liveTimeline)
        } else {
            super.onChildSourceInfoRefreshed(newTimeline)
        }
    }
}

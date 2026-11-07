package com.example.engine

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class PlayerTelemetry(
    val playbackState: Int = Player.STATE_IDLE,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val bufferedDurationMs: Long = 0L,
    val currentPositionMs: Long = 0L,
    val videoResolution: String = "",
    val videoCodec: String = "",
    val audioDetails: String = "",
    val reconnectCount: Int = 0,
    val lastError: String? = null,
    val isLive: Boolean = true,
    // Seamless dual-player telemetry
    val seamlessActivePlayer: String = "Player A",
    val seamlessCycleCount: Int = 0,
    val isSeamlessTransitioning: Boolean = false,
    val incomingReady: Boolean = false
)

@OptIn(UnstableApi::class)
class RawExoPlayerManager(
    private val context: Context,
    private val settings: PlayerSettings
) {

    // Dual ExoPlayer instances (Player A = index 0, Player B = index 1)
    private var playerA: ExoPlayer? = null
    private var playerB: ExoPlayer? = null

    private var baseStreamUri: Uri? = null
    private var currentUri: Uri? = null
    private var currentTitle: String = ""
    private val handler = Handler(Looper.getMainLooper())
    private var retryCount = 0
    private var telemetryJob: Job? = null
    private var seamlessLoopJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private val transitionMutex = Mutex()

    // Active player index: 0 for Player A, 1 for Player B
    private val _activePlayerIndex = MutableStateFlow(0)
    val activePlayerIndex: StateFlow<Int> = _activePlayerIndex.asStateFlow()

    // Video layer opacities for seamless crossfade
    private val _alphaA = MutableStateFlow(1f)
    val alphaA: StateFlow<Float> = _alphaA.asStateFlow()

    private val _alphaB = MutableStateFlow(0f)
    val alphaB: StateFlow<Float> = _alphaB.asStateFlow()

    private val _isTransitioning = MutableStateFlow(false)
    val isTransitioning: StateFlow<Boolean> = _isTransitioning.asStateFlow()

    private var isFirstFrameRenderedA = false
    private var isFirstFrameRenderedB = false

    private var cycleCounter = 0
    private var isPreparingBackground = false
    private var earlyTransitionRequested = false

    private val _telemetry = MutableStateFlow(PlayerTelemetry())
    val telemetry: StateFlow<PlayerTelemetry> = _telemetry.asStateFlow()

    fun getPlayer(): ExoPlayer {
        return getPlayerA()
    }

    fun getPlayerA(): ExoPlayer {
        if (playerA == null) {
            playerA = createExoPlayerInstance(0)
        }
        return playerA!!
    }

    fun getPlayerB(): ExoPlayer {
        if (playerB == null) {
            playerB = createExoPlayerInstance(1)
        }
        return playerB!!
    }

    private fun getPlayerByIndex(index: Int): ExoPlayer {
        return if (index == 0) getPlayerA() else getPlayerB()
    }

    val isSeamlessEnabled: Boolean
        get() = settings.seamlessPlaybackEnabled

    private fun createExoPlayerInstance(index: Int): ExoPlayer {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                settings.minBufferMs,
                settings.maxBufferMs,
                settings.bufferForPlaybackMs,
                settings.bufferForPlaybackAfterRebufferMs
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .apply {
                if (settings.targetBufferSizeBytes > 0) {
                    setTargetBufferBytes(settings.targetBufferSizeBytes)
                }
            }
            .build()

        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)

        val player = ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setRenderersFactory(renderersFactory)
            .build()

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (index == _activePlayerIndex.value) {
                    updateTelemetryState()
                    if (playbackState == Player.STATE_READY) {
                        retryCount = 0
                    }
                    // When the active player reaches STATE_ENDED (stream segment terminated upstream),
                    // trigger immediate handover to the waiting player
                    if (playbackState == Player.STATE_ENDED && settings.seamlessPlaybackEnabled) {
                        earlyTransitionRequested = true
                        handleEarlyTransition()
                    }
                } else {
                    // Background player state update
                    updateTelemetryState()
                }
            }

            override fun onRenderedFirstFrame() {
                if (index == 0) {
                    isFirstFrameRenderedA = true
                } else {
                    isFirstFrameRenderedB = true
                }
                updateTelemetryState()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (index == _activePlayerIndex.value) {
                    updateTelemetryState()
                }
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (index == _activePlayerIndex.value && videoSize.width > 0 && videoSize.height > 0) {
                    _telemetry.value = _telemetry.value.copy(
                        videoResolution = "${videoSize.width}x${videoSize.height}"
                    )
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (index == _activePlayerIndex.value) {
                    retryCount++
                    _telemetry.value = _telemetry.value.copy(
                        lastError = error.message ?: "Playback error",
                        reconnectCount = retryCount
                    )

                    if (settings.seamlessPlaybackEnabled) {
                        // Handover to prepared idle player on error
                        earlyTransitionRequested = true
                        handleEarlyTransition()
                    } else if (settings.fastReconnectEnabled && retryCount <= settings.maxRetryAttempts) {
                        handler.postDelayed({
                            retryPlaybackSingle()
                        }, settings.reconnectDelayMs)
                    }
                }
            }
        })

        return player
    }

    fun playStream(uri: Uri, title: String = "") {
        baseStreamUri = uri
        currentUri = uri
        currentTitle = title
        retryCount = 0
        cycleCounter = 0
        earlyTransitionRequested = false
        isPreparingBackground = false

        seamlessLoopJob?.cancel()

        if (!settings.seamlessPlaybackEnabled) {
            // Standard single-player mode
            val pA = getPlayerA()
            _activePlayerIndex.value = 0
            _alphaA.value = 1f
            _alphaB.value = 0f
            pA.volume = 1f
            val mediaSource = createMediaSource(uri)
            pA.setMediaSource(mediaSource, /* resetPosition = */ true)
            pA.prepare()
            pA.playWhenReady = true
        } else {
            // Dual-Player Seamless Mode (unlimited chunks)
            val pA = getPlayerA()
            val pB = getPlayerB()

            pA.stop()
            pA.clearMediaItems()
            pB.stop()
            pB.clearMediaItems()

            _activePlayerIndex.value = 0
            _alphaA.value = 1f
            _alphaB.value = 0f
            pA.volume = 1f
            pB.volume = 0f
            isFirstFrameRenderedA = false
            isFirstFrameRenderedB = false

            // Start Player A with initial stream
            val initialSource = createMediaSource(generateRefreshedUri(uri, 0))
            pA.setMediaSource(initialSource, /* resetPosition = */ true)
            pA.prepare()
            pA.playWhenReady = true

            // Start unlimited alternating cycle loop: A -> B -> A -> B -> ...
            startSeamlessLoop()
        }

        startTelemetryPolling()
    }

    /**
     * Alternating dual-player loop that runs indefinitely across unlimited stream chunks:
     * Cycle 1: Player A (active) -> Player B (background) -> transition -> Player B (active)
     * Cycle 2: Player B (active) -> Player A (background) -> transition -> Player A (active)
     * Cycle 3: Player A (active) -> Player B (background) -> transition -> Player B (active)
     * ...
     */
    private fun startSeamlessLoop() {
        seamlessLoopJob?.cancel()
        seamlessLoopJob = scope.launch {
            val segmentDurationMs = (settings.seamlessSegmentDurationSec * 1000L).coerceAtLeast(10_000L)
            val crossfadeDurationMs = settings.seamlessCrossfadeDurationMs.coerceIn(150L, 1000L)
            // Start preparing background player 8-10 seconds before segment end
            val prepLeadTimeMs = 8000L.coerceAtMost(segmentDurationMs / 2)
            val waitBeforePrepMs = (segmentDurationMs - prepLeadTimeMs).coerceAtLeast(2000L)

            while (isActive && settings.seamlessPlaybackEnabled) {
                earlyTransitionRequested = false
                val currentActiveIndex = _activePlayerIndex.value
                val idleIndex = if (currentActiveIndex == 0) 1 else 0
                val activePlayer = getPlayerByIndex(currentActiveIndex)
                val idlePlayer = getPlayerByIndex(idleIndex)

                // 1. Wait until preparation time, while checking if active player ended early
                var elapsedMs = 0L
                val pollStepMs = 200L
                while (isActive && elapsedMs < waitBeforePrepMs) {
                    if (earlyTransitionRequested || activePlayer.playbackState == Player.STATE_ENDED) {
                        break
                    }
                    delay(pollStepMs)
                    elapsedMs += pollStepMs
                }

                if (!isActive) break

                // 2. Prepare next refreshed stream chunk on the idle player
                isPreparingBackground = true
                if (idleIndex == 0) isFirstFrameRenderedA = false else isFirstFrameRenderedB = false
                cycleCounter++

                val nextUri = generateRefreshedUri(baseStreamUri ?: currentUri ?: Uri.EMPTY, cycleCounter)
                val nextSource = createMediaSource(nextUri)
                idlePlayer.stop()
                idlePlayer.clearMediaItems()
                idlePlayer.volume = 0f
                idlePlayer.setMediaSource(nextSource, /* resetPosition = */ true)
                idlePlayer.prepare()
                idlePlayer.playWhenReady = true

                updateTelemetryState()

                // 3. Monitor active player until segment expiration or early completion
                val remainingTimeMs = (segmentDurationMs - elapsedMs - crossfadeDurationMs).coerceAtLeast(500L)
                var elapsedWaitMs = 0L
                while (isActive && elapsedWaitMs < remainingTimeMs) {
                    if (earlyTransitionRequested || activePlayer.playbackState == Player.STATE_ENDED) {
                        break
                    }
                    delay(pollStepMs)
                    elapsedWaitMs += pollStepMs
                }

                if (!isActive) break

                // 4. Ensure incoming player is ready with buffered frames before switching
                var waitReadyMs = 0L
                val maxWaitReadyMs = 4000L
                while (isActive && waitReadyMs < maxWaitReadyMs) {
                    val isFirstFrame = if (idleIndex == 0) isFirstFrameRenderedA else isFirstFrameRenderedB
                    if (idlePlayer.playbackState == Player.STATE_READY && (isFirstFrame || waitReadyMs >= 1000L)) {
                        break
                    }
                    delay(50)
                    waitReadyMs += 50
                }

                if (!isActive) break

                // 5. Execute smooth transition (instant audio switch + video opacity crossfade)
                performTransition(currentActiveIndex, idleIndex, crossfadeDurationMs)

                // Loop seamlessly continues to the next cycle with swapped active/idle roles!
            }
        }
    }

    /**
     * Executes the handover from outgoing player to incoming player:
     * - Instant audio handover: outgoing volume immediately becomes 0, incoming immediately becomes 1 (no echo).
     * - Smooth video opacity crossfade between layers over durationMs.
     * - Outgoing player is cleanly stopped and reset for reuse in subsequent cycles.
     */
    private suspend fun performTransition(fromIndex: Int, toIndex: Int, durationMs: Long) {
        transitionMutex.withLock {
            _isTransitioning.value = true
            val fromPlayer = getPlayerByIndex(fromIndex)
            val toPlayer = getPlayerByIndex(toIndex)

            // CRITICAL: Instant Audio Handover!
            // No overlapping audio crossfade to completely eliminate any echo or phasing between streams.
            fromPlayer.volume = 0f
            toPlayer.volume = 1f

            // Smooth video crossfade
            val steps = (durationMs / 16).toInt().coerceAtLeast(8)
            val stepDelayMs = durationMs / steps

            for (step in 0..steps) {
                val progress = step.toFloat() / steps
                if (fromIndex == 0) {
                    _alphaA.value = 1f - progress
                    _alphaB.value = progress
                } else {
                    _alphaB.value = 1f - progress
                    _alphaA.value = progress
                }
                delay(stepDelayMs)
            }

            // Finalize video opacities
            if (toIndex == 0) {
                _alphaA.value = 1f
                _alphaB.value = 0f
            } else {
                _alphaA.value = 0f
                _alphaB.value = 1f
            }

            // Update active player index
            _activePlayerIndex.value = toIndex

            // Cleanly stop and reset outgoing player so it is ready for subsequent reuse
            fromPlayer.stop()
            fromPlayer.clearMediaItems()
            fromPlayer.volume = 0f

            if (fromIndex == 0) {
                isFirstFrameRenderedA = false
            } else {
                isFirstFrameRenderedB = false
            }

            isPreparingBackground = false
            earlyTransitionRequested = false
            _isTransitioning.value = false
            updateTelemetryState()
        }
    }

    private fun handleEarlyTransition() {
        scope.launch {
            if (_isTransitioning.value) return@launch
            val currentActive = _activePlayerIndex.value
            val idleIndex = if (currentActive == 0) 1 else 0
            val idlePlayer = getPlayerByIndex(idleIndex)

            if (idlePlayer.playbackState == Player.STATE_READY) {
                performTransition(currentActive, idleIndex, settings.seamlessCrossfadeDurationMs.coerceIn(150L, 500L))
            } else if (!isPreparingBackground) {
                // Background player was not preparing yet; initiate preparation immediately
                isPreparingBackground = true
                cycleCounter++
                val nextUri = generateRefreshedUri(baseStreamUri ?: currentUri ?: Uri.EMPTY, cycleCounter)
                idlePlayer.stop()
                idlePlayer.clearMediaItems()
                idlePlayer.volume = 0f
                idlePlayer.setMediaSource(createMediaSource(nextUri), /* resetPosition = */ true)
                idlePlayer.prepare()
                idlePlayer.playWhenReady = true
            }
        }
    }

    private fun generateRefreshedUri(baseUri: Uri, cycle: Int): Uri {
        val uriStr = baseUri.toString()
        if (uriStr.isBlank()) return baseUri
        // Generate refreshed stream request ticket with cache-busting timestamp and cycle count
        return baseUri.buildUpon()
            .appendQueryParameter("_cycle", cycle.toString())
            .appendQueryParameter("_ts", System.currentTimeMillis().toString())
            .build()
    }

    private fun retryPlaybackSingle() {
        val uri = currentUri ?: return
        val player = getPlayerA()
        val mediaSource = createMediaSource(uri)
        player.setMediaSource(mediaSource, /* resetPosition = */ false)
        player.prepare()
        player.playWhenReady = true
    }

    private fun createMediaSource(uri: Uri): MediaSource {
        val uriStr = uri.toString().lowercase()
        val dataSourceFactory = IpTvDataSourceFactory(context, settings)
        val loadErrorPolicy = FastReconnectErrorHandlingPolicy(settings)

        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder()
                    .setMaxPlaybackSpeed(1.0f)
                    .setMinPlaybackSpeed(1.0f)
                    .build()
            )
            .build()

        return if (uriStr.contains(".m3u8")) {
            HlsMediaSource.Factory(dataSourceFactory)
                .setLoadErrorHandlingPolicy(loadErrorPolicy)
                .setAllowChunklessPreparation(true)
                .createMediaSource(mediaItem)
        } else {
            val extractorsFactory = CustomTsExtractorsFactory.create(settings)
            val progressiveSource = ProgressiveMediaSource.Factory(dataSourceFactory, extractorsFactory)
                .setLoadErrorHandlingPolicy(loadErrorPolicy)
                .setContinueLoadingCheckIntervalBytes(settings.tsChunkSizeBytes.coerceAtLeast(1024 * 32))
                .createMediaSource(mediaItem)

            LiveTsWrappingMediaSource(progressiveSource, settings.forceLiveTimeline)
        }
    }

    private fun startTelemetryPolling() {
        telemetryJob?.cancel()
        telemetryJob = scope.launch {
            while (isActive) {
                updateTelemetryState()
                delay(1000)
            }
        }
    }

    private fun updateTelemetryState() {
        val activeIndex = _activePlayerIndex.value
        val player = if (activeIndex == 0) playerA else playerB
        if (player == null) return

        val bufferedDuration = (player.bufferedPosition - player.currentPosition).coerceAtLeast(0)
        val videoFormat = player.videoFormat
        val audioFormat = player.audioFormat
        val idleIndex = if (activeIndex == 0) 1 else 0
        val isIncomingReady = if (idleIndex == 0) isFirstFrameRenderedA else isFirstFrameRenderedB
        val idlePlayer = if (idleIndex == 0) playerA else playerB

        _telemetry.value = _telemetry.value.copy(
            playbackState = player.playbackState,
            isPlaying = player.isPlaying,
            isBuffering = player.playbackState == Player.STATE_BUFFERING,
            bufferedDurationMs = bufferedDuration,
            currentPositionMs = player.currentPosition,
            videoResolution = if (player.videoSize.width > 0) "${player.videoSize.width}x${player.videoSize.height}" else _telemetry.value.videoResolution,
            videoCodec = videoFormat?.sampleMimeType ?: _telemetry.value.videoCodec,
            audioDetails = if (audioFormat != null) "${audioFormat.sampleMimeType?.substringAfter('/') ?: "audio"} ${audioFormat.channelCount}ch" else "",
            isLive = player.isCurrentMediaItemLive || settings.forceLiveTimeline,
            seamlessActivePlayer = if (activeIndex == 0) "Player A" else "Player B",
            seamlessCycleCount = cycleCounter,
            isSeamlessTransitioning = _isTransitioning.value,
            incomingReady = isIncomingReady || idlePlayer?.playbackState == Player.STATE_READY
        )
    }

    fun release() {
        seamlessLoopJob?.cancel()
        telemetryJob?.cancel()
        handler.removeCallbacksAndMessages(null)
        playerA?.release()
        playerB?.release()
        playerA = null
        playerB = null
    }
}

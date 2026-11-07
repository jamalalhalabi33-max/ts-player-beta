package com.example.engine

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
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
import kotlin.math.cos
import kotlin.math.sin

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

    private var currentUri: Uri? = null
    private var currentTitle: String = ""
    private val handler = Handler(Looper.getMainLooper())
    private var retryCount = 0
    private var telemetryJob: Job? = null
    private var seamlessLoopJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

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
                    // If active player reaches STATE_ENDED and seamless playback is active,
                    // trigger immediate emergency transition if background player is ready!
                    if (playbackState == Player.STATE_ENDED && settings.seamlessPlaybackEnabled) {
                        handleActivePlayerEndedEarly()
                    }
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
                        // Check if idle player is ready to take over
                        handleActivePlayerEndedEarly()
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
        currentUri = uri
        currentTitle = title
        retryCount = 0
        cycleCounter = 0

        seamlessLoopJob?.cancel()

        if (!settings.seamlessPlaybackEnabled) {
            // Standard single-player mode
            val pA = getPlayerA()
            _activePlayerIndex.value = 0
            _alphaA.value = 1f
            _alphaB.value = 0f
            pA.volume = 1f
            val mediaSource = createMediaSource(uri)
            pA.setMediaSource(mediaSource)
            pA.prepare()
            pA.playWhenReady = true
        } else {
            // Dual-Player Seamless Mode
            val pA = getPlayerA()
            val pB = getPlayerB()
            _activePlayerIndex.value = 0
            _alphaA.value = 1f
            _alphaB.value = 0f
            pA.volume = 1f
            pB.volume = 0f
            isFirstFrameRenderedA = false
            isFirstFrameRenderedB = false

            // Start Player A with initial stream
            val initialSource = createMediaSource(generateRefreshedUri(uri, 0))
            pA.setMediaSource(initialSource)
            pA.prepare()
            pA.playWhenReady = true

            // Launch seamless transition loop
            startSeamlessLoop()
        }

        startTelemetryPolling()
    }

    private fun startSeamlessLoop() {
        seamlessLoopJob?.cancel()
        seamlessLoopJob = scope.launch {
            val segmentDurationMs = (settings.seamlessSegmentDurationSec * 1000L).coerceAtLeast(10_000L)
            val crossfadeDurationMs = settings.seamlessCrossfadeDurationMs.coerceIn(200L, 2000L)
            val prepLeadTimeMs = 6000L.coerceAtMost(segmentDurationMs / 3)

            while (isActive && settings.seamlessPlaybackEnabled) {
                val currentActiveIndex = _activePlayerIndex.value
                val activePlayer = if (currentActiveIndex == 0) getPlayerA() else getPlayerB()
                val idlePlayer = if (currentActiveIndex == 0) getPlayerB() else getPlayerA()
                val idleIndex = if (currentActiveIndex == 0) 1 else 0

                // 1. Wait until prepLeadTimeMs before segment ends
                val waitBeforePrepMs = (segmentDurationMs - prepLeadTimeMs).coerceAtLeast(1000L)
                delay(waitBeforePrepMs)

                if (!isActive) break

                // 2. Prepare next refreshed stream on idle player in the background
                isPreparingBackground = true
                if (idleIndex == 0) isFirstFrameRenderedA = false else isFirstFrameRenderedB = false
                cycleCounter++

                val nextUri = generateRefreshedUri(currentUri ?: Uri.EMPTY, cycleCounter)
                val nextSource = createMediaSource(nextUri)
                idlePlayer.volume = 0f
                idlePlayer.setMediaSource(nextSource)
                idlePlayer.prepare()
                idlePlayer.playWhenReady = true

                updateTelemetryState()

                // 3. Wait until segment end - crossfadeDurationMs
                val waitBeforeTransitionMs = (prepLeadTimeMs - crossfadeDurationMs).coerceAtLeast(500L)
                delay(waitBeforeTransitionMs)

                if (!isActive) break

                // 4. Ensure incoming video has rendered its first frame before transitioning
                var waitedForFrameMs = 0L
                val maxWaitMs = 5000L
                while (isActive && waitedForFrameMs < maxWaitMs) {
                    val isReady = if (idleIndex == 0) isFirstFrameRenderedA else isFirstFrameRenderedB
                    if (isReady && idlePlayer.playbackState == Player.STATE_READY) {
                        break
                    }
                    delay(50)
                    waitedForFrameMs += 50
                }

                if (!isActive) break

                // 5. Execute smooth crossfade between both players
                executeCrossfade(currentActiveIndex, idleIndex, crossfadeDurationMs)

                // 6. Incoming player is now active, old player becomes background and is stopped
                _activePlayerIndex.value = idleIndex
                activePlayer.stop()
                activePlayer.volume = 0f
                isPreparingBackground = false
                updateTelemetryState()
            }
        }
    }

    private suspend fun executeCrossfade(fromIndex: Int, toIndex: Int, durationMs: Long) {
        _isTransitioning.value = true
        val fromPlayer = if (fromIndex == 0) getPlayerA() else getPlayerB()
        val toPlayer = if (toIndex == 0) getPlayerA() else getPlayerB()

        val steps = (durationMs / 16).toInt().coerceAtLeast(10)
        val stepDelayMs = durationMs / steps

        for (step in 0..steps) {
            val progress = step.toFloat() / steps

            // Equal-power audio crossfade curve
            val volFrom = cos(progress * (Math.PI.toFloat() / 2f))
            val volTo = sin(progress * (Math.PI.toFloat() / 2f))

            fromPlayer.volume = volFrom
            toPlayer.volume = volTo

            if (fromIndex == 0) {
                _alphaA.value = 1f - progress
                _alphaB.value = progress
            } else {
                _alphaB.value = 1f - progress
                _alphaA.value = progress
            }

            delay(stepDelayMs)
        }

        // Finalize transition
        fromPlayer.volume = 0f
        toPlayer.volume = 1f
        if (fromIndex == 0) {
            _alphaA.value = 0f
            _alphaB.value = 1f
        } else {
            _alphaA.value = 1f
            _alphaB.value = 0f
        }
        _isTransitioning.value = false
    }

    private fun handleActivePlayerEndedEarly() {
        scope.launch {
            val currentActive = _activePlayerIndex.value
            val idleIndex = if (currentActive == 0) 1 else 0
            val isIdleReady = if (idleIndex == 0) isFirstFrameRenderedA else isFirstFrameRenderedB
            val idlePlayer = if (idleIndex == 0) getPlayerA() else getPlayerB()

            if (isIdleReady && idlePlayer.playbackState == Player.STATE_READY) {
                // Immediately crossfade to ready player
                executeCrossfade(currentActive, idleIndex, 300L)
                _activePlayerIndex.value = idleIndex
            } else {
                // Background player not ready yet; prepare and transition as soon as ready
                if (!isPreparingBackground) {
                    isPreparingBackground = true
                    cycleCounter++
                    val nextUri = generateRefreshedUri(currentUri ?: Uri.EMPTY, cycleCounter)
                    idlePlayer.volume = 0f
                    idlePlayer.setMediaSource(createMediaSource(nextUri))
                    idlePlayer.prepare()
                    idlePlayer.playWhenReady = true
                }
            }
        }
    }

    private fun generateRefreshedUri(baseUri: Uri, cycle: Int): Uri {
        val uriStr = baseUri.toString()
        if (uriStr.isBlank()) return baseUri
        // Append or update timestamp/cycle query to generate fresh stream ticket from provider
        val builder = baseUri.buildUpon()
        builder.appendQueryParameter("_cycle", cycle.toString())
        builder.appendQueryParameter("_ts", System.currentTimeMillis().toString())
        return builder.build()
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
            incomingReady = isIncomingReady
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

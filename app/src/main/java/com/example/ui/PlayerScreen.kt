package com.example.ui

import android.app.Activity
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.R
import com.example.engine.PlayerSettings
import com.example.engine.RawExoPlayerManager
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentPurple
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.LiveRed
import com.example.ui.theme.SuccessGreen
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    streamUrl: String,
    streamTitle: String,
    playerManager: RawExoPlayerManager,
    settings: PlayerSettings,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val telemetry by playerManager.telemetry.collectAsState()
    val activeIndex by playerManager.activePlayerIndex.collectAsState()
    val alphaA by playerManager.alphaA.collectAsState()
    val alphaB by playerManager.alphaB.collectAsState()
    val isTransitioning by playerManager.isTransitioning.collectAsState()

    var isOverlayVisible by remember { mutableStateOf(true) }
    var showStatsSheet by remember { mutableStateOf(false) }
    var resizeModeIndex by remember { mutableIntStateOf(0) }

    val resizeModes = listOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT to "Fit",
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Zoom",
        AspectRatioFrameLayout.RESIZE_MODE_FILL to "Fill",
        AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH to "Fixed Width",
        AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT to "Fixed Height"
    )

    // Complete Fullscreen Immersive Mode: Hides status bar & navigation bar completely
    DisposableEffect(activity) {
        val window = activity?.window
        if (window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            val window = activity?.window
            if (window != null) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Auto-hide top overlay after 4 seconds of inactivity
    LaunchedEffect(isOverlayVisible) {
        if (isOverlayVisible) {
            delay(4000)
            isOverlayVisible = false
        }
    }

    // Start stream playback
    LaunchedEffect(streamUrl) {
        playerManager.playStream(Uri.parse(streamUrl), streamTitle)
    }

    BackHandler {
        onNavigateBack()
    }

    // Dynamic Z-Ordering during seamless transitions: incoming player takes top drawing layer
    val zIndexA = if (isTransitioning) {
        if (activeIndex == 1) 2f else 1f // If transitioning to B, A is 1f; if transitioning to A, A is 2f
    } else {
        if (activeIndex == 0) 2f else 1f
    }

    val zIndexB = if (isTransitioning) {
        if (activeIndex == 0) 2f else 1f // If transitioning to B, B is 2f
    } else {
        if (activeIndex == 1) 2f else 1f
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                isOverlayVisible = !isOverlayVisible
            }
    ) {
        if (playerManager.isSeamlessEnabled) {
            // Dual-Player Overlapping Video Layers with TextureView for Smooth Alpha Crossfading
            // Layer A (Player A)
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(zIndexA)
                    .graphicsLayer { alpha = alphaA }
                    .testTag("raw_exoplayer_view_a"),
                factory = { ctx ->
                    val view = LayoutInflater.from(ctx).inflate(R.layout.player_view_texture, null) as PlayerView
                    view.layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    view.player = playerManager.getPlayerA()
                    view.useController = activeIndex == 0
                    view.controllerAutoShow = false
                    view.setKeepContentOnPlayerReset(settings.keepLastFrameOnReconnect)
                    view.setShowBuffering(
                        if (settings.hideBufferingSpinner) PlayerView.SHOW_BUFFERING_NEVER else PlayerView.SHOW_BUFFERING_ALWAYS
                    )
                    view.resizeMode = resizeModes[resizeModeIndex].first
                    view
                },
                update = { view ->
                    view.player = playerManager.getPlayerA()
                    view.useController = activeIndex == 0
                    view.setKeepContentOnPlayerReset(settings.keepLastFrameOnReconnect)
                    view.resizeMode = resizeModes[resizeModeIndex].first
                }
            )

            // Layer B (Player B)
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(zIndexB)
                    .graphicsLayer { alpha = alphaB }
                    .testTag("raw_exoplayer_view_b"),
                factory = { ctx ->
                    val view = LayoutInflater.from(ctx).inflate(R.layout.player_view_texture, null) as PlayerView
                    view.layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    view.player = playerManager.getPlayerB()
                    view.useController = activeIndex == 1
                    view.controllerAutoShow = false
                    view.setKeepContentOnPlayerReset(settings.keepLastFrameOnReconnect)
                    view.setShowBuffering(
                        if (settings.hideBufferingSpinner) PlayerView.SHOW_BUFFERING_NEVER else PlayerView.SHOW_BUFFERING_ALWAYS
                    )
                    view.resizeMode = resizeModes[resizeModeIndex].first
                    view
                },
                update = { view ->
                    view.player = playerManager.getPlayerB()
                    view.useController = activeIndex == 1
                    view.setKeepContentOnPlayerReset(settings.keepLastFrameOnReconnect)
                    view.resizeMode = resizeModes[resizeModeIndex].first
                }
            )
        } else {
            // Single-Player Mode with TextureView
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("raw_exoplayer_view"),
                factory = { ctx ->
                    val view = LayoutInflater.from(ctx).inflate(R.layout.player_view_texture, null) as PlayerView
                    view.layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    view.player = playerManager.getPlayer()
                    view.useController = true
                    view.controllerAutoShow = false
                    view.setKeepContentOnPlayerReset(settings.keepLastFrameOnReconnect)
                    view.setShowBuffering(
                        if (settings.hideBufferingSpinner) {
                            PlayerView.SHOW_BUFFERING_NEVER
                        } else {
                            PlayerView.SHOW_BUFFERING_ALWAYS
                        }
                    )
                    view.resizeMode = resizeModes[resizeModeIndex].first
                    view
                },
                update = { view ->
                    view.player = playerManager.getPlayer()
                    view.setKeepContentOnPlayerReset(settings.keepLastFrameOnReconnect)
                    view.resizeMode = resizeModes[resizeModeIndex].first
                }
            )
        }

        // Expressive Top Overlay Bar (Positioned nicely for immersive fullscreen)
        AnimatedVisibility(
            visible = isOverlayVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(10f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Back button & Title
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.15f))
                                .testTag("player_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Live pulsing badge
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (telemetry.isPlaying) LiveRed else Color.Gray)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (telemetry.isLive) "LIVE TS" else "STREAM",
                                    color = LiveRed,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                // Seamless Playback Badge if active
                                if (playerManager.isSeamlessEnabled) {
                                    Surface(
                                        color = if (isTransitioning) AccentAmber.copy(alpha = 0.3f) else AccentPurple.copy(alpha = 0.25f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Sync,
                                                contentDescription = "Seamless",
                                                tint = if (isTransitioning) AccentAmber else AccentPurple,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = if (isTransitioning) "TRANSITIONING" else "${telemetry.seamlessActivePlayer} (#${telemetry.seamlessCycleCount})",
                                                color = if (isTransitioning) AccentAmber else Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        }
                                    }
                                } else {
                                    Surface(
                                        color = ElectricCyan.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = settings.tsExtractorMode.name.replace("MODE_", ""),
                                            color = ElectricCyan,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            Text(
                                text = streamTitle.ifBlank { streamUrl },
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Action buttons (Aspect ratio & Stats toggle)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            onClick = {
                                resizeModeIndex = (resizeModeIndex + 1) % resizeModes.size
                            },
                            color = Color.White.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("aspect_ratio_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AspectRatio,
                                    contentDescription = "Aspect Ratio",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = resizeModes[resizeModeIndex].second,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = { showStatsSheet = !showStatsSheet },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (showStatsSheet) ElectricCyan.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.15f))
                                .testTag("stats_info_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Stream Stats",
                                tint = if (showStatsSheet) ElectricCyan else Color.White
                            )
                        }
                    }
                }
            }
        }

        // Telemetry Floating Card (Positioned nicely above bottom edge in immersive mode)
        AnimatedVisibility(
            visible = isOverlayVisible || showStatsSheet,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .zIndex(10f)
                .padding(16.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.75f)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("telemetry_card")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isTransitioning) AccentAmber
                                    else if (telemetry.isPlaying) SuccessGreen
                                    else ElectricCyan
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isTransitioning) "Seamless Handover Active (A ⇄ B)"
                            else if (playerManager.isSeamlessEnabled) "Seamless Dual-Player: ${telemetry.seamlessActivePlayer} (Cycle #${telemetry.seamlessCycleCount})"
                            else if (telemetry.isPlaying) "Playing Endless TS"
                            else if (telemetry.isBuffering) "Buffering..."
                            else "Connecting...",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Buffer: ${(telemetry.bufferedDurationMs / 1000.0).let { "%.1fs".format(it) }}",
                            color = ElectricCyan,
                            fontSize = 11.sp
                        )
                        if (telemetry.videoResolution.isNotEmpty()) {
                            Text(
                                text = telemetry.videoResolution,
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 11.sp
                            )
                        }
                        if (playerManager.isSeamlessEnabled) {
                            Text(
                                text = if (telemetry.incomingReady) "Next Stream: Primed" else "Next Stream: Preparing",
                                color = if (telemetry.incomingReady) SuccessGreen else AccentAmber,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (showStatsSheet) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Engine: Raw Media3 Dual-Instance • Chunk: ${settings.tsChunkSizeBytes / 1024}KB",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 10.sp
                        )
                        if (playerManager.isSeamlessEnabled) {
                            Text(
                                text = "Transition: ${settings.seamlessCrossfadeDurationMs}ms (Instant Audio) • Segment: ${settings.seamlessSegmentDurationSec}s",
                                color = AccentPurple,
                                fontSize = 10.sp
                            )
                        }
                        Text(
                            text = "Timeline: ${if (settings.forceLiveTimeline) "Force Live Unbounded" else "Default"}",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

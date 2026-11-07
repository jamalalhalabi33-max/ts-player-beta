package com.example.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SavedStream
import com.example.data.StreamHistoryRepository
import com.example.engine.PlayerSettings
import com.example.engine.SettingsRepository
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentPurple
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.DeepNavyBg
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.LiveRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.SurfaceContainerHigh
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceDarkVariant
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    settings: PlayerSettings,
    settingsRepository: SettingsRepository,
    historyRepository: StreamHistoryRepository,
    onPlayStream: (url: String, title: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    val streams by historyRepository.streamsFlow.collectAsState(initial = historyRepository.defaultPresets)

    val userTestUrl = "http://main.light-ott.net:80/play/live.php?mac=A0:BB:3E:02:40:2B&stream=985126&extension=ts"
    var inputUrl by remember { mutableStateOf(userTestUrl) }
    var inputTitle by remember { mutableStateOf("Light OTT Live TS") }

    Scaffold(
        containerColor = DeepNavyBg,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = ElectricCyan.copy(alpha = 0.2f),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "RawTS Logo",
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "RawTS Player",
                                    color = TextPrimary,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = if (settings.seamlessPlaybackEnabled) AccentPurple.copy(alpha = 0.25f) else LiveRed.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = if (settings.seamlessPlaybackEnabled) "SEAMLESS DUAL" else "IPTV ENGINE",
                                        color = if (settings.seamlessPlaybackEnabled) AccentPurple else LiveRed,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = if (settings.seamlessPlaybackEnabled) "Dual-Player Crossfade Active • ${settings.seamlessSegmentDurationSec}s Cycles" else "Raw Media3 • Single-PMT • Endless Live TS",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            inputUrl = userTestUrl
                            inputTitle = "Light OTT Live TS"
                            Toast.makeText(context, "Loaded user test stream URL", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(SurfaceDarkVariant)
                            .testTag("reload_user_test_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reload Test Stream",
                            tint = ElectricCyan
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DeepNavyBg
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = SurfaceDark,
                modifier = Modifier
                    .navigationBarsPadding()
                    .testTag("main_bottom_nav"),
                tonalElevation = 8.dp
            ) {
                val navItems = listOf(
                    Triple(0, "Quick Play", Icons.Default.PlayArrow),
                    Triple(1, "Library", Icons.Default.VideoLibrary),
                    Triple(2, "Settings", Icons.Default.Tune),
                    Triple(3, "External Guide", Icons.AutoMirrored.Filled.HelpOutline)
                )

                navItems.forEach { (index, label, icon) ->
                    val isSelected = selectedTab == index
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selectedTab = index },
                        icon = {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = if (isSelected) ElectricCyan else TextSecondary
                            )
                        },
                        label = {
                            Text(
                                text = label,
                                color = if (isSelected) ElectricCyan else TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = ElectricCyan.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier.testTag("nav_item_$index")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> QuickPlayTab(
                    inputUrl = inputUrl,
                    onUrlChange = { inputUrl = it },
                    inputTitle = inputTitle,
                    onTitleChange = { inputTitle = it },
                    settings = settings,
                    onLaunch = {
                        if (inputUrl.isNotBlank()) {
                            scope.launch {
                                historyRepository.recordStreamPlay(inputUrl, inputTitle)
                            }
                            onPlayStream(inputUrl, inputTitle)
                        } else {
                            Toast.makeText(context, "Please enter a valid stream URL", Toast.LENGTH_SHORT).show()
                        }
                    },
                    presets = historyRepository.defaultPresets,
                    onSelectPreset = { preset ->
                        inputUrl = preset.url
                        inputTitle = preset.title
                        scope.launch {
                            historyRepository.recordStreamPlay(preset.url, preset.title)
                        }
                        onPlayStream(preset.url, preset.title)
                    }
                )

                1 -> LibraryHistoryTab(
                    streams = streams,
                    onSelectStream = { stream ->
                        inputUrl = stream.url
                        inputTitle = stream.title
                        scope.launch {
                            historyRepository.recordStreamPlay(stream.url, stream.title)
                        }
                        onPlayStream(stream.url, stream.title)
                    },
                    onToggleFavorite = { streamId ->
                        scope.launch { historyRepository.toggleFavorite(streamId) }
                    },
                    onDeleteStream = { streamId ->
                        scope.launch { historyRepository.removeStream(streamId) }
                    }
                )

                2 -> EngineSettingsTab(
                    settings = settings,
                    onUpdateSettings = { newSettings ->
                        scope.launch { settingsRepository.updateSettings(newSettings) }
                    },
                    onResetDefaults = {
                        scope.launch {
                            settingsRepository.resetToDefaults()
                            Toast.makeText(context, "Reset engine settings to IPTV defaults", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                3 -> ExternalPlayerGuideTab()
            }
        }
    }
}

@Composable
private fun QuickPlayTab(
    inputUrl: String,
    onUrlChange: (String) -> Unit,
    inputTitle: String,
    onTitleChange: (String) -> Unit,
    settings: PlayerSettings,
    onLaunch: () -> Unit,
    presets: List<SavedStream>,
    onSelectPreset: (SavedStream) -> Unit
) {
    val context = LocalContext.current
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Expressive Hero Card
        ElevatedCard(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = SurfaceDark),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, if (settings.seamlessPlaybackEnabled) AccentPurple.copy(alpha = 0.4f) else ElectricCyan.copy(alpha = 0.3f), RoundedCornerShape(28.dp))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = if (settings.seamlessPlaybackEnabled) AccentPurple.copy(alpha = 0.25f) else SuccessGreen.copy(alpha = 0.2f),
                            modifier = Modifier.size(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(3.dp)
                                    .clip(CircleShape)
                                    .background(if (settings.seamlessPlaybackEnabled) AccentPurple else SuccessGreen)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (settings.seamlessPlaybackEnabled) "SEAMLESS DUAL-PLAYER ACTIVE" else "RAW ENGINE ACTIVE",
                            color = if (settings.seamlessPlaybackEnabled) AccentPurple else SuccessGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Surface(
                        color = ElectricCyan.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = settings.tsExtractorMode.name.replace("MODE_", ""),
                            color = ElectricCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = if (settings.seamlessPlaybackEnabled) "Dual-Player Alternating Crossfade" else "Raw ExoPlayer Single-PMT Engine",
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (settings.seamlessPlaybackEnabled)
                        "✓ Players A & B alternate every ${settings.seamlessSegmentDurationSec}s\n✓ Smooth simultaneous video/audio crossfade (${settings.seamlessCrossfadeDurationMs}ms)\n✓ Prepares in background with first-frame render check"
                    else
                        "✓ Infinite live timeline (fixes 15-30s fake duration crash)\n✓ Zero-blackscreen fast reconnect (stuck on last frame)\n✓ Chunk size: ${settings.tsChunkSizeBytes / 1024} KB • Max buffer: ${settings.maxBufferMs / 1000}s",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }

        // Direct Stream Input Card
        OutlinedCard(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.outlinedCardColors(containerColor = SurfaceDark),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BorderSubtle)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Direct Stream Play",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Enter any HTTP .ts live stream, HLS (.m3u8), or video URL",
                    color = TextSecondary,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = inputUrl,
                    onValueChange = onUrlChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("stream_url_input"),
                    label = { Text("Stream URL", color = TextSecondary) },
                    singleLine = false,
                    maxLines = 3,
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = BorderSubtle,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = SurfaceDarkVariant,
                        unfocusedContainerColor = SurfaceDarkVariant
                    ),
                    trailingIcon = {
                        Row {
                            if (inputUrl.isNotEmpty()) {
                                IconButton(onClick = { onUrlChange("") }) {
                                    Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear", tint = TextSecondary)
                                }
                            }
                            IconButton(
                                onClick = {
                                    val text = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()
                                    if (!text.isNullOrBlank()) {
                                        onUrlChange(text)
                                        Toast.makeText(context, "Pasted from clipboard", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.testTag("paste_clipboard_button")
                            ) {
                                Icon(imageVector = Icons.Default.ContentPaste, contentDescription = "Paste", tint = ElectricCyan)
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = inputTitle,
                    onValueChange = onTitleChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("stream_title_input"),
                    label = { Text("Stream Title (Optional)", color = TextSecondary) },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = BorderSubtle,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = SurfaceDarkVariant,
                        unfocusedContainerColor = SurfaceDarkVariant
                    )
                )

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = onLaunch,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("launch_player_button"),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (settings.seamlessPlaybackEnabled) AccentPurple else ElectricCyan,
                        contentColor = DeepNavyBg
                    )
                ) {
                    Icon(
                        imageVector = if (settings.seamlessPlaybackEnabled) Icons.Default.Sync else Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = DeepNavyBg,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (settings.seamlessPlaybackEnabled) "LAUNCH SEAMLESS DUAL-PLAYER" else "LAUNCH RAW EXOPLAYER",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = DeepNavyBg
                    )
                }
            }
        }

        // Highlight: User Problematic Stream Preset (Light OTT)
        ElevatedCard(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = SurfaceDarkVariant),
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    presets.firstOrNull()?.let { onSelectPreset(it) }
                }
                .testTag("user_test_stream_card")
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = AccentAmber.copy(alpha = 0.2f),
                            shape = CircleShape,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.FastForward,
                                    contentDescription = "Test Stream",
                                    tint = AccentAmber,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Light OTT Problematic Stream",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "30s stream with live position shift",
                                color = AccentAmber,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    AssistChip(
                        onClick = { presets.firstOrNull()?.let { onSelectPreset(it) } },
                        label = { Text("PLAY NOW", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = ElectricCyan,
                            labelColor = DeepNavyBg
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "http://main.light-ott.net:80/play/live.php?mac=A0:BB:3E:02:40:2B&stream=985126&extension=ts",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Text(
            text = "Test Stream Presets",
            color = TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )

        presets.drop(1).forEach { preset ->
            OutlinedCard(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.outlinedCardColors(containerColor = SurfaceDark),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BorderSubtle)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectPreset(preset) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = preset.title,
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = preset.url,
                            color = TextSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = ElectricCyan
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryHistoryTab(
    streams: List<SavedStream>,
    onSelectStream: (SavedStream) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onDeleteStream: (String) -> Unit
) {
    if (streams.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.VideoLibrary,
                    contentDescription = "Empty",
                    tint = TextSecondary,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "No saved streams yet",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Played streams will automatically appear here",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "Stream History & Bookmarks",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${streams.size} streams saved",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            items(streams, key = { it.id }) { stream ->
                ElevatedCard(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = SurfaceDark),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectStream(stream) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = ElectricCyan.copy(alpha = 0.15f),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play",
                                    tint = ElectricCyan
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stream.title,
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = stream.url,
                                color = TextSecondary,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(onClick = { onToggleFavorite(stream.id) }) {
                            Icon(
                                imageVector = if (stream.isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = "Favorite",
                                tint = if (stream.isFavorite) AccentAmber else TextSecondary
                            )
                        }

                        IconButton(onClick = { onDeleteStream(stream.id) }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EngineSettingsTab(
    settings: PlayerSettings,
    onUpdateSettings: (PlayerSettings) -> Unit,
    onResetDefaults: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Player & Engine Settings",
                    color = TextPrimary,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Configure Seamless Crossfade & Raw MPEG-TS",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            OutlinedButton(
                onClick = onResetDefaults,
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Reset Defaults", color = ElectricCyan, fontSize = 11.sp)
            }
        }

        // ==========================================
        // FEATURE: Seamless Playback (Dual-Player Crossfade)
        // ==========================================
        ElevatedCard(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = if (settings.seamlessPlaybackEnabled) SurfaceContainerHigh else SurfaceDark
            ),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    1.dp,
                    if (settings.seamlessPlaybackEnabled) AccentPurple else BorderSubtle,
                    RoundedCornerShape(26.dp)
                )
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                // Header with M3 ListItem
                ListItem(
                    headlineContent = {
                        Text(
                            text = "Seamless Playback",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    supportingContent = {
                        Text(
                            text = "Automatically refresh streams in the background and smoothly transition between them for uninterrupted playback.",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    },
                    leadingContent = {
                        Surface(
                            shape = CircleShape,
                            color = if (settings.seamlessPlaybackEnabled) AccentPurple else SurfaceDarkVariant,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = "Seamless Playback",
                                    tint = if (settings.seamlessPlaybackEnabled) Color.White else TextSecondary
                                )
                            }
                        }
                    },
                    trailingContent = {
                        Switch(
                            checked = settings.seamlessPlaybackEnabled,
                            onCheckedChange = { onUpdateSettings(settings.copy(seamlessPlaybackEnabled = it)) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = DeepNavyBg,
                                checkedTrackColor = AccentPurple,
                                uncheckedThumbColor = TextSecondary,
                                uncheckedTrackColor = SurfaceDarkVariant
                            ),
                            modifier = Modifier.testTag("seamless_playback_switch")
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.padding(0.dp)
                )

                AnimatedVisibility(
                    visible = settings.seamlessPlaybackEnabled,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(modifier = Modifier.padding(top = 12.dp)) {
                        HorizontalDivider(color = BorderSubtle)
                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Stream Segment Duration (Refresh Cycle)",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Background player pre-loads next stream before this time:",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Segmented Button Row for Segment Duration
                        val segmentDurations = listOf(15, 20, 25, 30, 45, 60)
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            segmentDurations.forEachIndexed { index, sec ->
                                SegmentedButton(
                                    selected = settings.seamlessSegmentDurationSec == sec,
                                    onClick = { onUpdateSettings(settings.copy(seamlessSegmentDurationSec = sec)) },
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = segmentDurations.size),
                                    colors = SegmentedButtonDefaults.colors(
                                        activeContainerColor = AccentPurple,
                                        activeContentColor = Color.White,
                                        inactiveContainerColor = SurfaceDarkVariant,
                                        inactiveContentColor = TextPrimary
                                    )
                                ) {
                                    Text("${sec}s", fontSize = 11.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Crossfade Transition Duration",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Video layer opacity crossfade with instant non-echo audio handover:",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        val transitions = listOf(
                            250L to "250ms",
                            350L to "350ms",
                            500L to "500ms (Default)",
                            800L to "800ms"
                        )
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            transitions.forEachIndexed { index, (ms, label) ->
                                SegmentedButton(
                                    selected = settings.seamlessCrossfadeDurationMs == ms,
                                    onClick = { onUpdateSettings(settings.copy(seamlessCrossfadeDurationMs = ms)) },
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = transitions.size),
                                    colors = SegmentedButtonDefaults.colors(
                                        activeContainerColor = AccentPurple,
                                        activeContentColor = Color.White,
                                        inactiveContainerColor = SurfaceDarkVariant,
                                        inactiveContentColor = TextPrimary
                                    )
                                ) {
                                    Text(label, fontSize = 10.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Surface(
                            color = AccentPurple.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "How it works: Player A plays the active stream while Player B prepares the next refreshed stream. Once ready, video crossfades smoothly while audio switches instantly with zero echo. Players A and B alternate indefinitely across unlimited chunks (A → B → C → D → ...).",
                                color = TextPrimary,
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }
        }

        // Section 2: MPEG-TS Extractor Settings
        ElevatedCard(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = SurfaceDark),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "MPEG-TS Extractor Tweaks",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Controls raw TS demuxing and live timeline recognition",
                    color = TextSecondary,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "TS Extractor Mode:",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))

                // M3 SingleChoiceSegmentedButtonRow
                val modes = PlayerSettings.TsExtractorMode.values()
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    modes.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = settings.tsExtractorMode == mode,
                            onClick = { onUpdateSettings(settings.copy(tsExtractorMode = mode)) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = ElectricCyan,
                                activeContentColor = DeepNavyBg,
                                inactiveContainerColor = SurfaceDarkVariant,
                                inactiveContentColor = TextPrimary
                            )
                        ) {
                            Text(mode.name.replace("MODE_", ""), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = BorderSubtle)
                Spacer(modifier = Modifier.height(14.dp))

                // M3 ListItems with Switches
                ListItem(
                    headlineContent = { Text("Force Live Unbounded Timeline", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("Replaces fake 30s VOD duration with infinite live window", color = TextSecondary, fontSize = 11.sp) },
                    trailingContent = {
                        Switch(
                            checked = settings.forceLiveTimeline,
                            onCheckedChange = { onUpdateSettings(settings.copy(forceLiveTimeline = it)) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = DeepNavyBg,
                                checkedTrackColor = ElectricCyan,
                                uncheckedThumbColor = TextSecondary,
                                uncheckedTrackColor = SurfaceDarkVariant
                            )
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )

                ListItem(
                    headlineContent = { Text("Allow Non-IDR Keyframes", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("Eliminates freeze on open-GOP frames without waiting for full IDR", color = TextSecondary, fontSize = 11.sp) },
                    trailingContent = {
                        Switch(
                            checked = settings.allowNonIdrKeyframes,
                            onCheckedChange = { onUpdateSettings(settings.copy(allowNonIdrKeyframes = it)) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = DeepNavyBg,
                                checkedTrackColor = ElectricCyan,
                                uncheckedThumbColor = TextSecondary,
                                uncheckedTrackColor = SurfaceDarkVariant
                            )
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }

        // Section 3: Chunking & Buffer Duration Tuning
        ElevatedCard(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = SurfaceDark),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Chunking & Buffer Duration",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Tuning read chunking and buffer depth up to minutes long",
                    color = TextSecondary,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "TS Read Chunk Size: ${settings.tsChunkSizeBytes / 1024} KB",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))

                val chunks = listOf(
                    64 * 1024 to "64K",
                    256 * 1024 to "256K",
                    512 * 1024 to "512K",
                    1024 * 1024 to "1M",
                    2 * 1024 * 1024 to "2M",
                    4 * 1024 * 1024 to "4M"
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    chunks.forEachIndexed { index, (bytes, label) ->
                        SegmentedButton(
                            selected = settings.tsChunkSizeBytes == bytes,
                            onClick = { onUpdateSettings(settings.copy(tsChunkSizeBytes = bytes)) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = chunks.size),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = ElectricCyan,
                                activeContentColor = DeepNavyBg,
                                inactiveContainerColor = SurfaceDarkVariant,
                                inactiveContentColor = TextPrimary
                            )
                        ) {
                            Text(label, fontSize = 11.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Max Buffer Duration: ${settings.maxBufferMs / 1000}s (${settings.maxBufferMs / 60000.0} min)",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))

                val maxBuffers = listOf(
                    30_000 to "30s",
                    60_000 to "1m",
                    120_000 to "2m",
                    300_000 to "5m",
                    600_000 to "10m"
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    maxBuffers.forEachIndexed { index, (ms, label) ->
                        SegmentedButton(
                            selected = settings.maxBufferMs == ms,
                            onClick = { onUpdateSettings(settings.copy(maxBufferMs = ms)) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = maxBuffers.size),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = ElectricCyan,
                                activeContentColor = DeepNavyBg,
                                inactiveContainerColor = SurfaceDarkVariant,
                                inactiveContentColor = TextPrimary
                            )
                        ) {
                            Text(label, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Section 4: Reconnection Engine Settings
        ElevatedCard(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = SurfaceDark),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Zero-Blackscreen Reconnection Engine",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Ultra-fast recovery keeping last frame frozen without black screen",
                    color = TextSecondary,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                ListItem(
                    headlineContent = { Text("Fast Auto-Reconnection", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("Immediately reconnects stream on socket drops", color = TextSecondary, fontSize = 11.sp) },
                    trailingContent = {
                        Switch(
                            checked = settings.fastReconnectEnabled,
                            onCheckedChange = { onUpdateSettings(settings.copy(fastReconnectEnabled = it)) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = DeepNavyBg,
                                checkedTrackColor = ElectricCyan,
                                uncheckedThumbColor = TextSecondary,
                                uncheckedTrackColor = SurfaceDarkVariant
                            )
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )

                ListItem(
                    headlineContent = { Text("Keep Last Frame on Reconnect", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("Stops video surface from blanking to black screen", color = TextSecondary, fontSize = 11.sp) },
                    trailingContent = {
                        Switch(
                            checked = settings.keepLastFrameOnReconnect,
                            onCheckedChange = { onUpdateSettings(settings.copy(keepLastFrameOnReconnect = it)) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = DeepNavyBg,
                                checkedTrackColor = ElectricCyan,
                                uncheckedThumbColor = TextSecondary,
                                uncheckedTrackColor = SurfaceDarkVariant
                            )
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )

                ListItem(
                    headlineContent = { Text("Hide Buffering Circle", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("Eliminates buffer spinner overlay when recovering", color = TextSecondary, fontSize = 11.sp) },
                    trailingContent = {
                        Switch(
                            checked = settings.hideBufferingSpinner,
                            onCheckedChange = { onUpdateSettings(settings.copy(hideBufferingSpinner = it)) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = DeepNavyBg,
                                checkedTrackColor = ElectricCyan,
                                uncheckedThumbColor = TextSecondary,
                                uncheckedTrackColor = SurfaceDarkVariant
                            )
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
    }
}

@Composable
private fun ExternalPlayerGuideTab() {
    val context = LocalContext.current
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val packageName = context.packageName

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ElevatedCard(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = SurfaceDark),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Using as External Player",
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "RawTS Player registers system VIEW intents to serve as the default high-performance engine for all your IPTV apps.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceDarkVariant)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(text = "Package Identifier", color = TextSecondary, fontSize = 10.sp)
                        Text(text = packageName, color = ElectricCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    IconButton(onClick = {
                        val clip = android.content.ClipData.newPlainText("Package Name", packageName)
                        clipboard?.setPrimaryClip(clip)
                        Toast.makeText(context, "Package name copied", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy", tint = ElectricCyan)
                    }
                }
            }
        }

        val apps = listOf(
            Triple(
                "IPTV Smarters Pro",
                "1. Open IPTV Smarters Pro\n2. Go to Settings ➔ Player Selection\n3. Tap 'Add Player' or change Live TV player to 'External Player'\n4. Select 'RawTS Player' from the app chooser.",
                ElectricCyan
            ),
            Triple(
                "TiviMate",
                "1. Open TiviMate Settings\n2. Navigate to Playback\n3. Enable 'Use external player' option\n4. When starting any channel, choose RawTS Player.",
                AccentPurple
            ),
            Triple(
                "OTT Navigator",
                "1. Open Settings ➔ Player ➔ Video Player\n2. Select 'External Player'\n3. Set RawTS Player as default.",
                AccentAmber
            ),
            Triple(
                "XCIPTV & Web Browsers",
                "1. In Settings ➔ Player Selection ➔ Set External\n2. Links with .ts, .m3u8, or video/mp2t MIME types will automatically open in RawTS Player.",
                SuccessGreen
            )
        )

        apps.forEach { (appName, instructions, accentColor) ->
            OutlinedCard(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.outlinedCardColors(containerColor = SurfaceDark),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BorderSubtle)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(accentColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = appName,
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = instructions,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 19.sp
                    )
                }
            }
        }

        Button(
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(
                        Uri.parse("http://main.light-ott.net:80/play/live.php?mac=A0:BB:3E:02:40:2B&stream=985126&extension=ts"),
                        "video/mp2t"
                    )
                    `package` = packageName
                }
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "Cannot launch: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("test_intent_button"),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = SurfaceDarkVariant,
                contentColor = ElectricCyan
            )
        ) {
            Icon(imageVector = Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Test Intent")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Test ACTION_VIEW Intent Launch", fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.data.StreamHistoryRepository
import com.example.engine.PlayerSettings
import com.example.engine.RawExoPlayerManager
import com.example.engine.SettingsRepository
import com.example.ui.HomeScreen
import com.example.ui.PlayerScreen
import com.example.ui.theme.DeepNavyBg
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var historyRepository: StreamHistoryRepository
    private var externalPlayerIntentUri: String? = null
    private var externalPlayerIntentTitle: String? = null

    // Mutable state to hold active stream across intent updates
    private val activeStreamState = mutableStateOf<Pair<String, String>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        settingsRepository = SettingsRepository(applicationContext)
        historyRepository = StreamHistoryRepository(applicationContext)

        extractIntentData(intent)

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DeepNavyBg
                ) {
                    val settings by settingsRepository.settingsFlow.collectAsState(initial = PlayerSettings())
                    val activeStream by activeStreamState

                    // Player manager instance remembered per settings
                    val playerManager = remember(settings) {
                        RawExoPlayerManager(applicationContext, settings)
                    }

                    DisposableEffect(playerManager) {
                        onDispose {
                            playerManager.release()
                        }
                    }

                    if (activeStream != null) {
                        PlayerScreen(
                            streamUrl = activeStream!!.first,
                            streamTitle = activeStream!!.second,
                            playerManager = playerManager,
                            settings = settings,
                            onNavigateBack = {
                                playerManager.getPlayerA().stop()
                                playerManager.getPlayerB().stop()
                                activeStreamState.value = null
                                // If launched from external app, finish activity on back
                                if (externalPlayerIntentUri != null) {
                                    finish()
                                }
                            }
                        )
                    } else {
                        HomeScreen(
                            settings = settings,
                            settingsRepository = settingsRepository,
                            historyRepository = historyRepository,
                            onPlayStream = { url, title ->
                                activeStreamState.value = Pair(url, title)
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractIntentData(intent)
    }

    private fun extractIntentData(intent: Intent?) {
        val uri = intent?.data
        if (uri != null) {
            val streamUrl = uri.toString()
            val streamTitle = intent.getStringExtra("title")
                ?: intent.getStringExtra("name")
                ?: uri.lastPathSegment
                ?: "External Stream"
            externalPlayerIntentUri = streamUrl
            externalPlayerIntentTitle = streamTitle
            activeStreamState.value = Pair(streamUrl, streamTitle)
        }
    }
}

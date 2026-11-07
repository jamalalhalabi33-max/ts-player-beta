package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

data class SavedStream(
    val id: String,
    val title: String,
    val url: String,
    val isFavorite: Boolean = false,
    val lastPlayedTime: Long = System.currentTimeMillis()
)

private val Context.historyDataStore: DataStore<Preferences> by preferencesDataStore(name = "raw_stream_history")

class StreamHistoryRepository(private val context: Context) {

    private object Keys {
        val RECENT_STREAMS = stringPreferencesKey("recent_streams_json")
        val FAVORITE_STREAMS = stringPreferencesKey("favorite_streams_json")
    }

    val defaultPresets = listOf(
        SavedStream(
            id = "user_reported_stream",
            title = "User Reported Stream (Light OTT TS)",
            url = "http://main.light-ott.net:80/play/live.php?mac=A0:BB:3E:02:40:2B&stream=985126&extension=ts",
            isFavorite = true
        ),
        SavedStream(
            id = "test_akamai_live",
            title = "Akamai Live HLS Stream",
            url = "https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8",
            isFavorite = false
        ),
        SavedStream(
            id = "test_redbull_ts",
            title = "Tears of Steel 1080p TS Stream",
            url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
            isFavorite = false
        )
    )

    val streamsFlow: Flow<List<SavedStream>> = context.historyDataStore.data.map { prefs ->
        val jsonStr = prefs[Keys.RECENT_STREAMS]
        if (jsonStr.isNullOrEmpty()) {
            defaultPresets
        } else {
            try {
                val array = JSONArray(jsonStr)
                val list = mutableListOf<SavedStream>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        SavedStream(
                            id = obj.optString("id", System.currentTimeMillis().toString()),
                            title = obj.optString("title", "Stream $i"),
                            url = obj.optString("url", ""),
                            isFavorite = obj.optBoolean("isFavorite", false),
                            lastPlayedTime = obj.optLong("lastPlayedTime", System.currentTimeMillis())
                        )
                    )
                }
                if (list.none { it.id == "user_reported_stream" }) {
                    listOf(defaultPresets[0]) + list
                } else {
                    list
                }
            } catch (_: Exception) {
                defaultPresets
            }
        }
    }

    suspend fun recordStreamPlay(url: String, title: String = "") {
        if (url.isBlank()) return
        context.historyDataStore.edit { prefs ->
            val existing = try {
                val array = JSONArray(prefs[Keys.RECENT_STREAMS] ?: "[]")
                val list = mutableListOf<SavedStream>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        SavedStream(
                            id = obj.optString("id"),
                            title = obj.optString("title"),
                            url = obj.optString("url"),
                            isFavorite = obj.optBoolean("isFavorite", false),
                            lastPlayedTime = obj.optLong("lastPlayedTime")
                        )
                    )
                }
                list
            } catch (_: Exception) {
                defaultPresets.toMutableList()
            }

            val autoTitle = if (title.isNotBlank()) title else "Stream ${url.substringAfterLast('/').substringBefore('?')}"
            val filtered = existing.filterNot { it.url == url }
            val updated = listOf(
                SavedStream(
                    id = System.currentTimeMillis().toString(),
                    title = autoTitle,
                    url = url,
                    isFavorite = existing.find { it.url == url }?.isFavorite ?: false,
                    lastPlayedTime = System.currentTimeMillis()
                )
            ) + filtered

            val finalArray = JSONArray()
            updated.take(30).forEach { item ->
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("title", item.title)
                    put("url", item.url)
                    put("isFavorite", item.isFavorite)
                    put("lastPlayedTime", item.lastPlayedTime)
                }
                finalArray.put(obj)
            }
            prefs[Keys.RECENT_STREAMS] = finalArray.toString()
        }
    }

    suspend fun toggleFavorite(streamId: String) {
        context.historyDataStore.edit { prefs ->
            val jsonStr = prefs[Keys.RECENT_STREAMS] ?: return@edit
            try {
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    if (obj.optString("id") == streamId) {
                        obj.put("isFavorite", !obj.optBoolean("isFavorite", false))
                    }
                }
                prefs[Keys.RECENT_STREAMS] = array.toString()
            } catch (_: Exception) {}
        }
    }

    suspend fun removeStream(streamId: String) {
        context.historyDataStore.edit { prefs ->
            val jsonStr = prefs[Keys.RECENT_STREAMS] ?: return@edit
            try {
                val array = JSONArray(jsonStr)
                val newArray = JSONArray()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    if (obj.optString("id") != streamId) {
                        newArray.put(obj)
                    }
                }
                prefs[Keys.RECENT_STREAMS] = newArray.toString()
            } catch (_: Exception) {}
        }
    }
}

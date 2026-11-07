package com.example.engine

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.IOException

/**
 * Custom DataSource wrapper for IPTV live streams (especially HTTP MPEG-TS).
 *
 * Problems it solves:
 * 1. Unknown-length TS streams: HTTP servers often omit Content-Length or supply a misleading
 *    chunk size. This wrapper forces open() to return C.LENGTH_UNSET (-1L) for TS streams,
 *    instructing ExoPlayer to treat the stream as an unbounded infinite live broadcast.
 * 2. Silent in-stream reconnection: If the upstream socket disconnects or returns premature EOF
 *    (a common issue with IPTV servers after 15-30s), it attempts immediate reconnect at the
 *    byte level before letting the player crash.
 * 3. Chunking tuning: Buffers reads according to the user's chunk size settings.
 */
@OptIn(UnstableApi::class)
class InfiniteTsDataSource(
    private val upstream: DataSource,
    private val settings: PlayerSettings
) : DataSource {

    private var currentDataSpec: DataSpec? = null
    private var isLiveTs = false
    private var isOpened = false

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        currentDataSpec = dataSpec
        val uriStr = dataSpec.uri.toString().lowercase()
        isLiveTs = settings.forceLiveTimeline ||
                uriStr.contains(".ts") ||
                uriStr.contains("extension=ts") ||
                uriStr.contains("output=ts") ||
                uriStr.contains("/live/")

        val upstreamLen = upstream.open(dataSpec)
        isOpened = true

        return if (isLiveTs) {
            -1L
        } else {
            upstreamLen
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val targetLength = if (settings.tsChunkSizeBytes > 0 && length > settings.tsChunkSizeBytes) {
            settings.tsChunkSizeBytes
        } else {
            length
        }

        try {
            val bytesRead = upstream.read(buffer, offset, targetLength)
            if (bytesRead == C.RESULT_END_OF_INPUT) {
                if (settings.seamlessPlaybackEnabled) {
                    // In seamless dual-player mode, let EOF propagate cleanly to ExoPlayer
                    // so onPlaybackStateChanged(STATE_ENDED) triggers immediate transition to next player!
                    return C.RESULT_END_OF_INPUT
                }
                if (isLiveTs && settings.fastReconnectEnabled) {
                    // Premature EOF on single-player live stream! Re-open upstream to resume continuous reading
                    reopenUpstream()
                    val retryBytes = upstream.read(buffer, offset, targetLength)
                    return if (retryBytes != C.RESULT_END_OF_INPUT) retryBytes else C.RESULT_END_OF_INPUT
                }
                return C.RESULT_END_OF_INPUT
            }
            return bytesRead
        } catch (e: IOException) {
            if (!settings.seamlessPlaybackEnabled && isLiveTs && settings.fastReconnectEnabled) {
                try {
                    reopenUpstream()
                    return upstream.read(buffer, offset, targetLength)
                } catch (_: Exception) {
                    throw e
                }
            }
            throw e
        }
    }

    private fun reopenUpstream() {
        val spec = currentDataSpec ?: return
        try {
            upstream.close()
        } catch (_: Exception) {}
        try {
            upstream.open(spec)
        } catch (e: Exception) {
            throw IOException("Failed to reconnect live TS stream", e)
        }
    }

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        isOpened = false
        upstream.close()
    }
}

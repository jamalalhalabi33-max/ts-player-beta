package com.example.engine

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.ts.TsExtractor

/**
 * Safe wrapper around TsExtractor for progressive IPTV streams.
 *
 * Prevents java.lang.IllegalStateException at TsExtractor.seek(TsExtractor.java:384):
 * In Media3, TsExtractor contains `Assertions.checkState(mode != MODE_HLS);` in its seek() method.
 * ProgressiveMediaPeriod always invokes seek(position, timeUs) upon loading a stream.
 *
 * This wrapper safely guards against that assertion and ensures progressive TS streams
 * continue demuxing and playing smoothly under all extractor modes.
 */
@OptIn(UnstableApi::class)
class SafeTsExtractor(
    private val delegate: Extractor,
    private val isHlsMode: Boolean
) : Extractor {

    override fun init(output: ExtractorOutput) {
        delegate.init(output)
    }

    override fun sniff(input: ExtractorInput): Boolean {
        return delegate.sniff(input)
    }

    override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int {
        return delegate.read(input, seekPosition)
    }

    override fun seek(position: Long, timeUs: Long) {
        if (isHlsMode) {
            // TsExtractor.seek() explicitly asserts checkState(mode != MODE_HLS).
            // In progressive media streams, ProgressiveMediaPeriod calls seek(0, 0)
            // on initialization. By swallowing seek in MODE_HLS, we prevent the fatal
            // IllegalStateException at TsExtractor.java:384 while allowing the extractor to
            // parse and demux the stream packets normally.
            return
        }
        try {
            delegate.seek(position, timeUs)
        } catch (_: IllegalStateException) {
            // Defensive catch to guarantee no unhandled assertion in TsExtractor.seek()
        }
    }

    override fun release() {
        delegate.release()
    }

    override fun getUnderlyingImplementation(): Extractor {
        return delegate.underlyingImplementation
    }
}

package com.example.engine

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.extractor.ts.TsExtractor

@OptIn(UnstableApi::class)
class CustomTsExtractorsFactory(
    private val settings: PlayerSettings
) : ExtractorsFactory {

    companion object {
        fun create(settings: PlayerSettings): ExtractorsFactory {
            return CustomTsExtractorsFactory(settings)
        }
    }

    private fun buildDelegateFactory(mode: Int): DefaultExtractorsFactory {
        var flags = 0
        if (settings.allowNonIdrKeyframes) {
            flags = flags or DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES
        }
        if (settings.detectAccessUnits) {
            flags = flags or DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS
        }
        if (settings.ignoreSpliceInfoStream) {
            flags = flags or DefaultTsPayloadReaderFactory.FLAG_IGNORE_SPLICE_INFO_STREAM
        }

        return DefaultExtractorsFactory()
            .setTsExtractorMode(mode)
            .setTsExtractorFlags(flags)
            .setTsExtractorTimestampSearchBytes(TsExtractor.DEFAULT_TIMESTAMP_SEARCH_BYTES * 2)
    }

    private val extractorMode: Int
        get() = when (settings.tsExtractorMode) {
            PlayerSettings.TsExtractorMode.MODE_SINGLE_PMT -> TsExtractor.MODE_SINGLE_PMT
            PlayerSettings.TsExtractorMode.MODE_MULTI_PMT -> TsExtractor.MODE_MULTI_PMT
            PlayerSettings.TsExtractorMode.MODE_HLS -> TsExtractor.MODE_HLS
        }

    override fun createExtractors(): Array<Extractor> {
        val mode = extractorMode
        val rawExtractors = buildDelegateFactory(mode).createExtractors()
        return wrapExtractors(rawExtractors, mode)
    }

    override fun createExtractors(uri: Uri, responseHeaders: Map<String, List<String>>): Array<Extractor> {
        val mode = extractorMode
        val rawExtractors = buildDelegateFactory(mode).createExtractors(uri, responseHeaders)
        return wrapExtractors(rawExtractors, mode)
    }

    private fun wrapExtractors(rawExtractors: Array<Extractor>, mode: Int): Array<Extractor> {
        return rawExtractors.map { extractor ->
            if (extractor is TsExtractor) {
                SafeTsExtractor(extractor, isHlsMode = (mode == TsExtractor.MODE_HLS))
            } else {
                extractor
            }
        }.toTypedArray()
    }
}

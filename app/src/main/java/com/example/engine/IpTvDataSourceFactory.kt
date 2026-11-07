package com.example.engine

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

@OptIn(UnstableApi::class)
class IpTvDataSourceFactory(
    private val context: Context,
    private val settings: PlayerSettings
) : DataSource.Factory {

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(settings.connectTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
        .readTimeout(settings.readTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    override fun createDataSource(): DataSource {
        val httpDataSource = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent(settings.userAgent)
            .setDefaultRequestProperties(
                mapOf(
                    "Accept" to "*/*",
                    "Connection" to "keep-alive",
                    "Cache-Control" to "no-cache",
                    "Pragma" to "no-cache"
                )
            )
            .createDataSource()

        val defaultDataSource = DefaultDataSource(context, httpDataSource)
        return InfiniteTsDataSource(defaultDataSource, settings)
    }
}

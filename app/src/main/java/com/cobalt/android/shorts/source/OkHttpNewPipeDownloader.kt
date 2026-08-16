package com.cobalt.android.shorts.source

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as NewPipeRequest
import org.schabi.newpipe.extractor.downloader.Response as NewPipeResponse
import java.util.concurrent.TimeUnit

/**
 * NewPipeExtractor requires the host app to supply an HTTP implementation
 * (it has no bundled HTTP client) via [NewPipe.init][org.schabi.newpipe.extractor.NewPipe.init].
 * This adapts the project's existing OkHttp dependency to NewPipeExtractor's
 * [Downloader] contract, rather than pulling in a second HTTP stack.
 */
class OkHttpNewPipeDownloader private constructor(
    private val client: OkHttpClient
) : Downloader() {

    override fun execute(request: NewPipeRequest): NewPipeResponse {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val builder = Request.Builder().url(url)
        headers.forEach { (name, values) ->
            values.forEach { value -> builder.addHeader(name, value) }
        }

        if (dataToSend != null) {
            val contentType = headers["Content-Type"]?.firstOrNull() ?: "application/octet-stream"
            builder.method(httpMethod, dataToSend.toRequestBody(contentType.toMediaType()))
        } else {
            builder.method(httpMethod, null)
        }

        client.newCall(builder.build()).execute().use { response ->
            val bodyString = response.body?.string().orEmpty()
            val responseHeaders = response.headers.toMultimap()
            return NewPipeResponse(
                response.code,
                response.message,
                responseHeaders,
                bodyString,
                response.request.url.toString()
            )
        }
    }

    companion object {
        @Volatile private var instance: OkHttpNewPipeDownloader? = null

        fun getInstance(): OkHttpNewPipeDownloader =
            instance ?: synchronized(this) {
                instance ?: OkHttpNewPipeDownloader(
                    OkHttpClient.Builder()
                        .connectTimeout(15, TimeUnit.SECONDS)
                        .readTimeout(20, TimeUnit.SECONDS)
                        .build()
                ).also { instance = it }
            }
    }
}

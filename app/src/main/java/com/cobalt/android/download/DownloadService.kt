package com.cobalt.android.download

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.cobalt.android.util.NotificationHelper
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class DownloadService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private val activeCount = AtomicInteger(0)
    private lateinit var repository: DownloadRepository
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var mediaStoreWriter: MediaStoreWriter

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    override fun onCreate() {
        super.onCreate()
        repository = DownloadRepository(this)
        notificationHelper = NotificationHelper(this)
        mediaStoreWriter = MediaStoreWriter(this)
        // Reset any downloads stuck in DOWNLOADING from a previous process kill
        scope.launch { repository.resetStuckDownloads() }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must call startForeground() on main thread before any coroutines
        startForeground()

        when (intent?.action) {
            ACTION_HTTPS -> {
                val record = DownloadRecord(
                    originalUrl = intent.getStringExtra(EXTRA_ORIGINAL_URL) ?: "",
                    cobaltUrl = intent.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY,
                    filename = intent.getStringExtra(EXTRA_FILENAME) ?: "download",
                    mimeType = intent.getStringExtra(EXTRA_MIME_TYPE) ?: "application/octet-stream",
                    cookies = intent.getStringExtra(EXTRA_COOKIES) ?: "",
                    userAgent = intent.getStringExtra(EXTRA_USER_AGENT) ?: ""
                )
                scope.launch {
                    val id = repository.insert(record)
                    processHttps(record.copy(id = id))
                }
            }
            ACTION_BLOB -> {
                val tempPath = intent.getStringExtra(EXTRA_TEMP_PATH) ?: return START_NOT_STICKY
                val record = DownloadRecord(
                    originalUrl = intent.getStringExtra(EXTRA_ORIGINAL_URL) ?: "",
                    filename = intent.getStringExtra(EXTRA_FILENAME) ?: "download",
                    mimeType = intent.getStringExtra(EXTRA_MIME_TYPE) ?: "application/octet-stream",
                    isBlobDownload = true,
                    tempFilePath = tempPath
                )
                scope.launch {
                    val id = repository.insert(record)
                    processBlob(record.copy(id = id))
                }
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun processHttps(record: DownloadRecord) {
        activeCount.incrementAndGet()
        try {
            repository.updateStatus(record.id, DownloadStatus.DOWNLOADING)
            val request = Request.Builder()
                .url(record.cobaltUrl)
                .apply { if (record.cookies.isNotBlank()) header("Cookie", record.cookies) }
                .apply { if (record.userAgent.isNotBlank()) header("User-Agent", record.userAgent) }
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val body = response.body ?: throw IOException("Empty body")
                val contentLength = body.contentLength()
                val opened = mediaStoreWriter.open(record.filename, record.mimeType)
                    ?: throw IOException("MediaStore open failed")

                try {
                    opened.stream.use { out ->
                        val buffer = ByteArray(16 * 1024)
                        var totalRead = 0L
                        var lastUpdate = 0L
                        body.byteStream().use { input ->
                            var n: Int
                            while (input.read(buffer).also { n = it } != -1) {
                                out.write(buffer, 0, n)
                                totalRead += n
                                val now = System.currentTimeMillis()
                                if (now - lastUpdate > 500) {
                                    lastUpdate = now
                                    repository.updateProgress(record.id, totalRead, contentLength)
                                    notificationHelper.updateProgress(
                                        record.id, totalRead.toInt(), contentLength.toInt()
                                    )
                                }
                            }
                        }
                    }
                    mediaStoreWriter.finalize(opened.uri)
                    repository.updateMediaStoreUri(record.id, opened.uri.toString())
                    repository.updateStatus(record.id, DownloadStatus.COMPLETE)
                    notificationHelper.showComplete(record.id, record.filename, opened.uri, record.mimeType)
                } catch (e: Exception) {
                    mediaStoreWriter.delete(opened.uri)
                    throw e
                }
            }
        } catch (e: UnknownHostException) {
            handleNetworkFail(record)
        } catch (e: IOException) {
            if (e.message?.contains("ENOSPC") == true) {
                notificationHelper.showStorageFull()
                repository.updateStatus(record.id, DownloadStatus.FAILED)
            } else {
                handleNetworkFail(record)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            repository.updateStatus(record.id, DownloadStatus.FAILED)
            notificationHelper.showFailed(record.id, record.filename)
        } finally {
            if (activeCount.decrementAndGet() == 0) stopSelf()
        }
    }

    private suspend fun processBlob(record: DownloadRecord) {
        activeCount.incrementAndGet()
        val tempFile = File(record.tempFilePath)
        try {
            repository.updateStatus(record.id, DownloadStatus.DOWNLOADING)
            if (!tempFile.exists()) throw IOException("Temp file missing: ${record.tempFilePath}")

            val opened = mediaStoreWriter.open(record.filename, record.mimeType)
                ?: throw IOException("MediaStore open failed")
            try {
                opened.stream.use { out ->
                    tempFile.inputStream().use { input -> input.copyTo(out) }
                }
                mediaStoreWriter.finalize(opened.uri)
                repository.updateMediaStoreUri(record.id, opened.uri.toString())
                repository.updateStatus(record.id, DownloadStatus.COMPLETE)
                notificationHelper.showComplete(record.id, record.filename, opened.uri, record.mimeType)
            } catch (e: Exception) {
                mediaStoreWriter.delete(opened.uri)
                throw e
            }
        } catch (e: Exception) {
            Log.e(TAG, "Blob download failed", e)
            repository.updateStatus(record.id, DownloadStatus.FAILED)
            notificationHelper.showFailed(record.id, record.filename)
        } finally {
            tempFile.delete()   // always clean up temp file
            if (activeCount.decrementAndGet() == 0) stopSelf()
        }
    }

    private suspend fun handleNetworkFail(record: DownloadRecord) {
        repository.updateStatus(record.id, DownloadStatus.FAILED_NETWORK)
        val current = repository.getById(record.id) ?: return
        if (current.retryCount < 3) {
            repository.incrementRetry(record.id)
            RetryDownloadWorker.schedule(this, record.id, record.originalUrl, record.filename)
        } else {
            notificationHelper.showFailed(record.id, record.filename)
        }
    }

    private fun startForeground() {
        val notif = notificationHelper.buildForegroundNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NotificationHelper.FOREGROUND_ID, notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NotificationHelper.FOREGROUND_ID, notif)
        }
    }

    override fun onDestroy() {
        job.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "DownloadService"
        const val ACTION_HTTPS = "com.cobalt.android.HTTPS"
        const val ACTION_BLOB = "com.cobalt.android.BLOB"
        const val EXTRA_URL = "url"
        const val EXTRA_ORIGINAL_URL = "originalUrl"
        const val EXTRA_FILENAME = "filename"
        const val EXTRA_MIME_TYPE = "mimeType"
        const val EXTRA_COOKIES = "cookies"
        const val EXTRA_USER_AGENT = "userAgent"
        const val EXTRA_TEMP_PATH = "tempPath"

        fun startHttps(
            ctx: Context, cobaltUrl: String, filename: String,
            mimeType: String, cookies: String, userAgent: String, originalUrl: String
        ) {
            ctx.startForegroundService(Intent(ctx, DownloadService::class.java).apply {
                action = ACTION_HTTPS
                putExtra(EXTRA_URL, cobaltUrl)
                putExtra(EXTRA_ORIGINAL_URL, originalUrl)
                putExtra(EXTRA_FILENAME, filename)
                putExtra(EXTRA_MIME_TYPE, mimeType)
                putExtra(EXTRA_COOKIES, cookies)
                putExtra(EXTRA_USER_AGENT, userAgent)
            })
        }

        fun startBlob(
            ctx: Context, tempPath: String, filename: String,
            mimeType: String, originalUrl: String
        ) {
            ctx.startForegroundService(Intent(ctx, DownloadService::class.java).apply {
                action = ACTION_BLOB
                putExtra(EXTRA_TEMP_PATH, tempPath)
                putExtra(EXTRA_ORIGINAL_URL, originalUrl)
                putExtra(EXTRA_FILENAME, filename)
                putExtra(EXTRA_MIME_TYPE, mimeType)
            })
        }
    }
}

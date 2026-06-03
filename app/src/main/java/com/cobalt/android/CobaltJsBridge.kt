package com.cobalt.android

import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import java.io.File
import java.io.FileOutputStream

class CobaltJsBridge(
    private val cacheDir: File,
    private val listener: Listener
) {
    interface Listener {
        fun onUrlSubmitted(originalUrl: String)
        fun onInjectComplete(success: Boolean)
        fun onBlobReady(tempFile: File, filename: String, mimeType: String)
        fun onBlobError(message: String)
    }

    private var blobFilename = ""
    private var blobMimeType = ""
    private var blobTempFile: File? = null
    private var blobOutputStream: FileOutputStream? = null

    @JavascriptInterface
    fun onUrlSubmitted(url: String) {
        listener.onUrlSubmitted(url)
    }

    @JavascriptInterface
    fun onInjectComplete(successStr: String) {
        listener.onInjectComplete(successStr == "true")
    }

    @JavascriptInterface
    fun onBlobStart(filename: String, mimeType: String, totalSize: Int) {
        blobFilename = filename.ifBlank { "cobalt_download" }
        blobMimeType = mimeType
        blobTempFile?.delete()
        val tempFile = File(cacheDir, "cobalt_blob_${System.currentTimeMillis()}.tmp")
        blobTempFile = tempFile
        blobOutputStream = FileOutputStream(tempFile)
    }

    @JavascriptInterface
    fun onBlobChunk(base64: String) {
        try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            blobOutputStream?.write(bytes)
        } catch (e: Exception) {
            Log.e("CobaltJsBridge", "Chunk write failed", e)
            cleanupBlob()
            listener.onBlobError("chunk write failed: ${e.message}")
        }
    }

    @JavascriptInterface
    fun onBlobComplete() {
        try {
            blobOutputStream?.flush()
            blobOutputStream?.close()
            blobOutputStream = null
            val file = blobTempFile ?: run {
                listener.onBlobError("temp file missing")
                return
            }
            blobTempFile = null
            listener.onBlobReady(file, blobFilename, blobMimeType)
        } catch (e: Exception) {
            cleanupBlob()
            listener.onBlobError("complete failed: ${e.message}")
        }
    }

    @JavascriptInterface
    fun onBlobError(message: String) {
        cleanupBlob()
        listener.onBlobError(message)
    }

    private fun cleanupBlob() {
        try { blobOutputStream?.close() } catch (e: Exception) { /* ignore */ }
        blobOutputStream = null
        blobTempFile?.delete()
        blobTempFile = null
    }
}

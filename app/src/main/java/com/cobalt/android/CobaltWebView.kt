package com.cobalt.android

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.AttributeSet
import android.webkit.*
import com.cobalt.android.download.DownloadService
import java.io.File

class CobaltWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : WebView(context, attrs) {

    interface Listener {
        fun onUrlSubmitted(originalUrl: String)
        fun onBlobDownloadReady(tempFile: File, filename: String, mimeType: String)
        fun onBlobError(message: String)
        fun onPageError(url: String, isCustomInstance: Boolean)
        fun onPageLoaded()
        fun onLocalProcessingDetected()
    }

    var listener: Listener? = null
    var audioOnlyMode: Boolean = false
    private var pendingUrl: String? = null
    private var currentOriginalUrl: String = ""
    private val bridgeJs: String by lazy {
        context.assets.open("js/cobalt_bridge.js").bufferedReader().readText()
    }

    private val jsBridge = CobaltJsBridge(
        cacheDir = context.cacheDir,
        listener = object : CobaltJsBridge.Listener {
            override fun onUrlSubmitted(originalUrl: String) {
                currentOriginalUrl = originalUrl
                listener?.onUrlSubmitted(originalUrl)
            }
            override fun onInjectComplete(success: Boolean) {
                if (!success) {
                    val url = pendingUrl ?: return
                    pendingUrl = null
                    post { loadUrl("https://cobalt.tools/#${Uri.encode(url)}") }
                }
            }
            override fun onBlobReady(tempFile: File, filename: String, mimeType: String) {
                post { listener?.onBlobDownloadReady(tempFile, filename, mimeType) }
            }
            override fun onBlobError(message: String) {
                post { listener?.onBlobError(message) }
            }
        }
    )

    init {
        configure()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configure() {
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = false
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            userAgentString = "Mozilla/5.0 (Linux; Android 14; SM-S908B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        addJavascriptInterface(jsBridge, "CobaltBridge")

        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                injectBridgeJs()
                val urlToInject = pendingUrl
                if (urlToInject != null) {
                    pendingUrl = null
                    postDelayed({ injectUrl(urlToInject, audioOnlyMode) }, 500)
                }
                listener?.onPageLoaded()
            }

            override fun onReceivedError(
                view: WebView, request: WebResourceRequest, error: WebResourceError
            ) {
                if (request.isForMainFrame) {
                    val host = request.url.host ?: ""
                    val isCustom = !host.equals("cobalt.tools", ignoreCase = true) &&
                                   !host.equals("www.cobalt.tools", ignoreCase = true)
                    listener?.onPageError(request.url.toString(), isCustom)
                }
            }
        }

        webChromeClient = object : WebChromeClient() {}

        setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            if (url.startsWith("blob:")) {
                listener?.onLocalProcessingDetected()
                val filename = extractFilename(contentDisposition, mimetype)
                evaluateJavascript(
                    "window._cobaltDownloadBlob('${url.replace("'", "\\'")}', " +
                    "'${filename.replace("'", "\\'")}', " +
                    "'${mimetype.replace("'", "\\'")}');",
                    null
                )
            } else {
                val filename = extractFilename(contentDisposition, mimetype)
                val cookies = CookieManager.getInstance().getCookie(url) ?: ""
                DownloadService.startHttps(
                    ctx = context,
                    cobaltUrl = url,
                    filename = filename,
                    mimeType = mimetype,
                    cookies = cookies,
                    userAgent = userAgent,
                    originalUrl = currentOriginalUrl
                )
            }
        }
    }

    fun submitUrl(url: String, audioOnly: Boolean) {
        audioOnlyMode = audioOnly
        pendingUrl = url
        post {
            if (progress >= 100) {
                pendingUrl = null
                injectUrl(url, audioOnly)
            }
            // else onPageFinished picks up pendingUrl
        }
    }

    private fun injectUrl(url: String, audioOnly: Boolean) {
        val escaped = url.replace("\\", "\\\\").replace("'", "\\'")
        evaluateJavascript(
            "window._cobaltInjectUrl('$escaped', ${audioOnly});", null
        )
    }

    private fun injectBridgeJs() {
        // Escape backticks and backslashes in the JS source before embedding
        val safe = bridgeJs.replace("\\", "\\\\").replace("`", "\\`")
        evaluateJavascript("(function(){$safe})()", null)
    }

    private fun extractFilename(contentDisposition: String, mimeType: String): String {
        val fromDisp = contentDisposition
            .split(";")
            .firstOrNull { it.trim().startsWith("filename") }
            ?.substringAfter("=")
            ?.trim()
            ?.trim('"')
        if (!fromDisp.isNullOrBlank()) return fromDisp
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "mp4"
        return "cobalt_${System.currentTimeMillis()}.$ext"
    }

    override fun destroy() {
        removeJavascriptInterface("CobaltBridge")
        super.destroy()
    }
}

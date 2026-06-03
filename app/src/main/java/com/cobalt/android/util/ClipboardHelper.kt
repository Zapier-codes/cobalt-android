package com.cobalt.android.util

import android.content.ClipboardManager
import android.content.Context

object ClipboardHelper {
    fun getSupportedUrl(context: Context): String? {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
        return UrlMatcher.extractUrl(text)
    }
}

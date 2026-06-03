package com.cobalt.android.util

object UrlMatcher {
    private val SUPPORTED_HOSTS = setOf(
        "youtube.com", "www.youtube.com", "youtu.be", "m.youtube.com",
        "tiktok.com", "www.tiktok.com", "vm.tiktok.com",
        "twitter.com", "www.twitter.com", "x.com", "www.x.com",
        "instagram.com", "www.instagram.com",
        "reddit.com", "www.reddit.com", "old.reddit.com", "redd.it",
        "soundcloud.com", "www.soundcloud.com",
        "vimeo.com", "www.vimeo.com",
        "twitch.tv", "www.twitch.tv", "clips.twitch.tv",
        "dailymotion.com", "www.dailymotion.com",
        "bilibili.com", "www.bilibili.com",
        "pinterest.com", "www.pinterest.com",
        "tumblr.com"
    )

    fun isSupportedUrl(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val trimmed = text.trim()
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) return false
        return try {
            val url = java.net.URL(trimmed)
            val host = url.host?.lowercase() ?: return false
            SUPPORTED_HOSTS.any { host == it || host.endsWith(".$it") }
        } catch (e: Exception) {
            false
        }
    }

    fun extractUrl(text: String?): String? {
        if (!isSupportedUrl(text)) return null
        return text?.trim()
    }
}

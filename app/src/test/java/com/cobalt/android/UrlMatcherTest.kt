package com.cobalt.android

import com.cobalt.android.util.UrlMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlMatcherTest {
    @Test fun youtubeFullUrl() = assertTrue(UrlMatcher.isSupportedUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
    @Test fun youtubeShortUrl() = assertTrue(UrlMatcher.isSupportedUrl("https://youtu.be/dQw4w9WgXcQ"))
    @Test fun tiktokUrl() = assertTrue(UrlMatcher.isSupportedUrl("https://www.tiktok.com/@user/video/123456789"))
    @Test fun twitterUrl() = assertTrue(UrlMatcher.isSupportedUrl("https://twitter.com/user/status/123"))
    @Test fun xUrl() = assertTrue(UrlMatcher.isSupportedUrl("https://x.com/user/status/123"))
    @Test fun instagramUrl() = assertTrue(UrlMatcher.isSupportedUrl("https://www.instagram.com/p/ABC123/"))
    @Test fun redditUrl() = assertTrue(UrlMatcher.isSupportedUrl("https://www.reddit.com/r/videos/comments/abc"))
    @Test fun soundcloudUrl() = assertTrue(UrlMatcher.isSupportedUrl("https://soundcloud.com/artist/track"))
    @Test fun vimeoUrl() = assertTrue(UrlMatcher.isSupportedUrl("https://vimeo.com/123456789"))
    @Test fun twitchUrl() = assertTrue(UrlMatcher.isSupportedUrl("https://www.twitch.tv/channel"))
    @Test fun randomText() = assertFalse(UrlMatcher.isSupportedUrl("hello world"))
    @Test fun mailtoLink() = assertFalse(UrlMatcher.isSupportedUrl("mailto:user@example.com"))
    @Test fun unsupportedSite() = assertFalse(UrlMatcher.isSupportedUrl("https://google.com/search?q=test"))
    @Test fun nullInput() = assertFalse(UrlMatcher.isSupportedUrl(null))
    @Test fun emptyInput() = assertFalse(UrlMatcher.isSupportedUrl(""))
    @Test fun bareHostNoScheme() = assertFalse(UrlMatcher.isSupportedUrl("youtube.com/watch?v=abc"))
    @Test fun extractReturnsUrl() = assertNotNull(UrlMatcher.extractUrl("https://youtube.com/watch?v=abc"))
    @Test fun extractReturnsNullForNonUrl() = assertNull(UrlMatcher.extractUrl("not a url"))

    // Embedded URL extraction
    @Test fun extractUrlFromProse() =
        assertEquals("https://youtu.be/dQw4w9WgXcQ",
            UrlMatcher.extractUrl("Check this out: https://youtu.be/dQw4w9WgXcQ"))

    @Test fun extractUrlFromMultiline() =
        assertEquals("https://soundcloud.com/artist/track",
            UrlMatcher.extractUrl("Song title\nhttps://soundcloud.com/artist/track"))

    @Test fun extractUrlStripsTrailingPeriod() =
        assertEquals("https://youtu.be/abc",
            UrlMatcher.extractUrl("Watch this: https://youtu.be/abc."))

    @Test fun extractUrlWithPrefixAtSign() =
        assertEquals("https://x.com/user/status/123",
            UrlMatcher.extractUrl("via @user https://x.com/user/status/123"))

    @Test fun extractReturnsNullWhenNoUrlInText() =
        assertNull(UrlMatcher.extractUrl("no link here at all"))

    @Test fun extractReturnsNullForUnsupportedEmbedded() =
        assertNull(UrlMatcher.extractUrl("check google: https://google.com/search?q=test"))
}

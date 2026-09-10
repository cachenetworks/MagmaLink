package lavalink.server.video

import java.net.URI

/**
 * Classifies web URLs before the video resolver is selected.
 *
 * Provider URLs are pages or identifiers, even though they use HTTP(S).
 * Treating them as already-resolved media makes the proxy return HTML to a
 * player instead of allowing the configured resolver or yt-dlp to extract a
 * playable stream.
 */
internal object VideoUrlClassifier {
    private val providerDomains = setOf(
        "youtube.com",
        "youtu.be",
        "youtube-nocookie.com",
        "tiktok.com",
        "vimeo.com",
        "dailymotion.com",
        "twitch.tv",
        "instagram.com",
        "twitter.com",
        "x.com"
    )

    private val directMediaExtensions = setOf(
        "mp4",
        "m4v",
        "mov",
        "webm",
        "mkv",
        "m3u8",
        "mpd",
        "ts",
        "m4s"
    )

    fun isProviderPage(uri: URI): Boolean {
        val host = uri.host?.lowercase()?.trim('.') ?: return false
        return providerDomains.any { host == it || host.endsWith(".$it") }
    }

    fun isLikelyDirectMediaUrl(uri: URI): Boolean {
        if (uri.scheme?.lowercase() !in setOf("http", "https")) return false
        if (isProviderPage(uri)) return false

        val path = uri.path?.substringAfterLast('/') ?: return false
        val extension = path.substringAfterLast('.', "").lowercase()
        return extension in directMediaExtensions
    }
}

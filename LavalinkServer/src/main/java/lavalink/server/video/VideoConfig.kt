package lavalink.server.video

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

/**
 * Configuration for MagmaLink's video plane.
 *
 * The audio plane remains configured under `lavalink.server`. Video is kept in
 * its own namespace so existing Lavalink configurations can be copied over
 * without having to change them.
 */
@ConfigurationProperties(prefix = "magmalink.video")
@Component
class VideoConfig {
    var enabled: Boolean = true
    var cacheDir: String = "./video-cache"
    var ffmpegPath: String = "ffmpeg"
    var maxSessions: Int = 32
    var sessionTtlMinutes: Long = 30
    var segmentDurationSeconds: Int = 4
    var hlsListSize: Int = 0
    var allowDirectUrls: Boolean = true
    var allowPrivateNetworks: Boolean = false
    var allowedOrigins: List<String> = listOf("*")
    var publicBaseUrl: String? = null
    var resolver: ResolverConfig = ResolverConfig()
    var ytDlp: YtDlpConfig = YtDlpConfig()
}

class ResolverConfig {
    var url: String? = null
    var apiToken: String? = null
    var connectTimeoutMs: Long = 5_000
    var readTimeoutMs: Long = 15_000
}

class YtDlpConfig {
    var enabled: Boolean = false
    var binary: String = "yt-dlp"
    var format: String = "best[ext=mp4]/best"
    var timeoutSeconds: Long = 60
    var extraArgs: List<String> = emptyList()
}

package lavalink.server.video

import kotlinx.serialization.Serializable
import java.net.URI

@Serializable
data class VideoLoadResponse(
    val loadType: String = "video",
    val data: VideoData
)

@Serializable
data class VideoData(
    val id: String,
    val identifier: String,
    val title: String? = null,
    val author: String? = null,
    val durationMs: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val mimeType: String? = null,
    val isSeekable: Boolean = true,
    val accessToken: String,
    val streamUrl: String,
    val manifestUrl: String
)

@Serializable
data class VideoLoadRequest(
    val identifier: String
)

@Serializable
data class VideoErrorResponse(
    val loadType: String = "error",
    val data: VideoErrorData
)

@Serializable
data class VideoErrorData(
    val message: String,
    val severity: String = "common",
    val cause: String? = null
)

@Serializable
data class ResolverRequest(
    val identifier: String
)

/**
 * Wire contract for an optional external resolver. A resolver may return a
 * progressive stream, or separate video and audio URLs for HLS muxing.
 */
@Serializable
data class ResolverResponse(
    val id: String? = null,
    val identifier: String? = null,
    val title: String? = null,
    val author: String? = null,
    val durationMs: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val mimeType: String? = null,
    val streamUrl: String? = null,
    val videoUrl: String? = null,
    val audioUrl: String? = null,
    val isSeekable: Boolean = true,
    val headers: Map<String, String> = emptyMap()
)

@Serializable
data class MagmaInfoResponse(
    val name: String = "MagmaLink",
    val version: String,
    val lavalinkCompatible: Boolean = true,
    val lavalinkApi: String = "v4",
    val videoEnabled: Boolean,
    val videoApi: String = "v1",
    val activeVideoSessions: Int
)

data class ResolvedVideoSource(
    val identifier: String,
    val title: String? = null,
    val author: String? = null,
    val durationMs: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val mimeType: String? = null,
    val mediaUrls: List<URI>,
    val isSeekable: Boolean = true,
    val headers: Map<String, String> = emptyMap()
) {
    init {
        require(mediaUrls.isNotEmpty()) { "At least one media URL is required" }
        require(mediaUrls.size <= 2) { "At most a video and an audio URL are supported" }
    }
}

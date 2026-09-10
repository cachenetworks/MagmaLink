package lavalink.server.video

import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets

/**
 * Performs a small bounded request for direct URLs.
 *
 * HTTP 200 only means that a server answered. It does not mean that the
 * response is media: provider pages, login pages, and bot challenges commonly
 * return HTML with status 200. Reject those responses before creating a video
 * session.
 */
internal object VideoMediaProbe {
    private val playlistContentTypes = setOf(
        "application/dash+xml",
        "application/vnd.apple.mpegurl",
        "application/x-mpegurl"
    )

    data class Result(val mimeType: String?)

    fun isHtmlContentType(contentType: String?): Boolean {
        val normalized = contentType
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase()
            ?: return false
        return normalized == "text/html" || normalized.contains("html")
    }

    fun isHtmlPayload(sample: ByteArray): Boolean = looksLikeHtml(sample)

    fun probe(
        uri: URI,
        allowPrivateNetworks: Boolean,
        connectTimeoutMs: Long,
        readTimeoutMs: Long
    ): Result {
        VideoUrlPolicy.validate(uri, allowPrivateNetworks)
        val connection = (uri.toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = connectTimeoutMs.coerceAtLeast(100).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            readTimeout = readTimeoutMs.coerceAtLeast(100).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("Accept", "video/*,audio/*,application/vnd.apple.mpegurl,application/dash+xml,application/octet-stream")
            setRequestProperty("Range", "bytes=0-511")
            setRequestProperty("User-Agent", "MagmaLink/4.2.2")
        }

        try {
            val responseCode = connection.responseCode
            require(responseCode in 200..299 && responseCode != HttpURLConnection.HTTP_NO_CONTENT) {
                "Direct video source returned HTTP $responseCode"
            }

            val contentType = connection.contentType
                ?.substringBefore(';')
                ?.trim()
                ?.lowercase()
                ?.takeIf { it.isNotEmpty() }
            val sample = runCatching {
                connection.inputStream.use { it.readNBytes(512) }
            }.getOrDefault(ByteArray(0))

            require(!isHtmlContentType(contentType) && !looksLikeHtml(sample)) {
                "Direct video source returned HTML instead of playable media"
            }

            val pathMimeType = mimeTypeFromPath(uri)
            val hasMediaContentType = contentType != null &&
                (contentType.startsWith("video/") ||
                    contentType.startsWith("audio/") ||
                    contentType in playlistContentTypes ||
                    contentType in setOf("application/ogg", "application/mp4"))

            require(hasMediaContentType || pathMimeType != null || looksLikeMedia(sample)) {
                "Direct video source did not return playable media" +
                    (contentType?.let { " (Content-Type: $it)" } ?: "")
            }

            runCatching { connection.url.toURI() }.getOrNull()?.let { finalUri ->
                VideoUrlPolicy.validate(finalUri, allowPrivateNetworks)
            }
            return Result(contentType ?: pathMimeType)
        } catch (exception: IllegalArgumentException) {
            throw exception
        } catch (exception: Exception) {
            throw IllegalArgumentException("Unable to probe direct video source '$uri'", exception)
        } finally {
            connection.disconnect()
        }
    }

    private fun mimeTypeFromPath(uri: URI): String? {
        return when (uri.path?.substringAfterLast('.', "")?.lowercase()) {
            "mp4", "m4v" -> "video/mp4"
            "webm" -> "video/webm"
            "mov" -> "video/quicktime"
            "m3u8" -> "application/vnd.apple.mpegurl"
            "mpd" -> "application/dash+xml"
            "ts" -> "video/mp2t"
            "m4s" -> "video/iso.segment"
            else -> null
        }
    }

    private fun looksLikeHtml(sample: ByteArray): Boolean {
        if (sample.isEmpty()) return false

        val text = String(sample, StandardCharsets.UTF_8)
            .trimStart('\uFEFF', ' ', '\t', '\r', '\n')
            .lowercase()
        return text.startsWith("<!doctype html") ||
            text.startsWith("<html") ||
            text.startsWith("<head") ||
            text.startsWith("<body") ||
            text.startsWith("<script")
    }

    private fun looksLikeMedia(sample: ByteArray): Boolean {
        if (sample.size >= 8 &&
            sample[4] == 'f'.code.toByte() &&
            sample[5] == 't'.code.toByte() &&
            sample[6] == 'y'.code.toByte() &&
            sample[7] == 'p'.code.toByte()
        ) {
            return true
        }

        if (sample.size >= 4 &&
            sample[0] == 0x1A.toByte() &&
            sample[1] == 0x45.toByte() &&
            sample[2] == 0xDF.toByte() &&
            sample[3] == 0xA3.toByte()
        ) {
            return true
        }

        if (sample.size >= 4 &&
            sample[0] == 'O'.code.toByte() &&
            sample[1] == 'g'.code.toByte() &&
            sample[2] == 'g'.code.toByte() &&
            sample[3] == 'S'.code.toByte()
        ) {
            return true
        }

        val text = String(sample, StandardCharsets.UTF_8).trimStart()
        return text.startsWith("#EXTM3U") || sample.firstOrNull() == 0x47.toByte()
    }
}

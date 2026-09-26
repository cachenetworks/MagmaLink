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
        readTimeoutMs: Long,
        headers: Map<String, String> = emptyMap()
    ): Result {
        VideoUrlPolicy.validate(uri, allowPrivateNetworks)
        // Some otherwise-valid CDNs reject Range probes. Retry once with a
        // bounded ordinary GET; the response body is never downloaded in full.
        var connection = openConnection(uri, allowPrivateNetworks, connectTimeoutMs, readTimeoutMs, headers, "bytes=0-511")

        try {
            var responseCode = connection.responseCode
            if (responseCode in setOf(400, 403, 405, 416, 501)) {
                connection.disconnect()
                connection = openConnection(uri, allowPrivateNetworks, connectTimeoutMs, readTimeoutMs, headers)
                responseCode = connection.responseCode
            }
            require(responseCode in 200..299 && responseCode != HttpURLConnection.HTTP_NO_CONTENT) {
                "Direct video source returned HTTP $responseCode"
            }

            val contentType = connection.contentType
                ?.substringBefore(';')
                ?.trim()
                ?.lowercase()
                ?.takeIf { it.isNotEmpty() }
            val sample = connection.inputStream.use { it.readNBytes(512) }
            require(sample.isNotEmpty()) { "Direct video source returned an empty response" }

            require(!isHtmlContentType(contentType) && !looksLikeHtml(sample)) {
                "Direct video source returned HTML instead of playable media"
            }

            val pathMimeType = mimeTypeFromPath(uri)
            val hasMediaContentType = contentType != null &&
                (contentType.startsWith("video/") ||
                    contentType.startsWith("audio/") ||
                    contentType in playlistContentTypes ||
                    contentType in setOf("application/ogg", "application/mp4"))

            // A .mp4 suffix alone cannot authenticate a text/plain error page.
            val genericBinary = contentType == null || contentType == "application/octet-stream"
            require(hasMediaContentType || looksLikeMedia(sample) || (genericBinary && pathMimeType != null)) {
                "Direct video source did not return playable media" +
                    (contentType?.let { " (Content-Type: $it)" } ?: "")
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

    /** Validate each redirect before following it, for probes and playback. */
    internal fun openConnection(
        uri: URI,
        allowPrivateNetworks: Boolean,
        connectTimeoutMs: Long,
        readTimeoutMs: Long,
        headers: Map<String, String>,
        range: String? = null
    ): HttpURLConnection {
        var target = uri
        repeat(6) {
            VideoUrlPolicy.validate(target, allowPrivateNetworks)
            val connection = (target.toURL().openConnection() as HttpURLConnection).apply {
                connectTimeout = connectTimeoutMs.coerceAtLeast(100).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                readTimeout = readTimeoutMs.coerceAtLeast(100).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                instanceFollowRedirects = false
                requestMethod = "GET"
                setRequestProperty("Accept", "video/*,audio/*,application/vnd.apple.mpegurl,application/dash+xml,application/octet-stream")
                setRequestProperty("User-Agent", "MagmaLink/4.2.2")
                headers.forEach { (key, value) ->
                    val forbidden = key.lowercase() in setOf("host", "content-length", "connection", "range")
                    val sensitiveCrossOrigin = !sameOrigin(uri, target) &&
                        key.lowercase() in setOf("authorization", "cookie", "proxy-authorization")
                    if (!forbidden && !sensitiveCrossOrigin &&
                        !key.contains('\r') && !key.contains('\n') &&
                        !value.contains('\r') && !value.contains('\n')) {
                        setRequestProperty(key, value)
                    }
                }
                if (range != null) setRequestProperty("Range", range)
            }
            if (connection.responseCode !in setOf(301, 302, 303, 307, 308)) return connection
            val location = connection.getHeaderField("Location")
            connection.disconnect()
            require(!location.isNullOrBlank()) { "Video source redirected without a Location header" }
            target = target.resolve(location)
        }
        throw IllegalArgumentException("Video source redirected too many times")
    }

    private fun sameOrigin(first: URI, second: URI) =
        first.scheme.equals(second.scheme, ignoreCase = true) &&
            first.host.equals(second.host, ignoreCase = true) && first.port == second.port

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

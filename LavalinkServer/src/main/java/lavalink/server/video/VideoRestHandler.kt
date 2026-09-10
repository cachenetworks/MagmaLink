package lavalink.server.video

import dev.arbjerg.lavalink.protocol.v4.json
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files

@RestController
@RequestMapping("/magma/v1/videos")
class VideoRestHandler(
    private val config: VideoConfig,
    private val sessions: VideoSessionManager
) {
    private val log = LoggerFactory.getLogger(VideoRestHandler::class.java)

    @GetMapping("/load", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun load(@RequestParam identifier: String): ResponseEntity<Any> = loadInternal(identifier)

    @PostMapping("/load", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun load(@RequestBody request: VideoLoadRequest): ResponseEntity<Any> = loadInternal(request.identifier)

    @org.springframework.web.bind.annotation.DeleteMapping("/{videoId}")
    fun close(@PathVariable videoId: String): ResponseEntity<Any> {
        return if (sessions.close(videoId)) {
            ResponseEntity.noContent().build<Any>()
        } else {
            error(HttpStatus.NOT_FOUND, "Video session not found")
        }
    }

    @GetMapping("/{videoId}/manifest.m3u8", produces = ["application/vnd.apple.mpegurl"])
    fun manifest(@PathVariable videoId: String): ResponseEntity<Any> {
        return try {
            val body = sessions.readManifest(videoId)
            val session = sessions.require(videoId)
            val signedBody = signManifest(body, session.accessToken)
            ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.apple.mpegurl"))
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                .body<Any>(signedBody)
        } catch (exception: VideoSessionNotFound) {
            error(HttpStatus.NOT_FOUND, exception.message ?: "Video session not found")
        } catch (exception: VideoStreamingException) {
            log.warn("Unable to produce HLS manifest for video session {}", videoId, exception)
            error(HttpStatus.BAD_GATEWAY, exception.message ?: "Unable to produce HLS manifest")
        } catch (exception: Exception) {
            log.error("Unexpected error while producing HLS manifest for {}", videoId, exception)
            error(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to produce HLS manifest")
        }
    }

    /**
     * FFmpeg writes relative segment names into the manifest. Keeping the
     * segment route beside the manifest makes the resulting URL portable to
     * browsers, media players, and reverse proxies.
     */
    @GetMapping("/{videoId}/{fileName:.+}")
    fun segment(@PathVariable videoId: String, @PathVariable fileName: String): ResponseEntity<Any> {
        if (fileName == "manifest.m3u8") return error(HttpStatus.NOT_FOUND, "Video segment not found")

        return try {
            val path = sessions.cachedFile(videoId, fileName)
            val resource: Resource = FileSystemResource(path)
            val headers = HttpHeaders()
            headers.contentLength = Files.size(path)
            headers.cacheControl = "public, max-age=600"
            ResponseEntity.ok()
                .headers(headers)
                .contentType(mediaTypeFor(fileName))
                .body(resource)
        } catch (exception: VideoSessionNotFound) {
            error(HttpStatus.NOT_FOUND, exception.message ?: "Video segment not found")
        } catch (exception: IllegalArgumentException) {
            error(HttpStatus.BAD_REQUEST, exception.message ?: "Invalid video segment")
        } catch (exception: Exception) {
            log.error("Unable to read video segment {} from session {}", fileName, videoId, exception)
            error(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to read video segment")
        }
    }

    @GetMapping("/{videoId}/stream")
    fun stream(@PathVariable videoId: String, request: HttpServletRequest): ResponseEntity<StreamingResponseBody> {
        val range = request.getHeader("Range")
        if (range != null && !RANGE_PATTERN.matches(range)) {
            return streamError(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE, "Invalid byte range")
        }

        return try {
            val session = sessions.require(videoId)
            val connection = sessions.openProgressiveConnection(videoId, range)
            val responseCode = connection.responseCode

            if (responseCode >= 400) {
                connection.disconnect()
                return streamError(
                    HttpStatus.resolve(responseCode) ?: HttpStatus.BAD_GATEWAY,
                    "The video source returned HTTP $responseCode"
                )
            }

            val headers = HttpHeaders()
            connection.contentType?.let { contentType ->
                runCatching { headers.contentType = MediaType.parseMediaType(contentType) }
            } ?: session.source.mimeType?.let { mimeType ->
                runCatching { headers.contentType = MediaType.parseMediaType(mimeType) }
            }
            connection.getHeaderField("Content-Range")?.let { headers.set("Content-Range", it) }
            connection.getHeaderField("Accept-Ranges")?.let { headers.set("Accept-Ranges", it) }
            connection.getHeaderField("ETag")?.let { headers.set("ETag", it) }
            connection.getHeaderField("Last-Modified")?.let { headers.set("Last-Modified", it) }
            connection.contentLengthLong.takeIf { it >= 0 }?.let { headers.contentLength = it }

            val body = StreamingResponseBody { output ->
                try {
                    connection.inputStream.use { input -> input.copyTo(output) }
                } finally {
                    connection.disconnect()
                }
            }

            ResponseEntity.status(responseCode).headers(headers).body(body)
        } catch (exception: VideoSessionNotFound) {
            streamError(HttpStatus.NOT_FOUND, exception.message ?: "Video session not found")
        } catch (exception: VideoStreamingException) {
            streamError(HttpStatus.BAD_GATEWAY, exception.message ?: "Unable to stream video")
        } catch (exception: Exception) {
            log.error("Unable to proxy progressive video session {}", videoId, exception)
            streamError(HttpStatus.BAD_GATEWAY, "Unable to stream video")
        }
    }

    private fun loadInternal(identifier: String): ResponseEntity<Any> {
        return try {
            val session = sessions.create(identifier)
            ResponseEntity.ok<Any>(
                VideoLoadResponse(
                    data = VideoData(
                        id = session.id,
                        identifier = session.source.identifier,
                        title = session.source.title,
                        author = session.source.author,
                        durationMs = session.source.durationMs,
                        width = session.source.width,
                        height = session.source.height,
                        mimeType = session.source.mimeType,
                        isSeekable = session.source.isSeekable,
                        accessToken = session.accessToken,
                        streamUrl = endpoint(session.id, "stream", session.accessToken),
                        manifestUrl = endpoint(session.id, "manifest.m3u8", session.accessToken)
                    )
                )
            )
        } catch (exception: IllegalArgumentException) {
            error(HttpStatus.BAD_REQUEST, exception.message ?: "Invalid video identifier")
        } catch (exception: VideoCapacityException) {
            error(HttpStatus.TOO_MANY_REQUESTS, exception.message ?: "Video capacity reached")
        } catch (exception: VideoStreamingException) {
            error(HttpStatus.BAD_GATEWAY, exception.message ?: "Unable to resolve video")
        } catch (exception: Exception) {
            log.error("Unexpected video resolution error for {}", identifier, exception)
            error(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to resolve video")
        }
    }

    private fun endpoint(id: String, resource: String, token: String): String {
        val path = "/magma/v1/videos/$id/$resource"
        val base = config.publicBaseUrl?.trimEnd('/')?.plus(path) ?: path
        return "$base?token=${URLEncoder.encode(token, StandardCharsets.UTF_8)}"
    }

    private fun signManifest(manifest: String, token: String): String {
        val encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8)
        return manifest.lineSequence().joinToString("\n") { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                line
            } else {
                val separator = if (line.contains('?')) '&' else '?'
                "$line${separator}token=$encodedToken"
            }
        }
    }

    private fun mediaTypeFor(fileName: String): MediaType {
        return when (fileName.substringAfterLast('.', "").lowercase()) {
            "ts" -> MediaType.parseMediaType("video/mp2t")
            "m4s" -> MediaType.parseMediaType("video/iso.segment")
            "mp4" -> MediaTypeFactoryCompat.videoMp4
            else -> MediaType.APPLICATION_OCTET_STREAM
        }
    }

    private fun error(status: HttpStatus, message: String): ResponseEntity<Any> {
        return ResponseEntity.status(status).body<Any>(
            VideoErrorResponse(data = VideoErrorData(message = message))
        )
    }

    /**
     * Keep the stream endpoint's declared body type as StreamingResponseBody.
     * Spring selects its async streaming return-value handler from the method's
     * declared generic type; ResponseEntity<Any> causes the streaming body to be
     * treated like a normal object and can turn otherwise-valid playback into
     * an HTTP 500 before a single media byte is written.
     */
    private fun streamError(status: HttpStatus, message: String): ResponseEntity<StreamingResponseBody> {
        val payload = json.encodeToString(
            VideoErrorResponse.serializer(),
            VideoErrorResponse(data = VideoErrorData(message = message))
        ).toByteArray(StandardCharsets.UTF_8)
        val body = StreamingResponseBody { output -> output.write(payload) }

        return ResponseEntity.status(status)
            .contentType(MediaType.APPLICATION_JSON)
            .contentLength(payload.size.toLong())
            .body(body)
    }

    companion object {
        private val RANGE_PATTERN = Regex("bytes=\\d*-\\d*(,\\d*-\\d*)*")
    }
}

private object MediaTypeFactoryCompat {
    val videoMp4: MediaType = MediaType.parseMediaType("video/mp4")
}

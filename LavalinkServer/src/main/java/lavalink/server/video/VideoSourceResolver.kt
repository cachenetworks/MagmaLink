package lavalink.server.video

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpClient
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

interface VideoSourceResolver {
    fun resolve(identifier: String): ResolvedVideoSource
}

/**
 * Resolves direct media URLs, an optional HTTP resolver service, or yt-dlp.
 * Keeping provider-specific extraction outside the audio player means the
 * Lavalink source/plugin API remains untouched.
 */
@Service
class CompositeVideoSourceResolver(private val config: VideoConfig) : VideoSourceResolver {
    private val log = LoggerFactory.getLogger(CompositeVideoSourceResolver::class.java)
    private val json = Json { ignoreUnknownKeys = true }
    private val resolverClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(config.resolver.connectTimeoutMs.coerceAtLeast(100)))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    override fun resolve(identifier: String): ResolvedVideoSource {
        val normalized = identifier.trim()
        require(normalized.isNotEmpty()) { "Video identifier must not be empty" }

        val direct = runCatching { URI.create(normalized) }.getOrNull()
        if (direct != null && direct.scheme?.lowercase() in setOf("http", "https")) {
            if (config.allowDirectUrls && VideoUrlClassifier.isLikelyDirectMediaUrl(direct)) {
                return resolveDirectUrl(normalized, direct)
            }
        }

        config.resolver.url?.trim()?.takeIf { it.isNotEmpty() }?.let {
            return resolveWithHttpService(normalized, URI.create(it))
        }

        if (config.ytDlp.enabled) {
            return resolveWithYtDlp(normalized)
        }

        if (direct != null &&
            direct.scheme?.lowercase() in setOf("http", "https") &&
            config.allowDirectUrls &&
            !VideoUrlClassifier.isProviderPage(direct)
        ) {
            VideoUrlPolicy.validate(direct, config.allowPrivateNetworks)
            return resolveDirectUrl(normalized, direct)
        }

        if (direct != null &&
            direct.scheme?.lowercase() in setOf("http", "https") &&
            !config.allowDirectUrls
        ) {
            throw IllegalArgumentException("Direct video URLs are disabled")
        }

        throw IllegalArgumentException(
            "No video resolver can handle '$normalized'. Configure " +
                    "magmalink.video.resolver.url or enable magmalink.video.yt-dlp."
        )
    }

    private fun resolveDirectUrl(identifier: String, uri: URI): ResolvedVideoSource {
        val probe = VideoMediaProbe.probe(
            uri = uri,
            allowPrivateNetworks = config.allowPrivateNetworks,
            connectTimeoutMs = config.resolver.connectTimeoutMs,
            readTimeoutMs = config.resolver.readTimeoutMs
        )
        return ResolvedVideoSource(
            identifier = identifier,
            mediaUrls = listOf(uri),
            mimeType = probe.mimeType ?: guessMimeType(uri)
        )
    }

    private fun resolveWithHttpService(identifier: String, endpoint: URI): ResolvedVideoSource {
        require(endpoint.scheme?.lowercase() in setOf("http", "https")) {
            "Video resolver URL must use http or https"
        }

        val requestBuilder = HttpRequest.newBuilder(endpoint)
            .timeout(Duration.ofMillis(config.resolver.readTimeoutMs.coerceAtLeast(100)))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(ResolverRequest.serializer(), ResolverRequest(identifier))))

        config.resolver.apiToken?.takeIf { it.isNotBlank() }?.let {
            requestBuilder.header("Authorization", "Bearer $it")
        }

        val response = resolverClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
        require(response.statusCode() in 200..299) {
            "Video resolver returned HTTP ${response.statusCode()}"
        }

        val resolved = json.decodeFromString(ResolverResponse.serializer(), response.body())
        return resolved.toSource(identifier)
    }

    private fun resolveWithYtDlp(identifier: String): ResolvedVideoSource {
        val args = buildList {
            add(config.ytDlp.binary)
            add("--no-playlist")
            add("--no-warnings")
            add("--skip-download")
            add("--dump-single-json")
            add("--format")
            add(config.ytDlp.format)
            addAll(config.ytDlp.extraArgs)
            add(identifier)
        }

        log.debug("Resolving video identifier with yt-dlp: {}", identifier)
        val process = ProcessBuilder(args).redirectErrorStream(false).start()
        val readers = Executors.newFixedThreadPool(2)
        val outputFuture = readers.submit<String> {
            BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
        }
        val errorFuture = readers.submit<String> {
            BufferedReader(InputStreamReader(process.errorStream)).use { it.readText() }
        }

        try {
            if (!process.waitFor(config.ytDlp.timeoutSeconds.coerceAtLeast(1), TimeUnit.SECONDS)) {
                process.destroyForcibly()
                throw IllegalStateException("yt-dlp timed out while resolving '$identifier'")
            }

            val output = outputFuture.get(2, TimeUnit.SECONDS)
            val error = errorFuture.get(2, TimeUnit.SECONDS)
            if (process.exitValue() != 0) {
                throw IllegalStateException(
                    "yt-dlp failed for '$identifier': ${error.trim().takeLast(500)}"
                )
            }

            return parseYtDlpResponse(identifier, output)
        } finally {
            readers.shutdownNow()
        }
    }

    private fun parseYtDlpResponse(identifier: String, output: String): ResolvedVideoSource {
        val root = json.parseToJsonElement(output).jsonObject
        val requestedFormats = root["requested_formats"]?.jsonArray.orEmpty()
        val urls = if (requestedFormats.isNotEmpty()) {
            requestedFormats.mapNotNull { it.jsonObject["url"]?.jsonPrimitive?.contentOrNull }
        } else {
            listOfNotNull(root["url"]?.jsonPrimitive?.contentOrNull)
        }.distinct().take(2)

        require(urls.isNotEmpty()) { "yt-dlp returned no playable video URL" }
        val mediaUrls = urls.map { URI.create(it).also { uri -> VideoUrlPolicy.validate(uri, config.allowPrivateNetworks) } }
        val headers = root["http_headers"]?.jsonObject?.mapNotNull { (key, value) ->
            value.jsonPrimitive.contentOrNull?.let { key to it }
        }?.toMap().orEmpty()

        val durationMs = root["duration"]?.jsonPrimitive?.doubleOrNull?.let { (it * 1_000).toLong() }
        return ResolvedVideoSource(
            identifier = identifier,
            title = root["title"]?.jsonPrimitive?.contentOrNull,
            author = root["uploader"]?.jsonPrimitive?.contentOrNull,
            durationMs = durationMs,
            width = root["width"]?.jsonPrimitive?.intOrNull,
            height = root["height"]?.jsonPrimitive?.intOrNull,
            mimeType = root["ext"]?.jsonPrimitive?.contentOrNull?.let { extension ->
                when (extension.lowercase()) {
                    "mp4" -> "video/mp4"
                    "webm" -> "video/webm"
                    else -> null
                }
            },
            mediaUrls = mediaUrls,
            headers = headers
        )
    }

    private fun ResolverResponse.toSource(requestedIdentifier: String): ResolvedVideoSource {
        val urls = if (streamUrl != null) {
            listOf(streamUrl)
        } else {
            listOfNotNull(videoUrl, audioUrl).distinct().take(2)
        }
        require(urls.isNotEmpty()) { "Video resolver returned no playable media URL" }

        val parsedUrls = urls.map { URI.create(it).also { uri -> VideoUrlPolicy.validate(uri, config.allowPrivateNetworks) } }
        return ResolvedVideoSource(
            identifier = identifier ?: requestedIdentifier,
            title = title,
            author = author,
            durationMs = durationMs,
            width = width,
            height = height,
            mimeType = mimeType,
            mediaUrls = parsedUrls,
            isSeekable = isSeekable,
            headers = headers
        )
    }

    private fun guessMimeType(uri: URI): String? {
        return when (uri.path?.substringAfterLast('.', "")?.lowercase()) {
            "mp4", "m4v" -> "video/mp4"
            "webm" -> "video/webm"
            "mov" -> "video/quicktime"
            "m3u8" -> "application/vnd.apple.mpegurl"
            "mpd" -> "application/dash+xml"
            else -> null
        }
    }
}

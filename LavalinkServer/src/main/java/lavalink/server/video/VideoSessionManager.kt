package lavalink.server.video

import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.Comparator
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class VideoSessionNotFound(message: String) : RuntimeException(message)

class VideoStreamingException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class VideoCapacityException(message: String) : RuntimeException(message)

internal class VideoSession(
    val id: String,
    val source: ResolvedVideoSource,
    val directory: Path,
    val accessToken: String
) {
    @Volatile
    var process: Process? = null

    @Volatile
    var lastAccess: Long = System.currentTimeMillis()

    fun touch() {
        lastAccess = System.currentTimeMillis()
    }
}

@Service
class VideoSessionManager(
    private val config: VideoConfig,
    private val resolver: VideoSourceResolver
) {
    private val log = LoggerFactory.getLogger(VideoSessionManager::class.java)
    private val sessions = ConcurrentHashMap<String, VideoSession>()
    private val cleanupExecutor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "magmalink-video-cleaner").apply { isDaemon = true }
    }
    private val cacheRoot: Path = Path.of(config.cacheDir).toAbsolutePath().normalize()

    init {
        if (config.enabled) {
            runCatching { Files.createDirectories(cacheRoot) }
                .onFailure { log.warn("Unable to create video cache directory ${cacheRoot}", it) }
        }

        cleanupExecutor.scheduleAtFixedRate(
            ::removeExpiredSessions,
            1,
            1,
            TimeUnit.MINUTES
        )
    }

    fun create(identifier: String): VideoSession {
        if (!config.enabled) {
            throw VideoStreamingException("Video streaming is disabled")
        }

        removeExpiredSessions()
        if (sessions.size >= config.maxSessions.coerceAtLeast(1)) {
            throw VideoCapacityException("The maximum number of video sessions is active")
        }

        val source = resolver.resolve(identifier)
        val id = UUID.randomUUID().toString()
        val directory = cacheRoot.resolve(id).normalize()
        if (!directory.startsWith(cacheRoot)) {
            throw VideoStreamingException("Invalid video cache path")
        }

        Files.createDirectories(directory)
        return VideoSession(id, source, directory, UUID.randomUUID().toString()).also { sessions[id] = it }
    }

    fun activeSessionCount(): Int = sessions.size

    fun close(id: String): Boolean {
        val session = sessions.remove(id) ?: return false
        stopAndDelete(session)
        return true
    }

    fun require(id: String): VideoSession {
        val session = sessions[id] ?: throw VideoSessionNotFound("Video session '$id' was not found")
        session.touch()
        return session
    }

    fun isAccessTokenValid(path: String, token: String?): Boolean {
        if (token.isNullOrBlank() || !path.startsWith("/magma/v1/videos/")) return false
        val id = path.removePrefix("/magma/v1/videos/").substringBefore('/')
        val session = sessions[id] ?: return false
        if (session.accessToken != token) return false
        session.touch()
        return true
    }

    fun ensureHls(id: String): VideoSession {
        val session = require(id)
        synchronized(session) {
            val playlist = session.directory.resolve("index.m3u8")
            if (!Files.isRegularFile(playlist) && session.process?.isAlive != true) {
                startHls(session)
            }
        }
        return session
    }

    fun readManifest(id: String): String {
        val session = ensureHls(id)
        val playlist = session.directory.resolve("index.m3u8")
        val deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos()

        while (System.nanoTime() < deadline) {
            if (Files.isRegularFile(playlist) && Files.size(playlist) > 0) {
                return Files.readString(playlist)
            }

            if (session.process?.isAlive == false) break
            Thread.sleep(100)
        }

        throw VideoStreamingException("FFmpeg did not produce an HLS manifest in time")
    }

    fun cachedFile(id: String, fileName: String): Path {
        val session = require(id)
        require(fileName.matches(Regex("[A-Za-z0-9._-]+"))) { "Invalid video segment name" }

        val file = session.directory.resolve(fileName).normalize()
        if (!file.startsWith(session.directory) || !Files.isRegularFile(file)) {
            throw VideoSessionNotFound("Video segment '$fileName' was not found")
        }

        return file
    }

    fun openProgressiveConnection(id: String, range: String?): HttpURLConnection {
        val session = require(id)
        if (session.source.mediaUrls.size != 1) {
            throw VideoStreamingException("Progressive streaming requires one muxed media URL; use HLS instead")
        }

        val uri = session.source.mediaUrls.single()
        VideoUrlPolicy.validate(uri, config.allowPrivateNetworks)
        val connection = (uri.toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = config.resolver.connectTimeoutMs.coerceAtLeast(100).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            readTimeout = config.resolver.readTimeoutMs.coerceAtLeast(100).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            instanceFollowRedirects = true
            requestMethod = "GET"

            session.source.headers.forEach { (key, value) ->
                if (key !in setOf("Host", "Content-Length", "Connection", "Range")) {
                    setRequestProperty(key, value)
                }
            }
            if (range != null) setRequestProperty("Range", range)
        }

        try {
            connection.connect()
        } catch (exception: Exception) {
            connection.disconnect()
            throw VideoStreamingException("Unable to connect to the video source", exception)
        }
        return connection
    }

    private fun startHls(session: VideoSession) {
        Files.createDirectories(session.directory)
        val playlist = session.directory.resolve("index.m3u8")
        val segmentPattern = session.directory.resolve("segment-%05d.ts")
        val args = mutableListOf<String>()
        args += config.ffmpegPath
        args += listOf("-hide_banner", "-loglevel", "error", "-y")

        session.source.mediaUrls.forEach { uri ->
            if (session.source.headers.isNotEmpty()) {
                args += listOf("-headers", session.source.headers.toFfmpegHeaders())
            }
            args += listOf("-i", uri.toString())
        }

        args += listOf("-map", "0:v:0")
        args += listOf("-map", if (session.source.mediaUrls.size == 2) "1:a:0?" else "0:a:0?")
        args += listOf(
            "-c:v", "copy",
            "-c:a", "aac",
            "-b:a", "128k",
            "-f", "hls",
            "-hls_time", config.segmentDurationSeconds.coerceAtLeast(1).toString(),
            "-hls_list_size", config.hlsListSize.coerceAtLeast(0).toString(),
            "-hls_flags", "independent_segments",
            "-hls_segment_filename", segmentPattern.toString(),
            playlist.toString()
        )

        log.info("Starting HLS video session {} for {}", session.id, session.source.identifier)
        val process = try {
            ProcessBuilder(args)
                .directory(session.directory.toFile())
                .redirectErrorStream(true)
                .start()
        } catch (exception: Exception) {
            throw VideoStreamingException("Unable to start FFmpeg at '${config.ffmpegPath}'", exception)
        }

        session.process = process
        thread(
            start = true,
            isDaemon = true,
            name = "magmalink-ffmpeg-${session.id}"
        ) {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line -> log.debug("FFmpeg {}: {}", session.id, redact(line)) }
            }
            val exitCode = process.waitFor()
            if (exitCode != 0 && !Files.isRegularFile(playlist)) {
                log.warn("FFmpeg exited with code {} for video session {}", exitCode, session.id)
            }
        }
    }

    private fun removeExpiredSessions() {
        val cutoff = System.currentTimeMillis() - Duration.ofMinutes(config.sessionTtlMinutes.coerceAtLeast(1)).toMillis()
        sessions.entries.removeIf { entry ->
            if (entry.value.lastAccess >= cutoff) return@removeIf false
            if (sessions.remove(entry.key, entry.value)) {
                stopAndDelete(entry.value)
            }
            true
        }
    }

    private fun stopAndDelete(session: VideoSession) {
        session.process?.let { process ->
            process.destroy()
            if (!process.waitFor(500, TimeUnit.MILLISECONDS)) process.destroyForcibly()
        }

        runCatching {
            if (Files.exists(session.directory)) {
                Files.walk(session.directory).use { paths ->
                    paths.sorted(Comparator.reverseOrder<Path>()).forEach { path -> Files.deleteIfExists(path) }
                }
            }
        }.onFailure { log.debug("Unable to remove video cache for session ${session.id}", it) }
    }

    private fun redact(line: String): String {
        return line.replace(Regex("(?i)([?&](?:sig|signature|token|key|auth)=)[^&\\s]+"), "$1…")
            .take(500)
    }

    @PreDestroy
    fun shutdown() {
        cleanupExecutor.shutdownNow()
        sessions.values.forEach(::stopAndDelete)
        sessions.clear()
    }
}

private fun Map<String, String>.toFfmpegHeaders(): String = entries.joinToString("\r\n") { (key, value) ->
    "${key.replace(Regex("[\\r\\n]"), "")}: ${value.replace(Regex("[\\r\\n]"), "")}"
} + "\r\n"

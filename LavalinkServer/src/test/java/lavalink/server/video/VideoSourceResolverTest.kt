package lavalink.server.video

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger

class VideoSourceResolverTest {
    @Test
    fun `provider page is delegated to the configured resolver`() {
        val calls = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/video.mp4") { exchange ->
            val bytes = byteArrayOf(0, 0, 0, 24, 102, 116, 121, 112, 105, 115, 111, 109)
            exchange.responseHeaders.add("Content-Type", "video/mp4")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.createContext("/resolve") { exchange ->
            calls.incrementAndGet()
            val body = """{"title":"Resolved YouTube","streamUrl":"http://127.0.0.1:${server.address.port}/video.mp4"}"""
                .toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()

        try {
            val config = VideoConfig().apply {
                allowPrivateNetworks = true
                resolver.url = "http://127.0.0.1:" + server.address.port + "/resolve"
            }

            val source = CompositeVideoSourceResolver(config)
                .resolve("https://www.youtube.com/watch?v=abc")

            assertEquals("Resolved YouTube", source.title)
            assertEquals(1, calls.get())
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `provider page is not accepted as a direct media source`() {
        val config = VideoConfig().apply {
            allowPrivateNetworks = true
            ytDlp.enabled = false
        }

        val exception = assertThrows<IllegalArgumentException> {
            CompositeVideoSourceResolver(config)
                .resolve("https://www.youtube.com/watch?v=abc")
        }

        assertTrue(exception.message.orEmpty().contains("No video resolver"))
    }

    @Test
    fun `an HTML 200 response is rejected for a direct media URL`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/video.mp4") { exchange ->
            val body = "<!doctype html><html><body>not video</body></html>"
                .toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/octet-stream")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()

        try {
            val config = VideoConfig().apply {
                allowPrivateNetworks = true
                ytDlp.enabled = false
            }

            val exception = assertThrows<IllegalArgumentException> {
                CompositeVideoSourceResolver(config)
                    .resolve("http://127.0.0.1:" + server.address.port + "/video.mp4")
            }

            assertTrue(exception.message.orEmpty().contains("HTML"))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `direct MP4 falls back to a bounded GET when its CDN rejects ranges`() {
        val ranged = AtomicInteger()
        val ordinary = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/movie.mp4") { exchange ->
            if (exchange.requestHeaders.containsKey("Range")) {
                ranged.incrementAndGet()
                exchange.sendResponseHeaders(403, -1)
            } else {
                ordinary.incrementAndGet()
                val bytes = byteArrayOf(0, 0, 0, 24, 102, 116, 121, 112, 105, 115, 111, 109)
                exchange.responseHeaders.add("Content-Type", "video/mp4")
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            exchange.close()
        }
        server.start()
        try {
            val config = VideoConfig().apply { allowPrivateNetworks = true }
            val source = CompositeVideoSourceResolver(config)
                .resolve("http://127.0.0.1:${server.address.port}/movie.mp4")

            assertEquals("video/mp4", source.mimeType)
            assertEquals(1, ranged.get())
            assertEquals(1, ordinary.get())
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `extensionless MP4 is probed before a configured provider resolver`() {
        val resolverCalls = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/stream") { exchange ->
            val bytes = byteArrayOf(0, 0, 0, 24, 102, 116, 121, 112, 105, 115, 111, 109)
            exchange.responseHeaders.add("Content-Type", "application/octet-stream")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.createContext("/resolver") { exchange ->
            resolverCalls.incrementAndGet()
            exchange.sendResponseHeaders(500, -1)
            exchange.close()
        }
        server.start()
        try {
            val config = VideoConfig().apply {
                allowPrivateNetworks = true
                resolver.url = "http://127.0.0.1:${server.address.port}/resolver"
            }
            val source = CompositeVideoSourceResolver(config)
                .resolve("http://127.0.0.1:${server.address.port}/stream?sig=test")

            assertEquals("application/octet-stream", source.mimeType)
            assertEquals(0, resolverCalls.get())
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `a text error is rejected even when the URL ends in mp4`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/movie.mp4") { exchange ->
            val body = "Video unavailable".toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "text/plain")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val config = VideoConfig().apply {
                allowPrivateNetworks = true
                ytDlp.enabled = false
            }
            assertThrows<IllegalArgumentException> {
                CompositeVideoSourceResolver(config)
                    .resolve("http://127.0.0.1:${server.address.port}/movie.mp4")
            }
        } finally {
            server.stop(0)
        }
    }
}

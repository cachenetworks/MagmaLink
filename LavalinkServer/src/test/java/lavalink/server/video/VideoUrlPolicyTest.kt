package lavalink.server.video

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.net.URI

class VideoUrlPolicyTest {
    @Test
    fun `private and loopback sources are rejected by default`() {
        assertThrows<IllegalArgumentException> {
            VideoUrlPolicy.validate(URI("http://127.0.0.1/video.mp4"), allowPrivateNetworks = false)
        }
    }

    @Test
    fun `private sources can be explicitly enabled`() {
        assertDoesNotThrow {
            VideoUrlPolicy.validate(URI("http://127.0.0.1/video.mp4"), allowPrivateNetworks = true)
        }
    }

    @Test
    fun `non-http sources are rejected`() {
        assertThrows<IllegalArgumentException> {
            VideoUrlPolicy.validate(URI("file:///tmp/video.mp4"), allowPrivateNetworks = true)
        }
    }
}

package lavalink.server.video

import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI

object VideoUrlPolicy {
    private val allowedSchemes = setOf("http", "https")

    fun validate(uri: URI, allowPrivateNetworks: Boolean) {
        require(uri.scheme?.lowercase() in allowedSchemes) {
            "Only http and https video sources are supported"
        }
        require(uri.userInfo == null) { "Video source URLs must not contain user information" }

        val host = uri.host?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Video source URL has no valid host")

        if (allowPrivateNetworks) return

        val addresses = try {
            InetAddress.getAllByName(host)
        } catch (exception: Exception) {
            throw IllegalArgumentException("Unable to resolve video source host '$host'", exception)
        }

        require(addresses.none(::isPrivateOrLocal)) {
            "Video source resolves to a private or local network address"
        }
    }

    private fun isPrivateOrLocal(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress
        ) {
            return true
        }

        val bytes = address.address
        if (address is Inet6Address) {
            // Unique-local IPv6 (fc00::/7) and link-local (fe80::/10).
            val first = bytes[0].toInt() and 0xff
            val second = bytes[1].toInt() and 0xff
            return ((first and 0xfe) == 0xfc) ||
                    (first == 0xfe && (second and 0xc0) == 0x80)
        }

        return false
    }
}

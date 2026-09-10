package lavalink.server.bootstrap

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.io.path.outputStream

class PluginManagerTest {
    @TempDir
    lateinit var pluginsDirectory: Path

    @Test
    fun `support jars do not prevent plugin jars from loading`() {
        createJar("plugin-support.jar")
        createJar(
            "example-plugin-1.0.0.jar",
            "lavalink-plugins/example.properties" to "name=example\npath=example.plugin\nversion=1.0.0\n"
        )

        val config = PluginsConfig().apply {
            pluginsDir = pluginsDirectory.toString()
        }

        val manifests = PluginManager(config).pluginManifests

        assertTrue(manifests.any { it.name == "example" && it.version == "1.0.0" })
    }

    private fun createJar(fileName: String, vararg entries: Pair<String, String>) {
        pluginsDirectory.resolve(fileName).outputStream().use { output ->
            JarOutputStream(output).use { jar ->
                entries.forEach { (name, content) ->
                    jar.putNextEntry(JarEntry(name))
                    jar.write(content.toByteArray())
                    jar.closeEntry()
                }
            }
        }
    }
}

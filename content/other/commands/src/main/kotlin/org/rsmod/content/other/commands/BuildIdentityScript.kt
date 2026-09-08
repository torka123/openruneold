package org.rsmod.content.other.commands

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import org.rsmod.api.server.config.ServerConfig
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Prints an unambiguous, non-secret identity for the source tree and runtime build in development.
 */
class BuildIdentityScript @Inject constructor(private val serverConfig: ServerConfig) :
    PluginScript() {
    private val logger = InlineLogger()

    override fun ScriptContext.startup() {
        val workingDirectory = Paths.get("").toAbsolutePath().normalize()
        val cachePath = workingDirectory.resolve(".data").resolve("cache").resolve("SERVER")
        val artifact =
            runCatching {
                    Paths.get(
                        BuildIdentityScript::class.java.protectionDomain.codeSource.location.toURI()
                    )
                }
                .getOrNull()
        val artifactModified = artifactModified(artifact)

        logger.info {
            buildString {
                appendLine("OPENRUNE CUSTOM BUILD ACTIVE")
                appendLine("  Working directory: $workingDirectory")
                appendLine("  Git branch: ${gitValue("branch", "--show-current")}")
                appendLine("  Git commit: ${gitValue("rev-parse", "--short", "HEAD")}")
                appendLine("  Runtime artifact: ${artifact ?: "unavailable"}")
                appendLine("  Artifact modified: $artifactModified")
                appendLine("  Cache: $cachePath")
                append("  Cache revision: ${serverConfig.revision}")
            }
        }
    }

    private fun artifactModified(artifact: Path?): String =
        artifact
            ?.let { runCatching { Files.getLastModifiedTime(it).toInstant() }.getOrNull() }
            ?.toString() ?: "unavailable"

    private fun gitValue(vararg args: String): String =
        runCatching {
                val process =
                    ProcessBuilder(listOf("git", *args))
                        .directory(Paths.get("").toFile())
                        .redirectErrorStream(true)
                        .start()
                val output = process.inputStream.bufferedReader().use { it.readText().trim() }
                if (!process.waitFor(1, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    "unavailable"
                } else {
                    output.ifBlank { "unavailable" }
                }
            }
            .getOrDefault("unavailable")
}

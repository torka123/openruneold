package dev.openrune

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.file.Path
import kotlin.io.path.readText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CollectionLogWikiMismatchAllowlistTest {
    @Test
    fun `every cache wiki mismatch is explicitly justified`() {
        val mapper = ObjectMapper()
        val report =
            mapper.readTree(
                repositoryFile("docs/collection-log/source/mismatch-report.json").readText()
            )
        val allowlist =
            mapper.readTree(
                repositoryFile("docs/collection-log/source/mismatch-allowlist.json").readText()
            )

        assertEquals(240, report.path("metadata").path("cacheRevision").asInt())
        assertEquals(240, allowlist.path("cacheRevision").asInt())
        val reported = report.path("mismatches").map(::identity)
        val allowed = allowlist.path("entries").map(::identity)
        val allowedKeys = allowed.map(::key).toSet()

        assertEquals(4, reported.size)
        assertEquals(reported.map(::key).toSet(), allowedKeys)
        assertTrue(allowed.all { it.path("reason").asText().isNotBlank() })
    }

    private fun key(node: JsonNode): Pair<String, String> =
        node.path("kind").asText() to node.path("message").asText()

    private fun identity(node: JsonNode): JsonNode = node

    private fun repositoryFile(relative: String): Path {
        var current = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        repeat(6) {
            val candidate = current.resolve(relative)
            if (candidate.toFile().isFile) return candidate
            current = current.parent ?: return@repeat
        }
        error("Could not locate repository file $relative from ${System.getProperty("user.dir")}")
    }
}

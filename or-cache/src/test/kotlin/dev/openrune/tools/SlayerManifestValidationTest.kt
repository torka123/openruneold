package dev.openrune.tools

import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SlayerManifestValidationTest {

    @Test
    fun `canonical manifest covers every audited master row and boss identity`() {
        val manifestPath = findManifest()
        val root = ObjectMapper().readTree(manifestPath.toFile())
        val counts = root.path("counts")
        val masterRows = root.path("masterTaskRows")
        val bossRows = root.path("directBossTasks")

        assertEquals(9, counts.path("masterRoleCount").asInt())
        assertEquals(13, counts.path("masterNpcVariantCount").asInt())
        assertEquals(330, masterRows.size())
        assertEquals(33, bossRows.size())
        assertEquals(363, counts.path("manifestEntryCount").asInt())
        assertEquals(148, counts.path("canonicalConcreteTaskKeyCount").asInt())

        val entries = masterRows.toList() + bossRows.toList()
        assertEquals(363, entries.size)
        assertTrue(entries.all { it.path("taskKey").asText().isNotBlank() })
        assertTrue(entries.all { it.path("source").path("accessDate").asText() == "2026-08-12" })
        assertTrue(entries.all { it.path("supportStatus").asText() == "BLOCKED" })
        assertTrue(entries.all { it.path("supportReason").asText().isNotBlank() })

        val standardKeys =
            masterRows
                .filter { it.path("kind").asText() == "standard" }
                .map { it.path("taskKey").asText() }
                .toSet()
        val bossKeys = bossRows.map { it.path("taskKey").asText() }.toSet()
        assertEquals(115, standardKeys.size)
        assertEquals(33, bossKeys.size)
        assertFalse(standardKeys.any(String::isBlank))
        assertFalse(bossKeys.any(String::isBlank))
    }

    private fun findManifest(): Path {
        val start = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        val path =
            generateSequence(start) { it.parent }
                .map { it.resolve("data/slayer/osrs_slayer_manifest.json") }
                .firstOrNull(Path::exists)
        assertTrue(
            path != null,
            "Could not locate data/slayer/osrs_slayer_manifest.json from $start",
        )
        requireNotNull(path)
        assertTrue(Files.isRegularFile(path), "Manifest path is not a regular file: $path")
        return path
    }
}

package org.rsmod.content.skills.construction

import dev.openrune.ServerCacheManager
import dev.openrune.gamevals.GameValProvider
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.rsmod.content.skills.construction.data.DonorConstructionData

class ConstructionBuildRequirementsTest {
    private val buildables = DonorConstructionData.buildables.associateBy { it.name }

    @Test
    fun `tip jar accepts its exact OSRS materials and mapped inventory ids`() {
        val tipJar = requireNotNull(buildables["TIP_JAR"])
        val materials = mapOf(8782 to 2, 1775 to 1, 8784 to 1, 13204 to 5)
        assertEquals(materials, ConstructionBuildRequirements.materialTotals(tipJar))

        val expectedMappings =
            mapOf(
                8782 to "obj.plank_mahogany",
                1775 to "obj.molten_glass",
                8784 to "obj.gold_leaf",
                13204 to "obj.platinum",
            )
        expectedMappings.forEach { (externalId, internalName) ->
            val mapped = RSCM.getReverseMapping(RSCMType.OBJ, externalId)
            assertEquals(internalName, mapped)
            assertNotNull(ServerCacheManager.getItem(mapped.asRSCM(RSCMType.OBJ)))
        }

        assertNull(
            ConstructionBuildRequirements.firstProblem(
                buildable = tipJar,
                constructionLevel = 99,
                hasHammer = true,
                hasSaw = true,
                hasCrystalSaw = false,
                currentBuildable = null,
                itemCount = { materials[it] ?: 0 },
            )
        )
    }

    @Test
    fun `portal nexus tiers require the exact prior OSRS tier`() {
        val marble = requireNotNull(buildables["MARBLE_PORTAL_NEXUS"])
        val gilded = requireNotNull(buildables["GILDED_PORTAL_NEXUS"])
        val crystalline = requireNotNull(buildables["CRYSTALLINE_PORTAL_NEXUS"])
        val materials = mapOf(8786 to 4, 8784 to 2, 8788 to 2)

        fun problem(buildableName: String, current: String?) =
            ConstructionBuildRequirements.firstProblem(
                buildable = requireNotNull(buildables[buildableName]),
                constructionLevel = 99,
                hasHammer = true,
                hasSaw = true,
                hasCrystalSaw = false,
                currentBuildable = current,
                itemCount = { materials[it] ?: 0 },
            )

        assertNull(problem(marble.name, null))
        assertEquals(ConstructionBuildProblem.Upgrade(marble.name), problem(gilded.name, null))
        assertNull(problem(gilded.name, marble.name))
        assertEquals(
            ConstructionBuildProblem.Upgrade(gilded.name),
            problem(crystalline.name, marble.name),
        )
        assertNull(problem(crystalline.name, gilded.name))

        listOf(33408, 33409).forEach { objectId ->
            val type = requireNotNull(ServerCacheManager.getObject(objectId))
            assertTrue(
                (0 until 5).any { op ->
                    type.actions.getOpOrNull(op).equals("Upgrade", ignoreCase = true) ||
                        type.actions.getOpOrNull(op).equals("Build", ignoreCase = true)
                },
                "Portal Nexus object $objectId has no Upgrade/Build option: ${type.actions}",
            )
        }
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun loadRevision240Cache() {
            val root =
                generateSequence(Path.of("").toAbsolutePath()) { it.parent }
                    .first { Files.exists(it.resolve(".data/gamevals-binary/gamevals.dat")) }
            System.setProperty("user.dir", root.toString())
            synchronized(ServerCacheManager) {
                if (ServerCacheManager.objectSize() == 0) {
                    GameValProvider.load("$root${java.io.File.separator}")
                    ServerCacheManager.init(root.resolve(".data/cache/SERVER"), 240)
                }
            }
        }
    }
}

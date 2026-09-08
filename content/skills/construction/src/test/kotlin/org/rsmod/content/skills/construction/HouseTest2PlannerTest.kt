package org.rsmod.content.skills.construction

import dev.openrune.ServerCacheManager
import dev.openrune.gamevals.GameValProvider
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.rsmod.content.skills.construction.data.DonorConstructionData

class HouseTest2PlannerTest {
    @Test
    fun `planner is deterministic and represents every room in all four rotations`() {
        val first = HouseTest2Planner.build()
        val second = HouseTest2Planner.build()
        println(
            "HOUSETEST2_COUNTS pages=${first.pages.size} rooms=${first.representedRooms}/${first.totalRooms} " +
                "rotations=${first.representedRotations}/${first.totalRotations} " +
                "buildables=${first.representedBuildables}/${first.totalBuildables} " +
                "components=${first.constructedComponents} inactive=${first.inactiveBuildables.size} unreachable=${first.unreachableBuildables.size}"
        )

        assertEquals(planSignature(first), planSignature(second))
        assertEquals(DonorConstructionData.rooms.map { it.name }.toSet(), first.roomDefinitions)
        assertEquals(
            DonorConstructionData.rooms
                .flatMap { room -> (0..3).map { rotation -> room.name to rotation } }
                .toSet(),
            first.roomRotations,
        )
    }

    @Test
    fun `every enabled furniture definition is assigned in every room rotation`() {
        val plan = HouseTest2Planner.build()
        val enabled = DonorConstructionData.hotspots.flatMap { it.buildables }.toSet()
        val entries = plan.entries.associateBy { it.buildable.name }

        assertEquals(enabled, entries.filterValues { it.placements.isNotEmpty() }.keys)
        enabled.forEach { name ->
            val entry = requireNotNull(entries[name])
            assertEquals(setOf(0, 1, 2, 3), entry.placements.map { it.rotation }.toSet(), name)
            assertTrue(entry.placements.all { it.builtLocIds.isNotEmpty() }, name)
        }
        assertTrue(plan.unreachableBuildables.isEmpty())
        assertEquals(
            DonorConstructionData.buildables.map { it.name }.toSet() - enabled,
            plan.inactiveBuildables,
        )
    }

    @Test
    fun `pages fit the dynamic house area and retain one entrance garden`() {
        val plan = HouseTest2Planner.build()

        assertFalse(plan.pages.isEmpty())
        plan.pages.forEach { page ->
            assertTrue(page.rooms.size <= 64, "page ${page.index}")
            assertEquals(
                1,
                page.rooms.count { it.placement.room == "GARDEN" && it.placement.level == 1 },
                "page ${page.index}",
            )
            assertEquals(
                page.rooms.size,
                page.rooms.map { it.placement.stableId }.distinct().size,
                "page ${page.index}: stable IDs",
            )
            assertEquals(page.rooms.size, page.blueprint.rooms.size)
            val roomPrefixes = page.blueprint.rooms.map { "${it.stableId}:" }
            assertTrue(
                page.blueprint.furniture.keys.all { key -> roomPrefixes.any(key::startsWith) },
                "page ${page.index}: orphan furniture",
            )
        }
    }

    @Test
    fun `room copies use the minimum maximum-hotspot option count`() {
        val plan = HouseTest2Planner.build()
        val hotspots = DonorConstructionData.hotspots.associateBy { it.name }

        DonorConstructionData.rooms.forEach { room ->
            val expected =
                maxOf(
                    1,
                    room.hotspots
                        .mapNotNull(hotspots::get)
                        .filter { it.name != "EMPTY" }
                        .maxOfOrNull { it.buildables.size } ?: 1,
                )
            (0..3).forEach { rotation ->
                val copies =
                    plan.pages
                        .flatMap { it.rooms }
                        .filter { it.room.name == room.name && it.rotation == rotation }
                assertEquals(expected, copies.size, "${room.name}/$rotation")
                assertTrue(copies.all { it.copiesAtRotation == expected })
            }
        }
    }

    @Test
    fun `report contains every registry definition exactly once`(@TempDir temp: Path) {
        val plan = HouseTest2Planner.build()
        val output = temp.resolve("housetest2-audit.txt")
        val summary = HouseTest2Report.write(plan, emptyMap(), { _, _ -> true }, output)
        val report = Files.readString(output)
        val auditEntries = Regex("(?m)^\\[(\\d+)] ").findAll(report).toList()

        assertEquals(DonorConstructionData.buildables.size, auditEntries.size)
        assertEquals(
            (1..DonorConstructionData.buildables.size).toList(),
            auditEntries.map { it.groupValues[1].toInt() },
        )
        assertEquals(0, summary.missingObjectIds)
        assertEquals(0, summary.missingHandlers)
        assertEquals(0, summary.placementFailures)
        assertTrue(report.contains("=== GROUPED FAILURES ==="))
    }

    private fun planSignature(plan: HouseTest2Plan): List<String> =
        plan.pages.flatMap { page ->
            page.rooms.map { room ->
                "${page.index}:${room.placement.stableId}:${room.rotation}:" +
                    room.furniture.entries.sortedBy { it.key }.joinToString()
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

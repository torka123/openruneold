package org.rsmod.content.areas.wilderness.interactions

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.rsmod.map.CoordGrid

class WildernessObeliskRegistryTest {
    @Test
    fun `six revision 240 obelisks have unique centers and twenty four unique pillars`() {
        val definitions = WildernessObeliskRegistry.definitions
        assertEquals(listOf(13, 19, 27, 35, 44, 50), definitions.map { it.wildernessLevel })
        assertEquals(6, definitions.map { it.center }.distinct().size)
        assertEquals(24, definitions.flatMap { it.pillars }.distinct().size)
        assertTrue(definitions.all { it.pillars.size == 4 && it.padTiles().size == 9 })
    }

    @Test
    fun `random destination indexes can never select the source obelisk`() {
        for (source in WildernessObeliskRegistry.definitions) {
            for (index in 0 until WildernessObeliskRegistry.definitions.size - 1) {
                assertNotEquals(source, WildernessObeliskRegistry.destination(source, index))
            }
        }
    }

    @Test
    fun `every selectable level resolves to its exact obelisk`() {
        for (definition in WildernessObeliskRegistry.definitions) {
            assertEquals(
                definition,
                WildernessObeliskRegistry.destination(definition.wildernessLevel),
            )
        }
        assertEquals(null, WildernessObeliskRegistry.destination(NO_OBELISK_DESTINATION))
        assertEquals(
            "wilderness_obelisk_destination_level",
            WILDERNESS_OBELISK_DESTINATION.persistenceKey,
        )
    }

    @Test
    fun `relative pad offsets are preserved as the first arrival candidate`() {
        val source = WildernessObeliskRegistry.definitions.first()
        val destination = WildernessObeliskRegistry.definitions.last()
        for (dx in -1..1) {
            for (dz in -1..1) {
                val player = source.center.translate(dx, dz)
                assertEquals(
                    destination.center.translate(dx, dz),
                    WildernessObeliskRegistry.arrivalCandidates(player, source, destination).first(),
                )
            }
        }
    }

    @Test
    fun `pad membership excludes nearby players and all pillars`() {
        val obelisk = WildernessObeliskRegistry.definitions.first()
        assertTrue(obelisk.contains(obelisk.center))
        assertTrue(obelisk.contains(obelisk.center.translate(1, 1)))
        assertTrue(obelisk.pillars.none(obelisk::contains))
        assertTrue(!obelisk.contains(CoordGrid(obelisk.center.x + 2, obelisk.center.z)))
    }

    @Test
    fun `activation timings are finite and visuals use the glowing cache loc`() {
        assertEquals(7, OBELISK_CHARGE_TICKS)
        assertEquals(10, OBELISK_COOLDOWN_TICKS)
        assertEquals("loc.wilderness_portal_stone_glowing", OBELISK_GLOWING)
    }
}

package org.rsmod.content.slayer.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.CanonicalSlayerCategoryMembership
import org.rsmod.map.CoordGrid

class CanonicalSlayerRegistryTest {
    @Test
    fun `every enabled task has complete explicit metadata`() {
        val categories = CanonicalSlayerRegistry.categories
        assertEquals(77, categories.size)
        assertEquals(categories.size, categories.map { it.id }.distinct().size)
        assertEquals(categories.size, categories.map { it.key }.distinct().size)
        for (category in categories) {
            assertTrue(category.npcIds.isNotEmpty(), category.key)
            assertTrue(category.locations.isNotEmpty(), category.key)
            assertTrue(category.slayerRequirement >= 1, category.key)
            assertTrue(category.minAmount in 1..category.maxAmount, category.key)
            assertTrue(category.weight > 0, category.key)
        }
    }

    @Test
    fun `required wilderness ecosystem roster is present`() {
        val keys = CanonicalSlayerRegistry.categories.map { it.key }.toSet()
        val required =
            setOf(
                "revenants", "callisto", "artio", "vetion", "calvarion", "venenatis",
                "spindel", "chaos_elemental", "chaos_fanatic", "crazy_archaeologist",
                "scorpia", "revenant_maledictus", "king_black_dragon", "corporeal_beast",
                "lava_dragons", "green_dragons", "ankous", "hydras", "demonic_gorillas",
                "dark_beasts", "ancient_wyverns", "brutal_black_dragons", "cave_horrors",
                "venators", "elder_custodian_stalkers", "mad_angel", "chronozon",
                "scurrius", "bryophyta", "obor", "skeletal_wyverns", "smoke_devils",
                "gargoyles",
            )
        assertTrue(keys.containsAll(required), required.subtract(keys).toString())
    }

    @Test
    fun `paired wilderness bosses never alias each other`() {
        assertSeparate(59, 60)
        assertSeparate(61, 62)
        assertSeparate(63, 64)
    }

    @Test
    fun `revenant and superior aliases are directed`() {
        assertTrue(CanonicalSlayerCategoryMembership.matches(41, 11246))
        assertTrue(CanonicalSlayerCategoryMembership.matches(69, 11246))
        assertFalse(CanonicalSlayerCategoryMembership.matches(69, 7881))
        assertTrue(CanonicalSlayerCategoryMembership.matches(52, 15770))
        assertNull(CanonicalSlayerRegistry.categories.find { it.npcIds.contentEquals(intArrayOf(15770)) })
    }

    @Test
    fun `only Krystilia is an enabled regular master`() {
        assertTrue(CanonicalSlayerRegistry.isEnabledMaster(7))
        for (master in listOf(1, 2, 3, 4, 5, 6, 8, 9, 10)) {
            assertFalse(CanonicalSlayerRegistry.isEnabledMaster(master))
        }
    }

    @Test
    fun `future safe boss service is disabled and empty`() {
        val service = CanonicalSlayerRegistry.FutureBossTaskService
        assertFalse(service.enabled)
        assertTrue(service.bosses.isEmpty())
        assertEquals(3, service.minimumKills)
        assertEquals(5, service.maximumKills)
    }

    @Test
    fun `three slot dungeon pools contain only real dungeon destinations`() {
        val edge = CanonicalSlayerRegistry.Location.EdgevilleDungeon
        val cave = CanonicalSlayerRegistry.Location.CustomSlayerCave
        assertTrue(edge in CanonicalSlayerRegistry.get(27)!!.locations)
        assertTrue(edge in CanonicalSlayerRegistry.get(28)!!.locations)
        assertTrue(edge in CanonicalSlayerRegistry.get(51)!!.locations)
        assertTrue(cave in CanonicalSlayerRegistry.get(32)!!.locations)
        assertFalse(edge in CanonicalSlayerRegistry.get(32)!!.locations)
        assertTrue(cave in CanonicalSlayerRegistry.get(46)!!.locations)
        assertFalse(cave in CanonicalSlayerRegistry.get(25)!!.locations)
        assertFalse(CanonicalSlayerRegistry.Location.Wilderness in CanonicalSlayerRegistry.get(24)!!.locations)
    }

    @Test
    fun `replacement and public boss rooms use exact bounds`() {
        for (categoryId in (46..58)) {
            assertTrue(CanonicalSlayerRegistry.authorizedRooms(categoryId).isNotEmpty(), "category=$categoryId")
        }
        assertTrue(CanonicalSlayerRegistry.authorizedRooms(57).single().contains(CoordGrid(3156, 9901, 0)))
        assertTrue(CanonicalSlayerRegistry.authorizedRooms(58).single().contains(CoordGrid(3111, 9841, 0)))
        assertTrue(CanonicalSlayerRegistry.authorizedRooms(58).single().contains(CoordGrid(3117, 9850, 0)))
        assertFalse(CanonicalSlayerRegistry.authorizedRooms(58).single().contains(CoordGrid(3126, 9861, 0)))
    }

    @Test
    fun `wilderness cave replacements use attackable task variants and revised rooms`() {
        assertTrue(CanonicalSlayerCategoryMembership.matches(77, 6608))
        assertTrue(CanonicalSlayerCategoryMembership.matches(48, 7250))
        assertTrue(CanonicalSlayerCategoryMembership.matches(32, 412))
        assertTrue(CanonicalSlayerCategoryMembership.matches(50, 7275))
        assertFalse(CanonicalSlayerCategoryMembership.matches(50, 8093))
        assertTrue(CanonicalSlayerCategoryMembership.matches(27, 423))
        for (npcId in 437..442) {
            assertTrue(CanonicalSlayerCategoryMembership.matches(28, npcId), "jelly=$npcId")
        }
        for (npcId in 1047..1051) {
            assertTrue(CanonicalSlayerCategoryMembership.matches(51, npcId), "cave-horror=$npcId")
        }

        assertTrue(CanonicalSlayerRegistry.authorizedRooms(27).single().contains(CoordGrid(3121, 9893, 0)))
        assertTrue(CanonicalSlayerRegistry.authorizedRooms(28).single().contains(CoordGrid(3095, 9907, 0)))
        assertTrue(CanonicalSlayerRegistry.authorizedRooms(51).single().contains(CoordGrid(3163, 9877, 0)))

        val darkBeastRoom = CanonicalSlayerRegistry.authorizedRooms(48).single()
        for (coords in listOf(
            CoordGrid(3426, 10102, 0),
            CoordGrid(3433, 10097, 0),
            CoordGrid(3424, 10108, 0),
            CoordGrid(3439, 10107, 0),
        )) {
            assertTrue(darkBeastRoom.contains(coords), coords.toString())
        }
        val gargoyleRooms = CanonicalSlayerRegistry.authorizedRooms(32)
        assertTrue(gargoyleRooms.any { it.contains(CoordGrid(3396, 10119, 0)) })
        assertTrue(gargoyleRooms.any { it.contains(CoordGrid(3402, 10119, 0)) })
        assertTrue(gargoyleRooms.any { it.contains(CoordGrid(3397, 10124, 0)) })

        assertTrue(CanonicalSlayerRegistry.isAssignmentEnabled(28))
        assertTrue(CanonicalSlayerRegistry.isAssignmentEnabled(51))
        assertFalse(CanonicalSlayerRegistry.Location.EdgevilleDungeon in CanonicalSlayerRegistry.get(34)!!.locations)
        assertFalse(CanonicalSlayerRegistry.Location.EdgevilleDungeon in CanonicalSlayerRegistry.get(42)!!.locations)
    }

    @Test
    fun `legacy migration accepts only unambiguous task rows`() {
        assertNotNull(CanonicalSlayerRegistry.migrateLegacyTask(24))
        assertNull(CanonicalSlayerRegistry.migrateLegacyTask(27))
        assertNull(CanonicalSlayerRegistry.migrateLegacyTask(98))
        assertNull(CanonicalSlayerRegistry.migrateLegacyTask(999_999))
    }

    private fun assertSeparate(leftCategory: Int, rightCategory: Int) {
        val left = CanonicalSlayerRegistry.get(leftCategory)!!.npcIds.toSet()
        val right = CanonicalSlayerRegistry.get(rightCategory)!!.npcIds.toSet()
        assertTrue(left.intersect(right).isEmpty())
    }
}

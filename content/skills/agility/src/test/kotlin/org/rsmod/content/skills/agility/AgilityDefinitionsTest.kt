package org.rsmod.content.skills.agility

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.rsmod.map.CoordGrid

class AgilityDefinitionsTest {
    @Test
    fun `all configured courses have continuous ordered stages`() {
        for (course in AgilityDefinitions.courses) {
            val stages = course.obstacles.map { it.stage }.distinct().sorted()
            assertEquals((1..course.finalStage).toList(), stages, course.key)
            assertEquals(
                1,
                course.obstacles.filter { it.finalObstacle }.map { it.stage }.distinct().size,
                course.key,
            )
        }
    }

    @Test
    fun `current OSRS full lap base XP totals are retained`() {
        val expected =
            mapOf(
                "gnome" to 110.5,
                "barbarian" to 153.3,
                "draynor" to 120.0,
                "al_kharid" to 216.0,
                "varrock" to 269.7,
                "canifis" to 240.0,
                "falador" to 586.0,
                "seers" to 570.0,
                "pollnivneach" to 890.0,
                "rellekka" to 780.0,
                "ardougne" to 889.0,
                "prifddinas" to 1285.2,
            )
        assertEquals(expected.keys, AgilityDefinitions.courses.map { it.key }.toSet())
        for ((key, xp) in expected) {
            val course = AgilityDefinitions.courses.single { it.key == key }
            assertEquals(xp, course.fullLapBaseXp, 0.001, key)
        }
    }

    @Test
    fun `coordinate-specific Barbarian walls resolve independently`() {
        assertEquals(6, AgilityDefinitions.resolve(1948, CoordGrid(2536, 3553))?.second?.stage)
        assertEquals(7, AgilityDefinitions.resolve(1948, CoordGrid(2539, 3553))?.second?.stage)
        assertEquals(8, AgilityDefinitions.resolve(1948, CoordGrid(2542, 3553))?.second?.stage)
    }

    @Test
    fun `every live obstacle resolves through its object id`() {
        for (course in AgilityDefinitions.courses) {
            for (obstacle in course.obstacles) {
                val coords = obstacle.coordinate ?: obstacle.movement.destination ?: CoordGrid(0, 0)
                val resolved = AgilityDefinitions.resolve(obstacle.objectId, coords)
                assertNotNull(resolved, "${course.key}:${obstacle.objectId}")
            }
        }
    }

    @Test
    fun `gnome balance and pipe phases use the verified revision sequences`() {
        val gnome = AgilityDefinitions.courses.single { it.key == "gnome" }
        assertEquals(763, gnome.obstacles.single { it.stage == 1 }.movement.animation)
        val pipes = gnome.obstacles.filter { it.stage == 7 }
        assertTrue(pipes.isNotEmpty())
        for (pipe in pipes) {
            assertEquals(749, pipe.movement.animation)
            assertEquals(749, pipe.movement.travelAnimation)
            assertEquals(749, pipe.movement.landingAnimation)
        }
    }

    @Test
    fun `rooftop and regular mark rates are valid`() {
        for (course in AgilityDefinitions.courses) {
            assertTrue(course.markNumerator > 0, course.key)
            assertTrue(course.markDenominator >= course.markNumerator, course.key)
            assertTrue(course.markSpawns.isNotEmpty(), course.key)
        }
    }
}

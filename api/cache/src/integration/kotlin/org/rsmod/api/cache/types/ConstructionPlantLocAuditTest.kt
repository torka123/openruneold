package org.rsmod.api.cache.types

import org.junit.jupiter.api.Test
import org.rsmod.api.testing.GameTestState

class ConstructionPlantLocAuditTest {
    @Test
    fun GameTestState.`dump construction plant locs`() = runBasicGameTest {
        val ids = 5129..5139
        ids.forEach { id ->
            val type = cacheTypes.locs.values.firstOrNull { it.id == id }
            println("CONSTRUCTION_PLANT_LOC $id = $type")
        }
        listOf(15364, 15365, 15366, 15367).forEach { id ->
            val type = cacheTypes.locs.values.firstOrNull { it.id == id }
            println("CONSTRUCTION_HOTSPOT_LOC $id = $type")
        }
    }

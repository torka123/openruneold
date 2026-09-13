package org.rsmod.content.bosses.nex

import dev.openrune.ServerCacheManager
import kotlin.test.*
import net.rsprot.protocol.game.outgoing.zone.payload.SoundArea
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.framework
import org.rsmod.annotations.InternalApi
import org.rsmod.api.config.refs.params
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey

/**
 * M11 cast presentation: retained3284 t406 L9773-80 identifies Fumus183; retained808 t216 L6376-86,
 * t256 L7617-38 and t349 L9837-56 identify Umbra178, Cruor106 and Glacies171. Each SoundArea has
 * loops1/range12 at the casting guard's tile, separate from delayed target synths. Native
 * WorldRepository/ZoneUpdateMap transformation and registered combat run here. The existing
 * five-cycle attack gate is characterization; these samples do not certify normal timing, damage or
 * impact sounds. Intermediate phase handoffs use the shared fixture's setup helper.
 */
@OptIn(InternalApi::class)
class NexMinionCastSoundIntegrationTest {
    @Test
    fun `each guard emits its captured area sound only for accepted accurate and missed casts`() {
        val captures =
            listOf(
                CastSound(11283, 183, CoordGrid(2913, 5215)),
                CastSound(11284, 178, CoordGrid(2937, 5215)),
                CastSound(11285, 106, CoordGrid(2937, 5191)),
                CastSound(11286, 171, CoordGrid(2913, 5191)),
            )
        for (capture in captures) {
            val f = NexMinionTestWorld(nativeWorld = true)
            val guard = f.npc(capture.npcId)
            assertEquals(capture.source, guard.coords)
            assertNull(
                guard.visType.paramOrNull(params.attack_sound),
                "The loaded guard must not also emit NvPCombat's target-only fallback synth",
            )
            f.worldUpdates.clear()
            f.attack(guard)
            assertTrue(sounds(f).isEmpty(), "An inactive guard must not emit a cast sound")
            assertTrue(f.hits().isEmpty())
            assertEquals(0, f.accuracyRolls)

            f.reachGuard(capture.npcId)
            f.worldUpdates.clear()
            val start = f.clock.cycle
            f.attack(guard)
            assertSound(capture, sounds(f).single())
            assertTrue(f.hits().single().second.damage > 0, "The first cast is accurate")
            assertEquals(1, f.accuracyRolls)

            f.attack(guard)
            f.tick(4)
            f.attack(guard)
            assertSound(capture, sounds(f).single())
            assertEquals(1, f.hits().size)
            assertEquals(1, f.accuracyRolls, "Cooldown rejection must not reach a new attack")

            f.tick()
            assertEquals(start + 5, f.clock.cycle)
            f.accurate = false
            f.attack(guard)
            assertEquals(2, sounds(f).size, "The next accepted cast adds exactly one sound")
            sounds(f).forEach { assertSound(capture, it) }
            assertEquals(2, f.hits().size)
            assertEquals(0, f.hits().last().second.damage, "A missed cast still has its cast sound")
            assertEquals(2, f.accuracyRolls)

            f.tick(5)
            f.player.coords = NEX_BANK_TELEPORT
            f.attack(guard)
            assertEquals(2, sounds(f).size, "An outside target must not cause a cast sound")
            assertEquals(2, f.hits().size)
            assertEquals(2, f.accuracyRolls)
            framework().clearInlineMocks()
        }
    }

    @Test
    fun `accepted Reaver attacks do not inherit a bodyguard area sound`() {
        val f = NexMinionTestWorld(nativeWorld = true)
        val reaver = f.summonReaver()
        f.worldUpdates.clear()
        val beforeRolls = f.accuracyRolls

        f.attack(reaver)

        assertEquals(1, f.hits().size, "The native Reaver attack must actually be accepted")
        assertEquals(beforeRolls + 1, f.accuracyRolls)
        assertTrue(sounds(f).isEmpty())
        f.tick(5)
        f.attack(reaver)
        assertEquals(2, f.hits().size)
        assertEquals(beforeRolls + 2, f.accuracyRolls)
        assertTrue(sounds(f).isEmpty(), "The sound selection remains absent on later Reaver casts")
    }

    private fun assertSound(capture: CastSound, actual: Pair<Int, SoundArea>) {
        val (zone, sound) = actual
        assertEquals(ZoneKey.from(capture.source).packed, zone)
        assertEquals(capture.source.x and 7, sound.xInZone)
        assertEquals(capture.source.z and 7, sound.zInZone)
        assertEquals(capture.synthId, sound.id)
        assertEquals(0, sound.delay)
        assertEquals(1, sound.loops)
        assertEquals(12, sound.range)
        assertEquals(0, sound.dropOffRange)
    }

    private fun sounds(f: NexMinionTestWorld): List<Pair<Int, SoundArea>> =
        f.worldUpdates.updatedZones.int2ObjectEntrySet().flatMap { entry ->
            entry.value.filterIsInstance<SoundArea>().map { entry.intKey to it }
        }

    private data class CastSound(val npcId: Int, val synthId: Int, val source: CoordGrid)

    @AfterEach fun releaseMocks() = framework().clearInlineMocks()

    companion object {
        @BeforeAll
        @JvmStatic
        fun cache() {
            if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
        }
    }
}

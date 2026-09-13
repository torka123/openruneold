package org.rsmod.content.bosses.nex

import dev.openrune.ServerCacheManager
import kotlin.test.*
import net.rsprot.protocol.game.outgoing.zone.payload.SoundArea
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.framework
import org.rsmod.annotations.InternalApi
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.npc.NpcInfoProtocol
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.entity.util.EntityFaceAngle
import org.rsmod.game.entity.util.EntityFaceTarget
import org.rsmod.game.map.Direction
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey

/**
 * M06 presentation: retained3284 t353 L8654-58 and t587 L14103-07, corroborated by808 t110
 * L3554-58/t449 L11895-99, show spawning11279, west/512, sequence9182, AT LAST and sound5303
 * (delay0, loops1, radius15, size0) centered at2925,5203 in the same frame. These Grid Master
 * captures do not certify normal opening duration or staged guard timings.
 *
 * The shared fixture runs native NPC post-tick and WorldRepository/ZoneUpdateMap. A delegating
 * NpcInfoProtocol observes the existing emission boundary; NetworkScript and encoded add-header
 * bytes are not run here. Native Create observations prove the direction is configured before a
 * production avatar would be allocated. Existing five-cycle readiness and eager guard staging
 * remain characterization. Intermediate guard handoffs use the established fixture setup helper.
 */
@OptIn(InternalApi::class)
class NexOpeningPresentationIntegrationTest {
    @Test
    fun `initial opening emits exact facing and centered sound once without changing readiness`() {
        val f = NexMinionTestWorld(duo = true, enterRoom = false, nativeWorld = true)
        val boss = f.npc(11278)
        val guards = guardSnapshot(f)
        val output = record(f, boss)
        assertEquals(Direction.West, boss.respawnDir)
        assertTrue(openingSounds(f).isEmpty(), "An idle roster must not announce an opening")

        boss.facePlayer(f.player)
        boss.faceSquare(NEX_SPAWN.translate(4, 4))
        assertTrue(f.enter())
        val openingCycle = f.clock.cycle
        assertOpening(f, boss, output, openingCycle, expectedSounds = 1)
        assertGuardsUnchanged(f, guards)

        val partner = assertNotNull(f.partner)
        f.players[partner.slotId] = partner
        assertTrue(f.enter(partner))
        assertOpening(f, boss, output, openingCycle, expectedSounds = 1)

        f.tick()
        assertEquals(listOf(Facing(openingCycle, 512, false)), output.facings)
        assertEquals(EntityFaceAngle.NULL, boss.pendingFaceAngle)
        f.tick(3)
        assertEquals("SPAWNING", f.encounters.of(boss).currentPhaseName)
        assertTrue(boss.movementLocked)
        assertGuardsUnchanged(f, guards)

        f.tick()
        assertEquals(openingCycle + 5, f.clock.cycle)
        assertEquals("SMOKE", f.encounters.of(boss).currentPhaseName)
        assertEquals(11278, boss.visType.id)
        assertFalse(boss.movementLocked)
        assertGuardsUnchanged(f, guards)
        f.tick(3)
        assertEquals(listOf(Facing(openingCycle, 512, false)), output.facings)
        assertEquals(
            1,
            openingSounds(f).size,
            "Readiness and later cycles must not replay the sound",
        )
    }

    @Test
    fun `native completed round prepares west at Create and presents one fresh opening`() {
        val f = NexMinionTestWorld(enterRoom = false, nativeWorld = true, nativeDeaths = true)
        val old = f.npc(11278)
        val session = f.session()
        assertTrue(f.enter())
        f.tick(5)
        f.player.coords = NEX_SPAWN.translateX(-10)
        val retainedTile = f.player.coords
        val initialGuards = guardSnapshot(f)
        val creations = mutableListOf<Npc>()
        var replacementOutput: OpeningOutput? = null
        f.events.subscribeUnbound(NpcStateEvents.Create::class.java) {
            if (npc.type.id == 11278 || npc.type.id in 11283..11286) {
                creations += npc
                assertEquals(session.id, f.instances.instanceForNpc(npc))
                if (npc.type.id == 11278) {
                    assertEquals(Direction.West, npc.respawnDir, "Native Create sees prepared west")
                    assertEquals(NEX_SPAWN, npc.coords)
                    replacementOutput = record(f, npc)
                } else {
                    assertEquals(
                        when (npc.type.id) {
                            11283 -> Direction.SouthEast
                            11284 -> Direction.SouthWest
                            11285 -> Direction.NorthWest
                            11286 -> Direction.NorthEast
                            else -> error("Unexpected bodyguard: ${npc.type.id}")
                        },
                        npc.respawnDir,
                        "Native Create sees the captured inward guard direction",
                    )
                }
            }
        }

        f.reachPhase(NexState.ZAROS)
        val oldEncounter = f.encounters.of(old)
        old.hitpoints = 1
        f.damage(old, 1)
        var deathTicks = 0
        while (oldEncounter.currentPhaseName != "RESETTING" && deathTicks++ < 40) f.tick()
        assertEquals("RESETTING", oldEncounter.currentPhaseName, "Native death must finish")
        assertTrue(old.hidden)

        // The existing fixture executes the completion callback before advancing MapClock.
        val openingCycle = f.clock.cycle + NEX_RESPAWN_TICKS - 1
        f.tick(NEX_RESPAWN_TICKS - 2)
        assertTrue(old.isSlotAssigned)
        assertTrue(creations.isEmpty())
        assertEquals(1, openingSounds(f).size)
        f.tick()

        val boss = f.npc(11278)
        val output = assertNotNull(replacementOutput)
        assertEquals(openingCycle, f.clock.cycle)
        assertNotSame(old, boss)
        assertFalse(old.isSlotAssigned)
        assertTrue(oldEncounter.isEnded)
        assertSame(session, f.session())
        assertEquals(retainedTile, f.player.coords)
        assertEquals(setOf(11278, 11283, 11284, 11285, 11286), creations.map { it.type.id }.toSet())
        assertEquals(5, creations.size, "Existing round publication creates the complete roster")
        val freshGuards = guardSnapshot(f)
        assertEquals(initialGuards.map { it.second }, freshGuards.map { it.second })
        assertTrue(initialGuards.all { (npc, _) -> !npc.isSlotAssigned })
        assertOpening(f, boss, output, openingCycle, expectedSounds = 2)

        f.tick(4)
        assertEquals(listOf(Facing(openingCycle, 512, false)), output.facings)
        assertEquals(EntityFaceAngle.NULL, boss.pendingFaceAngle)
        assertEquals("SPAWNING", f.encounters.of(boss).currentPhaseName)
        assertGuardsUnchanged(f, freshGuards)
        f.tick()
        assertEquals("SMOKE", f.encounters.of(boss).currentPhaseName)
        assertFalse(boss.movementLocked)
        assertGuardsUnchanged(f, freshGuards)
        f.tick(3)
        assertEquals(listOf(Facing(openingCycle, 512, false)), output.facings)
        assertEquals(2, openingSounds(f).size, "A completed round adds exactly one opening sound")
    }

    private fun assertOpening(
        f: NexMinionTestWorld,
        boss: Npc,
        output: OpeningOutput,
        cycle: Int,
        expectedSounds: Int,
    ) {
        assertEquals("SPAWNING", f.encounters.of(boss).currentPhaseName)
        assertEquals(11279, boss.visType.id)
        assertEquals(NEX_SPAWN, boss.coords)
        assertTrue(boss.movementLocked)
        assertEquals(512, boss.pendingFaceAngle.intValue)
        assertEquals(EntityFaceTarget.NULL, boss.faceEntity)
        assertEquals(CoordGrid.NULL, boss.pendingFaceSquare)
        assertEquals(listOf(cycle to 9182), output.sequences)
        assertEquals(listOf(cycle to "AT LAST!"), output.say)
        val sounds = openingSounds(f)
        assertEquals(expectedSounds, sounds.size)
        val (zone, sound) = sounds.last()
        val center = CoordGrid(2925, 5203, 0)
        assertEquals(ZoneKey.from(center).packed, zone)
        assertEquals(center.x and 7, sound.xInZone)
        assertEquals(center.z and 7, sound.zInZone)
        assertEquals(5303, sound.id)
        assertEquals(0, sound.delay)
        assertEquals(1, sound.loops)
        assertEquals(15, sound.range)
        assertEquals(0, sound.dropOffRange)
    }

    private fun guardSnapshot(f: NexMinionTestWorld): List<Pair<Npc, CoordGrid>> =
        (11283..11286).map { f.npc(it).let { npc -> npc to npc.coords } }

    private fun assertGuardsUnchanged(f: NexMinionTestWorld, guards: List<Pair<Npc, CoordGrid>>) {
        assertEquals(5, f.npcs.count())
        for ((guard, coords) in guards) {
            assertSame(guard, f.npc(guard.type.id))
            assertEquals(coords, guard.coords)
        }
    }

    private fun openingSounds(f: NexMinionTestWorld): List<Pair<Int, SoundArea>> =
        f.worldUpdates.updatedZones.int2ObjectEntrySet().flatMap { entry ->
            entry.value
                .filterIsInstance<SoundArea>()
                .filter { it.id == 5303 }
                .map { entry.intKey to it }
        }

    private fun record(f: NexMinionTestWorld, npc: Npc): OpeningOutput {
        val output = OpeningOutput()
        npc.infoProtocol =
            object : NpcInfoProtocol by npc.infoProtocol {
                override fun setFaceAngle(angle: Int, instant: Boolean) {
                    output.facings += Facing(f.clock.cycle, angle, instant)
                }

                override fun setSequence(seq: Int, delay: Int) {
                    output.sequences += f.clock.cycle to seq
                }

                override fun setSay(text: String) {
                    output.say += f.clock.cycle to text
                }
            }
        return output
    }

    private class OpeningOutput {
        val facings = mutableListOf<Facing>()
        val sequences = mutableListOf<Pair<Int, Int>>()
        val say = mutableListOf<Pair<Int, String>>()
    }

    private data class Facing(val cycle: Int, val angle: Int, val instant: Boolean)

    @AfterEach fun releaseMocks() = framework().clearInlineMocks()

    companion object {
        @BeforeAll
        @JvmStatic
        fun cache() {
            if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
        }
    }
}

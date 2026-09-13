package org.rsmod.content.bosses.nex

import dev.openrune.ServerCacheManager
import kotlin.test.*
import net.rsprot.protocol.game.outgoing.zone.payload.SoundArea
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.framework
import org.rsmod.annotations.InternalApi
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.PlayerTint
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.map.zone.ZoneKey

/**
 * M18 presentation mapping in docs/bosses/nex/NEX_OSRS_AUDIT.md: capture3284 t477 L11951-73 and
 * capture808 L7476-93 show target tint 0/210/0/6/28/112 and centered area sound5223. The 210 is a
 * client-cycle field, independent of the inherited eight-cycle callback and following hit cycle.
 * Real registered specials and native WorldRepository/ZoneUpdateMap payloads run here. Clearing
 * pendingTint explicitly models an already consumed outgoing update; shared PlayerPostTick tests
 * own actual synchronization/consumption coverage. No new damage, distance or timing certification.
 */
@OptIn(InternalApi::class)
class NexSacrificePresentationIntegrationTest {
    @Test
    fun `registered sacrifice marks only its target and emits the exact centered native sound`() {
        val f = world()
        val boss = f.npc(11278)
        val target = assertNotNull(f.partner)
        boss.coords = NEX_SPAWN.translate(3, -2)
        f.player.coords = boss.coords.translateX(-1)
        target.coords = boss.coords.translateX(-2)
        val encounter = f.encounters.of(boss)
        assertTrue(encounter.forceNextAbility("BLOOD.special"))

        f.attack(boss, target)

        assertEquals("BLOOD.special", encounter.lastAbilityName)
        assertEquals(MARK, target.pendingTint)
        assertNull(f.player.pendingTint)
        val (zone, sound) = sounds(f).single()
        val center = boss.coords.translate(boss.size / 2, boss.size / 2)
        assertEquals(ZoneKey.from(center).packed, zone)
        assertEquals(center.x and 7, sound.xInZone)
        assertEquals(center.z and 7, sound.zInZone)
        assertEquals(5223, sound.id)
        assertEquals(0, sound.delay)
        assertEquals(1, sound.loops)
        assertEquals(15, sound.range)
        assertEquals(0, sound.dropOffRange)
        consumeMark(target)

        f.clock.tick() // Finish the issuing cycle in the existing calibrated fixture.
        repeat(7) {
            f.tick()
            assertNull(target.pendingTint)
            assertTrue(f.hits(target).isEmpty())
        }
        f.tick()
        assertEquals(PlayerTint.CLEAR, target.pendingTint)
        assertEquals(1, f.hits(target).single().first)
        assertNull(f.player.pendingTint)
        assertEquals(1, sounds(f).size, "Settlement must not replay the mark sound")
    }

    @Test
    fun `old cast settlement cannot clear a newer same target or other target mark`() {
        for (sameTarget in listOf(true, false)) {
            val f = world()
            val partner = assertNotNull(f.partner)
            f.registeredSpecial()
            consumeMark(f.player)
            f.clock.tick()
            f.tick(3)
            val nextTarget = if (sameTarget) f.player else partner
            recast(f, nextTarget)
            consumeMark(nextTarget)

            f.tick(5) // First cast settles; the replacement still has three callbacks remaining.

            assertNull(nextTarget.pendingTint, "Old settlement cleared the latest mark")
            if (!sameTarget) assertEquals(PlayerTint.CLEAR, f.player.pendingTint)
            f.tick(3)
            assertEquals(PlayerTint.CLEAR, nextTarget.pendingTint)
            assertEquals(2, sounds(f).size)
            framework().clearInlineMocks()
        }
    }

    @Test
    fun `bank logout disconnect and death cleanup clear only the departing players mark`() {
        for (departure in listOf("bank", "logout", "disconnect", "death")) {
            val f = world()
            val partner = assertNotNull(f.partner)
            f.registeredSpecial()
            recast(f, partner)
            consumeMark(f.player)
            consumeMark(partner)

            when (departure) {
                "bank" -> assertTrue(f.travelBank())
                "logout" -> f.events.publish(SessionStateEvent.Logout(f.player))
                "disconnect" -> {
                    f.player.clientDisconnected.set(true)
                    f.tick()
                }
                else -> NexDeathCleanup(f.manager).cleanup(f.player)
            }

            assertEquals(PlayerTint.CLEAR, f.player.pendingTint, departure)
            assertNull(partner.pendingTint, "Departure cleared the remaining participant's mark")
            assertFalse(f.manager.isParticipant(f.player))
            assertTrue(f.manager.isParticipant(partner))
            f.player.pendingTint = null
            f.tick(12)
            assertNull(f.player.pendingTint, "Old callback emitted another departure clear")
            assertEquals(PlayerTint.CLEAR, partner.pendingTint)
            framework().clearInlineMocks()
        }
    }

    @Test
    fun `source encounter and session retirement clear marks immediately without later ownership`() {
        for (retirement in listOf("source", "encounter", "session")) {
            val f = world()
            val boss = f.npc(11278)
            val encounter = f.encounters.of(boss)
            f.registeredSpecial()
            consumeMark(f.player)

            when (retirement) {
                "source" -> f.repository.del(boss, Int.MAX_VALUE)
                "encounter" -> f.encounters.remove(boss)
                else -> assertTrue(f.instances.terminate(f.session()))
            }

            assertTrue(encounter.isEnded, retirement)
            assertEquals(PlayerTint.CLEAR, f.player.pendingTint, retirement)
            val later = f.player.tintOwned(MARK.copy())
            consumeMark(f.player)
            f.tick(12)
            assertNull(f.player.pendingTint, "Retired room cleared a subsequent producer")
            assertTrue(f.player.clearTint(later), "Later producer lost its native ownership")
            framework().clearInlineMocks()
        }
    }

    @Test
    fun `raw replacement with the same uid cannot inherit or lose the original mark`() {
        val f = world()
        f.registeredSpecial()
        consumeMark(f.player)
        val replacement = replacement(f.player, f)
        assertEquals(f.player.uid, replacement.uid)
        val replacementMark = replacement.tintOwned(MARK.copy())
        consumeMark(replacement)
        f.players[f.player.slotId] = replacement

        f.tick()

        assertEquals(PlayerTint.CLEAR, f.player.pendingTint)
        assertNull(replacement.pendingTint)
        assertFalse(f.manager.isParticipant(replacement))
        f.player.pendingTint = null
        f.tick(12)
        assertNull(replacement.pendingTint)
        assertNull(f.player.pendingTint)
        assertTrue(replacement.clearTint(replacementMark))
    }

    @Test
    fun `later equal valued owned legacy and raw tints survive stale sacrifice cleanup after sync`() {
        for (producer in listOf("owned", "legacy", "raw")) {
            for (retireSource in listOf(false, true)) {
                val f = world()
                f.registeredSpecial()
                consumeMark(f.player)
                val later = if (producer == "owned") f.player.tintOwned(MARK.copy()) else null
                if (producer == "legacy") f.player.tint(MARK.copy())
                if (producer == "raw") f.player.pendingTint = MARK.copy()
                consumeMark(f.player)

                if (retireSource) f.repository.del(f.npc(11278), Int.MAX_VALUE)
                f.tick(12)

                assertNull(f.player.pendingTint, "$producer was cleared after consumption")
                if (later != null) assertTrue(f.player.clearTint(later))
                framework().clearInlineMocks()
            }
        }
    }

    private fun world() =
        NexMinionTestWorld(duo = true, nativeWorld = true).apply {
            reachPhase(NexState.BLOOD)
            registeredSpecial() // Existing alternation begins with siphon.
            tick(12)
            player.coords = NEX_SPAWN.translateX(-1)
            assertNotNull(partner).coords = NEX_SPAWN.translateX(-2)
            player.queueList.clear()
            partner?.queueList?.clear()
            worldUpdates.clear()
            assertNull(player.pendingTint)
            assertNull(partner?.pendingTint)
        }

    /** Exercise the next existing alternation through the registered extension for race setup. */
    private fun recast(f: NexMinionTestWorld, target: Player) {
        f.registeredSpecial(target) // Intervening siphon; cadence is not being recertified.
        f.registeredSpecial(target)
        assertEquals(MARK, target.pendingTint)
    }

    private fun consumeMark(player: Player) {
        assertEquals(MARK, player.pendingTint)
        player.pendingTint = null
    }

    private fun sounds(f: NexMinionTestWorld): List<Pair<Int, SoundArea>> =
        f.worldUpdates.updatedZones.int2ObjectEntrySet().flatMap { entry ->
            entry.value.filterIsInstance<SoundArea>().map { entry.intKey to it }
        }

    private fun replacement(original: Player, f: NexMinionTestWorld) =
        Player().apply {
            slotId = original.slotId
            uuid = original.uuid
            observerUUID = 999L
            assignUid()
            coords = original.coords
            currentMapClock = f.clock.cycle
            processedMapClock = f.clock.cycle
            for (stat in listOf("hitpoints", "prayer")) {
                statMap.setBaseLevel("stat.$stat", 99)
                statMap.setCurrentLevel("stat.$stat", 99)
            }
            inv = invMap.getOrPut("inv.inv")
            worn = invMap.getOrPut("inv.worn")
        }

    @AfterEach fun releaseMocks() = framework().clearInlineMocks()

    companion object {
        private val MARK = PlayerTint(0, 210, 0, 6, 28, 112)

        @BeforeAll
        @JvmStatic
        fun cache() {
            if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
        }
    }
}

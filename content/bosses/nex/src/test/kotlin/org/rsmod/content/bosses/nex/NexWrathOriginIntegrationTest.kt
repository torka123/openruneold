package org.rsmod.content.bosses.nex

import dev.openrune.ServerCacheManager
import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.framework
import org.rsmod.annotations.InternalApi
import org.rsmod.api.player.events.PlayerHitpointsChangedEvent
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.map.CoordGrid

/**
 * M24 coordinate-only mapping: docs/bosses/nex/NEX_OSRS_AUDIT.md. Retained
 * logs/nex-audit-20260909/captures/rsprox-808.txt L19223 records death anchor (2920,5191),
 * L19257-72 records effects centered at (2921,5192), and L19309-13 records a player at (2918,5191)
 * taking 41 damage before the same corpse disappears. Capture3284 L13922 and L13960-80
 * independently show death-relative effects. These Grid Master observations do not establish normal
 * damage, timing, the complete radius or the damage center's relation to FX. Radius5, damage0..55,
 * emission-time selection and the existing delayed callback(+3)/hit(+1) remain characterization.
 * Native final death and player impact run; intermediate guard handoffs use fixture setup. Existing
 * NexSpecialLifecycleTest and NexRoundHandoffIntegrationTest retain source-deletion/retired-round
 * cancellation coverage. This file does not assert reward tile policy.
 */
@OptIn(InternalApi::class)
class NexWrathOriginIntegrationTest {
    @Test
    fun `native off-spawn death hits the nearby occupant through its original hidden source`() {
        val f = world()
        val partner = assertNotNull(f.partner)
        val impacts = mutableListOf<PlayerHitpointsChangedEvent>()
        f.events.subscribeUnbound(PlayerHitpointsChangedEvent::class.java) { impacts += this }
        val boss = beginDeath(f)
        val uid = boss.uid
        emitWrath(f, boss)

        val queued = f.hits().single()
        assertEquals(1, queued.first, "Preserve the characterized one-cycle native hit delay")
        assertEquals(HitType.Typeless, queued.second.type)
        assertEquals(55, queued.second.damage, "Existing maximum RNG fixture, not the captured 41")
        assertTrue(
            f.hits(partner).isEmpty(),
            "The old fixed spawn must not select its nearby player",
        )
        assertTrue(queued.second.isValidNow())
        assertSame(boss, queued.second.resolveNpcSource(f.npcs))
        assertSame(boss, uid.resolve(f.npcs))
        assertTrue(boss.hidden)
        assertEquals(0, boss.hitpoints)

        f.processPlayerHits(1)
        assertEquals(44, f.player.hitpoints)
        assertEquals(99, partner.hitpoints)
        val impact = impacts.single()
        assertSame(f.player, impact.player)
        assertSame(boss, impact.source)
        assertSame(boss, uid.resolve(f.npcs))
    }

    @Test
    fun `accepted death captures origin before native completion and duplicate handling cannot move it`() {
        val f = world()
        val partner = assertNotNull(f.partner)
        val boss = beginDeath(f)
        assertFalse(boss.hidden, "Exercise the snapshot before native death completion")

        // Fault injection after accepted DYING, before deathNoDrops returns. Neither a later
        // mutable corpse position nor a duplicate death call may replace the captured origin.
        boss.coords = NEX_SPAWN
        assertEquals(NexEncounterManager.DeathKind.UNRELATED, f.manager.handleDeath(boss))
        emitWrath(f, boss)
        val hit = f.hits().single().second
        assertTrue(f.hits(partner).isEmpty())

        boss.coords = NEX_SPAWN.translateX(10)
        assertTrue(hit.isValidNow(), "A corpse-coordinate change must not change queued selection")
        f.processPlayerHits(1)
        assertEquals(44, f.player.hitpoints)
        assertEquals(99, partner.hitpoints)
        assertSame(boss, hit.resolveNpcSource(f.npcs))
    }

    @Test
    fun `selection remains at emission and later movement within the room does not resample radius`() {
        val f = world()
        val partner = assertNotNull(f.partner)
        val boss = beginDeath(f)
        awaitHidden(f, boss)

        // The first occupant leaves the characterized radius before emission; the partner enters.
        f.player.coords = OLD_SPAWN_TARGET
        partner.coords = OBSERVED_TARGET
        emitWrath(f, boss)
        assertTrue(
            f.hits().isEmpty(),
            "Death-time positions must not replace emission-time selection",
        )
        val hit = f.hits(partner).single().second
        assertEquals(55, hit.damage)

        partner.coords = OLD_SPAWN_TARGET
        assertTrue(f.manager.isParticipant(partner))
        assertTrue(
            hit.isValidNow(),
            "Preserve selection at emission while room membership survives",
        )
        f.processPlayerHits(1)
        assertEquals(99, f.player.hitpoints)
        assertEquals(44, partner.hitpoints)
    }

    @Test
    fun `off-spawn queued wrath cannot cross departure or a same uid replacement session`() {
        for (replaceIdentity in listOf(false, true)) {
            val f = world()
            val partner = assertNotNull(f.partner)
            val boss = beginDeath(f)
            emitWrath(f, boss)
            val hit = f.hits().single().second
            var replacement: Player? = null

            if (replaceIdentity) {
                val newPlayer =
                    Player().apply {
                        slotId = f.player.slotId
                        uuid = f.player.uuid
                        observerUUID = 999L
                        assignUid()
                        coords = OBSERVED_TARGET
                        currentMapClock = f.clock.cycle
                        processedMapClock = f.clock.cycle
                        statMap.setBaseLevel("stat.hitpoints", 99)
                        statMap.setCurrentLevel("stat.hitpoints", 99)
                    }
                replacement = newPlayer
                assertEquals(f.player.uid, newPlayer.uid)
                assertNotSame(f.player, newPlayer)
                f.players[f.player.slotId] = newPlayer
            } else {
                f.player.coords = NEX_BANK_RETURN
            }

            // Resolve through the native player queue before a room/player cleanup callback.
            assertFalse(hit.isValidNow())
            f.processPlayerHits(1)
            assertEquals(99, f.player.hitpoints)
            replacement?.let { assertEquals(99, it.hitpoints) }
            assertEquals(99, partner.hitpoints)
            assertTrue(f.manager.isParticipant(partner))
            framework().clearInlineMocks()
        }
    }

    private fun world(): NexMinionTestWorld =
        NexMinionTestWorld(duo = true, nativeDeaths = true).apply {
            reachPhase(NexState.ZAROS)
            player.coords = OBSERVED_TARGET
            assertNotNull(partner).coords = OLD_SPAWN_TARGET
        }

    private fun beginDeath(f: NexMinionTestWorld): Npc {
        val boss = f.npc(11278)
        val encounter = f.encounters.of(boss)
        boss.coords = OBSERVED_DEATH_ANCHOR
        boss.hitpoints = 1
        f.damage(boss, 1)
        var ticks = 0
        while (encounter.currentPhaseName != "DYING" && ticks++ < 40) f.tick()
        assertEquals(
            "DYING",
            encounter.currentPhaseName,
            "Registered native death must be accepted",
        )
        assertFalse(boss.hidden, "The native death coroutine must still be in progress")
        assertTrue(boss.isSlotAssigned)
        return boss
    }

    private fun awaitHidden(f: NexMinionTestWorld, boss: Npc) {
        var ticks = 0
        while (!boss.hidden && ticks++ < 40) f.tick()
        assertTrue(boss.hidden, "Native final death must finish before the delayed Wrath callback")
        assertTrue(boss.isSlotAssigned, "Retain the original source for the native queued hit")
        assertEquals(-1, boss.lifecycleRespawnCycle, "No competing automatic reveal")
        assertEquals("DYING", f.encounters.of(boss).currentPhaseName)
    }

    private fun emitWrath(f: NexMinionTestWorld, boss: Npc) {
        awaitHidden(f, boss)
        assertTrue(f.hits().isEmpty())
        assertTrue(f.hits(assertNotNull(f.partner)).isEmpty())
        f.tick(2)
        assertEquals("DYING", f.encounters.of(boss).currentPhaseName)
        assertTrue(f.hits().isEmpty())
        assertTrue(f.hits(assertNotNull(f.partner)).isEmpty())
        f.tick()
        assertEquals("RESETTING", f.encounters.of(boss).currentPhaseName)
    }

    @AfterEach fun releaseMocks() = framework().clearInlineMocks()

    companion object {
        private val OBSERVED_DEATH_ANCHOR = CoordGrid(2920, 5191)
        private val OBSERVED_TARGET = CoordGrid(2918, 5191)
        private val OLD_SPAWN_TARGET = NEX_SPAWN.translateX(-1)

        @BeforeAll
        @JvmStatic
        fun cache() {
            if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
        }
    }
}

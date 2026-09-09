package org.rsmod.content.bosses.royaltitans

import kotlin.test.*
import net.rsprot.protocol.game.outgoing.zone.payload.LocAddChangeV2
import net.rsprot.protocol.game.outgoing.zone.payload.MapAnim
import org.junit.jupiter.api.BeforeAll
import org.rsmod.game.interact.InteractionNpcOp
import org.rsmod.game.interact.InteractionOp
import org.rsmod.map.zone.ZoneKey

class RoyalTitansPresentationTest {
    @Test
    fun `landing telegraphs use the wider captured fifteen tiles at each cast`() {
        val f = RoyalTitansTestWorld()
        val player = f.enter()
        f.tick(6)
        f.updates.clear()
        f.hit(player, f.npc(player, 12596), 406)
        f.tick()
        fun assertTelegraph(id: Int, row: Int) {
            val tiles =
                f.updates.updatedZones.flatMap { (zone, updates) ->
                    updates
                        .toList()
                        .filterIsInstance<MapAnim>()
                        .filter { it.id == id }
                        .map {
                            assertEquals(0, it.delay)
                            assertEquals(0, it.height)
                            ZoneKey(zone).toCoords().translate(it.xInZone, it.zInZone)
                        }
                }
            assertEquals(
                (row..row + 2).flatMap { z -> (29..33).map { x -> f.tile(player, x, z) } }.toSet(),
                tiles.toSet(),
            )
            assertEquals(15, tiles.size)
        }
        assertTelegraph(3219, 35)
        f.updates.clear()
        f.tick(4)
        assertTelegraph(3222, 25)
    }

    @Test
    fun `first death preserves the survivors current charge clock and cancels only defeated summon`() {
        for (defeated in listOf(12596, 14147)) {
            val f = RoyalTitansTestWorld()
            val player = f.enter()
            f.tick(6)
            val b = f.npc(player, 12596)
            val e = f.npc(player, 14147)
            f.hit(player, b, 406)
            f.tick(16) // Transition+15: first charge.
            val survivor = if (defeated == 12596) e else b
            val victim = if (defeated == 12596) b else e
            val charge = f.clock.cycle
            f.tick(2)
            f.hit(player, victim, victim.hitpoints)
            f.tick(4)
            assertEquals(charge + 6, f.clock.cycle)
            assertEquals(if (defeated == 12596) 11980 else 11982, survivor.pendingSequence.id)
            f.tick(30)
            assertEquals(charge + 36, f.clock.cycle)
            assertEquals(if (defeated == 12596) 11979 else 11981, survivor.pendingSequence.id)
        }
        val f = RoyalTitansTestWorld()
        val player = f.enter()
        f.tick(6)
        val b = f.npc(player, 12596)
        f.hit(player, b, 406)
        f.tick(30) // Transition+29: both summons, births due+31.
        assertEquals(11978, b.pendingSequence.id)
        f.hit(player, b, b.hitpoints)
        f.tick(2)
        assertEquals(3, f.activeNpcs(player).count { it.id == 14151 })
        assertEquals(0, f.activeNpcs(player).count { it.id == 14150 })
    }

    @Test
    fun `landing emits captured side pieces blockers and safe tile with rotation zero`() {
        val f = RoyalTitansTestWorld()
        val player = f.enter()
        f.tick(6)
        f.hit(player, f.npc(player, 12596), 406)
        f.tick(20)
        val locs =
            f.updates.updatedZones.values.flatMap { it.toList() }.filterIsInstance<LocAddChangeV2>()
        val floors = locs.filter { it.id in 55993..56000 }
        assertEquals(listOf(55993, 55994, 55998, 55997).sorted(), floors.map { it.id }.sorted())
        assertTrue(floors.all { it.shape == 10 && it.rotation == 0 })
        val blockers = locs.filter { it.id == 26209 }
        assertEquals(18, blockers.size)
        assertTrue(blockers.all { it.shape == 10 && it.rotation == 0 })
        val safe = locs.single { it.id == 56003 }
        assertEquals(22, safe.shape)
        assertEquals(0, safe.rotation)
        for (row in listOf(25, 35)) {
            for (x in 30..32) for (z in row..row + 2) assertTrue(
                f.blockedByLoc(f.tile(player, x, z))
            )
            // Correct1x3 side orientation must not extend across the central walkway.
            assertFalse(f.blockedByLoc(f.tile(player, 31, if (row == 25) 28 else 34)))
        }
    }

    @Test
    fun `each viewer sees current target resolved HP rather than queued hit prediction`() {
        val f = RoyalTitansTestWorld(hudBeforeInstances = false)
        val owner = f.enter()
        val partner = f.newPlayer(2)
        assertTrue(f.manager.join(partner, owner.displayName))
        f.tick(6)
        val b = f.npc(owner, 12596)
        val e = f.npc(owner, 14147)
        owner.interaction = InteractionNpcOp(InteractionOp.Op2, b, false, true)
        partner.interaction = InteractionNpcOp(InteractionOp.Op2, e, false, true)
        f.tick()
        assertEquals(12596, owner.vars["varp.hpbar_hud_npc"])
        assertEquals(14147, partner.vars["varp.hpbar_hud_npc"])
        f.hit(owner, b, 27, delay = 3)
        assertEquals(600, owner.vars["varbit.hpbar_hud_hp"])
        f.tick(2)
        assertEquals(600, owner.vars["varbit.hpbar_hud_hp"])
        f.tick()
        assertEquals(573, owner.vars["varbit.hpbar_hud_hp"])
        assertEquals(600, partner.vars["varbit.hpbar_hud_hp"])
        owner.interaction = InteractionNpcOp(InteractionOp.Op2, e, false, true)
        f.tick()
        assertEquals(14147, owner.vars["varp.hpbar_hud_npc"])
        assertEquals(600, owner.vars["varbit.hpbar_hud_hp"])
        owner.interaction = null
        f.tick()
        assertEquals(0, owner.vars["varp.hpbar_hud_npc"])
    }

    @Test
    fun `first charge follows second landing by eight ticks and never interrupts a stomp next tick`() {
        val f = RoyalTitansTestWorld()
        val player = f.enter()
        f.tick(6)
        val b = f.npc(player, 12596)
        f.hit(player, b, 406)
        f.tick()
        val transition = f.clock.cycle
        val actions = mutableMapOf<Int, Int>()
        repeat(90) {
            f.tick()
            if (b.pendingSequence.id in listOf(11981, 11982, 11974, 11978))
                actions[f.clock.cycle - transition] = b.pendingSequence.id
        }
        assertEquals(11981, actions[15]) // 1555 ->1570, Eldric lands+7.
        assertEquals(11982, actions[21])
        assertEquals(11978, actions[29])
        assertEquals(11974, actions[36])
        assertEquals(11974, actions[43])
        assertEquals(11981, actions[51])
        assertEquals(11978, actions[65])
        assertEquals(11974, actions[72])
        assertNull(actions[79], "No stomp immediately before the legacy29-tick next charge")
        assertEquals(11981, actions[80])
        // This tests the retained legacy action-selection policy, not the unproved OSRS selection.
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() = RoyalTitansCombatIntegrationTest.cache()
    }
}

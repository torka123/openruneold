package org.rsmod.content.bosses.royaltitans

import kotlin.test.*
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.*
import org.rsmod.api.combat.commons.npc.queueCombatRetaliate
import org.rsmod.api.random.GameRandom
import org.rsmod.game.seq.EntitySeq
import org.rsmod.routefinder.flag.CollisionFlag

class RoyalTitansMovementTest {
    @Test
    fun `native retaliation cannot replace a patrol while incoming damage still resolves`() {
        val rng = mock(GameRandom::class.java)
        `when`(rng.of(10)).thenReturn(1, 0)
        val f = RoyalTitansTestWorld(random = rng, nativeAttackAi = true)
        val player = f.enter()
        f.tick(18)
        val branda = f.npc(player, 12596)
        assertEquals(f.tile(player, 24, 28), branda.coords)
        branda.queueCombatRetaliate(player)
        f.hit(player, branda, 1)
        f.tick(2)
        assertEquals(f.tile(player, 24, 26), branda.coords)
        assertEquals(599, branda.hitpoints)
        assertEquals(1, f.manager.contribution(player))
        f.tick(2)
        assertEquals(11976, branda.pendingSequence.id)
    }

    @Test
    fun `threshold crossing on or before wall readiness selects one action per Titan`() {
        for (delay in 1..2) {
            val f = RoyalTitansTestWorld()
            val player = f.enter()
            f.tick(6)
            val b = f.npc(player, 12596)
            val e = f.npc(player, 14147)
            f.hit(player, b, 100, delay)
            f.tick(2)
            assertEquals(11978, b.pendingSequence.id)
            assertTrue(f.walls(player, 14152).isEmpty())
            f.tick(2)
            assertEquals(11977, e.pendingSequence.id)
            assertEquals(3, f.activeNpcs(player).count { it.id == 14150 })
            assertEquals(0, f.activeNpcs(player).count { it.id == 14151 })
            f.tick(2)
            assertEquals(3, f.activeNpcs(player).count { it.id == 14151 })
            assertTrue(f.walls(player, 14152).isEmpty())
            assertTrue(f.walls(player, 14153).isEmpty())
        }
    }

    @Test
    fun `normal 2283 first retreats and patrols match anchors facing and arrival actions`() {
        val rng = mock(GameRandom::class.java)
        `when`(rng.of(10)).thenReturn(1, 0) // Captured patrol rows, not a production RNG assertion.
        `when`(rng.of(2)).thenReturn(1, 0) // Captured return anchors 26/27 at player row 28.
        val f = RoyalTitansTestWorld(nativeMovement = true, random = rng, arenaCollision = true)
        val player = f.enter()
        val branda = f.npc(player, 12596)
        val bPositions = mutableMapOf<Int, Pair<Int, Int>>()
        val ePositions = mutableMapOf<Int, Pair<Int, Int>>()
        val actions = mutableMapOf<Pair<Int, Int>, Int>()
        for (tick in 1..38) {
            if (tick == 30) player.coords = f.tile(player, 31, 28)
            f.tick()
            for (npc in f.activeNpcs(player).filter { it.id in listOf(12596, 14147) }) {
                val base = f.tile(player, 0, 0)
                (if (npc.id == 12596) bPositions else ePositions)[tick] =
                    npc.coords.x - base.x to npc.coords.z - base.z
                if (npc.pendingSequence != EntitySeq.NULL)
                    actions[npc.id to tick] = npc.pendingSequence.id
            }
            if (tick == 21) assertEquals(f.tile(player, 30, 26), branda.pendingFaceSquare)
            if (tick == 24)
                assertEquals(f.tile(player, 30, 25), f.npc(player, 14147).pendingFaceSquare)
            if (tick == 33) assertEquals(f.tile(player, 32, 26), branda.pendingFaceSquare)
            if (tick == 36)
                assertEquals(f.tile(player, 28, 27), f.npc(player, 14147).pendingFaceSquare)
        }
        // Capture1269 maps to fixture100. All walking is native step/footprint processing.
        assertEquals(listOf(25 to 30, 24 to 30), (5..6).map { bPositions[it] })
        assertEquals(listOf(35 to 30, 36 to 30), (7..8).map { ePositions[it] })
        assertEquals((29 downTo 26).map { 24 to it }, (17..20).map { bPositions[it] })
        assertEquals((29 downTo 25).map { 36 to it }, (19..23).map { ePositions[it] })
        assertEquals(11976, actions[12596 to 8])
        assertEquals(11975, actions[14147 to 10])
        assertEquals(11976, actions[12596 to 22])
        assertEquals(11975, actions[14147 to 25])
        assertEquals(36 to 26, ePositions[22], "Eldric is still walking when Branda casts")
        // Capture1300..1306: return, then facing one tick after arrival, then stomp.
        assertEquals(listOf(25 to 26, 26 to 26), (31..32).map { bPositions[it] })
        assertEquals(listOf(35 to 26, 34 to 27), (34..35).map { ePositions[it] })
        assertEquals(11974, actions[12596 to 34])
        assertEquals(11973, actions[14147 to 37])
        assertFalse(
            actions.values.any { it == 11971 || it == 11972 },
            "Do not force walk action sequences",
        )
    }

    @Test
    fun `one blocked footprint cannot advance either route by elapsed phase time`() {
        val f = RoyalTitansTestWorld(nativeMovement = true)
        val player = f.enter()
        val branda = f.npc(player, 12596)
        // Destination anchor is clear but its northernmost occupied row is blocked.
        val blocked = f.tile(player, 24, 32)
        f.collision.add(blocked.x, blocked.z, blocked.level, CollisionFlag.LOC)
        f.tick(24)
        assertEquals(f.tile(player, 26, 30), branda.coords)
        assertNotEquals(f.tile(player, 34, 30), f.npc(player, 14147).coords)
        assertNull(
            f.lastAnimation(branda),
            "No action after the initial spawn while the route is blocked",
        )
        assertFalse(branda.pendingTelejump || branda.pendingTeleport)
    }

    @Test
    fun `an aborted route cannot become a successful arrival`() {
        val state = RoyalTitanMovement(100)
        val target = org.rsmod.map.CoordGrid(2904, 9562)
        state.beginRoute(target, RoyalTitanMovementPhase.SECOND_EDGE_ACTION)
        state.arrive(target.translate(0, 1), 120)
        assertNull(state.arrivalCycle)
        state.arrive(target, 125)
        assertEquals(125, state.arrivalCycle)
    }

    @Test
    fun `return turn ends only that Titans ranged vulnerability`() {
        val rng = mock(GameRandom::class.java)
        `when`(rng.of(10)).thenReturn(1, 0)
        val f = RoyalTitansTestWorld(nativeMovement = true, random = rng, arenaCollision = true)
        val player = f.enter()
        f.tick(4)
        assertEquals(6.0, f.npc(player, 12596).incomingRangedAccuracyMultiplier)
        assertEquals(1.0, f.npc(player, 14147).incomingRangedAccuracyMultiplier)
        f.tick(2)
        assertEquals(6.0, f.npc(player, 14147).incomingRangedAccuracyMultiplier)
        f.tick(24)
        assertEquals(1.0, f.npc(player, 12596).incomingRangedAccuracyMultiplier)
        assertEquals(6.0, f.npc(player, 14147).incomingRangedAccuracyMultiplier)
        f.tick(3)
        assertEquals(1.0, f.npc(player, 14147).incomingRangedAccuracyMultiplier)
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() = RoyalTitansCombatIntegrationTest.cache()
    }
}

package org.rsmod.content.bosses.royaltitans

import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.*
import org.rsmod.api.game.process.player.PlayerQueueProcessor
import org.rsmod.api.player.protect.ProtectedAccessContext
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.game.hit.Hit

/** Executes the return and outgoing hit on the real revision-240 arena collision. */
class RoyalTitansPhaseOneDamageTest {
    @AfterEach fun clearMocks() = framework().clearInlineMocks()

    @Test
    fun `each Titan returns and damages an unprotected stationary player before teleport`() {
        verifyFirstStomps(protected = false)
    }

    @Test
    fun `protect from melee blocks both first phase stomps on real arena terrain`() {
        verifyFirstStomps(protected = true)
    }

    @Test
    fun `all side platform destinations route on blocked terrain without opening it to players`() {
        val f = RoyalTitansTestWorld(arenaCollision = true)
        val player = f.enter()
        f.tick(2)
        val routes = org.rsmod.api.route.RouteFactory(f.collision)
        for ((id, columns) in listOf(12596 to listOf(24, 26), 14147 to listOf(34, 36))) {
            val npc = f.npc(player, id)
            assertEquals(dev.openrune.types.MoveRestrict.Blocked, npc.moveRestrict)
            for (x in columns) for (z in 25..35) {
                val destination = f.tile(player, x, z)
                for (dx in 0..2) for (dz in 0..2) {
                    val c = destination.translate(dx, dz)
                    assertNotEquals(
                        0,
                        f.collision[c.x, c.z, c.level] and
                            org.rsmod.routefinder.flag.CollisionFlag.BLOCK_WALK,
                        "$id: $c",
                    )
                }
                val path =
                    routes.create(npc.avatar, destination, assertNotNull(npc.collisionStrategy))
                assertTrue(path.success, "$id: $destination")
                if (destination != npc.coords)
                    assertEquals(destination.x to destination.z, path.last().let { it.x to it.z })
            }
        }
        val retreat = f.tile(player, 24, 30)
        val normal = routes.create(player.avatar, retreat)
        assertTrue(normal.lastOrNull()?.let { it.x to it.z } != retreat.x to retreat.z)
        val b = f.npc(player, 12596)
        val blocked = f.tile(player, 24, 32)
        f.collision.add(
            blocked.x,
            blocked.z,
            blocked.level,
            org.rsmod.routefinder.flag.CollisionFlag.LOC,
        )
        val wall = routes.create(b.avatar, retreat, assertNotNull(b.collisionStrategy))
        assertTrue(
            wall.lastOrNull()?.let { it.x to it.z } != retreat.x to retreat.z,
            "A blocked northern footprint tile must still prevent arrival",
        )
    }

    @Test
    fun `transition restores native movement setting without changing second phase`() {
        val f = RoyalTitansTestWorld(arenaCollision = true)
        val player = f.enter()
        f.tick(6)
        val b = f.npc(player, 12596)
        val e = f.npc(player, 14147)
        f.hit(player, b, 406)
        f.tick(8)
        assertEquals(b.type.moveRestrict, b.moveRestrict)
        assertEquals(e.type.moveRestrict, e.moveRestrict)
    }

    private fun verifyFirstStomps(protected: Boolean) {
        val rng =
            object : GameRandom {
                private var patrolRoll = 0

                override fun of(maxExclusive: Int): Int =
                    when (maxExclusive) {
                        10 -> if (patrolRoll++ == 0) 1 else 0
                        27 -> 12
                        else -> 0
                    }

                override fun of(minInclusive: Int, maxInclusive: Int) = minInclusive

                override fun randomDouble() = 0.0
            }
        val f = RoyalTitansTestWorld(random = rng, nativeAttackAi = true, arenaCollision = true)
        val player = f.enter()
        VarPlayerIntMapSetter.set(player, "varbit.prayer_protectfrommelee", if (protected) 1 else 0)
        val context = mock(ProtectedAccessContext::class.java)
        `when`(context.eventBus).thenReturn(f.events)
        `when`(context.npcList).thenReturn(f.npcList)
        `when`(context.playerList).thenReturn(f.players)
        `when`(context.random).thenReturn(rng)
        val factory = mock(ProtectedAccessContextFactory::class.java)
        `when`(factory.create()).thenReturn(context)
        val queues = PlayerQueueProcessor(f.events, ProtectedAccessLauncher(factory))
        val sources = mutableSetOf<Int>()
        val trace = mutableListOf<String>()
        repeat(65) { tick ->
            f.tick()
            for (npc in f.activeNpcs(player).filter { it.id == 12596 || it.id == 14147 }) {
                trace += "$tick: ${npc.id} ${npc.coords} seq=${npc.pendingSequence.id}"
            }
            val iterator = player.queueList.iterator() ?: return@repeat
            val hits = mutableListOf<Hit>()
            try {
                while (iterator.hasNext()) {
                    val hit = iterator.next().args as? Hit
                    if (hit?.context?.attribution?.mechanic == "royal_titans.stomp") hits += hit
                    else iterator.remove()
                }
            } finally {
                iterator.cleanUp()
            }
            val stomps = hits.filter { it.context.attribution.mechanic == "royal_titans.stomp" }
            if (stomps.isNotEmpty()) {
                // Delivery is one game tick after the queued stomp animation.
                val before = player.hitpoints
                f.tick()
                for (hit in stomps) {
                    assertTrue(hit.isValidNow())
                    assertEquals(if (protected) 0 else 12, hit.damage)
                    sources += assertNotNull(hit.resolveNpcSource(f.npcList)).id
                }
                queues.process(player)
                assertEquals(before - stomps.sumOf { it.damage }, player.hitpoints)
                if (sources.size == 2) return
            }
        }
        fail("Both Titans must stomp before teleport; got $sources.\n${trace.joinToString("\n")}")
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() = RoyalTitansCombatIntegrationTest.cache()
    }
}

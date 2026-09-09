package org.rsmod.content.bosses.royaltitans

import kotlin.test.*
import org.junit.jupiter.api.BeforeAll
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.routefinder.flag.CollisionFlag

class RoyalTitansElementalMovementTest {
    @Test
    fun `native adjacent interaction uses four tick melee cadence after pathing`() {
        val f = RoyalTitansTestWorld(nativeAttackAi = true)
        val player = f.enter()
        val npc = f.summon(player).first { it.id == 14151 }
        PathingEntityCommon.telejump(npc, f.collision, f.tile(player, 31, 36))
        npc.resetMovement()
        npc.actionDelay = f.clock.cycle
        val attacks = mutableListOf<Int>()
        val states = mutableListOf<String>()
        repeat(11) {
            f.tick()
            states +=
                "${f.clock.cycle}:${npc.coords},spawn=${npc.spawnCoords},leash=${npc.maxOpRange},visible=${player.isVisible},mode=${npc.mode},interaction=${npc.interaction},delay=${npc.actionDelay},busy=${npc.isBusy},hp=${npc.hitpoints}"
            if (npc.pendingSequence.id == 11950) attacks += f.clock.cycle
        }
        assertTrue(
            attacks.size >= 3,
            "Must actually dispatch native approach attacks: $attacks; $states",
        )
        assertTrue(attacks.zipWithNext().all { (a, b) -> b - a == 4 }, "$attacks")
    }

    @Test
    fun `a wall forming across an existing route triggers a detour without a moving target`() {
        val f = RoyalTitansTestWorld(arenaCollision = true)
        val player = f.enter()
        val npc = f.summon(player).first { it.id == 14151 }
        PathingEntityCommon.telejump(npc, f.collision, f.tile(player, 31, 29))
        npc.resetMovement()
        f.tick(2)
        assertTrue(npc.routeDestination.isNotEmpty())
        val wall = (29..33).map { f.tile(player, it, 32) }
        wall.forEach { f.collision.add(it.x, it.z, it.level, CollisionFlag.LOC) }
        f.tick(4)
        assertTrue(npc.coords.z < wall.first().z, "A complete wall closes the walkway")
        val gap = wall.first()
        f.collision.remove(gap.x, gap.z, gap.level, CollisionFlag.LOC)
        val anchors = mutableListOf(npc.coords)
        repeat(12) {
            f.tick()
            anchors += npc.coords
        }
        assertTrue(anchors.none { it in wall && it != gap })
        assertTrue(gap in anchors, "Use the open segment, not the blocked Titan platforms")
        assertTrue(anchors.last().z > wall.first().z)
        assertTrue(anchors.zipWithNext().all { (a, b) -> a.chebyshevDistance(b) <= 1 })
    }

    @Test
    fun `packs are vertical and can occupy and traverse identical tiles`() {
        val f = RoyalTitansTestWorld()
        val player = f.enter()
        val packs = f.summon(player).groupBy { it.id }
        for (pack in packs.values) {
            assertEquals(1, pack.map { f.spawnPositions.getValue(it).x }.distinct().size)
            val rows = pack.map { f.spawnPositions.getValue(it).z }.sorted()
            assertEquals((rows.first()..rows.first() + 2).toList(), rows)
        }
        val pack = packs.getValue(14151)
        for (npc in pack) {
            PathingEntityCommon.telejump(npc, f.collision, f.tile(player, 31, 29))
            npc.resetMovement()
        }
        repeat(5) {
            f.tick()
            assertEquals(
                1,
                pack.map { it.coords }.distinct().size,
                "No artificial tile reservations",
            )
        }
        assertNotEquals(f.tile(player, 31, 29), pack.first().coords)
    }

    @Test
    fun `elemental routes through an opened wall segment without walking on Titan platforms`() {
        val f = RoyalTitansTestWorld(arenaCollision = true)
        val player = f.enter()
        val npc = f.summon(player).first { it.id == 14151 }
        PathingEntityCommon.telejump(npc, f.collision, f.tile(player, 31, 29))
        npc.resetMovement()
        val wall = (29..33).map { f.tile(player, it, 32) }
        wall.forEach { f.collision.add(it.x, it.z, it.level, CollisionFlag.LOC) }
        val gap = wall.first()
        f.collision.remove(gap.x, gap.z, gap.level, CollisionFlag.LOC)
        val anchors = mutableListOf(npc.coords)
        repeat(13) {
            f.tick()
            anchors += npc.coords
        }
        assertTrue(anchors.none { it in wall && it != gap })
        assertTrue(gap in anchors, "Route must detour through the open segment")
        assertTrue(anchors.all { it.x in wall.first().x..wall.last().x })
        assertTrue(
            anchors.last().z > wall.first().z,
            "A failed greedy walk must not stall at the wall",
        )
        assertTrue(anchors.zipWithNext().all { (a, b) -> a.chebyshevDistance(b) <= 1 })
    }

    @Test
    fun `each Titan selects its own pack target and retains it until departure`() {
        val f = RoyalTitansTestWorld()
        val owner = f.enter()
        val partner = f.newPlayer(2)
        assertTrue(f.manager.join(partner, owner.displayName))
        PathingEntityCommon.telejump(owner, f.collision, f.tile(owner, 24, 37))
        PathingEntityCommon.telejump(partner, f.collision, f.tile(owner, 39, 25))
        val packs = f.summon(owner).groupBy { it.id }
        val fire = packs.getValue(14150)
        val ice = packs.getValue(14151)
        assertTrue(fire.all { it.facingTarget(f.players) === owner })
        assertTrue(ice.all { it.facingTarget(f.players) === partner })
        PathingEntityCommon.telejump(owner, f.collision, f.tile(owner, 39, 25))
        PathingEntityCommon.telejump(partner, f.collision, f.tile(owner, 24, 37))
        f.tick(2)
        assertTrue(fire.all { it.facingTarget(f.players) === owner })
        assertTrue(ice.all { it.facingTarget(f.players) === partner })
        f.manager.escape(owner)
        f.tick(2)
        assertTrue(fire.all { it.facingTarget(f.players) === partner })
        assertTrue(ice.all { it.facingTarget(f.players) === partner })
        // These are deterministic retention-policy tests, not proof of unpublished OSRS retarget
        // rules.
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() = RoyalTitansCombatIntegrationTest.cache()
    }
}

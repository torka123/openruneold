package org.rsmod.content.bosses.cerberus

import kotlin.test.*
import org.junit.jupiter.api.BeforeAll
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.hit.HitType
import org.rsmod.game.seq.EntitySeq

/** Executed draft behavior, not additional OSRS evidence. Kept through the wiring migration. */
class CerberusMigrationCharacterizationTest {
    @org.junit.jupiter.api.AfterEach
    fun releaseFixtureMocks() = org.mockito.Mockito.framework().clearInlineMocks()

    @Test
    fun `ordinary launch locks six ticks and blocked opportunities do not consume override`() {
        val world = CerberusTestWorld()
        val player = world.enter()
        world.wake(player)
        world.force(player, "magic")
        val first = world.clock.cycle
        assertEquals(first + 6, world.boss(player).actionDelay)
        assertEquals(listOf(HitType.Magic), world.hits(player).map { it.type })
        world.manager.debug(player, listOf("next", "ranged"))
        world.updates.clear()
        repeat(5) {
            world.tick()
            world.attack(player)
            assertEquals(0, world.projectileCount())
            assertTrue(world.status(player).contains("counter=2 "))
        }
        world.tick()
        world.attack(player)
        assertEquals(CerberusAssets.RANGED_PROJECTILE_SPOTANIM, world.projectiles().single().id)
        assertEquals(first + 12, world.boss(player).actionDelay)
        assertEquals(listOf(HitType.Magic, HitType.Ranged), world.hits(player).map { it.type })
    }

    @Test
    fun `special thresholds are strict and fallback uses ordinary recovery`() {
        for ((hp, counter, special, rate) in
            listOf(
                listOf(400, 7, 0, 6),
                listOf(399, 7, 1, 10),
                listOf(200, 5, 0, 6),
                listOf(199, 5, 2, 8),
            )) {
            val world = CerberusTestWorld()
            val player = world.enter()
            world.wake(player)
            world.boss(player).hitpoints = hp
            world.manager.debug(player, listOf("counter", counter.toString()))
            world.manager.debug(player, listOf("skip", "suppress"))
            val launch = world.clock.cycle
            world.updates.clear()
            world.attack(player)
            assertEquals(launch + rate, world.boss(player).actionDelay, "HP $hp")
            assertEquals(if (special == 1) 3 else 0, world.souls(player).size, "HP $hp")
            assertEquals(
                if (special == 2) 3 else 0,
                world.projectiles().count { it.id == CerberusAssets.LAVA_FLAME_SPOTANIM },
                "HP $hp",
            )
        }
    }

    @Test
    fun `forced skip uses normal attack without changing the next opportunity`() {
        val world = CerberusTestWorld()
        val player = world.enter()
        world.wake(player)
        world.boss(player).hitpoints = 150
        world.manager.debug(player, listOf("counter", "7"))
        world.manager.debug(player, listOf("skip", "force"))
        world.attack(player)
        assertTrue(world.souls(player).isEmpty())
        assertEquals(world.clock.cycle + 6, world.boss(player).actionDelay)
        assertTrue(world.status(player).contains("counter=8 "))
        assertFalse(world.status(player).contains("eligible=true"))
    }

    @Test
    fun `triple retains dedicated animation and ten tick recovery while prayer changes`() {
        val world = CerberusTestWorld()
        val player = world.enter()
        world.wake(player)
        val launch = world.clock.cycle
        world.attack(player)
        val boss = world.boss(player)
        assertEquals(CerberusAssets.TRIPLE_SPRAY_ANIMATION, boss.pendingSequence.id)
        world.manager.defend(boss)
        assertEquals(CerberusAssets.TRIPLE_SPRAY_ANIMATION, boss.pendingSequence.id)
        VarPlayerIntMapSetter.set(player, "varbit.prayer_protectfrommissiles", 1)
        world.tick(2)
        assertEquals(HitType.Ranged, world.hits(player).last().type)
        assertEquals(0, world.hits(player).last().damage)
        VarPlayerIntMapSetter.set(player, "varbit.prayer_protectfrommissiles", 0)
        VarPlayerIntMapSetter.set(player, "varbit.prayer_protectfrommelee", 1)
        world.tick(2)
        assertEquals(HitType.Melee, world.hits(player).last().type)
        assertEquals(0, world.hits(player).last().damage)
        assertEquals(launch + 10, boss.actionDelay)
        // The real NPC post-tick clears emitted animation packets; this small fixture does not.
        boss.pendingSequence = EntitySeq.NULL
        world.manager.defend(boss)
        assertEquals(EntitySeq.NULL, boss.pendingSequence)
        world.tick(2)
        world.manager.defend(boss)
        assertEquals(CerberusAssets.DEFEND_ANIMATION, boss.pendingSequence.id)
    }

    companion object {
        @JvmStatic @BeforeAll fun loadCache() = CerberusTestCache.ensureLoaded()
    }
}

package org.rsmod.content.bosses.cerberus

import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.framework
import org.rsmod.game.hit.HitType

class CerberusNativeCombatTest {
    @AfterEach fun releaseFixtureMocks() = framework().clearInlineMocks()

    @Test
    fun `later triple components do not replay a default animation after packet flush`() {
        val world = CerberusTestWorld()
        val player = world.enter()
        world.wake(player)
        val boss = world.boss(player)
        world.attack(player)
        assertEquals(CerberusAssets.TRIPLE_SPRAY_ANIMATION, boss.pendingSequence.id)
        boss.pendingSequence = org.rsmod.game.seq.EntitySeq.NULL
        world.tick(2)
        assertEquals(org.rsmod.game.seq.EntitySeq.NULL, boss.pendingSequence)
        world.tick(2)
        assertEquals(org.rsmod.game.seq.EntitySeq.NULL, boss.pendingSequence)
        assertEquals(3, world.hits(player).size)
    }

    @Test
    fun `standing reaches active on the fourth tick even outside attack range`() {
        val world = CerberusTestWorld()
        val player = world.enter()
        val boss = world.boss(player)
        player.coords = world.tile(player, 43, 55)
        world.tick()
        assertTrue(world.status(player).contains("phase=STANDING "))
        assertEquals(CerberusAssets.STAND_ANIMATION, boss.pendingSequence.id)
        repeat(3) {
            world.tick()
            assertEquals(CerberusAssets.SITTING_NPC_ID, boss.visType.id)
        }
        world.tick()
        assertEquals(CerberusAssets.ACTIVE_NPC_ID, boss.visType.id)
        assertTrue(world.hits(player).isEmpty())
    }

    @Test
    fun `returning home retains four tick minimum and four tick sitting presentation`() {
        val world = CerberusTestWorld()
        val player = world.enter()
        world.wake(player)
        val boss = world.boss(player)
        player.coords = world.tile(player, 24, 24)
        world.tick()
        assertEquals(CerberusAssets.RESETTING_NPC_ID, boss.visType.id)
        world.tick(3)
        assertTrue(world.status(player).contains("phase=RESETTING "))
        world.tick()
        assertEquals(CerberusAssets.SIT_ANIMATION, boss.pendingSequence.id)
        world.tick(3)
        assertEquals(CerberusAssets.RESETTING_NPC_ID, boss.visType.id)
        world.tick()
        assertEquals(CerberusAssets.SITTING_NPC_ID, boss.visType.id)
        assertEquals(600, boss.hitpoints)
        assertTrue(world.status(player).contains("phase=SITTING "))
    }

    @Test
    fun `ordinary native attacks update combat attribution and share DSL readiness`() {
        val world = CerberusTestWorld()
        val player = world.enter()
        world.wake(player)
        world.force(player, "magic")
        val boss = world.boss(player)
        val encounter = assertNotNull(world.deps.encounterRegistry.existing(boss))
        assertEquals(world.clock.cycle, boss.vars["varn.lastattack"])
        assertEquals(world.clock.cycle, player.vars["varp.lastcombat"])
        assertEquals(1, encounter.phaseAbilityCount)
        assertEquals(6, encounter.expectedAttackRate())
        repeat(5) {
            world.tick()
            world.attack(player, operate = true)
        }
        assertEquals(1, encounter.phaseAbilityCount)
        world.tick()
        world.attack(player, operate = true)
        assertEquals(2, encounter.phaseAbilityCount)
        assertEquals(2, world.hits(player).size)
    }

    @Test
    fun `triple is one native ability with launches at zero two four and next attack at ten`() {
        val world = CerberusTestWorld()
        val player = world.enter()
        world.wake(player)
        val boss = world.boss(player)
        val encounter = assertNotNull(world.deps.encounterRegistry.existing(boss))
        val start = world.clock.cycle
        world.attack(player)
        val first = world.projectiles().single()
        repeat(9) { index ->
            world.tick()
            world.attack(player, operate = index % 2 == 0)
            assertEquals(
                1,
                encounter.phaseAbilityCount,
                "An attempt during the volley/recovery must not commit",
            )
            assertEquals(
                when {
                    index < 1 -> 1
                    index < 3 -> 2
                    else -> 3
                },
                world.hits(player).size,
            )
        }
        val second =
            world.projectiles().single { it.id == CerberusAssets.RANGED_PROJECTILE_SPOTANIM }
        assertNotEquals(
            first.xInZone to first.zInZone,
            second.xInZone to second.zInZone,
            "Magic and ranged must use opposite outer heads",
        )
        assertEquals(
            listOf(HitType.Magic, HitType.Ranged, HitType.Melee),
            world.hits(player).map { it.type },
        )
        assertEquals(start + 10, boss.actionDelay)
        world.tick()
        world.attack(player)
        assertEquals(listOf(start, start + 10), encounter.recentAbilityCommitTicks())
    }

    @Test
    fun `dormant actor is locked and cannot commit through either native AI event`() {
        val world = CerberusTestWorld()
        val player = world.enter()
        val boss = world.boss(player)
        assertTrue(boss.movementLocked)
        world.attack(player)
        world.attack(player, operate = true)
        assertEquals(0, world.deps.encounterRegistry.existing(boss)?.phaseAbilityCount)
        assertTrue(world.hits(player).isEmpty())
        world.wake(player)
        assertFalse(boss.movementLocked)
        world.attack(player)
        assertEquals(1, world.deps.encounterRegistry.existing(boss)?.phaseAbilityCount)
    }

    @Test
    fun `reset retires native volley scope and starts a fresh opening triple`() {
        val world = CerberusTestWorld()
        val player = world.enter()
        world.wake(player)
        val boss = world.boss(player)
        world.attack(player)
        val old = assertNotNull(world.deps.encounterRegistry.existing(boss))
        val launched = world.hits(player).single()
        player.coords = world.tile(player, 24, 24)
        world.tick()
        assertTrue(old.isEnded)
        assertFalse(launched.isValidNow())
        world.updates.clear()
        world.tick(12)
        assertEquals(0, world.projectileCount())
        world.wake(player)
        world.attack(player)
        val fresh = assertNotNull(world.deps.encounterRegistry.existing(boss))
        assertNotSame(old, fresh)
        assertEquals("triple", fresh.lastAbilityName)
        assertEquals(1, fresh.phaseAbilityCount)
    }

    companion object {
        @JvmStatic @BeforeAll fun loadCache() = CerberusTestCache.ensureLoaded()
    }
}

package org.rsmod.content.bosses.dt2

import dev.openrune.ServerCacheManager
import dev.openrune.types.NpcMode
import kotlin.test.*
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.framework
import org.rsmod.annotations.InternalApi
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.hit.HitType

/**
 * Architecture regression against the retained Dt2Rules/manager contract: real registration, native
 * NvP launch and queued damage, with no new timing or mechanic assumptions.
 */
@OptIn(InternalApi::class)
class WhispererBossCombatTest {
    @Test
    fun `native primary keeps stage volleys ten ticks and launch prayer snapshot`() {
        for (difficulty in Dt2Difficulty.entries) {
            for (stage in listOf(0, 1, 3)) {
                try {
                    val f =
                        Dt2NativeCombatFixture(
                            bossKind = Dt2Boss.WHISPERER,
                            difficulty = difficulty,
                        )
                    f.reachDistanceStage(stage)
                    VarPlayerIntMapSetter.set(f.player, "varbit.prayer_protectfrommagic", 1)
                    f.attack()
                    val primary = f.hits().single()
                    assertEquals(HitType.Magic, primary.second.type)
                    assertEquals(2, primary.first)
                    assertEquals(0, primary.second.damage)
                    assertEquals(110, f.boss.actionDelay)
                    assertEquals(1, f.run.attackCounter)
                    assertEquals(2, f.phases.shotOwner.size)
                    assertEquals(if (stage >= 2) 4 else 0, f.run.pendingWhispererTentacles.size)
                    VarPlayerIntMapSetter.set(f.player, "varbit.prayer_protectfrommagic", 0)
                    assertEquals(
                        0,
                        primary.second.damage,
                        "Primary protection was captured at launch",
                    )
                    for ((index, style) in
                        Dt2Rules.whispererVolley(0, stage, false).styles.drop(1).withIndex()) {
                        f.advance(1)
                        f.manager.tick(f.player)
                        assertEquals(101 + index, f.clock.cycle)
                        assertEquals(
                            if (style == Dt2AttackStyle.MAGIC) HitType.Magic else HitType.Ranged,
                            f.hits().last().second.type,
                        )
                        assertEquals(1 - index, f.phases.shotOwner.size)
                    }
                    f.advance(7)
                    f.attack()
                    assertEquals(1, f.run.attackCounter)
                    f.advance(1)
                    f.attack()
                    assertEquals(HitType.Ranged, f.hits().last().second.type)
                    assertEquals(
                        if (difficulty == Dt2Difficulty.AWAKENED) 60 else 40,
                        f.hits().last().second.damage,
                    )
                    assertEquals(120, f.boss.actionDelay)
                } finally {
                    framework().clearInlineMocks()
                }
            }
        }
    }

    @Test
    fun `followup launch retains its own prayer snapshot and seeded damage draw`() {
        val f = Dt2NativeCombatFixture(bossKind = Dt2Boss.WHISPERER)
        f.activateWhisperer()
        f.attack()
        assertEquals(36, f.hits().single().second.damage)
        val firstDestination = f.projectiles.single().endCoord
        f.player.coords = f.player.coords.translate(-3, 0)
        f.advance(1)
        VarPlayerIntMapSetter.set(f.player, "varbit.prayer_protectfrommagic", 1)
        f.manager.tick(f.player)
        assertEquals(0, f.hits().last().second.damage)
        assertEquals(f.player.coords, f.projectiles.last().endCoord)
        assertNotEquals(firstDestination, f.projectiles.last().endCoord)
        assertEquals(1, f.phases.shotOwner.size)
        f.advance(1)
        VarPlayerIntMapSetter.set(f.player, "varbit.prayer_protectfrommagic", 0)
        f.manager.tick(f.player)
        val expected = Dt2SeededRng(42).apply { nextInt(37) }.nextInt(37)
        assertEquals(expected, f.hits().last().second.damage)
        assertEquals(0, f.phases.shotOwner.size)
        assertEquals(
            listOf(37),
            f.rollBounds,
            "Followups retain the content RNG; only primary uses native accuracy RNG",
        )
    }

    @Test
    fun `special resume preserves two tick melee deadline and thirteen tick return window`() {
        val f = Dt2NativeCombatFixture(bossKind = Dt2Boss.WHISPERER)
        f.activateWhisperer()
        f.attack()
        assertTrue(f.manager.forcePhase(f.player, WhispererSpecial.SOUL_SIPHON.name))
        assertEquals(
            whispererSpecialPhase(WhispererSpecial.SOUL_SIPHON),
            f.registry.of(f.boss).currentPhaseName,
        )
        assertEquals(0, f.phases.shotOwner.size)
        f.attack()
        assertEquals(1, f.run.attackCounter)
        f.run.specialProgress = f.run.specialRequired
        f.manager.tick(f.player)
        assertEquals(12205, f.boss.visType.id)
        assertEquals(
            102,
            f.boss.actionDelay,
            "Existing finishSpecial deadline supersedes prior ten-tick launch",
        )
        assertEquals(1, f.phases.formOwner.size)
        assertFalse(f.boss.movementLocked)
        f.advance(1)
        f.attack()
        assertEquals(1, f.hits().size)
        f.advance(1)
        VarPlayerIntMapSetter.set(f.player, "varbit.prayer_protectfrommelee", 1)
        f.attack()
        assertEquals(HitType.Melee, f.hits().last().second.type)
        assertEquals(1, f.hits().last().first)
        assertEquals(21, f.hits().last().second.damage)
        assertEquals(104, f.boss.actionDelay)
        assertEquals(1, f.run.attackCounter, "Melee does not advance the distance volley rotation")
        assertEquals(1, f.phases.shotOwner.size)
        val projectiles = f.projectiles.size
        f.advance(1)
        f.manager.tick(f.player)
        assertEquals(HitType.Melee, f.hits().last().second.type)
        assertEquals(1, f.hits().last().first)
        assertEquals(projectiles, f.projectiles.size, "Melee followup never creates a projectile")
        f.advance(9)
        f.manager.tick(f.player)
        f.attack()
        assertEquals(12205, f.boss.visType.id)
        assertEquals(114, f.boss.actionDelay)
        f.advance(1)
        f.manager.tick(f.player)
        assertEquals(12204, f.boss.visType.id)
        assertTrue(f.boss.movementLocked)
        assertEquals(12, f.boss.apRangeOverride)
        f.attack()
        assertEquals(
            1,
            f.run.attackCounter,
            "Rate change must preserve the outstanding melee deadline",
        )
        f.advance(1)
        f.attack()
        assertEquals(2, f.run.attackCounter)
        assertEquals(HitType.Ranged, f.hits().last().second.type)
        assertEquals(
            124,
            f.boss.actionDelay,
            "Returning to distance must not add a fresh ten-tick wait",
        )
    }

    @Test
    fun `lethal transition keeps three tick opening and paired enraged rotation`() {
        val f = Dt2NativeCombatFixture(bossKind = Dt2Boss.WHISPERER)
        f.activateWhisperer()
        f.attack()
        assertEquals(0, f.damageBoss(f.boss.hitpoints).damage)
        assertTrue(f.run.enraged)
        assertEquals(140, f.boss.hitpoints)
        assertEquals(0, f.phases.shotOwner.size)
        assertEquals(103, f.boss.actionDelay)
        f.advance(2)
        f.attack()
        assertEquals(0, f.run.attackCounter)
        f.advance(1)
        for ((index, type) in
            listOf(HitType.Ranged, HitType.Ranged, HitType.Magic, HitType.Magic, HitType.Ranged)
                .withIndex()) {
            if (index > 0) f.advance(2)
            f.attack()
            assertEquals(type, f.hits().last().second.type)
            assertEquals(index + 1, f.run.attackCounter)
            assertEquals(f.clock.cycle + 2, f.boss.actionDelay)
            assertEquals(0, f.phases.shotOwner.size)
            assertEquals(index + 1, f.run.pendingWhispererTentacles.size)
        }
    }

    @Test
    fun `enrage while melee preserves visual first branch beyond normal melee expiry`() {
        val f =
            Dt2NativeCombatFixture(
                bossKind = Dt2Boss.WHISPERER,
                difficulty = Dt2Difficulty.AWAKENED,
            )
        f.activateWhisperer()
        f.manager.forcePhase(f.player, WhispererSpecial.SOUL_SIPHON.name)
        f.run.specialProgress = f.run.specialRequired
        f.manager.tick(f.player)
        f.damageBoss(f.boss.hitpoints)
        assertEquals(250, f.boss.hitpoints)
        assertEquals(WHISPERER_MELEE, f.registry.of(f.boss).currentPhaseName)
        f.advance(13)
        f.manager.tick(f.player)
        f.attack()
        assertEquals(12205, f.boss.visType.id)
        assertEquals(HitType.Melee, f.hits().last().second.type)
        assertEquals(63, f.hits().last().second.damage)
        assertEquals(0, f.run.attackCounter)
        assertEquals(115, f.boss.actionDelay)
        assertFalse(f.boss.movementLocked)
        assertTrue(f.run.pendingWhispererTentacles.isEmpty())
    }

    @Test
    fun `rejected native target cannot consume the primary rotation`() {
        val f = Dt2NativeCombatFixture(bossKind = Dt2Boss.WHISPERER)
        f.attack()
        assertTrue(f.hits().isEmpty())
        f.activateWhisperer()
        // Visibility is reversible. pendingLogout permanently retires the membership HitScope.
        f.player.hidden = true
        f.attack()
        f.player.hidden = false
        f.boss.mode = null
        f.attack()
        f.boss.mode = NpcMode.ApPlayer2
        f.attack()
        assertEquals(1, f.run.attackCounter)
        assertEquals(HitType.Magic, f.hits().single().second.type)
        assertEquals(listOf(37), f.rollBounds)
    }

    @Test
    fun `inactive transitions retain the current visual form movement policy`() {
        val f = Dt2NativeCombatFixture(bossKind = Dt2Boss.WHISPERER)
        f.activateWhisperer()
        assertTrue(f.boss.movementLocked)
        f.manager.forcePhase(f.player, WhispererSpecial.SOUL_SIPHON.name)
        assertEquals(
            whispererSpecialPhase(WhispererSpecial.SOUL_SIPHON),
            f.registry.of(f.boss).currentPhaseName,
        )
        assertTrue(f.boss.movementLocked, "Distance form stays locked during a special")
        f.run.specialProgress = f.run.specialRequired
        f.manager.tick(f.player)
        assertEquals(12205, f.boss.visType.id)
        assertFalse(f.boss.movementLocked)
        f.manager.forcePhase(f.player, WhispererSpecial.SOUL_SIPHON.name)
        assertEquals(
            whispererSpecialPhase(WhispererSpecial.SOUL_SIPHON),
            f.registry.of(f.boss).currentPhaseName,
        )
        assertFalse(
            f.boss.movementLocked,
            "Special entry from melee retains the native movement flag",
        )
        f.attack()
        assertEquals(0, f.run.attackCounter)
        assertTrue(f.hits().isEmpty())
        assertNotNull(f.manager.beginBossDeath(f.boss))
        assertFalse(f.boss.movementLocked, "Death also retains the current form's movement flag")
    }

    @Test
    fun `launched primary keeps death lifetime while cleanup retires followups and deletion retires hit`() {
        val f = Dt2NativeCombatFixture(bossKind = Dt2Boss.WHISPERER)
        f.reachDistanceStage(2)
        f.attack()
        val primary = f.hits().single().second
        f.boss.hitpoints = 0
        assertNotNull(f.manager.beginBossDeath(f.boss))
        assertTrue(primary.isValidNow())
        assertEquals(0, f.phases.shotOwner.size)
        assertTrue(f.phases.shotOwner.isClosed)
        assertTrue(f.run.pendingWhispererTentacles.isEmpty())
        f.advance(10)
        f.attack()
        assertEquals(1, f.run.attackCounter)
        f.events.publish(NpcStateEvents.Delete(f.boss))
        assertFalse(primary.isValidNow())
    }

    private fun Dt2NativeCombatFixture.damageBoss(damage: Int): HitBuilder {
        val builder =
            HitBuilder(
                type = HitType.Melee,
                damage = damage,
                sourceUid = player.uid.packed,
                sourceSlot = player.slotId,
                isFromNpc = false,
                isFromPlayer = true,
                clientDelay = 0,
                righthandType = null,
                secondaryType = null,
                targetHitmark = 0,
                sourceHitmark = 0,
                publicHitmark = null,
                zeroDamageHitmarkLit = null,
                zeroDamageHitmarkTint = null,
                maxDamageHitmarkLit = null,
                targetMaxDamageThreshold = Int.MAX_VALUE,
                sourceMaxDamageThreshold = Int.MAX_VALUE,
            )
        events.publish(NpcHitEvents.Modify(boss, builder))
        return builder
    }

    /**
     * Reach a volley stage through real special completion while retaining first attack tick 100.
     */
    private fun Dt2NativeCombatFixture.reachDistanceStage(stage: Int) {
        clock.cycle -= stage * 13
        activateWhisperer()
        repeat(stage) {
            assertTrue(manager.forcePhase(player, WhispererSpecial.SOUL_SIPHON.name))
            run.specialProgress = run.specialRequired
            manager.tick(player)
            advance(13)
            manager.tick(player)
        }
        assertEquals(100, clock.cycle)
        assertEquals(stage, phases.startedSpecials)
        assertEquals(Dt2Assets.WHISPERER_ACTIVE_ID, boss.visType.id)
    }

    private val Dt2NativeCombatFixture.phases: WhispererCombatPhases
        get() = assertNotNull(run.whispererCombatPhases)

    @AfterTest
    fun releaseMocks() {
        framework().clearInlineMocks()
    }

    companion object {
        @BeforeAll
        @JvmStatic
        fun cache() {
            if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
        }
    }
}

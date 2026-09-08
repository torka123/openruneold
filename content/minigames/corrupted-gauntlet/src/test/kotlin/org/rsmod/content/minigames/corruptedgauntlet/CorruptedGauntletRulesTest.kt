package org.rsmod.content.minigames.corruptedgauntlet

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CorruptedGauntletRulesTest {
    @Test
    fun `preparation lasts exactly seven minutes and thirty seconds`() {
        assertEquals(750, CorruptedGauntletConfig.PREPARATION_TICKS)
        assertEquals(750, CorruptedGauntletRules.remainingPreparationTicks(0))
        assertEquals(1, CorruptedGauntletRules.remainingPreparationTicks(749))
        assertEquals(0, CorruptedGauntletRules.remainingPreparationTicks(750))
        assertFalse(CorruptedGauntletRules.preparationExpired(749))
        assertTrue(CorruptedGauntletRules.preparationExpired(750))
    }

    @Test
    fun `hunllef attacks every five ticks and alternates style after four attacks`() {
        assertEquals(5, CorruptedGauntletConfig.HUNLLEF_ATTACK_INTERVAL)
        var state = CorruptedGauntletHunllefState()
        repeat(3) { state = CorruptedGauntletRules.afterHunllefAttack(state) }
        assertEquals(CorruptedGauntletCombatStyle.RANGED, state.outgoingStyle)
        assertEquals(3, state.attacksInStyle)
        state = CorruptedGauntletRules.afterHunllefAttack(state)
        assertEquals(CorruptedGauntletCombatStyle.MAGIC, state.outgoingStyle)
        assertEquals(0, state.attacksInStyle)
        repeat(4) { state = CorruptedGauntletRules.afterHunllefAttack(state) }
        assertEquals(CorruptedGauntletCombatStyle.RANGED, state.outgoingStyle)
    }

    @Test
    fun `initial protection is deterministic per seed and covers every combat style`() {
        repeat(128) { seed ->
            assertEquals(
                CorruptedGauntletRules.initialHunllefState(seed.toLong()),
                CorruptedGauntletRules.initialHunllefState(seed.toLong()),
            )
            assertEquals(
                CorruptedGauntletCombatStyle.RANGED,
                CorruptedGauntletRules.initialHunllefState(seed.toLong()).outgoingStyle,
            )
        }
        assertEquals(
            CorruptedGauntletCombatStyle.entries.toSet(),
            (0L until 128L).map(CorruptedGauntletRules::initialHunllefProtection).toSet(),
        )
    }

    @Test
    fun `protected attacks do not count and sixth valid attack changes protection`() {
        var state =
            CorruptedGauntletHunllefState(protectionStyle = CorruptedGauntletCombatStyle.RANGED)
        val ignored =
            CorruptedGauntletRules.afterValidPlayerAttack(
                state,
                CorruptedGauntletCombatStyle.RANGED,
            )
        assertSame(state, ignored)

        repeat(5) {
            state =
                CorruptedGauntletRules.afterValidPlayerAttack(
                    state,
                    CorruptedGauntletCombatStyle.MAGIC,
                )
        }
        assertEquals(CorruptedGauntletCombatStyle.RANGED, state.protectionStyle)
        assertEquals(5, state.validHitsTowardProtectionSwitch)
        state =
            CorruptedGauntletRules.afterValidPlayerAttack(state, CorruptedGauntletCombatStyle.MAGIC)
        assertEquals(CorruptedGauntletCombatStyle.MAGIC, state.protectionStyle)
        assertEquals(0, state.validHitsTowardProtectionSwitch)
    }

    @Test
    fun `tornado cadence count and lifetime follow reference phase rules`() {
        var state = CorruptedGauntletHunllefState()
        repeat(9) { state = CorruptedGauntletRules.afterHunllefAttack(state) }
        assertFalse(CorruptedGauntletRules.tornadoesDue(state))
        state = CorruptedGauntletRules.afterHunllefAttack(state)
        assertTrue(CorruptedGauntletRules.tornadoesDue(state))
        state = CorruptedGauntletRules.afterTornadoCast(state)
        assertFalse(CorruptedGauntletRules.tornadoesDue(state))
        assertEquals(2, CorruptedGauntletRules.tornadoCount(1_000))
        assertEquals(3, CorruptedGauntletRules.tornadoCount(666))
        assertEquals(4, CorruptedGauntletRules.tornadoCount(333))
        assertEquals(20, CorruptedGauntletConfig.HUNLLEF_TORNADO_LIFETIME)
    }

    @Test
    fun `tornado cast consumes an outgoing attack turn and can switch style`() {
        var state = CorruptedGauntletHunllefState()
        repeat(3) { state = CorruptedGauntletRules.afterHunllefAttack(state) }
        assertEquals(3, state.attacksInStyle)

        state = CorruptedGauntletRules.afterTornadoCast(state)

        assertEquals(CorruptedGauntletCombatStyle.MAGIC, state.outgoingStyle)
        assertEquals(0, state.attacksInStyle)
        assertEquals(0, state.attacksSinceTornadoes)
    }

    @Test
    fun `complete armour sets reduce correct-prayer and tornado damage`() {
        assertEquals(16, CorruptedGauntletRules.correctPrayerMaximumHit(0))
        assertEquals(13, CorruptedGauntletRules.correctPrayerMaximumHit(3))
        assertEquals(10, CorruptedGauntletRules.correctPrayerMaximumHit(6))
        assertEquals(8, CorruptedGauntletRules.correctPrayerMaximumHit(9))
        assertEquals(
            CorruptedGauntletTornadoDamage(15, 30),
            CorruptedGauntletRules.tornadoDamage(0),
        )
        assertEquals(
            CorruptedGauntletTornadoDamage(14, 25),
            CorruptedGauntletRules.tornadoDamage(3),
        )
        assertEquals(
            CorruptedGauntletTornadoDamage(10, 20),
            CorruptedGauntletRules.tornadoDamage(6),
        )
        assertEquals(CorruptedGauntletTornadoDamage(8, 15), CorruptedGauntletRules.tornadoDamage(9))
        assertEquals(9, CorruptedGauntletRules.armourTierSum(3, 3, 3))
    }

    @Test
    fun `floor tiles warn then damage then expire on named tick windows`() {
        assertFalse(CorruptedGauntletRules.floorDamagesPlayer(4, playerOnTile = true))
        assertTrue(CorruptedGauntletRules.floorDamagesPlayer(5, playerOnTile = true))
        assertTrue(CorruptedGauntletRules.floorDamagesPlayer(10, playerOnTile = true))
        assertFalse(CorruptedGauntletRules.floorDamagesPlayer(11, playerOnTile = true))
        assertFalse(CorruptedGauntletRules.floorDamagesPlayer(7, playerOnTile = false))
        assertTrue(CorruptedGauntletRules.floorExpired(11))
    }

    @Test
    fun `phase floor schedules accelerate without overlapping patterns`() {
        val first = CorruptedGauntletRules.floorSchedule(1_000)
        val second = CorruptedGauntletRules.floorSchedule(666)
        val final = CorruptedGauntletRules.floorSchedule(333)

        assertEquals(
            CorruptedGauntletHunllefPhase.FIRST,
            CorruptedGauntletRules.hunllefPhase(1_000),
        )
        assertEquals(CorruptedGauntletHunllefPhase.SECOND, CorruptedGauntletRules.hunllefPhase(666))
        assertEquals(CorruptedGauntletHunllefPhase.FINAL, CorruptedGauntletRules.hunllefPhase(333))
        assertEquals(
            listOf(24, 16, 11),
            listOf(first.cadenceTicks, second.cadenceTicks, final.cadenceTicks),
        )
        listOf(first, second, final).forEach { schedule ->
            assertEquals(5, schedule.warningTicks)
            assertEquals(6, schedule.activeTicks)
            assertTrue(schedule.cadenceTicks >= schedule.lifetimeTicks)
            assertFalse(
                CorruptedGauntletRules.floorDamagesPlayer(schedule.warningTicks - 1, true, schedule)
            )
            assertTrue(
                CorruptedGauntletRules.floorDamagesPlayer(schedule.warningTicks, true, schedule)
            )
            assertFalse(
                CorruptedGauntletRules.floorDamagesPlayer(schedule.lifetimeTicks, true, schedule)
            )
            assertTrue(CorruptedGauntletRules.floorExpired(schedule.lifetimeTicks, schedule))
        }
    }

    @Test
    fun `resource yields and monster drop contracts remain centralized`() {
        assertEquals(3, CorruptedGauntletResource.ORE.yield)
        assertEquals(3, CorruptedGauntletResource.BARK.yield)
        assertEquals(3, CorruptedGauntletResource.LINUM.yield)
        assertEquals(4, CorruptedGauntletResource.FISH.yield)
        assertEquals(1, CorruptedGauntletResource.GRYM.yield)
        assertEquals(
            10..37,
            CorruptedGauntletRules.monsterShardRange(CorruptedGauntletMonsterTier.WEAK),
        )
        assertEquals(
            50..105,
            CorruptedGauntletRules.monsterShardRange(CorruptedGauntletMonsterTier.STRONG),
        )
        assertTrue(
            CorruptedGauntletRules.guaranteesWeaponFrame(
                CorruptedGauntletMonsterTier.WEAK,
                2,
                false,
            )
        )
        assertTrue(
            CorruptedGauntletRules.guaranteesWeaponFrame(
                CorruptedGauntletMonsterTier.STRONG,
                0,
                false,
            )
        )
        assertFalse(
            CorruptedGauntletRules.guaranteesWeaponFrame(
                CorruptedGauntletMonsterTier.STRONG,
                0,
                true,
            )
        )
    }

    @Test
    fun `duplicate demi component reroutes to an unowned component`() {
        val spike = CorruptedGauntletConfig.Obj.SPIKE
        val orb = CorruptedGauntletConfig.Obj.ORB
        val bowstring = CorruptedGauntletConfig.Obj.BOWSTRING
        assertEquals(
            spike,
            CorruptedGauntletRules.demiBossComponent(CorruptedGauntletDemiBoss.BEAR, emptySet(), 0),
        )
        assertEquals(
            orb,
            CorruptedGauntletRules.demiBossComponent(
                CorruptedGauntletDemiBoss.BEAR,
                setOf(spike),
                0,
            ),
        )
        assertEquals(
            bowstring,
            CorruptedGauntletRules.demiBossComponent(
                CorruptedGauntletDemiBoss.BEAR,
                setOf(spike, orb),
                0,
            ),
        )
    }

    @Test
    fun `corrupted singing bowl recipes encode each progression tier`() {
        val recipes = CorruptedGauntletCraftingRules.byOutput
        assertEquals(22, recipes.size)
        assertEquals(10, recipes.getValue(CorruptedGauntletConfig.Obj.VIAL).shardCost)
        assertEquals(40, recipes.getValue(CorruptedGauntletConfig.Obj.BASIC_BODY).shardCost)
        assertEquals(60, recipes.getValue(CorruptedGauntletConfig.Obj.ATTUNED_BODY).shardCost)
        assertEquals(
            3,
            recipes.getValue(CorruptedGauntletConfig.Obj.ATTUNED_BODY).ingredients.count {
                it.amount == 2
            },
        )
        assertEquals(80, recipes.getValue(CorruptedGauntletConfig.Obj.PERFECTED_BODY).shardCost)
        assertEquals(0, recipes.getValue(CorruptedGauntletConfig.Obj.PERFECTED_BOW).shardCost)
        assertTrue(
            recipes.getValue(CorruptedGauntletConfig.Obj.PERFECTED_BOW).ingredients.any {
                it.itemId == CorruptedGauntletConfig.Obj.BOWSTRING
            }
        )
        val inventory =
            mapOf(
                CorruptedGauntletConfig.Obj.SHARDS to 60,
                CorruptedGauntletConfig.Obj.BASIC_STAFF to 1,
            )
        assertTrue(
            CorruptedGauntletCraftingRules.canCraft(
                recipes.getValue(CorruptedGauntletConfig.Obj.ATTUNED_STAFF),
                inventory,
            )
        )
        assertFalse(
            CorruptedGauntletCraftingRules.canCraft(
                recipes.getValue(CorruptedGauntletConfig.Obj.ATTUNED_BOW),
                inventory,
            )
        )
    }

    @Test
    fun `successful rewards require a living contributor and each chest token is one shot`() {
        assertTrue(CorruptedGauntletRewardRules.eligibleForCompletionRewards(true, false, 1))
        assertFalse(CorruptedGauntletRewardRules.eligibleForCompletionRewards(true, true, 1))
        assertFalse(CorruptedGauntletRewardRules.eligibleForCompletionRewards(true, false, 0))
        assertFalse(CorruptedGauntletRewardRules.eligibleForCompletionRewards(false, false, 1_000))
        assertTrue(CorruptedGauntletRewardRules.canClaim(123L, emptySet()))
        assertFalse(CorruptedGauntletRewardRules.canClaim(123L, setOf(123L)))
        assertFalse(CorruptedGauntletRewardRules.canClaim(null, emptySet()))
    }

    @Test
    fun `completion reward rates and shard range match corrupted gauntlet`() {
        assertEquals(3, CorruptedGauntletConfig.COMPLETION_COMMON_ROLLS)
        assertEquals((7..12).toList(), (0..5).map(CorruptedGauntletRewardRules::completionShards))
        CorruptedGauntletUniqueReward.entries.forEach { reward ->
            assertTrue(CorruptedGauntletRewardRules.uniqueAwarded(reward, 0))
            assertFalse(CorruptedGauntletRewardRules.uniqueAwarded(reward, reward.denominator - 1))
            assertTrue(CorruptedGauntletRewardRules.uniqueAwarded(reward, reward.denominator))
        }
        assertEquals(50, CorruptedGauntletUniqueReward.CRYSTAL_WEAPON_SEED.denominator)
        assertEquals(50, CorruptedGauntletUniqueReward.CRYSTAL_ARMOUR_SEED.denominator)
        assertEquals(400, CorruptedGauntletUniqueReward.ENHANCED_CRYSTAL_WEAPON_SEED.denominator)
        assertEquals(800, CorruptedGauntletUniqueReward.YOUNGLLEF.denominator)
        assertEquals(20, CorruptedGauntletUniqueReward.ELITE_CLUE_SCROLL.denominator)
    }
}

package org.rsmod.content.minigames.cox

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.rsmod.map.CoordGrid

class CoxRulesTest {
    @Test
    fun deterministicNormalLayoutsAreConnectedAndEndAtOlm() {
        repeat(1_000) { seed ->
            val first = CoxLayoutGenerator.generate(seed.toLong(), RaidDifficulty.NORMAL)
            val second = CoxLayoutGenerator.generate(seed.toLong(), RaidDifficulty.NORMAL)
            assertEquals(first, second)
            assertTrue(CoxLayoutGenerator.isConnected(first))
            assertEquals(CoxRoom.START, first.placements.first().room)
            assertEquals(CoxRoom.OLM, first.placements.last().room)
            assertEquals(
                first.placements.map { it.room }.size,
                first.placements.map { it.room }.toSet().size,
            )
        }
        assertNotEquals(
            CoxLayoutGenerator.generate(1, RaidDifficulty.NORMAL),
            CoxLayoutGenerator.generate(2, RaidDifficulty.NORMAL),
        )
    }

    @Test
    fun challengeLayoutIsFixedCompleteAndConnected() {
        val first = CoxLayoutGenerator.generate(1, RaidDifficulty.CHALLENGE)
        val second = CoxLayoutGenerator.generate(999, RaidDifficulty.CHALLENGE)
        assertEquals(first.placements.map { it.room }, second.placements.map { it.room })
        assertEquals(CoxRoom.entries.toSet(), first.placements.map { it.room }.toSet())
        assertTrue(CoxLayoutGenerator.isConnected(first))
    }

    @Test
    fun scalingHasExpectedPartyAndChallengeBoundaries() {
        assertEquals(100, CoxScaling.hitpoints(100, 1, RaidDifficulty.NORMAL))
        assertEquals(250, CoxScaling.hitpoints(100, 4, RaidDifficulty.NORMAL))
        assertEquals(375, CoxScaling.hitpoints(100, 4, RaidDifficulty.CHALLENGE))
    }

    @Test
    fun combatPointsMatchTheFullAndOlmOnlyTargetsWithoutNegativeOrOverflowedAwards() {
        assertEquals(0, CoxPoints.forCombatDamage(-1, olmOnly = false))
        assertEquals(0, CoxPoints.forCombatDamage(0, olmOnly = true))
        assertEquals(8, CoxPoints.forCombatDamage(1, olmOnly = false))
        assertEquals(10, CoxPoints.forCombatDamage(1, olmOnly = true))
        val pointableFullRaidDamage = 7_095
        val tightropePoints = 1_200
        val fullRaidPoints =
            CoxPoints.forCombatDamage(pointableFullRaidDamage, olmOnly = false) + tightropePoints
        assertTrue(fullRaidPoints in 55_000..60_000)
        assertTrue(CoxPoints.forCombatDamage(3_200, olmOnly = true) in 30_000..33_000)
        assertEquals(Int.MAX_VALUE, CoxPoints.forCombatDamage(Int.MAX_VALUE, olmOnly = true))
    }

    @Test
    fun rewardChanceAndWeightsMatchTheDonorRaid() {
        assertEquals(0.0, CoxRewardRules.uniqueChance(0))
        assertEquals(0.01, CoxRewardRules.uniqueChance(8_676), 0.0000001)
        assertEquals(CoxRewardRules.MAX_UNIQUE_CHANCE, CoxRewardRules.uniqueChance(Int.MAX_VALUE))
        assertEquals(74, CoxRewardRules.donorUniqueTable(olmOnly = false).sumOf { it.weight })
        assertEquals(147, CoxRewardRules.donorUniqueTable(olmOnly = true).sumOf { it.weight })
        assertEquals(30_029, CoxLocs.REWARD_BEAM_BASIC)
        assertEquals(30_030, CoxLocs.REWARD_BEAM_UNIQUE)
        assertEquals(3, CoxRewardBeamRules.PRIMARY_SHAPE)
        assertEquals(10, CoxRewardBeamRules.TRIGGER_SHAPE)
        assertEquals(0, CoxRewardBeamRules.ANGLE)
        assertEquals(1, CoxRewardBeamRules.TRIGGER_LIFETIME_TICKS)
        assertEquals(
            CoordGrid(100, 201, 1),
            CoxRewardBeamRules.triggerTile(CoordGrid(100, 200, 1)),
        )
        assertEquals(21_034, CoxRewardRules.uniqueAtTicket(0).objId)
        assertEquals(20_997, CoxRewardRules.uniqueAtTicket(73).objId)
        assertTrue(CoxRewardRules.uniqueTable.any { it.objId == 24_670 })
        assertFalse(CoxRewardRules.uniqueTable.any { it.objId == 24_466 })
        assertEquals(0.10, CoxRewardRules.donorPurpleChance(18_000, olmOnly = false), 0.0000001)
        assertEquals(
            CoxRewardRules.DONOR_FULL_PURPLE_CAP,
            CoxRewardRules.donorPurpleChance(Int.MAX_VALUE, olmOnly = false),
        )
        assertEquals(
            CoxRewardRules.DONOR_OLM_PURPLE_CAP,
            CoxRewardRules.donorPurpleChance(Int.MAX_VALUE, olmOnly = true),
        )
        assertEquals(6_554, CoxRewardRules.donorCommonAmount(Int.MAX_VALUE, 20))
        assertEquals(1, CoxRewardRules.donorCommonAmount(0, 131_071))
    }

    @Test
    fun excessPointsCreateAtMostThreeIndependentUniqueRolls() {
        assertEquals(
            listOf(CoxRewardRules.MAX_UNIQUE_CHANCE),
            CoxRewardRules.uniqueRollChances(570_000),
        )
        val chances = CoxRewardRules.uniqueRollChances(855_000)
        assertEquals(2, chances.size)
        assertEquals(CoxRewardRules.MAX_UNIQUE_CHANCE, chances[0])
        assertEquals(CoxRewardRules.uniqueChance(285_000), chances[1])
        assertEquals(3, CoxRewardRules.uniqueRollChances(Int.MAX_VALUE).size)
    }

    @Test
    fun challengeTimedRewardThresholdScalesWithPartySize() {
        assertEquals(70 * 100, CoxChallengeRules.targetTicks(1))
        assertEquals(65 * 100, CoxChallengeRules.targetTicks(2))
        assertEquals(50 * 100, CoxChallengeRules.targetTicks(3))
        assertEquals(42 * 100, CoxChallengeRules.targetTicks(10))
        assertEquals(80 * 100, CoxChallengeRules.targetTicks(24))
    }

    @Test
    fun olmRequiresThreeHandSetsBeforeTheFinalHead() {
        val olm = GreatOlmState(1, RaidDifficulty.NORMAL)
        olm.activate()
        repeat(3) { set ->
            assertTrue(olm.canDamage(GreatOlmTarget.LEFT_HAND))
            assertTrue(olm.canDamage(GreatOlmTarget.RIGHT_HAND))
            assertFalse(olm.canDamage(GreatOlmTarget.HEAD))
            assertEquals(
                GreatOlmTransition.HAND_DEFEATED,
                olm.defeat(GreatOlmTarget.LEFT_HAND),
            )
            assertFalse(olm.canDamage(GreatOlmTarget.LEFT_HAND))
            val expected =
                if (set < 2) GreatOlmTransition.NEXT_PHASE else GreatOlmTransition.FINAL_HEAD
            assertEquals(expected, olm.defeat(GreatOlmTarget.RIGHT_HAND))
            assertTrue(olm.isAlive(GreatOlmTarget.HEAD))
        }
        assertEquals(GreatOlmPhase.FINAL_HEAD, olm.phase)
        assertEquals(7345, GreatOlmVisuals.headAttackSequence(enraged = false))
        assertEquals(7371, GreatOlmVisuals.headAttackSequence(enraged = true))
        assertTrue(olm.canDamage(GreatOlmTarget.HEAD))
        assertFalse(olm.canDamage(GreatOlmTarget.LEFT_HAND))
        assertFalse(olm.canDamage(GreatOlmTarget.RIGHT_HAND))
        assertEquals(GreatOlmTransition.RAID_COMPLETE, olm.defeat(GreatOlmTarget.HEAD))
        assertEquals(GreatOlmPhase.DEFEATED, olm.phase)
    }

    @Test
    fun olmHandsUseTheirRetreatAndRiseAnimationsBetweenSets() {
        val left = GreatOlmVisuals[GreatOlmTarget.LEFT_HAND]
        val right = GreatOlmVisuals[GreatOlmTarget.RIGHT_HAND]
        assertEquals(7354, left.riseSequence)
        assertEquals(7370, left.retreatSequence)
        assertEquals(7350, right.riseSequence)
        assertEquals(7352, right.retreatSequence)
        assertEquals(101, GreatOlmVisualTiming.riseAnimationCycle(100))
        assertEquals(106, GreatOlmVisualTiming.riseIdleCycle(100))
        assertEquals(7356, GreatOlmVisuals.leftHandSpecialSequence(GreatOlmSpecial.CRYSTALS))
        assertEquals(7358, GreatOlmVisuals.leftHandSpecialSequence(GreatOlmSpecial.LIGHTNING))
        assertEquals(7359, GreatOlmVisuals.leftHandSpecialSequence(GreatOlmSpecial.PORTALS))
    }

    @Test
    fun olmBasicAttacksRetainTheirStyleUntilTheDonorSwitchRollSucceeds() {
        val cycle = GreatOlmBasicAttackCycle()
        assertEquals(GreatOlmBasicAttackStyle.MAGIC, cycle.current)
        cycle.advance(switchStyle = false)
        assertEquals(GreatOlmBasicAttackStyle.MAGIC, cycle.current)
        cycle.advance(switchStyle = true)
        assertEquals(GreatOlmBasicAttackStyle.RANGED, cycle.current)
        cycle.advance(switchStyle = true)
        assertEquals(GreatOlmBasicAttackStyle.MAGIC, cycle.current)
    }

    @Test
    fun olmUsesOneFourTickRotationWithoutStackingSpecialsOverBasics() {
        val cycle = GreatOlmAttackCycle()
        val observed = buildList {
            repeat(6) {
                add(cycle.current)
                cycle.advance()
            }
        }
        assertEquals(
            listOf(
                GreatOlmAttackSlot.BASIC,
                GreatOlmAttackSlot.BASIC,
                GreatOlmAttackSlot.SPECIAL,
                GreatOlmAttackSlot.BASIC,
                GreatOlmAttackSlot.BASIC,
                GreatOlmAttackSlot.EMPTY,
            ),
            observed,
        )
        assertEquals(GreatOlmAttackSlot.BASIC, cycle.current)
    }

    @Test
    fun olmNormalStatsAreNotPreScaledToChallengeMode() {
        val meleeHand = checkNotNull(coxBaseStats(CoxNpcs.OLM_LEFT_HAND))
        val mageHand = checkNotNull(coxBaseStats(CoxNpcs.OLM_RIGHT_HAND))
        val head = checkNotNull(coxBaseStats(CoxNpcs.OLM_HEAD))
        assertEquals(175, meleeHand.defence)
        assertEquals(175, mageHand.defence)
        assertEquals(87, mageHand.magic)
        assertEquals(150, head.defence)
        assertEquals(262, CoxScaling.combatStat(meleeHand.defence, 1, RaidDifficulty.CHALLENGE))
    }

    @Test
    fun olmFinalHandCanReviveWhileTheOtherHandStillLives() {
        val olm = GreatOlmState(1, RaidDifficulty.NORMAL)
        olm.activate()
        repeat(2) {
            olm.defeat(GreatOlmTarget.LEFT_HAND)
            olm.defeat(GreatOlmTarget.RIGHT_HAND)
        }
        assertEquals(GreatOlmPhase.PHASE_THREE, olm.phase)
        olm.defeat(GreatOlmTarget.LEFT_HAND)
        assertTrue(olm.canRevive(GreatOlmTarget.LEFT_HAND))
        olm.revive(GreatOlmTarget.LEFT_HAND)
        assertTrue(olm.canDamage(GreatOlmTarget.LEFT_HAND))
        assertFalse(olm.canRevive(GreatOlmTarget.LEFT_HAND))
    }

    @Test
    fun olmBarrierUsesTheDonorTwoTileNorthwardPass() {
        val start = CoordGrid(3200, 3200, 0)
        assertEquals(CoordGrid(3200, 3202, 0), olmBarrierDestination(start))
        assertTrue(CoxLocs.OLM_BARRIER in CoxLocs.all)
    }

    @Test
    fun largeMuttadileUsesTheDonorSixTileSevenTickEmergence() {
        val start = CoordGrid(3200, 3200, 0)
        val destination = MuttadileEmergence.destination(start)
        assertEquals(CoordGrid(3194, 3200, 0), destination)
        assertEquals(7, MuttadileEmergence.DURATION_TICKS)
        var step = start
        repeat(6) { step = MuttadileEmergence.nextStep(step, destination) }
        assertEquals(destination, step)
        assertEquals(7423, MuttadileEmergence.SEQUENCE)
    }

    @Test
    fun muttadilesUseTheirOwnOsrsPartyAndChallengeScaling() {
        assertEquals(250, MuttadileScaling.hitpoints(1, RaidDifficulty.NORMAL))
        assertEquals(500, MuttadileScaling.hitpoints(2, RaidDifficulty.NORMAL))
        assertEquals(375, MuttadileScaling.hitpoints(1, RaidDifficulty.CHALLENGE))
        assertEquals(243, MuttadileScaling.combatStat(225, 2, RaidDifficulty.NORMAL))
        assertEquals(209, MuttadileScaling.defenceStat(207, 2, RaidDifficulty.NORMAL))
        assertEquals(1_000, MuttadileScaling.combatDamageCap(2, RaidDifficulty.NORMAL))
        assertEquals(25, MuttadileScaling.pointableDamage(975, 100, 1_000))
        assertEquals(0, MuttadileScaling.pointableDamage(1_000, 100, 1_000))
    }

    @Test
    fun meatTreeUsesCurrentOsrsChoppingEatingAndPointCapRules() {
        assertEquals(100, MuttadileMeatTreeRules.health(1))
        assertEquals(500, MuttadileMeatTreeRules.health(99))
        assertEquals(20, MuttadileMeatTreeRules.accuracyPercent(1))
        assertEquals(78, MuttadileMeatTreeRules.accuracyPercent(99))
        assertEquals(42..52, MuttadileMeatTreeRules.damageRange(98))
        assertEquals(42..52, MuttadileMeatTreeRules.damageRange(99))
        assertTrue(MuttadileMeatTreeRules.shouldEat(125, 250))
        assertFalse(MuttadileMeatTreeRules.shouldEat(126, 250))
        assertEquals(100, MuttadileMeatTreeRules.healAmount(250))
        assertEquals(25, MuttadileMeatTreeRules.GIVE_UP_TICKS)
        assertEquals(5, MuttadileMeatTreeRules.CHOP_INTERVAL_TICKS)
        assertEquals(30012, MuttadileMeatTreeRules.LIVE_LOC_ID)
        assertEquals(30013, MuttadileMeatTreeRules.DEAD_LOC_ID)
        assertEquals(7564, MuttadileMeatTreeRules.NPC_ID)
        assertEquals("obj.bronze_axe", MuttadileMeatTreeRules.BRONZE_AXE)
        assertTrue(CoxNpcs.MUTTADILE_MEAT_TREE !in CoxNpcs.all)
        assertTrue(CoxNpcs.MUTTADILE_MEAT_TREE in CoxNpcs.assets)
        val tree = CoordGrid(100, 200, 1)
        assertEquals(
            CoordGrid(102, 201, 1),
            MuttadileMeatTreeRules.feedingTile(CoordGrid(110, 201, 1), 5, tree),
        )
        assertEquals(
            CoordGrid(95, 198, 1),
            MuttadileMeatTreeRules.feedingTile(CoordGrid(90, 198, 1), 5, tree),
        )
        assertEquals(1, MuttadileMeatTreeRules.feedingTile(CoordGrid(110, 201, 1), 5, tree).level)
    }

    @Test
    fun deathlyRangerAndMageUseHumanDefendAndDeathSequences() {
        assertEquals(424, DeathlyNpcAnimations.DEFEND_SEQUENCE)
        assertEquals(836, DeathlyNpcAnimations.DEATH_SEQUENCE)
        assertTrue(DeathlyNpcAnimations.appliesTo(CoxNpcs.DEATHLY_RANGER))
        assertTrue(DeathlyNpcAnimations.appliesTo(CoxNpcs.DEATHLY_MAGE))
        assertEquals(null, DeathlyNpcAnimations.deathSequence(CoxNpcs.MUTTADILE_SMALL))
    }

    @Test
    fun skeletalMysticsUseTheirDonorAnimationFamilyAndFiveTickCastingSpeed() {
        assertEquals(5523, SkeletalMysticAnimations.ATTACK_SEQUENCE)
        assertEquals(5489, SkeletalMysticAnimations.DEFEND_SEQUENCE)
        assertEquals(5527, SkeletalMysticAnimations.DEATH_SEQUENCE)
        assertTrue(SkeletalMysticAnimations.appliesTo(CoxNpcs.SKELETAL_MYSTIC))
        assertTrue(SkeletalMysticAnimations.appliesTo(CoxNpcs.SKELETAL_MYSTIC_ALT))
        assertTrue(SkeletalMysticAnimations.appliesTo(CoxNpcs.SKELETAL_MYSTIC_ALT_2))
        assertEquals(5, combatProfile(CoxNpcs.SKELETAL_MYSTIC).attackRate)
    }

    @Test
    fun guardiansRoomProvidesAReachableFallbackIronPickaxe() {
        assertEquals("obj.iron_pickaxe", GuardianRoomSupplies.IRON_PICKAXE)
        assertEquals(23, GuardianRoomSupplies.LOCAL_X)
        assertEquals(45, GuardianRoomSupplies.LOCAL_Z)
        CoxInstanceLayout.local(
            CoxDonorArea.FLOOR_TWO_SE,
            GuardianRoomSupplies.LOCAL_X,
            GuardianRoomSupplies.LOCAL_Z,
        )
    }

    @Test
    fun vanguardsUseOneCombatTriangleWeaknessAndTheirOwnAnimationFamilies() {
        val melee = VanguardCombatRules.getValue(CoxNpcs.VANGUARD_MELEE)
        val ranged = VanguardCombatRules.getValue(CoxNpcs.VANGUARD_RANGED)
        val magic = VanguardCombatRules.getValue(CoxNpcs.VANGUARD_MAGIC)
        assertEquals(CoxCombatStyle.MAGIC, melee.weakness)
        assertEquals(CoxCombatStyle.MELEE, ranged.weakness)
        assertEquals(CoxCombatStyle.RANGED, magic.weakness)
        assertEquals(listOf(7441, 7440, 7442), listOf(melee.attackSequence, melee.defendSequence, melee.deathSequence))
        assertEquals(listOf(7446, 7445, 7447), listOf(ranged.attackSequence, ranged.defendSequence, ranged.deathSequence))
        assertEquals(listOf(7436, 7435, 7437), listOf(magic.attackSequence, magic.defendSequence, magic.deathSequence))
        assertEquals(9, VanguardCombatRules.sequenceIds.size)
    }

    @Test
    fun tightropeScalesDeathlyNpcCountAndMaxHitByPartySize() {
        assertEquals(listOf(2, 2, 3, 4, 5), (1..5).map(DeathlyNpcCombatRules::spawnsPerStyle))
        assertEquals(listOf(28, 28, 32, 34, 36), (1..5).map(DeathlyNpcCombatRules::maxHit))
        assertEquals(5, DeathlyNpcCombatRules.spawnsPerStyle(8))
        assertEquals(36, DeathlyNpcCombatRules.maxHit(8))
        assertEquals(5, DeathlyNpcCombatRules.MAX_SPAWNS_PER_STYLE)
        assertEquals(5, DeathlyNpcCombatRules.RANGER_SPAWNS.size)
        assertEquals(5, DeathlyNpcCombatRules.MAGE_SPAWNS.size)
        assertEquals(5, DeathlyNpcCombatRules.RANGER_SPAWNS.toSet().size)
        assertEquals(5, DeathlyNpcCombatRules.MAGE_SPAWNS.toSet().size)
        assertEquals(50, DeathlyNpcCombatRules.PROTECTION_PRAYER_REDUCTION_PERCENT)
        assertEquals(1_200, DeathlyNpcCombatRules.PUZZLE_DOOR_POINTS)
        assertTrue(DeathlyNpcCombatRules.appliesTo(CoxNpcs.DEATHLY_RANGER))
        assertTrue(DeathlyNpcCombatRules.appliesTo(CoxNpcs.DEATHLY_MAGE))

        assertEquals(5, combatProfile(CoxNpcs.VANGUARD_MELEE).attackRate)
        assertEquals(5, combatProfile(CoxNpcs.VANGUARD_RANGED).attackRate)
        assertEquals(5, combatProfile(CoxNpcs.VANGUARD_MAGIC).attackRate)
        val ranger = combatProfile(CoxNpcs.DEATHLY_RANGER)
        val mage = combatProfile(CoxNpcs.DEATHLY_MAGE)
        assertEquals(28, ranger.maxHit)
        assertEquals(28, mage.maxHit)
        assertEquals(5, ranger.attackRate)
        assertEquals(5, mage.attackRate)
    }

    @Test
    fun muttadileFightUsesTwoStagesAndStyleSpecificAttacks() {
        assertEquals(
            MuttadileAttackStyle.RANGED,
            MuttadileCombatRules.smallAttack(inMeleeRange = false, roll = 1),
        )
        assertEquals(
            MuttadileAttackStyle.MELEE,
            MuttadileCombatRules.smallAttack(inMeleeRange = true, roll = 1),
        )
        assertEquals(MuttadileAttackStyle.MELEE, MuttadileCombatRules.largeCloseAttack(0))
        assertEquals(MuttadileAttackStyle.STOMP, MuttadileCombatRules.largeCloseAttack(1))
        assertEquals(
            CoxCombatStyle.MAGIC,
            combatProfile(CoxNpcs.MUTTADILE_LARGE_UNDERWATER).style,
        )
    }

    @Test
    fun largeMuttadileKeepsDistanceStyleForAtLeastThreeAttacks() {
        val cycle = MuttadileDistanceAttackCycle()
        assertEquals(MuttadileAttackStyle.MAGIC, cycle.next(0))
        assertEquals(MuttadileAttackStyle.MAGIC, cycle.next(1))
        assertEquals(MuttadileAttackStyle.MAGIC, cycle.next(1))
        assertEquals(MuttadileAttackStyle.RANGED, cycle.next(1))
    }

    @Test
    fun coxEntranceCandidatesStartBesideTheDoorTowardTheLobby() {
        val entrance = CoordGrid(1245, 3560, 0)
        val lobby = CoordGrid(1254, 3566, 0)
        val candidates = coxEntranceAdjacentCandidates(entrance, lobby)
        assertEquals(CoordGrid(1246, 3561, 0), candidates.first())
        assertEquals(80, candidates.size)
        assertEquals(candidates.size, candidates.toSet().size)
    }

    @Test
    fun olmVisualsUseSeparateRiseIdleAndDormantObjects() {
        GreatOlmTarget.entries.forEach { target ->
            val visual = GreatOlmVisuals[target]
            assertNotEquals(visual.riseLoc, visual.idleLoc)
            assertNotEquals(visual.idleLoc, visual.dormantLoc)
            assertTrue(visual.riseSequence > 0)
        }
        assertEquals(9, GreatOlmVisuals.locIds.size)
    }

    @Test
    fun olmSpecialsRequireALiveAssignedHeadAndArenaTarget() {
        assertTrue(
            canQueueOlmSpecial(sourceAssigned = true, sourceHitpoints = 1, arenaPlayerCount = 1)
        )
        assertFalse(
            canQueueOlmSpecial(sourceAssigned = false, sourceHitpoints = 1, arenaPlayerCount = 1)
        )
        assertFalse(
            canQueueOlmSpecial(sourceAssigned = true, sourceHitpoints = 0, arenaPlayerCount = 1)
        )
        assertFalse(
            canQueueOlmSpecial(sourceAssigned = true, sourceHitpoints = 1, arenaPlayerCount = 0)
        )
    }

    @Test
    fun olmCrystalBurstUsesRealWarningAndOnlyHitsPlayersWhoStayMarked() {
        assertEquals(30033, CoxLocs.OLM_CRYSTAL_WARNING)
        assertEquals(30034, CoxLocs.OLM_CRYSTAL_ERUPTION)
        assertTrue(CoxLocs.OLM_CRYSTAL_WARNING in CoxLocs.all)
        assertTrue(CoxLocs.OLM_CRYSTAL_ERUPTION in CoxLocs.all)
        assertEquals(3, GreatOlmCrystalRules.BURST_WARNING_TICKS)
        assertEquals(1, GreatOlmCrystalRules.BURST_ERUPTION_TICKS)
        val marked = CoordGrid(3200, 3200, 0)
        assertTrue(GreatOlmCrystalRules.burstHits(marked, marked))
        assertFalse(GreatOlmCrystalRules.burstHits(marked, marked.translate(1, 0)))
    }

    @Test
    fun olmFallingCrystalsUseDonorAssetsArenaAndThreeByThreeDamage() {
        assertEquals(setOf(1357, 1447, 1358), GreatOlmCrystalRules.spotanimIds)
        assertEquals(2, GreatOlmCrystalRules.FALLING_INTERVAL_TICKS)
        assertEquals(4, GreatOlmCrystalRules.FALLING_TARGETED_INTERVAL_TICKS)
        assertEquals(16..25, GreatOlmCrystalRules.fallingDamageRange(0))
        assertEquals(10..16, GreatOlmCrystalRules.fallingDamageRange(1))
        assertEquals(null, GreatOlmCrystalRules.fallingDamageRange(2))
        assertTrue(GreatOlmCrystalRules.isTargetedFallingWave(0))
        assertFalse(GreatOlmCrystalRules.isTargetedFallingWave(1))
        assertTrue(GreatOlmCrystalRules.isTargetedFallingWave(2))
        val target = CoordGrid(3200, 3200, 0)
        assertEquals(target.translate(1, 0), GreatOlmCrystalRules.fallingSource(target, true))
        assertEquals(target.translate(0, 1), GreatOlmCrystalRules.fallingSource(target, false))
        assertEquals(28..37, GreatOlmCrystalRules.ARENA_MIN_LOCAL_X..GreatOlmCrystalRules.ARENA_MAX_LOCAL_X)
        assertEquals(35..51, GreatOlmCrystalRules.ARENA_MIN_LOCAL_Z..GreatOlmCrystalRules.ARENA_MAX_LOCAL_Z)
    }}

package org.rsmod.content.minigames.toa

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.rsmod.api.config.refs.done.hitmark_groups
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.LocAngle

class ToaWardensEncounterTest {
    @Test
    fun `crystal teleports across the Wardens threshold using the packet-audited effects`() {
        assertEquals(45579, ToaLocs.WARDENS_CRYSTAL)
        assertEquals(
            org.rsmod.api.instances.RegionLocal(1, 59, 80, 31, 56),
            ToaInstanceLayout.local(ToaRoom.WARDENS),
        )
        assertEquals(
            org.rsmod.api.instances.RegionLocal(1, 59, 80, 32, 46),
            ToaInstanceLayout.fightLocal(ToaRoom.WARDENS),
        )
        assertEquals(32, WardensEntryLifecycle.CRYSTAL_LOCAL_X)
        assertEquals(52, WardensEntryLifecycle.CRYSTAL_LOCAL_Z)
        assertEquals(409, WardensEntryLifecycle.PLAYER_SPOTANIM)
        assertEquals(198, WardensEntryLifecycle.TELEPORT_SOUND)
        assertTrue(WardensEntryLifecycle.FIGHT_LOCAL_Z < WardensEntryLifecycle.CRYSTAL_LOCAL_Z)
    }

    @Test
    fun `phase one projectile timing accepts the first lane's zero tick launch delay`() {
        assertEquals(0, ToaProjectileTiming.clientCyclesForServerDelay(0))
        assertEquals(0, ToaProjectileTiming.clientCyclesForServerDelay(1))
        assertEquals(30, ToaProjectileTiming.clientCyclesForServerDelay(2))
        assertEquals(90, ToaProjectileTiming.clientCyclesForServerTicks(4))
        assertFailsWith<IllegalArgumentException> {
            ToaProjectileTiming.clientCyclesForServerDelay(-1)
        }
    }

    @Test
    fun `phase one projectile segments meet without an invisible turn`() {
        val rules = WardensRuleset.MAXIMUM
        val lane = WardenPhaseOneLayout.energyStreamLane(WardenGod.ELIDINIS, 1)
        val timeline = WardenPhaseOneProjectileTiming.timeline(lane, rules)
        assertEquals(0, timeline.trunkStartCycles)
        assertEquals(30, timeline.turnStartCycles)
        assertEquals(120, timeline.laneStartCycles)
        assertEquals(420, timeline.laneEndCycles)
        assertEquals(
            timeline.laneStartCycles,
            timeline.turnStartCycles +
                ToaProjectileTiming.clientCyclesForServerTicks(rules.phaseOneEnergyTurnTicks),
        )
        assertEquals(
            timeline.laneEndCycles,
            timeline.trunkStartCycles +
                ToaProjectileTiming.clientCyclesForServerTicks(rules.phaseOneEnergyTravelTicks),
        )
    }

    @Test
    fun `phase one actors use the two marked Warden circle centers`() {
        assertEquals(WardenPhaseOneLayout.ARENA_CENTER_X, WardenPhaseOneLayout.obeliskCenterX())
        assertEquals(WardenPhaseOneLayout.ARENA_CENTER_Z, WardenPhaseOneLayout.obeliskCenterZ())
        assertEquals(18, WardenPhaseOneLayout.elidinisCenterX())
        assertEquals(46, WardenPhaseOneLayout.tumekenCenterX())
        assertEquals(34, WardenPhaseOneLayout.WARDEN_CENTER_Z)
        assertEquals(16, WardenPhaseOneLayout.ELIDINIS_X)
        assertEquals(44, WardenPhaseOneLayout.TUMEKEN_X)
        assertEquals(32, WardenPhaseOneLayout.WARDEN_Z)
    }

    @Test
    fun `phase one charge follows centre right left lanes into each Warden`() {
        assertEquals(3, WardenPhaseOneLayout.ENERGY_STREAM_ORBS_PER_SIDE)
        assertEquals(
            listOf(
                WardenEnergyLane(0, 1, -2, 1, -4, 1, 4, 2, 0),
                WardenEnergyLane(0, 1, -2, 1, -4, 3, 4, 4, 0),
                WardenEnergyLane(0, 1, -2, 1, -4, -1, 4, 0, 0),
            ),
            WardenPhaseOneLayout.energyStreamLanes(WardenGod.ELIDINIS),
        )
        assertEquals(
            listOf(
                WardenEnergyLane(2, 1, 4, 1, 6, 1, 0, 2, 0),
                WardenEnergyLane(2, 1, 4, 1, 6, -1, 0, 0, 0),
                WardenEnergyLane(2, 1, 4, 1, 6, 3, 0, 4, 0),
            ),
            WardenPhaseOneLayout.energyStreamLanes(WardenGod.TUMEKEN),
        )
        assertEquals(
            WardenPhaseOneLayout.energyStreamLanes(WardenGod.ELIDINIS),
            (0..2).map { WardenPhaseOneLayout.energyStreamLane(WardenGod.ELIDINIS, it) },
        )
        assertEquals(
            WardenPhaseOneLayout.energyStreamLanes(WardenGod.ELIDINIS),
            (3..5).map { WardenPhaseOneLayout.energyStreamLane(WardenGod.ELIDINIS, it) },
        )
    }

    @Test
    fun `phase one uses inactive and charging cache variants at their proper times`() {
        assertEquals(11746, ElidinisWarden.INACTIVE)
        assertEquals(11748, ElidinisWarden.CHARGING)
        assertEquals(11747, TumekenWarden.INACTIVE)
        assertEquals(11749, TumekenWarden.CHARGING)
    }

    @Test
    fun `charging Warden visuals retain their inactive actor identities`() {
        assertEquals(
            WardenChargeActorIdentity(11746, 11748),
            WardenGod.ELIDINIS.chargeActorIdentity(),
        )
        assertEquals(
            WardenChargeActorIdentity(11747, 11749),
            WardenGod.TUMEKEN.chargeActorIdentity(),
        )
    }

    @Test
    fun `phase one pyramids use the native animated three by three locs`() {
        assertEquals(45750, WardenPhaseOneMechanics.RED_PYRAMID_LOC)
        assertEquals(45751, WardenPhaseOneMechanics.GOLD_PYRAMID_LOC)
        assertEquals(setOf(45748, 45749, 45750, 45751), WardenPhaseOneMechanics.PYRAMID_LOCS)
        assertEquals(3, WardenPhaseOneMechanics.PYRAMID_SIZE)
        assertEquals(1, WardenPhaseOneMechanics.PYRAMID_CENTER_OFFSET)
    }

    @Test
    fun `only the two energy troughs block phase one charge`() {
        val x = WardenPhaseOneLayout.OBELISK_X
        val z = WardenPhaseOneLayout.OBELISK_Z
        assertEquals(WardenGod.ELIDINIS, WardenPhaseOneLayout.blockingSide(x, z, x - 1, z + 1))
        assertEquals(WardenGod.TUMEKEN, WardenPhaseOneLayout.blockingSide(x, z, x + 3, z + 1))
        assertEquals(null, WardenPhaseOneLayout.blockingSide(x, z, x - 1, z + 4))
        assertEquals(null, WardenPhaseOneLayout.blockingSide(x, z, x + 1, z + 1))
        assertEquals(0, WardenObelisk.chargeAfterOrb(0, blocked = true, WardensRuleset.BASELINE))
        assertEquals(1, WardenObelisk.chargeAfterOrb(0, blocked = false, WardensRuleset.BASELINE))
    }

    @Test
    fun `obelisk footprint adjacency helper covers the full three by three actor`() {
        val x = WardenPhaseOneLayout.OBELISK_X
        val z = WardenPhaseOneLayout.OBELISK_Z
        assertTrue(WardenPhaseOneLayout.isAdjacentToObelisk(x, z, x - 1, z + 1))
        assertTrue(WardenPhaseOneLayout.isAdjacentToObelisk(x, z, x + 3, z + 1))
        assertTrue(WardenPhaseOneLayout.isAdjacentToObelisk(x, z, x + 1, z - 1))
        assertFalse(WardenPhaseOneLayout.isAdjacentToObelisk(x, z, x - 2, z + 1))
    }

    @Test
    fun `phase one specials alternate per Warden charge cycle`() {
        assertEquals(
            WardenPhaseOneSpecial.RED_PYRAMID,
            WardenPhaseOneMechanics.nextSpecial(WardenGod.ELIDINIS, 0),
        )
        assertEquals(
            WardenPhaseOneSpecial.ELIDINIS_ORB,
            WardenPhaseOneMechanics.nextSpecial(WardenGod.ELIDINIS, 1),
        )
        assertEquals(
            WardenPhaseOneSpecial.GOLD_PYRAMID,
            WardenPhaseOneMechanics.nextSpecial(WardenGod.TUMEKEN, 0),
        )
        assertEquals(
            WardenPhaseOneSpecial.TUMEKEN_ORB,
            WardenPhaseOneMechanics.nextSpecial(WardenGod.TUMEKEN, 1),
        )
        assertEquals(9682, WardenPhaseOneMechanics.CHARGED_SHOT_ANIMATION)
        assertEquals(3, WardenPhaseOneMechanics.CHARGED_SHOT_TRAVEL_TICKS)
        assertEquals(
            ToaAssets.WARDEN_PHASE_ONE_SMALL_BALL,
            WardenPhaseOneMechanics.chargedShotGraphic(WardenGod.ELIDINIS),
        )
        assertEquals(
            ToaAssets.WARDEN_PHASE_ONE_BIG_BALL,
            WardenPhaseOneMechanics.chargedShotGraphic(WardenGod.TUMEKEN),
        )
    }

    @Test
    fun `phase one skull disappears at landing and advances the native platform ring`() {
        val lane = WardenPhaseOneLayout.energyStreamLanes(WardenGod.ELIDINIS).first()
        val rules = WardensRuleset.MAXIMUM
        val landing = WardenPhaseOneTiming.ballLandsAt(100, lane, rules)
        assertEquals(115, landing)
        assertEquals(2238, ToaAssets.WARDEN_PHASE_ONE_SMALL_BALL)
        assertEquals(2227, ToaAssets.WARDEN_CHARGE_INDICATOR)
        assertEquals(10, WardenChargeCircle.SECTIONS)
        assertEquals(45605, WardenChargeCircle.BASE_PLATFORM_LOC)
        assertEquals(45606, WardenChargeCircle.platformStateLoc(0))
        assertEquals(45608, WardenChargeCircle.platformStateLoc(2))
        assertEquals(45626, WardenChargeCircle.platformStateLoc(20))
    }

    @Test
    fun `ten normal skulls complete one native platform circle`() {
        var units = 0
        repeat(9) { section ->
            val advance = WardenChargeCircle.advance(units, WardenObelisk.ENERGY_PULSE)
            assertEquals((section + 1) * 2, advance.displayUnits)
            assertFalse(advance.completedCycle)
            units = advance.remainingUnits
        }
        val final = WardenChargeCircle.advance(units, WardenObelisk.ENERGY_PULSE)
        assertEquals(20, final.displayUnits)
        assertTrue(final.completedCycle)
        assertEquals(0, final.remainingUnits)
    }

    @Test
    fun `baseline passive charge continues when an incoming orb is blocked`() {
        val rules = WardensRuleset.BASELINE
        assertEquals(49, WardenObelisk.chargeAfterOrb(49, blocked = true, rules))
        assertEquals(50, WardenObelisk.chargeAfterOrb(49, blocked = false, rules))
        assertEquals(49, WardenObelisk.chargeAfterPassiveTicks(49, 1, rules))
        assertEquals(51, WardenObelisk.chargeAfterPassiveTicks(49, 2, rules))
        assertEquals(53, WardenObelisk.chargeAfterPassiveTicks(49, 5, rules))
    }

    @Test
    fun `Elidinis charged shot spreads and Tumeken charged shot stacks`() {
        assertEquals(45, WardenPhaseOneMechanics.elidinisSpreadDamage(1, false))
        assertEquals(30, WardenPhaseOneMechanics.elidinisSpreadDamage(4, false))
        assertEquals(45, WardenPhaseOneMechanics.elidinisSpreadDamage(4, true))
        assertEquals(30, WardenPhaseOneMechanics.tumekenStackDamage(1))
        assertEquals(15, WardenPhaseOneMechanics.tumekenStackDamage(2))
        assertEquals(8, WardenPhaseOneMechanics.tumekenStackDamage(4))
    }

    @Test
    fun `baseline charge sources cap at one hundred`() {
        val rules = WardensRuleset.BASELINE
        assertEquals(99, WardenObelisk.chargeAfterOrb(99, blocked = true, rules))
        assertEquals(100, WardenObelisk.chargeAfterOrb(99, blocked = false, rules))
        assertEquals(100, WardenObelisk.chargeAfterPassiveTicks(99, 2, rules))
    }

    @Test
    fun `phase one transition gives the dying warden enough time to launch its orb`() {
        assertTrue(
            WardenPhaseOneMechanics.TRANSITION_DURATION_TICKS >
                WardenPhaseOneMechanics.TRANSITION_DESTROYED_DELAY_TICKS
        )
        assertTrue(WardenPhaseOneMechanics.TRANSITION_DURATION_TICKS >= 5)
    }

    @Test
    fun `obelisk disruption selects the more charged phase two Warden`() {
        assertEquals(WardenGod.ELIDINIS, WardenObelisk.phaseTwoWarden(50, 50))
        assertEquals(WardenGod.ELIDINIS, WardenObelisk.phaseTwoWarden(51, 50))
        assertEquals(WardenGod.TUMEKEN, WardenObelisk.phaseTwoWarden(50, 51))
    }

    @Test
    fun `phase two transition recovers when the activated obelisk dies or disappears`() {
        assertFalse(WardenObelisk.shouldRecoverPhaseTwoTransition(false, false, false, 0))
        assertFalse(WardenObelisk.shouldRecoverPhaseTwoTransition(true, true, true, 1))
        assertTrue(WardenObelisk.shouldRecoverPhaseTwoTransition(true, true, true, 0))
        assertTrue(WardenObelisk.shouldRecoverPhaseTwoTransition(true, true, false, 0))
        assertTrue(WardenObelisk.shouldRecoverPhaseTwoTransition(true, false, false, 0))
    }

    @Test
    fun `charge bar selects the packet verified headbar for each Wardens phase`() {
        assertEquals("headbar.charge_100", WardenChargeHeadbar.gameval(100))
        assertEquals("headbar.charge_140", WardenChargeHeadbar.gameval(140))
        assertEquals("headbar.charge_160", WardenChargeHeadbar.gameval(160))
    }

    @Test
    fun `charge bar safely falls back when revision 240 has no decoded width`() {
        assertEquals(0, WardenChargeHeadbar.fill(0, 100, -1))
        assertEquals(80, WardenChargeHeadbar.fill(50, 100, -1))
        assertEquals(160, WardenChargeHeadbar.fill(100, 100, -1))
        assertEquals(160, WardenChargeHeadbar.fill(101, 100, -1))
    }

    @Test
    fun `charge bar honors valid cache segments and protocol bounds`() {
        assertEquals(35, WardenChargeHeadbar.fill(50, 100, 70))
        assertEquals(160, WardenChargeHeadbar.fill(100, 100, 300))
    }

    @Test
    fun `core exposure expands at the OSRS health thresholds`() {
        assertEquals(21, WardenCores.exposureTicks(100))
        assertEquals(21, WardenCores.exposureTicks(80))
        assertEquals(29, WardenCores.exposureTicks(79))
        assertEquals(29, WardenCores.exposureTicks(60))
        assertEquals(37, WardenCores.exposureTicks(59))
        assertEquals(37, WardenCores.exposureTicks(1))
    }

    @Test
    fun `phase two forms accept only their exposed combat style`() {
        assertEquals(HitType.Magic, ElidinisWarden.requiredStyle(ElidinisWarden.MAGIC_FORM))
        assertEquals(HitType.Ranged, ElidinisWarden.requiredStyle(ElidinisWarden.RANGED_FORM))
        assertEquals(HitType.Ranged, TumekenWarden.requiredStyle(TumekenWarden.RANGED_FORM))
        assertEquals(HitType.Magic, TumekenWarden.requiredStyle(TumekenWarden.MAGIC_FORM))
        assertEquals(null, TumekenWarden.requiredStyle(TumekenWarden.CORE_EJECTED))
    }

    @Test
    fun `phase two Obelisk cycles all three native specials`() {
        assertEquals(WardenObeliskSpecial.CONVERGING_BEAM, WardenPhaseTwoMechanics.special(0))
        assertEquals(WardenObeliskSpecial.WINDMILL_BEAM, WardenPhaseTwoMechanics.special(1))
        assertEquals(WardenObeliskSpecial.LIGHTNING_SKULL, WardenPhaseTwoMechanics.special(2))
        assertEquals(WardenObeliskSpecial.CONVERGING_BEAM, WardenPhaseTwoMechanics.special(3))
        assertEquals(
            WardenPhaseTwoMechanics.OBELISK_CONVERGING_BEAM_ANIMATION,
            WardenPhaseTwoMechanics.obeliskAnimation(WardenObeliskSpecial.CONVERGING_BEAM),
        )
        assertEquals(
            WardenPhaseTwoMechanics.OBELISK_WINDMILL_ANIMATION,
            WardenPhaseTwoMechanics.obeliskAnimation(WardenObeliskSpecial.WINDMILL_BEAM),
        )
        assertEquals(
            WardenPhaseTwoMechanics.OBELISK_LIGHTNING_ANIMATION,
            WardenPhaseTwoMechanics.obeliskAnimation(WardenObeliskSpecial.LIGHTNING_SKULL),
        )
    }

    @Test
    fun `phase two Warden and Obelisk clocks advance independently`() {
        val rules = WardensRuleset.BASELINE
        assertEquals(8, rules.phaseTwoWardenAttackInterval)
        assertEquals(40, rules.phaseTwoObeliskAttackInterval)
        assertEquals(8, WardenPhaseTwoCadence.nextMechanicAt(8, 40))
        assertEquals(40, WardenPhaseTwoCadence.nextMechanicAt(48, 40))
        assertEquals(80, WardenPhaseTwoCadence.advancePast(40, 40, 40))
        assertEquals(80, WardenPhaseTwoCadence.advancePast(41, 40, 40))
        assertEquals(120, WardenPhaseTwoCadence.advancePast(80, 40, 40))
    }

    @Test
    fun `phase two Warden basic attacks use the native melee range and magic assets`() {
        val melee = WardenPhaseTwoMechanics.basicAttack(distance = 2, attackCounter = 1)
        val ranged = WardenPhaseTwoMechanics.basicAttack(distance = 3, attackCounter = 1)
        val magic = WardenPhaseTwoMechanics.basicAttack(distance = 3, attackCounter = 2)

        assertEquals(WardenBasicAttack.MELEE, melee.attack)
        assertEquals(HitType.Melee, melee.hitType)
        assertEquals(9659, melee.animation)
        assertEquals(null, melee.projectile)
        assertEquals(WardenBasicAttack.RANGED, ranged.attack)
        assertEquals(HitType.Ranged, ranged.hitType)
        assertEquals(9660, ranged.animation)
        assertEquals(2241, ranged.projectile)
        assertEquals(null, ranged.impactGraphic)
        assertEquals(WardenBasicAttack.MAGIC, magic.attack)
        assertEquals(HitType.Magic, magic.hitType)
        assertEquals(9661, magic.animation)
        assertEquals(2224, magic.projectile)
        assertEquals(2213, magic.impactGraphic)
    }

    @Test
    fun `scripted Warden attacks use deterministic bounded damage rolls`() {
        assertEquals(20, WardenDamageRolls.basicMaximum(WardenGod.ELIDINIS))
        assertEquals(22, WardenDamageRolls.basicMaximum(WardenGod.TUMEKEN))
        assertEquals(38, WardenDamageRolls.DIVINE_PROJECTILE_MAX_HIT)
        assertEquals(28, WardenDamageRolls.PHANTOM_MAX_HIT)

        val first =
            (0 until 64).map { sequence ->
                WardenDamageRolls.roll(
                    maximum = WardenDamageRolls.DIVINE_PROJECTILE_MAX_HIT,
                    raidSeed = 0x5EEDL,
                    sequence = sequence,
                )
            }
        val replay =
            (0 until 64).map { sequence ->
                WardenDamageRolls.roll(
                    maximum = WardenDamageRolls.DIVINE_PROJECTILE_MAX_HIT,
                    raidSeed = 0x5EEDL,
                    sequence = sequence,
                )
            }
        assertEquals(first, replay)
        assertTrue(first.all { it in 0..WardenDamageRolls.DIVINE_PROJECTILE_MAX_HIT })
        assertTrue(first.toSet().size > 1)
        assertEquals(0, WardenDamageRolls.roll(0, raidSeed = 1L, sequence = 0))
        assertFailsWith<IllegalArgumentException> { WardenDamageRolls.roll(-1, 1L, 0) }
    }

    @Test
    fun `divine projectile uses native travel and impact pairs`() {
        assertEquals(
            WardenDivineProjectileDefinition(HitType.Melee, 2204, 2205),
            WardenPhaseTwoMechanics.divineProjectile(0),
        )
        assertEquals(
            WardenDivineProjectileDefinition(HitType.Ranged, 2206, 2207),
            WardenPhaseTwoMechanics.divineProjectile(1),
        )
        assertEquals(
            WardenDivineProjectileDefinition(HitType.Magic, 2208, 2209),
            WardenPhaseTwoMechanics.divineProjectile(2),
        )
    }

    @Test
    fun `converging and windmill beam patterns remain inside the P2 arena`() {
        val northSouth = WardenPhaseTwoMechanics.convergingBeam(6, northSouth = true)
        val eastWest = WardenPhaseTwoMechanics.convergingBeam(6, northSouth = false)
        assertTrue(northSouth.all { it.x in 31..33 })
        assertTrue(eastWest.all { it.z in 33..35 })
        assertTrue(WardenPhaseTwoMechanics.windmill(0).isNotEmpty())
        assertTrue(WardenPhaseTwoMechanics.windmill(1).isNotEmpty())
        assertTrue(
            (WardenPhaseTwoMechanics.windmill(0) + WardenPhaseTwoMechanics.windmill(1)).all {
                it.x in WardenPhaseTwoMechanics.ARENA_MIN_X..WardenPhaseTwoMechanics.ARENA_MAX_X &&
                    it.z in WardenPhaseTwoMechanics.ARENA_MIN_Z..WardenPhaseTwoMechanics.ARENA_MAX_Z
            }
        )
    }

    @Test
    fun `lightning skull leaves a cardinal safe cross`() {
        val danger = WardenPhaseTwoMechanics.lightningSkull(32, 34)
        assertFalse(WardenLocalTile(32, 36) in danger)
        assertFalse(WardenLocalTile(35, 34) in danger)
        assertTrue(WardenLocalTile(34, 36) in danger)
    }

    @Test
    fun `core damage is transferred to the four thousand five hundred health Warden at five times damage`() {
        assertEquals(4_500, WardenCores.PHASE_TWO_TRUE_HITPOINTS)
        assertEquals(900, WardenCores.coreHitpoints(4_500))
        assertEquals(246, WardenCores.coreHitpoints(1_230))
        assertEquals(250, WardenCores.wardenDamage(50))
        assertEquals(250, WardenCores.hiddenHitpointsAfterCoreDamage(500, 50))
        assertEquals(4_250, WardenCores.hiddenHitpointsAfterCoreDamage(4_500, 50))
        assertEquals(0, WardenCores.wardenDamage(-10))
        assertEquals(21, WardenCores.exposureTicks(100))
        assertEquals(29, WardenCores.exposureTicks(79))
        assertEquals(37, WardenCores.exposureTicks(59))
        assertEquals(4, WardenPhaseTwoMechanics.CORE_X_OFFSET)
        assertEquals(3, WardenPhaseTwoMechanics.CORE_Z_OFFSET)
        assertEquals(2239, WardenPhaseTwoMechanics.coreProjectile(WardenGod.TUMEKEN))
        assertEquals(2240, WardenPhaseTwoMechanics.coreProjectile(WardenGod.ELIDINIS))
    }

    @Test
    fun `phase two mustard shield fills from zero to one hundred forty`() {
        assertEquals(140, WardenCores.PHASE_TWO_SHIELD_HITPOINTS)
        assertEquals(40, WardenCores.shieldDamage(100, 100))
        assertEquals(0, WardenCores.shieldDamage(140, 100))
        assertEquals(0, WardenCores.shieldCharge(140, 140))
        assertEquals(100, WardenCores.shieldCharge(40, 140))
        assertEquals(140, WardenCores.shieldCharge(0, 140))
    }

    @Test
    fun `phase two boss hud is an exact yellow shield counter`() {
        assertEquals(hitmark_groups.poise_damage, WardenPhaseTwoShieldHud.HITMARK)
        assertTrue(WardenPhaseTwoShieldHud.STYLE.forceExactValues)
        assertEquals(98, WardenPhaseTwoShieldHud.currentValue(98))
        assertEquals(0, WardenPhaseTwoShieldHud.currentValue(-1))
        assertEquals(140, WardenPhaseTwoShieldHud.currentValue(341))
        assertEquals(255, WardenPhaseTwoShieldHud.STYLE.inner.red)
        assertEquals(218, WardenPhaseTwoShieldHud.STYLE.inner.green)
    }

    @Test
    fun `phase three transition uses captured timing and final arena entry`() {
        assertEquals(3, WardenPhaseThreeTransition.DURATION_TICKS)
        assertEquals(31, WardenPhaseThreeTransition.ENTRY_LOCAL_X)
        assertEquals(41, WardenPhaseThreeTransition.ENTRY_LOCAL_Z)
        assertEquals(9662, WardenPhaseThreeTransition.SURVIVING_WARDEN_ANIMATION)
        assertEquals(9664, WardenPhaseThreeTransition.DESTROYED_WARDEN_ANIMATION)
        assertEquals(2218, WardenPhaseThreeTransition.DESTROYED_WARDEN_SPOTANIM)
        assertEquals(2219, WardenPhaseThreeTransition.FINAL_WARDEN_SPOTANIM)
    }

    @Test
    fun `white light uses the captured reward transition and treasure tile`() {
        assertEquals(32, WardenRewardTransition.LIGHT_LOCAL_X)
        assertEquals(34, WardenRewardTransition.LIGHT_LOCAL_Z)
        assertEquals(33, WardenRewardTransition.ENTRY_LOCAL_X)
        assertEquals(49, WardenRewardTransition.ENTRY_LOCAL_Z)
        assertEquals(3, WardenRewardTransition.FADE_DELAY_TICKS)
        assertEquals(50, WardenRewardTransition.FADE_CLIENT_DURATION)
        assertEquals(WardenRewardTransition.ENTRY_LOCAL_X, ToaRoom.REWARD.entryLocalX)
        assertEquals(WardenRewardTransition.ENTRY_LOCAL_Z, ToaRoom.REWARD.entryLocalZ)
        assertEquals(
            org.rsmod.api.instances.RegionLocal(1, 61, 80, 32, 34),
            ToaInstanceLayout.rewardEntryLocal(),
        )
    }

    @Test
    fun `raid-owned energy siphons bypass normal remains drops`() {
        assertEquals(ToaNpcDeathRoute.RAID_NO_DROPS, toaSiphonDeathRoute(raidOwned = true))
        assertEquals(ToaNpcDeathRoute.STANDARD_DROPS, toaSiphonDeathRoute(raidOwned = false))
    }

    @Test
    fun `energy siphons scale health and count with party size`() {
        assertEquals(4, WardenEnergySiphons.count(1, 0))
        assertEquals(7, WardenEnergySiphons.count(1, 3))
        assertEquals(6, WardenEnergySiphons.count(2, 0))
        assertEquals(13, WardenEnergySiphons.count(8, 3))
        assertContentEquals(
            intArrayOf(1, 1, 1, 2, 2, 3, 3, 4),
            (1..8).map(WardenEnergySiphons::hitpoints).toIntArray(),
        )
        assertEquals(50, WardenEnergySiphons.reflectedDamage(1_000))
        assertEquals(225, WardenEnergySiphons.reflectedDamage(4_500))
        assertEquals(2226, WardenEnergySiphons.OUTWARD_PROJECTILE)
        assertEquals(2227, WardenEnergySiphons.LINK_PROJECTILE)
        assertEquals(1447, WardenEnergySiphons.LANDING_GRAPHIC)
        assertEquals(2158, WardenEnergySiphons.FAILURE_GRAPHIC)
        assertEquals(6, WardenEnergySiphons.SPAWN_DELAY_TICKS)
        assertEquals(7, WardenEnergySiphons.LINK_DELAY_TICKS)
        assertEquals(2, WardenEnergySiphons.LINK_INTERVAL_TICKS)

        assertEquals(
            listOf(
                WardenLocalTile(29, 42),
                WardenLocalTile(32, 44),
                WardenLocalTile(35, 42),
                WardenLocalTile(32, 40),
            ),
            WardenEnergySiphons.localTiles(1, 0, 4),
        )
        assertEquals(
            listOf(
                WardenLocalTile(26, 41),
                WardenLocalTile(28, 44),
                WardenLocalTile(30, 41),
                WardenLocalTile(32, 44),
                WardenLocalTile(34, 41),
                WardenLocalTile(36, 44),
                WardenLocalTile(38, 41),
            ),
            WardenEnergySiphons.localTiles(1, 3, 7),
        )
    }

    @Test
    fun `phase three damage stops at every siphon and enrage threshold`() {
        assertEquals(200, TumekenWarden.clampDamageToNextTransition(1_000, 1_000, 900, 0, false))
        assertEquals(200, TumekenWarden.clampDamageToNextTransition(800, 1_000, 900, 1, false))
        assertEquals(200, TumekenWarden.clampDamageToNextTransition(600, 1_000, 900, 2, false))
        assertEquals(200, TumekenWarden.clampDamageToNextTransition(400, 1_000, 900, 3, false))
        assertEquals(150, TumekenWarden.clampDamageToNextTransition(200, 1_000, 900, 4, false))
        assertEquals(50, TumekenWarden.clampDamageToNextTransition(50, 1_000, 900, 4, true))
    }

    @Test
    fun `phase three selects the correct phantom branch`() {
        assertContentEquals(
            intArrayOf(TumekenWarden.ZEBAK_PHANTOM, TumekenWarden.BABA_PHANTOM),
            TumekenWarden.phantomIds(WardenGod.TUMEKEN),
        )
        assertContentEquals(
            intArrayOf(TumekenWarden.AKKHA_PHANTOM, TumekenWarden.KEPHRI_PHANTOM),
            TumekenWarden.phantomIds(WardenGod.ELIDINIS),
        )
    }

    @Test
    fun `phase three phantoms enter only at the sixty and forty percent siphons`() {
        assertNull(TumekenWarden.phantomIndexForSiphonPhase(0))
        assertEquals(0, TumekenWarden.phantomIndexForSiphonPhase(1))
        assertEquals(1, TumekenWarden.phantomIndexForSiphonPhase(2))
        assertNull(TumekenWarden.phantomIndexForSiphonPhase(3))
        assertEquals(60, TumekenWarden.siphonThresholds[1])
        assertEquals(40, TumekenWarden.siphonThresholds[2])
    }

    @Test
    fun `phase three slam cycles right left and both`() {
        assertEquals(WardenSlamPattern.RIGHT, WardenFloor.pattern(0))
        assertEquals(WardenSlamPattern.LEFT, WardenFloor.pattern(1))
        assertEquals(WardenSlamPattern.BOTH, WardenFloor.pattern(2))
        assertEquals(WardenSlamPattern.RIGHT, WardenFloor.pattern(3))
    }

    @Test
    fun `phase three slam safe tiles match the Warden facing direction`() {
        val center = 100
        assertFalse(WardenFloor.isSafe(WardenSlamPattern.RIGHT, center, 99))
        assertTrue(WardenFloor.isSafe(WardenSlamPattern.RIGHT, center, 101))
        assertTrue(WardenFloor.isSafe(WardenSlamPattern.LEFT, center, 99))
        assertFalse(WardenFloor.isSafe(WardenSlamPattern.LEFT, center, 101))
        assertTrue(WardenFloor.isSafe(WardenSlamPattern.BOTH, center, 100))
        assertFalse(WardenFloor.isSafe(WardenSlamPattern.BOTH, center, 103))
    }

    @Test
    fun `phase three uses each native slam animation`() {
        assertEquals(9674, WardenFloor.slamAnimation(WardenSlamPattern.RIGHT))
        assertEquals(9676, WardenFloor.slamAnimation(WardenSlamPattern.LEFT))
        assertEquals(9678, WardenFloor.slamAnimation(WardenSlamPattern.BOTH))
    }

    @Test
    fun `energy siphons release only valid melee attack delays`() {
        assertTrue(WardenEnergySiphons.releasesAttackDelay(HitType.Melee))
        assertFalse(WardenEnergySiphons.releasesAttackDelay(HitType.Ranged))
        assertFalse(WardenEnergySiphons.releasesAttackDelay(HitType.Magic))
    }

    @Test
    fun `fatal enrage impact starts the captured Warden death timeline immediately`() {
        assertFalse(WardenDeathSequence.shouldBegin(ToaWardenStage.PHASE_THREE, 2, 2))
        assertFalse(WardenDeathSequence.shouldBegin(ToaWardenStage.ENRAGE, 2, 1))
        assertFalse(WardenDeathSequence.shouldBegin(ToaWardenStage.ENRAGE, 2, 0))
        assertTrue(WardenDeathSequence.shouldBegin(ToaWardenStage.ENRAGE, 2, 2))
        assertTrue(WardenDeathSequence.shouldBegin(ToaWardenStage.ENRAGE, 2, 20))
        assertFalse(WardenDeathSequence.shouldBegin(ToaWardenStage.DEATH_SEQUENCE, 0, 2))

        assertEquals(1, WardenDeathSequence.BOSS_REMOVAL_TICKS)
        assertEquals(3, WardenDeathSequence.PHANTOM_REMOVAL_TICKS)
        assertEquals(9, WardenDeathSequence.BOSS_BAR_CLOSE_TICKS)
        assertEquals(14, WardenDeathSequence.REWARD_LIGHT_TICKS)
    }

    @Test
    fun `enrage removes the arena from north to south`() {
        assertTrue(WardenFloor.isCollapsed(1, 32, 45))
        assertFalse(WardenFloor.isCollapsed(1, 32, 44))
        assertTrue(WardenFloor.isCollapsed(4, 23, 42))
        assertFalse(WardenFloor.isCollapsed(4, 22, 42))
        assertEquals(8, WardensRuleset.MAXIMUM.phaseFourCollapseInterval(0))
        assertEquals(WardenLocalTile(32, 44), WardenFloor.pushedLocalTile(1, 32, 45))
        assertNull(WardenFloor.pushedLocalTile(1, 32, 44))
    }

    @Test
    fun `enrage collapse starts the captured three-axis room shake`() {
        assertEquals(
            listOf(
                WardenCameraShake(axis = 0, random = 5, amplitude = 0, rate = 0),
                WardenCameraShake(axis = 1, random = 3, amplitude = 0, rate = 0),
                WardenCameraShake(axis = 2, random = 2, amplitude = 0, rate = 0),
            ),
            WardenFloor.collapseCameraShake,
        )
        assertEquals(setOf(0, 1, 2), WardenFloor.collapseCameraShake.map { it.axis }.toSet())
    }

    @Test
    fun `enrage floor uses the captured revision 240 ground decoration states`() {
        val first = WardenFloor.replacementChanges(0)
        assertEquals(21, first.size)
        assertEquals(WardenFloorLocChange(22, 45, 45731, 1), first.first())
        assertEquals(WardenFloorLocChange(42, 45, 45732, 1), first.last())
        assertTrue(first.drop(1).dropLast(1).all { it.locId == 45730 && it.rotation == 1 })

        val second = WardenFloor.replacementChanges(1)
        assertTrue(WardenFloorLocChange(22, 45, 45735, 1) in second)
        assertTrue(WardenFloorLocChange(42, 45, 45737, 1) in second)
        assertTrue(WardenFloorLocChange(22, 44, 45736, 0) in second)
        assertTrue(WardenFloorLocChange(42, 44, 45736, 3) in second)
        assertTrue(
            second
                .filter { it.localZ == 44 && it.localX in 23..41 }
                .all { it.locId == 45726 && it.rotation == 3 }
        )
    }

    @Test
    fun `captured floor rotations preserve raw OpenRune angle ids`() {
        assertEquals(LocAngle.West, WardenFloor.rawLocAngle(0))
        assertEquals(LocAngle.North, WardenFloor.rawLocAngle(1))
        assertEquals(LocAngle.East, WardenFloor.rawLocAngle(2))
        assertEquals(LocAngle.South, WardenFloor.rawLocAngle(3))
        assertEquals(1, WardenFloor.rawLocAngle(1).id)
        assertEquals(3, WardenFloor.rawLocAngle(3).id)
    }

    @Test
    fun `later enrage rows replace the previous broken edge with void seams`() {
        val third = WardenFloor.replacementChanges(2)
        assertTrue(WardenFloorLocChange(22, 44, 45726, 0) in third)
        assertTrue(WardenFloorLocChange(42, 44, 45726, 2) in third)
        assertTrue(
            third
                .filter { it.localZ == 44 && it.localX in 23..41 }
                .all { it.locId == 45738 && it.rotation == 0 }
        )

        val fourth = WardenFloor.replacementChanges(3)
        assertTrue(fourth.filter { it.localZ == 43 }.map { it.localX }.toSet() == (23..41).toSet())
        assertTrue(fourth.filter { it.localZ == 43 }.all { it.locId == 45738 })
        assertTrue(WardenFloorLocChange(23, 42, 45736, 0) in fourth)
        assertTrue(WardenFloorLocChange(41, 42, 45736, 3) in fourth)
        assertTrue(
            fourth
                .filter { it.localZ == 42 && it.localX in 24..40 }
                .all { it.locId in setOf(45726, 45728) }
        )
        assertTrue(WardenFloor.replacementChanges(8).isEmpty())
    }

    @Test
    fun `collapsed rows retain one authoritative visual state per tile`() {
        val state = WardenFloor.replacementStateAfter(5)

        assertEquals(45727, state.getValue(WardenLocalTile(32, 45)).locId)
        assertEquals(45738, state.getValue(WardenLocalTile(32, 44)).locId)
        assertEquals(45738, state.getValue(WardenLocalTile(32, 43)).locId)
        assertEquals(45738, state.getValue(WardenLocalTile(32, 42)).locId)
        assertTrue(state.getValue(WardenLocalTile(32, 41)).locId in setOf(45726, 45728))

        // Narrowing rows leave only the captured side caps behind. Older row interiors must be
        // permanent void, not stale broken-edge models layered over the red arena floor.
        assertEquals(45736, state.getValue(WardenLocalTile(22, 43)).locId)
        assertEquals(45736, state.getValue(WardenLocalTile(42, 43)).locId)
        assertTrue((23..41).all { x -> state.getValue(WardenLocalTile(x, 43)).locId == 45738 })
        assertEquals(
            state.size,
            state.keys.distinct().size,
            "Every collapsed tile must resolve to exactly one current visual state",
        )
    }

    @Test
    fun `floor location journal preserves the first original and drains once`() {
        val journal = WardenFloorLocJournal<String, String, String?>()

        journal.recordFirst("raid-a", "tile", "original")
        journal.recordFirst("raid-a", "tile", "replacement")

        assertEquals(mapOf("tile" to "original"), journal.drain("raid-a"))
        assertTrue(journal.drain("raid-a").isEmpty())
    }

    @Test
    fun `floor location journal isolates instances and permits placement reuse`() {
        val journal = WardenFloorLocJournal<String, String, String?>()

        journal.recordFirst("raid-a", "shared-tile", "raid-a-original")
        journal.recordFirst("raid-b", "shared-tile", "raid-b-original")

        assertEquals(mapOf("shared-tile" to "raid-a-original"), journal.drain("raid-a"))
        assertEquals(mapOf("shared-tile" to "raid-b-original"), journal.drain("raid-b"))

        journal.recordFirst("raid-a", "shared-tile", "reused-placement-original")
        assertEquals(mapOf("shared-tile" to "reused-placement-original"), journal.drain("raid-a"))
    }

    @Test
    fun `phase four lightning only selects remaining floor and leaves nearby safe tiles`() {
        val players = listOf(WardenLocalTile(30, 41), WardenLocalTile(34, 41))
        val remaining = WardenFloor.remainingLocalTiles(5)
        val lightning = WardenFloor.lightningLocalTiles(5, 17, players)
        assertTrue(lightning.isNotEmpty())
        assertTrue(lightning.all { it in remaining })
        assertTrue(lightning.size <= remaining.size / 3)
        players.forEach { player ->
            assertTrue(
                remaining.any {
                    it !in lightning &&
                        kotlin.math.abs(it.x - player.x) <= 1 &&
                        kotlin.math.abs(it.z - player.z) <= 1
                }
            )
        }
    }

    @Test
    fun `level zero selects the immutable baseline Wardens ruleset`() {
        assertEquals(WardensRuleset.BASELINE, ToaEncounterState().wardensRuleset)
        val rules = WardensRuleset.BASELINE
        assertEquals(2, rules.phaseOneEnergyPulseInterval)
        assertEquals(2, rules.phaseOnePassiveChargeInterval)
        assertEquals(2, rules.phaseOnePassiveChargeAmount)
        assertEquals(0, rules.phaseOnePassiveCharge(1))
        assertEquals(2, rules.phaseOnePassiveCharge(2))
        assertEquals(4, rules.phaseOnePassiveCharge(5))
        assertEquals(0, rules.phaseOneOrbCharge(blocked = true))
        assertEquals(1, rules.phaseOneOrbCharge(blocked = false))
        assertEquals(15, rules.phaseOneEnergyTravelTicks)
        assertEquals(2, rules.phaseOneEnergyTrunkTicks)
        assertEquals(4, rules.phaseOneEnergyTurnTicks)
        assertEquals(4, rules.phaseOneChargeIndicatorTicks)
        assertEquals(8, rules.phaseTwoWardenAttackInterval)
        assertEquals(7, rules.phaseTwoWardenAttackInterval(WardenGod.ELIDINIS))
        assertEquals(8, rules.phaseTwoWardenAttackInterval(WardenGod.TUMEKEN))
        assertEquals(40, rules.phaseTwoObeliskAttackInterval)
        assertFalse(rules.penetration)
        assertEquals(3, rules.phaseThreeInitialSlamDelay)
        assertEquals(7, rules.phaseThreeFloorSlamInterval)
        assertTrue(rules.resetsSlamAfterSiphons)
        assertEquals(2, rules.phaseThreeSlamWarningTicks)
        assertEquals(9, rules.phaseThreeSiphonResumeDelay)
        assertEquals(8, rules.phaseThreePhantomAttackInterval)
        assertEquals(3, rules.energySiphonDuration(0))
        assertEquals(15, rules.energySiphonDuration(4))
        assertEquals(42, rules.energySiphonDuration(13))
        assertEquals(5, rules.phaseFourFirstLightningDelay)
        assertEquals(6, rules.phaseFourLightningInterval)
        assertEquals(3, rules.phaseFourLightningWarningTicks)
        assertEquals(5, rules.phaseFourPhantomAttackInterval)
        assertEquals(6, rules.phaseFourFirstFloorWarningTicks)
        assertEquals(18, rules.phaseFourCollapseInterval(0))
        assertEquals(18, rules.phaseFourCollapseInterval(7))
        assertEquals(6, rules.phaseFourCollapseOffset(0))
        assertEquals(24, rules.phaseFourCollapseOffset(1))
        assertEquals(42, rules.phaseFourCollapseOffset(2))
    }

    @Test
    fun `maximum Wardens ruleset remains available for invocation composition`() {
        val rules = WardensRuleset.MAXIMUM
        assertEquals(2, rules.phaseOneEnergyPulseInterval)
        assertEquals(2, rules.phaseOnePassiveChargeInterval)
        assertEquals(2, rules.phaseOneChargePerOrb)
        assertEquals(4, rules.phaseTwoWardenAttackInterval)
        assertEquals(30, rules.phaseTwoObeliskAttackInterval)
        assertTrue(rules.penetration)
        assertEquals(3, rules.phaseThreeFloorSlamInterval)
        assertFalse(rules.resetsSlamAfterSiphons)
        assertEquals(5, rules.energySiphonDuration(1))
        assertEquals(19, rules.energySiphonDuration(8))
        assertEquals(1, rules.phaseFourFirstLightningDelay)
        assertEquals(3, rules.phaseFourLightningInterval)
        assertEquals(2, rules.phaseFourLightningWarningTicks)
    }

    @Test
    fun `individual Wardens invocations can be selected`() {
        val invocations = ToaInvocationSet()
        assertTrue(ToaInvocation.PENETRATION.selectable)
        assertTrue(invocations.toggle(ToaInvocation.PENETRATION))
        assertTrue(ToaInvocation.PENETRATION in invocations)
    }

    @Test
    fun `maximum phase four collapse timestamps are absolute`() {
        val rules = WardensRuleset.MAXIMUM
        assertEquals(2, rules.phaseFourCollapseOffset(0))
        assertEquals(10, rules.phaseFourCollapseOffset(1))
        assertEquals(19, rules.phaseFourCollapseOffset(2))
    }
}

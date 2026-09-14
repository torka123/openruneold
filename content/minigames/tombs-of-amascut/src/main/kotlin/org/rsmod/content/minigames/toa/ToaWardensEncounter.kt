package org.rsmod.content.minigames.toa

import java.awt.Color
import kotlin.math.abs
import kotlin.random.Random
import org.rsmod.api.bossbar.plugin.BossHpBarStyle
import org.rsmod.api.config.refs.done.hitmark_groups
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.LocAngle

/** Revision-240 Wardens crystal transition observed in the OSRS packet capture. */
internal object WardensEntryLifecycle {
    const val WAITING_LOCAL_X = 31
    const val WAITING_LOCAL_Z = 56
    const val CRYSTAL_LOCAL_X = 32
    const val CRYSTAL_LOCAL_Z = 52
    const val FIGHT_LOCAL_X = 32
    const val FIGHT_LOCAL_Z = 46
    const val PLAYER_SPOTANIM = 409
    const val TELEPORT_SOUND = 198
}

/** Packet-captured transition from the phase-two arena into the final Warden arena. */
internal object WardenPhaseThreeTransition {
    const val DURATION_TICKS = 3
    const val ENTRY_LOCAL_X = 31
    const val ENTRY_LOCAL_Z = 41
    const val CAMERA_LOCAL_X = 32
    const val CAMERA_LOCAL_Z = 46
    const val FOCUS_LOCAL_X = 32
    const val FOCUS_LOCAL_Z = 34
    const val CAMERA_HEIGHT = 900
    const val FOCUS_HEIGHT = 120
    const val SURVIVING_WARDEN_ANIMATION = 9662
    const val DESTROYED_WARDEN_ANIMATION = 9664
    const val DESTROYED_WARDEN_SPOTANIM = 2218
    const val FINAL_WARDEN_SPOTANIM = 2219
}

internal enum class ToaWardenStage {
    OBELISK,
    PHASE_ONE_TRANSITION,
    PHASE_TWO,
    CORE,
    PHASE_THREE_TRANSITION,
    PHASE_THREE,
    ENERGY_SIPHONS,
    ENRAGE,
    DEATH_SEQUENCE,
}

internal object WardenDeathSequence {
    const val BOSS_REMOVAL_TICKS = 1
    const val PHANTOM_REMOVAL_TICKS = 3
    const val BOSS_BAR_CLOSE_TICKS = 9
    const val REWARD_LIGHT_TICKS = 14

    fun shouldBegin(stage: ToaWardenStage, currentHitpoints: Int, damage: Int): Boolean =
        stage == ToaWardenStage.ENRAGE && damage > 0 && damage >= currentHitpoints.coerceAtLeast(0)
}

/** Packet-captured transition from the white completion light into the treasure room. */
internal object WardenRewardTransition {
    const val LIGHT_LOCAL_X = 32
    const val LIGHT_LOCAL_Z = 34
    const val ENTRY_LOCAL_X = 33
    const val ENTRY_LOCAL_Z = 49
    const val FADE_DELAY_TICKS = 3
    const val FADE_START_COLOUR = 0
    const val FADE_START_TRANSPARENCY = 255
    const val FADE_END_COLOUR = 0
    const val FADE_END_TRANSPARENCY = 0
    const val FADE_CLIENT_DURATION = 50
}

internal enum class WardenGod {
    ELIDINIS,
    TUMEKEN,
}

internal fun WardenGod.displayName(): String =
    when (this) {
        WardenGod.ELIDINIS -> "Elidinis'"
        WardenGod.TUMEKEN -> "Tumeken's"
    }

internal data class WardenChargeActorIdentity(val originalId: Int, val chargingVisualId: Int)

/** One of the three distinct phase-one energy lanes between the Obelisk and a Warden. */
internal data class WardenEnergyLane(
    val obeliskXOffset: Int,
    val obeliskZOffset: Int,
    val forkXOffset: Int,
    val forkZOffset: Int,
    val branchXOffset: Int,
    val branchZOffset: Int,
    val wardenXOffset: Int,
    val wardenZOffset: Int,
    val launchDelayTicks: Int,
)

internal fun WardenGod.chargeActorIdentity(): WardenChargeActorIdentity =
    when (this) {
        WardenGod.ELIDINIS ->
            WardenChargeActorIdentity(ElidinisWarden.INACTIVE, ElidinisWarden.CHARGING)
        WardenGod.TUMEKEN ->
            WardenChargeActorIdentity(TumekenWarden.INACTIVE, TumekenWarden.CHARGING)
    }

internal enum class WardenPhaseOneSpecial(val god: WardenGod, val chargedShot: Boolean) {
    RED_PYRAMID(WardenGod.ELIDINIS, false),
    GOLD_PYRAMID(WardenGod.TUMEKEN, false),
    ELIDINIS_ORB(WardenGod.ELIDINIS, true),
    TUMEKEN_ORB(WardenGod.TUMEKEN, true),
}

internal object WardenPhaseOneMechanics {
    const val RED_PYRAMID_APPEAR_LOC = 45748
    const val GOLD_PYRAMID_APPEAR_LOC = 45749
    const val RED_PYRAMID_LOC = 45750
    const val GOLD_PYRAMID_LOC = 45751
    const val PYRAMID_SIZE = 3
    const val PYRAMID_CENTER_OFFSET = PYRAMID_SIZE / 2
    val PYRAMID_LOCS =
        setOf(RED_PYRAMID_APPEAR_LOC, GOLD_PYRAMID_APPEAR_LOC, RED_PYRAMID_LOC, GOLD_PYRAMID_LOC)
    const val SPECIAL_DURATION_TICKS = 4
    const val TRANSITION_DURATION_TICKS = 6
    const val TRANSITION_DESTROYED_DELAY_TICKS = 3
    const val PYRAMID_DAMAGE = 35
    const val ELIDINIS_SPREAD_DAMAGE = 30
    const val ELIDINIS_OVERLAP_DAMAGE = 45
    const val TUMEKEN_STACK_DAMAGE = 30
    const val DEATH_ORB_DAMAGE = 28
    const val CHARGED_SHOT_ANIMATION = 9682
    const val CHARGED_SHOT_TRAVEL_TICKS = 3

    /** Charged Wardens alternate their pyramid/UFO attack and their divine energy shot. */
    fun nextSpecial(god: WardenGod, completedChargeCycles: Int): WardenPhaseOneSpecial =
        when (god) {
            WardenGod.ELIDINIS ->
                if (completedChargeCycles % 2 == 0) WardenPhaseOneSpecial.RED_PYRAMID
                else WardenPhaseOneSpecial.ELIDINIS_ORB
            WardenGod.TUMEKEN ->
                if (completedChargeCycles % 2 == 0) WardenPhaseOneSpecial.GOLD_PYRAMID
                else WardenPhaseOneSpecial.TUMEKEN_ORB
        }

    /** Elidinis fires the small spread shot; Tumeken fires the large stack shot. */
    fun chargedShotGraphic(god: WardenGod): Int =
        when (god) {
            WardenGod.ELIDINIS -> ToaAssets.WARDEN_PHASE_ONE_SMALL_BALL
            WardenGod.TUMEKEN -> ToaAssets.WARDEN_PHASE_ONE_BIG_BALL
        }

    fun chargedShotWarning(god: WardenGod): String =
        when (god) {
            WardenGod.ELIDINIS -> "<col=00ffff>A large ball of energy is shot your way...</col>"
            WardenGod.TUMEKEN -> "<col=ff8c00>A large ball of energy is shot your way...</col>"
        }

    fun elidinisSpreadDamage(partySize: Int, hasNearbyTeammate: Boolean): Int =
        if (partySize <= 1 || hasNearbyTeammate) ELIDINIS_OVERLAP_DAMAGE else ELIDINIS_SPREAD_DAMAGE

    fun tumekenStackDamage(stackingPlayers: Int): Int =
        (TUMEKEN_STACK_DAMAGE + stackingPlayers.coerceAtLeast(1) - 1) /
            stackingPlayers.coerceAtLeast(1)
}

internal object WardenPhaseOneLayout {
    const val ARENA_CENTER_X = 32
    const val ARENA_CENTER_Z = 34

    const val OBELISK_SIZE = 3
    const val OBELISK_X = ARENA_CENTER_X - (OBELISK_SIZE / 2)
    const val OBELISK_Z = ARENA_CENTER_Z - (OBELISK_SIZE / 2)

    const val WARDEN_SIZE = 5
    // Both 5x5 Wardens occupy the north side of the phase-one arena. Their
    // south-west tiles are (16, 32) and (44, 32) in map square 15184.
    const val WARDEN_CENTER_Z = 34
    const val ELIDINIS_CENTER_X = 18
    const val TUMEKEN_CENTER_X = 46
    const val WARDEN_Z = WARDEN_CENTER_Z - (WARDEN_SIZE / 2)
    const val ELIDINIS_X = ELIDINIS_CENTER_X - (WARDEN_SIZE / 2)
    const val TUMEKEN_X = TUMEKEN_CENTER_X - (WARDEN_SIZE / 2)
    const val ENERGY_STREAM_ORBS_PER_SIDE = 3
    private const val TROUGH_MIN_Z_OFFSET = 0
    private const val TROUGH_MAX_Z_OFFSET = 2
    private const val ELIDINIS_TROUGH_MIN_X_OFFSET = -8
    private const val ELIDINIS_TROUGH_MAX_X_OFFSET = -1
    private const val TUMEKEN_TROUGH_MIN_X_OFFSET = 3
    private const val TUMEKEN_TROUGH_MAX_X_OFFSET = 10

    fun obeliskCenterX(): Int = OBELISK_X + (OBELISK_SIZE / 2)

    fun obeliskCenterZ(): Int = OBELISK_Z + (OBELISK_SIZE / 2)

    fun elidinisCenterX(): Int = ELIDINIS_CENTER_X

    fun tumekenCenterX(): Int = TUMEKEN_CENTER_X

    /**
     * Every skull leaves through the centre channel, reaches the physical fork, turns onto its
     * selected red floor lane, and stays on that lane until the Warden. Pulses repeat in the OSRS
     * order: centre, right, left. Right and left mirror because the Wardens face opposite ways.
     */
    fun energyStreamLanes(god: WardenGod): List<WardenEnergyLane> {
        val obeliskXOffset = if (god == WardenGod.ELIDINIS) 0 else OBELISK_SIZE - 1
        val forkXOffset = if (god == WardenGod.ELIDINIS) -2 else OBELISK_SIZE + 1
        val branchXOffset = if (god == WardenGod.ELIDINIS) -4 else OBELISK_SIZE + 3
        val wardenXOffset = if (god == WardenGod.ELIDINIS) WARDEN_SIZE - 1 else 0
        val laneZOffsets = if (god == WardenGod.ELIDINIS) listOf(1, 3, -1) else listOf(1, -1, 3)
        return laneZOffsets.map { zOffset ->
            WardenEnergyLane(
                obeliskXOffset = obeliskXOffset,
                obeliskZOffset = 1,
                forkXOffset = forkXOffset,
                forkZOffset = 1,
                branchXOffset = branchXOffset,
                branchZOffset = zOffset,
                wardenXOffset = wardenXOffset,
                wardenZOffset = zOffset + 1,
                launchDelayTicks = 0,
            )
        }
    }

    fun energyStreamLane(god: WardenGod, pulseIndex: Int): WardenEnergyLane {
        val lanes = energyStreamLanes(god)
        return lanes[pulseIndex.mod(lanes.size)]
    }

    fun indicatorLocalTile(god: WardenGod): WardenLocalTile =
        when (god) {
            WardenGod.ELIDINIS -> WardenLocalTile(ELIDINIS_CENTER_X, WARDEN_CENTER_Z)
            WardenGod.TUMEKEN -> WardenLocalTile(TUMEKEN_CENTER_X, WARDEN_CENTER_Z)
        }

    fun blockingSide(obeliskX: Int, obeliskZ: Int, playerX: Int, playerZ: Int): WardenGod? {
        if (playerZ !in (obeliskZ + TROUGH_MIN_Z_OFFSET)..(obeliskZ + TROUGH_MAX_Z_OFFSET)) {
            return null
        }
        return when (playerX) {
            in (obeliskX + ELIDINIS_TROUGH_MIN_X_OFFSET)..(obeliskX +
                    ELIDINIS_TROUGH_MAX_X_OFFSET) -> WardenGod.ELIDINIS
            in (obeliskX + TUMEKEN_TROUGH_MIN_X_OFFSET)..(obeliskX + TUMEKEN_TROUGH_MAX_X_OFFSET) ->
                WardenGod.TUMEKEN
            else -> null
        }
    }

    fun isAdjacentToObelisk(obeliskX: Int, obeliskZ: Int, playerX: Int, playerZ: Int): Boolean {
        val dx = maxOf(obeliskX - playerX, 0, playerX - (obeliskX + OBELISK_SIZE - 1))
        val dz = maxOf(obeliskZ - playerZ, 0, playerZ - (obeliskZ + OBELISK_SIZE - 1))
        return maxOf(dx, dz) <= 1
    }
}

internal object WardenObelisk {
    const val INACTIVE = 11750
    const val ATTACKABLE = 11751
    const val EXHAUSTED = 11752
    const val BASE_HITPOINTS = 260
    const val ELIDINIS_DESTROYED = 11759
    const val TUMEKEN_DESTROYED = 11760
    const val ENERGY_PULSE = 2

    fun chargeAfterOrb(charge: Int, blocked: Boolean, ruleset: WardensRuleset): Int =
        (charge + ruleset.phaseOneOrbCharge(blocked)).coerceAtMost(100)

    fun chargeAfterPassiveTicks(charge: Int, elapsedTicks: Int, ruleset: WardensRuleset): Int =
        (charge + ruleset.phaseOnePassiveCharge(elapsedTicks)).coerceAtMost(100)

    fun phaseTwoWarden(elidinisCharge: Int, tumekenCharge: Int): WardenGod =
        if (tumekenCharge > elidinisCharge) WardenGod.TUMEKEN else WardenGod.ELIDINIS

    /**
     * Recovers phase-one completion when the death lifecycle has already detached the attackable
     * Obelisk actor before its kill hook can advance the encounter.
     */
    fun shouldRecoverPhaseTwoTransition(
        activated: Boolean,
        actorPresent: Boolean,
        actorAssigned: Boolean,
        hitpoints: Int,
    ): Boolean = activated && (!actorPresent || !actorAssigned || hitpoints <= 0)
}

internal data class WardenLocalTile(val x: Int, val z: Int)

internal object WardenPhaseOneTiming {
    fun ballLandsAt(launchCycle: Int, lane: WardenEnergyLane, ruleset: WardensRuleset): Int =
        launchCycle + lane.launchDelayTicks + ruleset.phaseOneEnergyTravelTicks
}

internal data class WardenChargeProjectileTimeline(
    val trunkStartCycles: Int,
    val turnStartCycles: Int,
    val laneStartCycles: Int,
    val laneEndCycles: Int,
)

/**
 * Client projectile durations are interval based: an N-tick projectile lasts (N - 1) client
 * intervals. Converting each cumulative server delay independently inserts a one-tick blank frame
 * at the second handoff. Build every subsequent start from the previous segment's exact
 * client-cycle end so the skull remains visible while it turns onto its selected floor lane.
 */
internal object WardenPhaseOneProjectileTiming {
    fun timeline(lane: WardenEnergyLane, ruleset: WardensRuleset): WardenChargeProjectileTimeline {
        val trunkStart = ToaProjectileTiming.clientCyclesForServerDelay(lane.launchDelayTicks)
        val turnStart =
            trunkStart +
                ToaProjectileTiming.clientCyclesForServerTicks(ruleset.phaseOneEnergyTrunkTicks)
        val laneStart =
            turnStart +
                ToaProjectileTiming.clientCyclesForServerTicks(ruleset.phaseOneEnergyTurnTicks)
        val laneEnd =
            trunkStart +
                ToaProjectileTiming.clientCyclesForServerTicks(ruleset.phaseOneEnergyTravelTicks)
        require(laneEnd > laneStart)
        return WardenChargeProjectileTimeline(trunkStart, turnStart, laneStart, laneEnd)
    }
}

internal data class WardenChargeCircleAdvance(
    val remainingUnits: Int,
    val displayUnits: Int,
    val completedCycle: Boolean,
)

/**
 * Revision 240 contains the complete native phase-one charging-platform sequence. The base loc is
 * placed in the map and the `_0` through `_20` locs render the red circular meter from empty to
 * full. Incoming unblocked orbs advance this visual while their gameplay charge is applied once on
 * arrival. Blocked orbs never reach the platform, and passive charge uses its own clock.
 */
internal object WardenChargeCircle {
    const val BASE_PLATFORM_LOC = 45605
    const val FIRST_PLATFORM_STATE_LOC = 45606
    const val LAST_PLATFORM_STATE_LOC = 45626
    const val RESET_VISUAL_TICKS = 2
    const val SECTIONS = 10
    const val UNITS_PER_SECTION = WardenObelisk.ENERGY_PULSE
    const val UNITS_PER_CYCLE = SECTIONS * UNITS_PER_SECTION
    val PLATFORM_LOCS = (BASE_PLATFORM_LOC..LAST_PLATFORM_STATE_LOC).toSet()
    val PLATFORM_STATE_LOCS = (FIRST_PLATFORM_STATE_LOC..LAST_PLATFORM_STATE_LOC).toSet()

    fun advance(currentUnits: Int, chargeAmount: Int): WardenChargeCircleAdvance {
        require(currentUnits in 0 until UNITS_PER_CYCLE)
        require(chargeAmount in 1..UNITS_PER_SECTION)
        val total = currentUnits + chargeAmount
        return WardenChargeCircleAdvance(
            remainingUnits = total % UNITS_PER_CYCLE,
            displayUnits = total.coerceAtMost(UNITS_PER_CYCLE),
            completedCycle = total >= UNITS_PER_CYCLE,
        )
    }

    fun platformStateLoc(units: Int): Int =
        FIRST_PLATFORM_STATE_LOC + units.coerceIn(0, UNITS_PER_CYCLE)
}

internal object WardenPhaseTwoCadence {
    fun advancePast(currentCycle: Int, scheduledAt: Int, interval: Int): Int {
        require(interval > 0)
        if (scheduledAt > currentCycle) return scheduledAt
        val elapsed = currentCycle - scheduledAt
        return scheduledAt + (elapsed / interval + 1) * interval
    }

    fun nextMechanicAt(nextWardenAttackAt: Int, nextObeliskAttackAt: Int): Int =
        minOf(nextWardenAttackAt, nextObeliskAttackAt)
}

internal enum class WardenObeliskSpecial {
    CONVERGING_BEAM,
    WINDMILL_BEAM,
    LIGHTNING_SKULL,
}

internal enum class WardenBasicAttack {
    MELEE,
    RANGED,
    MAGIC,
}

internal data class WardenBasicAttackDefinition(
    val attack: WardenBasicAttack,
    val hitType: HitType,
    val animation: Int,
    val projectile: Int? = null,
    val impactGraphic: Int? = null,
)

internal data class WardenDivineProjectileDefinition(
    val hitType: HitType,
    val projectile: Int,
    val impactGraphic: Int,
)

/** Seeded OSRS damage rolls for attacks that are not guaranteed-damage mechanics. */
internal object WardenDamageRolls {
    const val ELIDINIS_BASIC_MAX_HIT = 20
    const val TUMEKEN_BASIC_MAX_HIT = 22
    const val DIVINE_PROJECTILE_MAX_HIT = 38
    const val PHANTOM_MAX_HIT = 28
    private const val SEQUENCE_MIX = -7046029254386353131L

    fun basicMaximum(god: WardenGod): Int =
        when (god) {
            WardenGod.ELIDINIS -> ELIDINIS_BASIC_MAX_HIT
            WardenGod.TUMEKEN -> TUMEKEN_BASIC_MAX_HIT
        }

    fun roll(maximum: Int, raidSeed: Long, sequence: Int): Int {
        require(maximum >= 0) { "maximum must not be negative" }
        if (maximum == 0) return 0
        val mixed = raidSeed xor ((sequence.toLong() + 1L) * SEQUENCE_MIX)
        return Random(mixed).nextInt(maximum + 1)
    }
}

/** Native room-local patterns for the three phase-two Obelisk specials. */
internal object WardenPhaseTwoMechanics {
    const val ARENA_MIN_X = 21
    const val ARENA_MAX_X = 43
    const val ARENA_MIN_Z = 22
    const val ARENA_MAX_Z = 46
    const val CENTER_X = 32
    const val CENTER_Z = 34
    const val STONE_DURATION_TICKS = 8
    const val STONE_PLAYER_ANIMATION = 739
    const val STONE_PROJECTILE = 2210
    const val STONE_GRAPHIC = 2211
    const val MELEE_ATTACK_ANIMATION = 9659
    const val RANGED_ATTACK_ANIMATION = 9660
    const val MAGIC_ATTACK_ANIMATION = 9661
    const val SPECIAL_ATTACK_ANIMATION = MAGIC_ATTACK_ANIMATION
    const val RANGED_PROJECTILE = 2241
    const val MAGIC_PROJECTILE = 2224
    const val MAGIC_IMPACT = 2213
    const val DIVINE_MELEE_PROJECTILE = 2204
    const val DIVINE_MELEE_IMPACT = 2205
    const val DIVINE_RANGED_PROJECTILE = 2206
    const val DIVINE_RANGED_IMPACT = 2207
    const val DIVINE_MAGIC_PROJECTILE = 2208
    const val DIVINE_MAGIC_IMPACT = 2209
    const val LIGHTNING_SKULL_PROJECTILE = 2225
    const val OBELISK_LIGHTNING_ANIMATION = 9727
    const val OBELISK_CONVERGING_BEAM_ANIMATION = 9732
    const val OBELISK_WINDMILL_ANIMATION = 9733
    const val TUMEKEN_CORE_PROJECTILE = 2239
    const val ELIDINIS_CORE_PROJECTILE = 2240
    const val CORE_EJECT_ANIMATION = 9670
    const val CORE_REVIVE_ANIMATION = 9672
    const val CORE_X_OFFSET = 4
    const val CORE_Z_OFFSET = 3
    const val BEAM_WARNING = 2234
    const val BEAM_ACTIVE = 2235
    const val WINDMILL_WARNING = 2236

    fun special(index: Int): WardenObeliskSpecial =
        WardenObeliskSpecial.entries[index.mod(WardenObeliskSpecial.entries.size)]

    fun basicAttack(distance: Int, attackCounter: Int): WardenBasicAttackDefinition =
        basicAttack(
            if (distance <= 2) WardenBasicAttack.MELEE
            else if (attackCounter.mod(2) == 0) WardenBasicAttack.MAGIC
            else WardenBasicAttack.RANGED
        )

    fun basicAttack(attack: WardenBasicAttack): WardenBasicAttackDefinition =
        when (attack) {
            WardenBasicAttack.MELEE -> {
                WardenBasicAttackDefinition(
                    WardenBasicAttack.MELEE,
                    HitType.Melee,
                    MELEE_ATTACK_ANIMATION,
                )
            }
            WardenBasicAttack.MAGIC -> {
                WardenBasicAttackDefinition(
                    WardenBasicAttack.MAGIC,
                    HitType.Magic,
                    MAGIC_ATTACK_ANIMATION,
                    MAGIC_PROJECTILE,
                    MAGIC_IMPACT,
                )
            }
            WardenBasicAttack.RANGED -> {
                WardenBasicAttackDefinition(
                    WardenBasicAttack.RANGED,
                    HitType.Ranged,
                    RANGED_ATTACK_ANIMATION,
                    RANGED_PROJECTILE,
                )
            }
        }

    fun divineProjectile(index: Int): WardenDivineProjectileDefinition =
        when (index.mod(3)) {
            0 ->
                WardenDivineProjectileDefinition(
                    HitType.Melee,
                    DIVINE_MELEE_PROJECTILE,
                    DIVINE_MELEE_IMPACT,
                )
            1 ->
                WardenDivineProjectileDefinition(
                    HitType.Ranged,
                    DIVINE_RANGED_PROJECTILE,
                    DIVINE_RANGED_IMPACT,
                )
            else ->
                WardenDivineProjectileDefinition(
                    HitType.Magic,
                    DIVINE_MAGIC_PROJECTILE,
                    DIVINE_MAGIC_IMPACT,
                )
        }

    fun obeliskAnimation(special: WardenObeliskSpecial): Int =
        when (special) {
            WardenObeliskSpecial.CONVERGING_BEAM -> OBELISK_CONVERGING_BEAM_ANIMATION
            WardenObeliskSpecial.WINDMILL_BEAM -> OBELISK_WINDMILL_ANIMATION
            WardenObeliskSpecial.LIGHTNING_SKULL -> OBELISK_LIGHTNING_ANIMATION
        }

    fun coreProjectile(god: WardenGod): Int =
        when (god) {
            WardenGod.TUMEKEN -> TUMEKEN_CORE_PROJECTILE
            WardenGod.ELIDINIS -> ELIDINIS_CORE_PROJECTILE
        }

    fun convergingBeam(step: Int, northSouth: Boolean): Set<WardenLocalTile> {
        val distance = step.coerceIn(2, 12)
        return buildSet {
            for (width in -1..1) {
                val first =
                    if (northSouth) WardenLocalTile(CENTER_X + width, CENTER_Z + distance)
                    else WardenLocalTile(CENTER_X + distance, CENTER_Z + width)
                val second =
                    if (northSouth) WardenLocalTile(CENTER_X + width, CENTER_Z - distance)
                    else WardenLocalTile(CENTER_X - distance, CENTER_Z + width)
                if (first.x in ARENA_MIN_X..ARENA_MAX_X && first.z in ARENA_MIN_Z..ARENA_MAX_Z) {
                    add(first)
                }
                if (second.x in ARENA_MIN_X..ARENA_MAX_X && second.z in ARENA_MIN_Z..ARENA_MAX_Z) {
                    add(second)
                }
            }
        }
    }

    fun windmill(step: Int): Set<WardenLocalTile> {
        val diagonal = step.mod(2) == 1
        return buildSet {
            for (distance in 2..12) {
                val offsets =
                    if (diagonal) {
                        arrayOf(
                            distance to distance,
                            -distance to distance,
                            -distance to -distance,
                            distance to -distance,
                        )
                    } else {
                        arrayOf(distance to 0, 0 to distance, -distance to 0, 0 to -distance)
                    }
                offsets.forEach { (dx, dz) ->
                    val x = CENTER_X + dx
                    val z = CENTER_Z + dz
                    if (x in ARENA_MIN_X..ARENA_MAX_X && z in ARENA_MIN_Z..ARENA_MAX_Z) {
                        add(WardenLocalTile(x, z))
                    }
                }
            }
        }
    }

    /** The cardinal cross through the chosen tile is the lightning-skull safe lane. */
    fun lightningSkull(centerX: Int, centerZ: Int): Set<WardenLocalTile> = buildSet {
        for (dx in -3..3) {
            for (dz in -3..3) {
                if (dx == 0 || dz == 0) continue
                add(WardenLocalTile(centerX + dx, centerZ + dz))
            }
        }
    }
}

internal object WardenChargeHeadbar {
    const val PHASE_ONE_GAMEVAL = "headbar.charge_100"
    const val PHASE_TWO_GAMEVAL = "headbar.charge_140"
    const val FALLBACK_GAMEVAL = "headbar.charge_160"
    const val CACHE_FALLBACK_SEGMENTS = 160

    fun gameval(maximum: Int): String =
        when (maximum) {
            100 -> PHASE_ONE_GAMEVAL
            WardenCores.PHASE_TWO_SHIELD_HITPOINTS -> PHASE_TWO_GAMEVAL
            else -> FALLBACK_GAMEVAL
        }

    fun fill(value: Int, maximum: Int, decodedSegments: Int): Int {
        val safeMaximum = maximum.coerceAtLeast(1)
        val segments = decodedSegments.takeIf { it in 1..255 } ?: CACHE_FALLBACK_SEGMENTS
        return ((value.coerceIn(0, safeMaximum).toLong() * segments) / safeMaximum)
            .toInt()
            .coerceIn(0, 255)
    }
}

internal object ElidinisWarden {
    const val INACTIVE = 11746
    const val CHARGING = 11748
    const val MAGIC_FORM = 11753
    const val RANGED_FORM = 11754
    const val CORE_EJECTED = 11755
    const val DESTROYED = 11759
    const val FINAL_FORM = 11761
    const val FINAL_INVULNERABLE = 11763
    const val ATTACKS_BEFORE_SPECIAL = 7

    fun requiredStyle(form: Int): HitType? =
        when (form) {
            MAGIC_FORM -> HitType.Magic
            RANGED_FORM -> HitType.Ranged
            else -> null
        }
}

internal object TumekenWarden {
    const val INACTIVE = 11747
    const val CHARGING = 11749
    const val RANGED_FORM = 11756
    const val MAGIC_FORM = 11757
    const val CORE_EJECTED = 11758
    const val DESTROYED = 11760
    const val FINAL_FORM = 11762
    const val FINAL_INVULNERABLE = 11764
    const val FINAL_BASE_HITPOINTS = 880
    const val NPC = FINAL_FORM
    const val ZEBAK_PHANTOM = 11774
    const val BABA_PHANTOM = 11775
    const val KEPHRI_PHANTOM = 11776
    const val AKKHA_PHANTOM = 11777
    val siphonThresholds = intArrayOf(80, 60, 40, 20)
    const val ENRAGE_PERCENT = 5
    const val ENRAGE_HEAL_PERCENT = 20

    fun requiredStyle(form: Int): HitType? =
        when (form) {
            RANGED_FORM -> HitType.Ranged
            MAGIC_FORM -> HitType.Magic
            else -> null
        }

    fun phantomIds(god: WardenGod): IntArray =
        if (god == WardenGod.TUMEKEN) {
            intArrayOf(ZEBAK_PHANTOM, BABA_PHANTOM)
        } else {
            intArrayOf(AKKHA_PHANTOM, KEPHRI_PHANTOM)
        }

    fun phantomIndexForSiphonPhase(phase: Int): Int? =
        when (phase) {
            1 -> 0 // 60% siphon
            2 -> 1 // 40% siphon
            else -> null
        }

    fun clampDamageToNextTransition(
        currentHitpoints: Int,
        maximumHitpoints: Int,
        requestedDamage: Int,
        siphonsSent: Int,
        enraged: Boolean,
    ): Int {
        if (requestedDamage <= 0 || currentHitpoints <= 0) return 0
        val maximum = maximumHitpoints.coerceAtLeast(1)
        val percent =
            if (enraged) null else siphonThresholds.getOrNull(siphonsSent) ?: ENRAGE_PERCENT
        if (percent == null) return requestedDamage.coerceAtMost(currentHitpoints)
        val transitionHp = (maximum.toLong() * percent / 100L).toInt().coerceAtLeast(1)
        if (currentHitpoints <= transitionHp) return 0
        return requestedDamage.coerceAtMost(currentHitpoints - transitionHp)
    }
}

internal object WardenPhaseTwoShieldHud {
    val HITMARK = hitmark_groups.poise_damage
    val STYLE =
        BossHpBarStyle(
            inner = Color(255, 218, 0),
            back = Color(48, 53, 4),
            sliding = Color(255, 236, 80),
            forceExactValues = true,
        )

    fun currentValue(charge: Int): Int = charge.coerceIn(0, WardenCores.PHASE_TWO_SHIELD_HITPOINTS)
}

internal object WardenCores {
    const val ELIDINIS = 11771
    const val TUMEKEN = 11770
    const val NPC = ELIDINIS
    const val DAMAGE_MULTIPLIER = 5
    const val PHASE_TWO_TRUE_HITPOINTS = 4_500
    const val PHASE_TWO_SHIELD_HITPOINTS = 140
    const val POINTED_DOWNS = 3

    fun exposureTicks(remainingPercent: Int): Int =
        when {
            remainingPercent >= 80 -> 21
            remainingPercent >= 60 -> 29
            else -> 37
        }

    fun coreHitpoints(remainingWardenHitpoints: Int): Int =
        ((remainingWardenHitpoints.coerceAtLeast(1) + DAMAGE_MULTIPLIER - 1) / DAMAGE_MULTIPLIER)
            .coerceAtLeast(1)

    fun wardenDamage(coreDamage: Int): Int = coreDamage.coerceAtLeast(0) * DAMAGE_MULTIPLIER

    fun shieldDamage(currentCharge: Int, requestedDamage: Int): Int =
        requestedDamage.coerceAtLeast(0).coerceAtMost(PHASE_TWO_SHIELD_HITPOINTS - currentCharge)

    fun hiddenHitpointsAfterCoreDamage(remainingHitpoints: Int, coreDamage: Int): Int =
        (remainingHitpoints - wardenDamage(coreDamage)).coerceAtLeast(0)

    fun shieldCharge(currentHitpoints: Int, maximumHitpoints: Int): Int =
        (maximumHitpoints - currentHitpoints).coerceIn(0, maximumHitpoints.coerceAtLeast(0))
}

internal object WardenEnergySiphons {
    const val ACTIVE = 11772
    const val DISABLED = 11773
    const val OUTWARD_PROJECTILE = 2226
    const val LINK_PROJECTILE = 2227
    const val LANDING_GRAPHIC = 1447
    const val FAILURE_GRAPHIC = 2158
    const val RETURN_ANIMATION = 9680
    const val FAILURE_ANIMATION = 9681
    const val LINK_INTERVAL_TICKS = 2
    const val RETURN_TRAVEL_TICKS = 2
    const val RETURN_LOCAL_X = 32
    const val RETURN_LOCAL_Z = 36

    const val SPAWN_DELAY_TICKS = 6
    const val LINK_DELAY_TICKS = 7

    fun releasesAttackDelay(type: HitType): Boolean = type == HitType.Melee

    private val SOLO_LAYOUTS =
        arrayOf(
            listOf(
                WardenLocalTile(29, 42),
                WardenLocalTile(32, 44),
                WardenLocalTile(35, 42),
                WardenLocalTile(32, 40),
            ),
            listOf(
                WardenLocalTile(28, 44),
                WardenLocalTile(30, 41),
                WardenLocalTile(32, 44),
                WardenLocalTile(34, 41),
                WardenLocalTile(36, 44),
            ),
            listOf(
                WardenLocalTile(30, 44),
                WardenLocalTile(30, 42),
                WardenLocalTile(30, 40),
                WardenLocalTile(34, 44),
                WardenLocalTile(34, 42),
                WardenLocalTile(34, 40),
            ),
            listOf(
                WardenLocalTile(26, 41),
                WardenLocalTile(28, 44),
                WardenLocalTile(30, 41),
                WardenLocalTile(32, 44),
                WardenLocalTile(34, 41),
                WardenLocalTile(36, 44),
                WardenLocalTile(38, 41),
            ),
        )

    /** Unique fallback formation for group counts while preserving the packet-proven solo sets. */
    private val GROUP_LAYOUT =
        listOf(
            WardenLocalTile(26, 41),
            WardenLocalTile(28, 44),
            WardenLocalTile(30, 41),
            WardenLocalTile(32, 44),
            WardenLocalTile(34, 41),
            WardenLocalTile(36, 44),
            WardenLocalTile(38, 41),
            WardenLocalTile(28, 39),
            WardenLocalTile(30, 37),
            WardenLocalTile(32, 40),
            WardenLocalTile(34, 37),
            WardenLocalTile(36, 39),
            WardenLocalTile(32, 36),
        )

    fun count(partySize: Int, phase: Int): Int =
        when {
            partySize <= 1 -> intArrayOf(4, 5, 6, 7)[phase.coerceIn(0, 3)]
            partySize == 2 -> intArrayOf(6, 8, 9, 10)[phase.coerceIn(0, 3)]
            else -> intArrayOf(9, 10, 13, 13)[phase.coerceIn(0, 3)]
        }

    fun hitpoints(partySize: Int): Int =
        when {
            partySize >= 8 -> 4
            partySize >= 6 -> 3
            partySize >= 4 -> 2
            else -> 1
        }

    fun localTiles(partySize: Int, phase: Int, expectedCount: Int): List<WardenLocalTile> {
        val layout =
            if (partySize <= 1) SOLO_LAYOUTS[phase.coerceIn(SOLO_LAYOUTS.indices)] else GROUP_LAYOUT
        return layout.take(expectedCount.coerceIn(0, layout.size))
    }

    /** A fully disabled Siphon set reflects five percent of the Warden's maximum health. */
    fun reflectedDamage(maximumHitpoints: Int): Int =
        (maximumHitpoints.coerceAtLeast(1) * 5 / 100).coerceAtLeast(1)
}

internal enum class WardenSlamPattern {
    RIGHT,
    LEFT,
    BOTH,
}

internal data class WardenCollapseRow(val localZ: Int, val minLocalX: Int, val maxLocalX: Int)

internal data class WardenCameraShake(
    val axis: Int,
    val random: Int,
    val amplitude: Int,
    val rate: Int,
)

internal data class WardenFloorLocChange(
    val localX: Int,
    val localZ: Int,
    val locId: Int,
    val rotation: Int,
)

internal class WardenFloorLocJournal<I, T, V> {
    private val originals = mutableMapOf<I, MutableMap<T, V>>()

    fun recordFirst(instanceId: I, tile: T, original: V) {
        val instanceOriginals = originals.getOrPut(instanceId) { linkedMapOf() }
        if (!instanceOriginals.containsKey(tile)) {
            instanceOriginals[tile] = original
        }
    }

    fun drain(instanceId: I): Map<T, V> = originals.remove(instanceId) ?: emptyMap()
}

internal data class WardenSlamRuntime(
    val pattern: WardenSlamPattern,
    val landsAt: Int,
    val sourceKey: Int,
)

internal object WardenFloor {
    const val WARNING_GRAPHIC = 1446
    const val LIGHTNING_GRAPHIC = 2197
    const val BREAK_GRAPHIC = 2220
    const val REMOVE_PROJECTILE = 2228
    const val VOID_LOCAL_X = 32
    const val VOID_LOCAL_Z = 10
    private const val BROKEN_EDGE = 45726
    private const val BROKEN_EDGE_VARIANT = 45728
    private const val FIRST_VOID = 45730
    private const val FIRST_VOID_LEFT = 45731
    private const val FIRST_VOID_RIGHT = 45732
    private const val CAP_LEFT = 45735
    private const val EDGE_END = 45736
    private const val CAP_RIGHT = 45737
    private const val VOID = 45738
    val replacementLocIds =
        setOf(45726, 45727, 45728, 45730, 45731, 45732, 45735, 45736, 45737, 45738)
    const val SLAM_LEFT_ANIMATION = 9676
    const val SLAM_RIGHT_ANIMATION = 9674
    const val SLAM_BOTH_ANIMATION = 9678

    /** Three-axis P4 room shake recorded in the revision-240 Wardens capture. */
    val collapseCameraShake =
        listOf(
            WardenCameraShake(axis = 0, random = 5, amplitude = 0, rate = 0),
            WardenCameraShake(axis = 1, random = 3, amplitude = 0, rate = 0),
            WardenCameraShake(axis = 2, random = 2, amplitude = 0, rate = 0),
        )

    val collapseRows =
        listOf(
            WardenCollapseRow(45, 22, 42),
            WardenCollapseRow(44, 22, 42),
            WardenCollapseRow(43, 22, 42),
            WardenCollapseRow(42, 23, 41),
            WardenCollapseRow(41, 24, 40),
            WardenCollapseRow(40, 25, 39),
            WardenCollapseRow(39, 26, 38),
            WardenCollapseRow(38, 27, 37),
        )
    private val finalRow = WardenCollapseRow(37, 28, 37)

    /**
     * Revision-240 ground-decoration changes used by the P4 floor collapse. The first row falls
     * into the void, the next row exposes the north cap, and each later row replaces the previous
     * broken edge with the permanent void seam. Interior edge variants are cosmetic, so their
     * deterministic selection keeps replays stable while preserving the captured loc set.
     */
    fun replacementChanges(rowIndex: Int, visualSeed: Long = 0L): List<WardenFloorLocChange> {
        val row = collapseRows.getOrNull(rowIndex) ?: return emptyList()
        if (rowIndex == 0) {
            return (row.minLocalX..row.maxLocalX).map { x ->
                val id =
                    when (x) {
                        row.minLocalX -> FIRST_VOID_LEFT
                        row.maxLocalX -> FIRST_VOID_RIGHT
                        else -> FIRST_VOID
                    }
                WardenFloorLocChange(x, row.localZ, id, 1)
            }
        }
        return buildList {
            val previous = collapseRows[rowIndex - 1]
            if (rowIndex == 1) {
                for (x in previous.minLocalX..previous.maxLocalX) {
                    val id =
                        when (x) {
                            previous.minLocalX -> CAP_LEFT
                            previous.maxLocalX -> CAP_RIGHT
                            else -> 45727
                        }
                    add(WardenFloorLocChange(x, previous.localZ, id, 1))
                }
            } else if (previous.minLocalX == row.minLocalX && previous.maxLocalX == row.maxLocalX) {
                for (x in previous.minLocalX..previous.maxLocalX) {
                    val (id, rotation) =
                        when (x) {
                            previous.minLocalX -> BROKEN_EDGE to 0
                            previous.maxLocalX -> BROKEN_EDGE to 2
                            else -> VOID to 0
                        }
                    add(WardenFloorLocChange(x, previous.localZ, id, rotation))
                }
            } else {
                for (x in row.minLocalX..row.maxLocalX) {
                    add(WardenFloorLocChange(x, previous.localZ, VOID, 0))
                }
            }
            for (x in row.minLocalX..row.maxLocalX) {
                val (id, rotation) =
                    when (x) {
                        row.minLocalX -> EDGE_END to 0
                        row.maxLocalX -> EDGE_END to 3
                        else ->
                            if (rowIndex > 1 && useBrokenEdgeVariant(rowIndex, x, visualSeed)) {
                                BROKEN_EDGE_VARIANT to 0
                            } else {
                                BROKEN_EDGE to 3
                            }
                    }
                add(WardenFloorLocChange(x, row.localZ, id, rotation))
            }
        }
    }

    /** RsProx LOC_ADD_CHANGE rotations are already raw LocAngle ids; do not compass-remap them. */
    fun rawLocAngle(rotation: Int): LocAngle {
        require(rotation in 0..3) { "Invalid Warden floor rotation $rotation" }
        return LocAngle[rotation]
    }

    private fun useBrokenEdgeVariant(rowIndex: Int, localX: Int, visualSeed: Long): Boolean {
        val salt =
            (visualSeed xor (rowIndex.toLong() shl 32) xor (localX.toLong() * 2_654_435_769L))
                .hashCode()
        // The accepted non-League trace uses the flatter 45728 edge on 31 of 51 later-row
        // interior tiles. A seeded three-in-five choice preserves that visual balance while
        // allowing each raid floor to have the same irregular outline as OSRS.
        return Random(salt).nextInt(5) < 3
    }

    fun replacementStateAfter(
        collapsedRowCount: Int,
        visualSeed: Long = 0L,
    ): Map<WardenLocalTile, WardenFloorLocChange> = buildMap {
        repeat(collapsedRowCount.coerceIn(0, collapseRows.size)) { rowIndex ->
            replacementChanges(rowIndex, visualSeed).forEach { change ->
                put(WardenLocalTile(change.localX, change.localZ), change)
            }
        }
    }

    fun pattern(step: Int): WardenSlamPattern =
        when (step.mod(3)) {
            0 -> WardenSlamPattern.RIGHT
            1 -> WardenSlamPattern.LEFT
            else -> WardenSlamPattern.BOTH
        }

    fun slamAnimation(pattern: WardenSlamPattern): Int =
        when (pattern) {
            WardenSlamPattern.RIGHT -> SLAM_RIGHT_ANIMATION
            WardenSlamPattern.LEFT -> SLAM_LEFT_ANIMATION
            WardenSlamPattern.BOTH -> SLAM_BOTH_ANIMATION
        }

    fun isCollapsed(rowCount: Int, localX: Int, localZ: Int): Boolean =
        collapseRows.take(rowCount.coerceAtLeast(0)).any {
            localZ == it.localZ && localX in it.minLocalX..it.maxLocalX
        }

    fun pushedLocalTile(rowCount: Int, localX: Int, localZ: Int): WardenLocalTile? {
        if (!isCollapsed(rowCount, localX, localZ)) return null
        for (z in (localZ - 1) downTo 35) {
            val x = localX.coerceIn(ARENA_PUSH_MIN_X, ARENA_PUSH_MAX_X)
            if (!isCollapsed(rowCount, x, z)) return WardenLocalTile(x, z)
        }
        return WardenLocalTile(32, 37)
    }

    fun remainingLocalTiles(rowCount: Int): Set<WardenLocalTile> =
        (collapseRows.drop(rowCount.coerceIn(0, collapseRows.size)) + finalRow).flatMapTo(
            linkedSetOf()
        ) { row ->
            (row.minLocalX..row.maxLocalX).map { x -> WardenLocalTile(x, row.localZ) }
        }

    fun lightningLocalTiles(
        rowCount: Int,
        cycle: Int,
        playerTiles: Collection<WardenLocalTile>,
    ): Set<WardenLocalTile> {
        val remaining = remainingLocalTiles(rowCount)
        if (remaining.isEmpty()) return emptySet()
        val safe = linkedSetOf<WardenLocalTile>()
        playerTiles.forEachIndexed { index, player ->
            val offsets =
                listOf(-1 to -1, 0 to -1, 1 to -1, -1 to 0, 1 to 0, -1 to 1, 0 to 1, 1 to 1)
            val start = (cycle + index).mod(offsets.size)
            offsets.indices
                .asSequence()
                .map { offsets[(start + it).mod(offsets.size)] }
                .map { (dx, dz) -> WardenLocalTile(player.x + dx, player.z + dz) }
                .firstOrNull { it in remaining && it !in playerTiles }
                ?.let(safe::add)
        }
        val candidates = remaining.filterNot { it in safe }
        val count = (candidates.size / 3).coerceAtLeast(1)
        return candidates.shuffled(Random(cycle)).take(count).toSet()
    }

    private const val ARENA_PUSH_MIN_X = 22
    private const val ARENA_PUSH_MAX_X = 42

    fun isSafe(pattern: WardenSlamPattern, arenaCenterX: Int, playerX: Int): Boolean =
        when (pattern) {
            // The final Warden faces south: its right side is the west half of the room.
            WardenSlamPattern.RIGHT -> playerX >= arenaCenterX + 1
            WardenSlamPattern.LEFT -> playerX <= arenaCenterX - 1
            WardenSlamPattern.BOTH -> abs(playerX - arenaCenterX) <= 1
        }
}

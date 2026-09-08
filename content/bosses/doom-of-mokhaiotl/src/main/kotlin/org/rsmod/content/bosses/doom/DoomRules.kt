package org.rsmod.content.bosses.doom

import org.rsmod.map.CoordGrid

/**
 * Live-game Doom combat constants.
 *
 * Values represented in the supplied rev-235 RSProx recording are tagged by deterministic tests.
 * Combat values absent from that recording remain separately documented in the capture-validation
 * ledger and must not be described as packet-verified.
 */
internal object DoomRules {
    const val ACID_MAX_HIT: Int = 7
    const val SHOCKWAVE_MIN_HIT: Int = 30
    const val SHOCKWAVE_MAX_HIT: Int = 41
    const val SHOCKWAVE_WARNING_TICKS: Int = 15
    const val POST_CAR_SHOCKWAVE_DELAY_TICKS: Int = 6
    const val SHOCKWAVE_INTERVAL_TICKS: Int = 2
    const val ROCK_EXPLOSION_TICKS: Int = 7
    const val ROCK_SHADOW_TICKS: Int = 2
    const val ROCK_DEBRIS_COUNT: Int = 8
    const val VOLATILE_EARTH_COUNT: Int = 18
    const val SHIELD_HITPOINTS: Int = 500
    /**
     * Covers the D1 entry at local (31,23), every combat tile through (40,46), and all deeper
     * entry tiles while Doom remains fixed at local (29,35). Doom's arena projectiles are not
     * blocked by temporary rubble or scenery, so NPC interaction routing must not require LOS.
     */
    const val BOSS_ARENA_ATTACK_RANGE: Int = 12
    const val BOSS_ARENA_MAX_OP_RANGE: Int = 12
    const val BOSS_ATTACK_REQUIRES_LINE_OF_SIGHT: Boolean = false
    const val SHIELD_TRANSITION_TICKS: Int = 2
    const val MELEE_CHARGE_TICKS: Int = 13
    const val SHIELD_CHARGE_TICKS: Int = 17
    const val BURROW_CHARGE_TICKS: Int = 20
    /**
     * Larvae become visible immediately, begin moving one tick later, and become attackable at T+2.
     */
    const val LARVA_MOVEMENT_DELAY_TICKS: Int = 1
    const val LARVA_ATTACKABLE_DELAY_TICKS: Int = 2
    /** Compatibility alias for encounter code which still calls the T+2 state active. */
    const val LARVA_SPAWN_TICKS: Int = 2
    /** Packet-confirmed delay from the shield becoming active to its first larva spawn. */
    const val SHIELD_FIRST_LARVA_DELAY_TICKS: Int = 5
    const val LARVA_HITPOINTS: Int = 2
    const val GIANT_LARVA_HITPOINTS: Int = 4
    const val SHIELD_STANDARD_ATTACKS: Int = 2
    const val INITIAL_SHIELD_PERCENT: Int = 75
    const val FIRST_SHOCKWAVE_TICKS: Int = 100
    const val BOSS_SPAWN_DELAY_TICKS: Int = 3
    const val BURROW_HAZARD_GRACE_TICKS: Int = 3
    const val POST_SHOCKWAVE_ATTACKS: Int = 2
    const val HOLY_WATER_HITPOINTS: Int = 32
    const val HOLY_WATER_PRAYER: Int = 24
    const val HOLY_WATER_SPECIAL_ENERGY: Int = 250

    fun hitpoints(delve: Int) =
        when (delve.coerceAtLeast(1)) {
            1 -> 525
            2 -> 550
            3 -> 575
            4 -> 600
            5 -> 625
            6 -> 650
            7 -> 650
            8 -> 675
            else -> 625
        }

    /** Packet-captured NPC combat-level overrides for the eight numbered delves. */
    fun combatLevel(delve: Int) =
        when (delve.coerceAtLeast(1)) {
            1 -> 558
            2 -> 608
            3 -> 658
            4 -> 708
            5 -> 758
            6 -> 808
            7 -> 858
            else -> 908
        }

    fun attackRate(delve: Int) =
        when {
            delve <= 2 -> 6
            delve <= 6 -> 5
            else -> 4
        }

    /** Captures show the adjacent tongue lash is always two ticks faster than a standard attack. */
    fun tongueAttackRate(delve: Int) = (attackRate(delve) - 2).coerceAtLeast(2)

    /**
     * Standard-projectile travel reaches its fastest tier at delve six. Delves three and six are
     * packet-proven; the earlier monotonic tiers are intentionally isolated for capture updates.
     */
    fun standardProjectileTravelTicks(delve: Int) =
        when (delve.coerceAtLeast(1)) {
            1 -> 6
            2 -> 5
            in 3..5 -> 4
            else -> 3
        }

    fun standardMaxHit(delve: Int) =
        when (delve.coerceAtLeast(1)) {
            1 -> 47
            2,
            3 -> 50
            4 -> 52
            5 -> 53
            6 -> 55
            7 -> 57
            else -> 65
        }

    fun beamMaxHit(delve: Int) =
        when {
            delve <= 3 -> 60
            delve <= 5 -> 80
            else -> 99
        }

    /** Max hit of standard projectiles launched by the burrowed form. */
    fun burrowMaxHit(delve: Int) =
        when (delve.coerceAtLeast(5)) {
            5 -> 0
            6 -> 55
            7 -> 56
            8 -> 58
            else -> 60
        }

    fun rockProjectiles(delve: Int) =
        when {
            delve <= 1 -> 1
            delve <= 3 -> 2
            else -> 3
        }

    fun rockSets(delve: Int) = if (delve >= 8) 2 else 1

    fun chargeTicks(state: DoomState) =
        when (state) {
            DoomState.MELEE_CHARGE -> MELEE_CHARGE_TICKS
            DoomState.SHIELDED -> SHIELD_CHARGE_TICKS
            DoomState.BURROWING -> BURROW_CHARGE_TICKS
            else -> error("No Doom beam charge is defined for $state")
        }

    /** Projectile flight time after each Rock Throw follow-up is launched. */
    fun rockFollowupTravelTicks(delve: Int): List<Int> =
        when {
            delve <= 1 -> listOf(3)
            delve <= 3 -> listOf(3, 4)
            delve <= 6 -> listOf(3, 4, 5)
            delve == 7 -> listOf(1, 2, 3)
            else -> listOf(1, 2, 3)
        }

    /** Volatile-earth/shockwave may occur in any delve when the fight runs long enough. */
    fun shockwaveEnabled(delve: Int): Boolean = delve >= 1

    /** The moving earth shield is available in every delve; its slam count scales by delve. */
    fun volatileEarthEnabled(delve: Int): Boolean = delve >= 1

    fun shockwaveSlams(delve: Int) =
        when (delve.coerceAtLeast(1)) {
            1,
            2 -> 1
            3,
            4 -> 2
            5,
            6 -> 3
            7 -> 4
            else -> 5
        }

    fun outsideLarvaCount(delve: Int) =
        when {
            delve >= 8 -> 3
            delve in 5..7 -> 2
            else -> 1
        }

    /**
     * Packet-confirmed interval window between later shield-phase larvae.
     *
     * The first shield larva does not use this range; it always uses
     * [SHIELD_FIRST_LARVA_DELAY_TICKS].
     */
    fun shieldLarvaIntervalRange(delve: Int): IntRange {
        require(delve >= 3)
        return when {
            delve <= 4 -> 7..9
            delve <= 6 -> 5..7
            else -> 4..6
        }
    }

    fun larvaReachDamage(delve: Int, demonicCharge: Int, giant: Boolean): Int {
        val cap = if (giant) 30 else if (delve >= 8) 20 else 12
        val normal = 1 + demonicCharge
        return (if (giant) normal * 3 else normal).coerceAtMost(cap)
    }

    fun larvaHeal(demonicCharge: Int, giant: Boolean): Int =
        (10 * (1 + demonicCharge)) * if (giant) 3 else 1

    fun carZooms(delve: Int) = if (delve <= 5) 2 else 3

    /**
     * Captured exact moves use distinct speed tiers: four tiles at D5-6, six at D7, and eight at
     * D8+. These are the cardinal caps used by the swept-footprint resolver.
     */
    fun carTilesPerTick(delve: Int) =
        when {
            delve >= 8 -> 8
            delve == 7 -> 6
            delve >= 5 -> 4
            else -> 0
        }

    fun carTravelTicks(delve: Int, distance: Int): Int {
        val speed = carTilesPerTick(delve)
        require(speed > 0) { "Car travel is unavailable before delve five." }
        return (distance.coerceAtLeast(0) + speed - 1) / speed
    }

    /**
     * Arena rock budgets. The delve-six/seven value is an approximately 23-rock capture count; the
     * other tiers are stable observed budgets.
     */
    fun carRockCount(delve: Int) =
        when {
            delve < 5 -> 0
            delve == 5 -> 25
            delve <= 7 -> 23
            delve == 8 -> 16
            else -> 12
        }

    fun carStandardAttacks(distance: Int) =
        when {
            distance <= 4 -> 1
            distance <= 8 -> 2
            else -> 3
        }

    fun acidTilesPerHit(delve: Int): IntRange = if (delve >= 8) 4..4 else 2..3

    fun acidSplashGraphic(dx: Int, dz: Int): Int =
        when (dx to dz) {
            0 to 1 -> 3429
            1 to 0 -> 3430
            0 to -1 -> 3431
            -1 to 0 -> 3432
            else -> error("Invalid Doom acid direction ($dx, $dz)")
        }

    fun preShockwaveAttacks(delve: Int) = if (delve >= 7) 4 else 3

    fun meleePunishDelay(delve: Int) =
        when {
            delve <= 2 -> 8
            delve <= 6 -> 7
            else -> 6
        }

    fun trampleDamage(delve: Int) =
        when {
            delve <= 5 -> 10
            delve == 6 -> 20
            delve == 7 -> 30
            else -> 40
        }

    /** OSRS quantity modifier as an integer percentage of the delve-three roll. */
    fun rewardQuantityModifierPercent(delve: Int) =
        when (delve.coerceAtLeast(1)) {
            1 -> -50
            2 -> -35
            3 -> 0
            4 -> 5
            5 -> 10
            6 -> 12
            7 -> 14
            8 -> 17
            else -> 20
        }

    /** Qn = Q3 + trunc(Q3 * Mn); Kotlin integer division truncates toward zero. */
    fun scaleRewardQuantity(delve: Int, delveThreeQuantity: Int): Int {
        require(delveThreeQuantity >= 0)
        val modifier = rewardQuantityModifierPercent(delve)
        return delveThreeQuantity + (delveThreeQuantity * modifier / 100)
    }

    /** Compatibility display value: the final percentage of a D3 quantity. */
    fun rewardQuantityPercent(delve: Int): Int = 100 + rewardQuantityModifierPercent(delve)

    fun uniqueDenominator(delve: Int): Int? =
        when (delve.coerceAtLeast(1)) {
            1 -> null
            2 -> 2500
            3 -> 2000
            4 -> 1350
            5 -> 810
            6 -> 765
            7 -> 720
            8 -> 630
            else -> 540
        }

    fun domDenominator(delve: Int): Int? =
        when (delve.coerceAtLeast(1)) {
            in 1..5 -> null
            6 -> 1000
            7 -> 750
            8 -> 500
            else -> 250
        }

    fun eliteClueDenominator(delve: Int): Int = if (delve <= 2) 75 else 50

    fun guaranteedTears(delve: Int) =
        when {
            delve < 3 -> 0
            delve == 3 -> 50
            delve == 4 -> 60
            delve == 5 -> 70
            delve == 6 -> 80
            delve == 7 -> 90
            else -> 100
        }

    /** A complete immutable rules snapshot for one delve. */
    fun profile(delve: Int): DoomDelveProfile =
        DoomDelveProfile(
            delve = delve.coerceAtLeast(1),
            hitpoints = hitpoints(delve),
            attackRateTicks = attackRate(delve),
            standardMaxHit = standardMaxHit(delve),
            standardProjectileTravelTicks = standardProjectileTravelTicks(delve),
            beamMaxHit = beamMaxHit(delve),
            burrowMaxHit = burrowMaxHit(delve),
            rockProjectiles = rockProjectiles(delve),
            rockSets = rockSets(delve),
            shockwaveSlams = shockwaveSlams(delve),
            outsideLarvaCount = outsideLarvaCount(delve),
            shieldFirstLarvaDelayTicks = if (delve >= 3) SHIELD_FIRST_LARVA_DELAY_TICKS else 0,
            shieldLarvaIntervalRangeTicks =
                if (delve >= 3) shieldLarvaIntervalRange(delve) else IntRange.EMPTY,
            carZoomCount = carZooms(delve),
            carTilesPerTick = carTilesPerTick(delve),
            carRockCount = carRockCount(delve),
            acidTilesPerHit = acidTilesPerHit(delve),
            preShockwaveAttacks = preShockwaveAttacks(delve),
            meleePunishDelayTicks = meleePunishDelay(delve),
            trampleMaxHit = trampleDamage(delve),
            meleeProjectileEnabled = delve >= 2,
            acidEnabled = delve >= 3,
            shieldEnabled = delve >= 3,
            carEnabled = delve >= 5,
            carProjectilesEnabled = delve >= 6,
            carSlamEnabled = delve >= 6,
            shockwaveEnabled = shockwaveEnabled(delve),
            volatileEarthEnabled = volatileEarthEnabled(delve),
        )

    fun formatTicks(ticks: Int): String {
        val t = ticks.coerceAtLeast(0) * 6
        return "%d:%02d.%d".format(t / 600, (t % 600) / 10, t % 10)
    }
}

internal data class DoomDelveProfile(
    val delve: Int,
    val hitpoints: Int,
    val attackRateTicks: Int,
    val standardMaxHit: Int,
    val standardProjectileTravelTicks: Int,
    val beamMaxHit: Int,
    val burrowMaxHit: Int,
    val rockProjectiles: Int,
    val rockSets: Int,
    val shockwaveSlams: Int,
    val outsideLarvaCount: Int,
    val shieldFirstLarvaDelayTicks: Int,
    val shieldLarvaIntervalRangeTicks: IntRange,
    val carZoomCount: Int,
    val carTilesPerTick: Int,
    val carRockCount: Int,
    val acidTilesPerHit: IntRange,
    val preShockwaveAttacks: Int,
    val meleePunishDelayTicks: Int,
    val trampleMaxHit: Int,
    val meleeProjectileEnabled: Boolean,
    val acidEnabled: Boolean,
    val shieldEnabled: Boolean,
    val carEnabled: Boolean,
    val carProjectilesEnabled: Boolean,
    val carSlamEnabled: Boolean,
    val shockwaveEnabled: Boolean,
    val volatileEarthEnabled: Boolean,
)

internal enum class DoomLarvaContext {
    OUTSIDE_SHIELD,
    SHIELD,
}

internal data class DoomLarvaProfile(
    val giant: Boolean,
    val hitpoints: Int,
    val demonicChargeGain: Int,
)

internal object DoomLarvaRules {
    fun profile(delve: Int, context: DoomLarvaContext): DoomLarvaProfile {
        // The launch-video progression uses three ordinary larvae at delve 8.
        // Keep the later giant-larva variant out of this baseline state machine.
        val giant = false
        return DoomLarvaProfile(
            giant = giant,
            hitpoints = if (giant) DoomRules.GIANT_LARVA_HITPOINTS else DoomRules.LARVA_HITPOINTS,
            demonicChargeGain = 1,
        )
    }

    fun styleOptions(delve: Int, context: DoomLarvaContext): List<DoomAttackStyle?> =
        when {
            delve <= 3 -> listOf(null)
            context == DoomLarvaContext.SHIELD -> DoomAttackStyle.entries
            else -> listOf(DoomAttackStyle.RANGED, DoomAttackStyle.MAGIC)
        }

    fun headIconIndex(requiredStyle: DoomAttackStyle?, protectedStyle: DoomAttackStyle?): Int? =
        when {
            protectedStyle == DoomAttackStyle.MELEE -> 0
            protectedStyle == DoomAttackStyle.RANGED -> 1
            protectedStyle == DoomAttackStyle.MAGIC -> 2
            requiredStyle == DoomAttackStyle.MELEE -> 6
            requiredStyle == DoomAttackStyle.MAGIC -> 7
            requiredStyle == DoomAttackStyle.RANGED -> 8
            else -> null
        }

    /** Returns all active protection icons in stable melee/ranged/magic order. */
    fun headIconIndices(
        requiredStyle: DoomAttackStyle?,
        protectedStyles: Set<DoomAttackStyle>,
    ): List<Int> =
        if (requiredStyle != null) {
            // Coloured larvae expose the one attack style that can hurt them. The two
            // protections are implicit and must not replace this with raw prayer icons.
            listOfNotNull(headIconIndex(requiredStyle, null))
        } else {
            listOf(DoomAttackStyle.MELEE, DoomAttackStyle.RANGED, DoomAttackStyle.MAGIC)
                .filter { it in protectedStyles }
                .mapNotNull { headIconIndex(null, it) }
        }

    /** Delve one is neutral and unprotected; only delves two and three roll a protection. */
    fun protectionEnabled(delve: Int): Boolean = delve in 2..3

    /** Delves four and above protect two styles; the larva colour shows the remaining style. */
    fun twoProtectionEnabled(delve: Int): Boolean = delve >= 4

    fun hazardsSuppressed(burrowStartCycle: Int, currentCycle: Int): Boolean =
        currentCycle < burrowStartCycle + DoomRules.BURROW_HAZARD_GRACE_TICKS

    /** The eight boulder anchors immediately outside Doom's square footprint. */
    fun bossSurroundingRockAnchors(bossSouthWest: CoordGrid, bossSize: Int): List<CoordGrid> {
        require(bossSize > 0)
        val east = bossSouthWest.x + bossSize - 1
        val north = bossSouthWest.z + bossSize - 1
        val centreX = bossSouthWest.x + bossSize / 2
        val centreZ = bossSouthWest.z + bossSize / 2
        return listOf(
            bossSouthWest.translate(-1, -1),
            CoordGrid(centreX, bossSouthWest.z - 1, bossSouthWest.level),
            CoordGrid(east + 1, bossSouthWest.z - 1, bossSouthWest.level),
            CoordGrid(east + 1, centreZ, bossSouthWest.level),
            CoordGrid(east + 1, north + 1, bossSouthWest.level),
            CoordGrid(centreX, north + 1, bossSouthWest.level),
            CoordGrid(bossSouthWest.x - 1, north + 1, bossSouthWest.level),
            CoordGrid(bossSouthWest.x - 1, centreZ, bossSouthWest.level),
        )
    }

    /**
     * Live's stuck-larva exception is about Doom being boxed by boulders, not the larva being
     * unable to path around its own tile.
     */
    fun isBossSurroundedByRocks(
        bossSouthWest: CoordGrid,
        bossSize: Int,
        rockTiles: Set<CoordGrid>,
    ): Boolean =
        bossSurroundingRockAnchors(bossSouthWest, bossSize).all { anchor -> anchor in rockTiles }

    fun shouldDespawn(
        spawnCycle: Int,
        currentCycle: Int,
        bossRepositioned: Boolean,
        bossSurroundedByRocks: Boolean,
    ): Boolean =
        currentCycle - spawnCycle >= STUCK_DESPAWN_TICKS &&
            (bossRepositioned || bossSurroundedByRocks)

    const val STUCK_DESPAWN_TICKS: Int = 34

    fun explosionTiles(origin: CoordGrid, giant: Boolean): Set<CoordGrid> {
        val offsets = if (giant) -1..2 else -1..1
        return buildSet {
            offsets.forEach { x -> offsets.forEach { z -> add(origin.translate(x, z)) } }
        }
    }
}

internal object DoomAccuracyRules {
    /**
     * Returns the damage range after the caller decides whether accuracy is explicitly guaranteed.
     */
    fun damageRange(maxHit: Int, guaranteedAccuracy: Boolean): IntRange {
        require(maxHit >= 0)
        return when {
            maxHit == 0 -> 0..0
            guaranteedAccuracy -> 0..maxHit
            else -> 0..maxHit
        }
    }
}

internal object DoomRockFollowupRules {
    /** Rock Throw follow-ups cannot miss, independently of Doom's standard-attack accuracy. */
    const val GUARANTEED_ACCURACY: Boolean = true

    fun alternatingStyles(first: DoomAttackStyle, count: Int): List<DoomAttackStyle> {
        require(first == DoomAttackStyle.RANGED || first == DoomAttackStyle.MAGIC)
        require(count >= 0)
        val second =
            if (first == DoomAttackStyle.RANGED) DoomAttackStyle.MAGIC else DoomAttackStyle.RANGED
        return List(count) { index -> if (index % 2 == 0) first else second }
    }

    /**
     * Returns launch and impact offsets from this volley's debris-impact cycle.
     *
     * Delves one through six retain their packet-captured simultaneous launches. At delve seven,
     * each two-tick projectile is launched one tick apart so the three alternating styles land on
     * debris+2, +3 and +4. Delve eight throws its second boulder one tick after the first; delaying
     * the second follow-up set by two further ticks produces six unique consecutive impacts rather
     * than overlapping the two sets.
     */
    fun timing(delve: Int, volleyIndex: Int): List<DoomRockFollowupTiming> {
        val volleyCount = DoomRules.rockSets(delve)
        require(volleyIndex in 0 until volleyCount) {
            "Invalid Rock Throw volley $volleyIndex for delve $delve (count=$volleyCount)."
        }
        val travelTicks = DoomRules.rockFollowupTravelTicks(delve)
        if (delve < 7) {
            return travelTicks.map { travel ->
                DoomRockFollowupTiming(
                    launchOffsetFromDebris = 1,
                    impactOffsetFromDebris = 1 + travel,
                )
            }
        }

        return travelTicks.map { travel ->
            DoomRockFollowupTiming(launchOffsetFromDebris = 1, impactOffsetFromDebris = 1 + travel)
        }
    }
}

internal data class DoomRockFollowupTiming(
    val launchOffsetFromDebris: Int,
    val impactOffsetFromDebris: Int,
) {
    init {
        require(launchOffsetFromDebris >= 0)
        require(impactOffsetFromDebris > launchOffsetFromDebris)
    }

    val travelTicks: Int
        get() = impactOffsetFromDebris - launchOffsetFromDebris
}

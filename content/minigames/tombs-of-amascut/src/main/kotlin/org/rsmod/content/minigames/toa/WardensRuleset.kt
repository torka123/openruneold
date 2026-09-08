package org.rsmod.content.minigames.toa

/**
 * Immutable Wardens mechanics captured from [ToaRaidStartSnapshot.invocations] when a raid begins.
 *
 * The individual switches are deliberately composed here instead of branching on invocation UI
 * state inside the encounter. This keeps the protected level-zero profile exact and prevents party
 * edits after entry from changing an active Wardens fight.
 */
internal data class WardensRuleset(
    val phaseOneEnergyPulseInterval: Int,
    val phaseOnePassiveChargeInterval: Int,
    val phaseOnePassiveChargeAmount: Int,
    val phaseOneChargePerOrb: Int,
    val phaseTwoWardenAttackInterval: Int,
    val phaseTwoObeliskAttackInterval: Int,
    val penetration: Boolean,
    private val penetrationDamageBonus: Int,
    val phaseThreeFloorSlamInterval: Int,
    val resetsSlamAfterSiphons: Boolean,
    private val energySiphonBaseTicks: Int,
    private val energySiphonTicksPerTarget: Int,
    val energySiphonFailureDamagePerTarget: Int,
    val phaseThreePhantomAttackInterval: Int,
    val phaseFourFirstLightningDelay: Int,
    val phaseFourLightningWarningTicks: Int,
    val phaseFourLightningInterval: Int,
    val phaseFourPhantomAttackInterval: Int,
    val phaseFourFirstFloorWarningTicks: Int,
    private val phaseFourCollapseIntervals: List<Int>,
) {
    // The visual is split into centre-channel, turning, and lane-following segments. Fifteen
    // server ticks produce a continuous slow route after each projectile segment's client-cycle
    // lead-in is accounted for.
    val phaseOneEnergyTravelTicks: Int = 15
    val phaseOneEnergyTrunkTicks: Int = 2
    val phaseOneEnergyTurnTicks: Int = 4
    val phaseOneChargeIndicatorTicks: Int = 4

    val phaseTwoFirstAttackDelay: Int = 5
    val phaseTwoRecoveryAttackDelay: Int = 4
    val phaseTwoAttacksBeforeSpecial: Int = 7

    val phaseThreeInitialSlamDelay: Int = 3
    val phaseThreeSlamWarningTicks: Int = 2
    val phaseThreeSiphonResumeDelay: Int = 9

    /** Number of phantom attacks before rotating to the other active phantom. */
    val phaseThreePhantomAttackCadence: Int = 3

    fun phaseOnePassiveCharge(elapsedTicks: Int): Int =
        elapsedTicks.coerceAtLeast(0) / phaseOnePassiveChargeInterval * phaseOnePassiveChargeAmount

    fun phaseOneOrbCharge(blocked: Boolean): Int = if (blocked) 0 else phaseOneChargePerOrb

    fun phaseTwoObeliskDamage(baseDamage: Int): Int =
        if (baseDamage <= 0) 0 else baseDamage + penetrationDamageBonus

    fun phaseTwoWardenAttackInterval(god: WardenGod): Int =
        if (phaseTwoWardenAttackInterval == 8 && god == WardenGod.ELIDINIS) 7
        else phaseTwoWardenAttackInterval

    fun energySiphonDuration(siphonCount: Int): Int =
        energySiphonBaseTicks + siphonCount.coerceAtLeast(0) * energySiphonTicksPerTarget

    fun phaseFourCollapseInterval(step: Int): Int =
        phaseFourCollapseIntervals[step.coerceIn(0, phaseFourCollapseIntervals.lastIndex)]

    /** Absolute timestamp offset for the next row collapse after phase four begins. */
    fun phaseFourCollapseOffset(row: Int): Int {
        var offset = phaseFourFirstFloorWarningTicks
        repeat(row.coerceAtLeast(0)) { step -> offset += phaseFourCollapseInterval(step) }
        return offset
    }

    internal companion object {
        private val WARDENS_INVOCATIONS =
            setOf(
                ToaInvocation.ANCIENT_HASTE,
                ToaInvocation.ACCELERATION,
                ToaInvocation.PENETRATION,
                ToaInvocation.OVERCLOCKED,
                ToaInvocation.OVERCLOCKED_2,
                ToaInvocation.INSANITY,
            )

        val BASELINE: WardensRuleset = create(emptySet())

        /** Full Wardens profile retained for deterministic encounter and cache regression tests. */
        val MAXIMUM: WardensRuleset = create(WARDENS_INVOCATIONS)

        fun from(invocations: Set<ToaInvocation>): WardensRuleset {
            if (invocations.none { it in WARDENS_INVOCATIONS }) return BASELINE
            return create(invocations)
        }

        private fun create(invocations: Set<ToaInvocation>): WardensRuleset {
            val ancientHaste = ToaInvocation.ANCIENT_HASTE in invocations
            val acceleration = ToaInvocation.ACCELERATION in invocations
            val penetration = ToaInvocation.PENETRATION in invocations
            val overclocked = ToaInvocation.OVERCLOCKED in invocations
            val overclockedTwo = ToaInvocation.OVERCLOCKED_2 in invocations
            val insanity = ToaInvocation.INSANITY in invocations

            val slamInterval =
                when {
                    insanity -> 3
                    overclockedTwo -> 4
                    overclocked -> 6
                    else -> 7
                }

            return WardensRuleset(
                // Ancient Haste doubles energy delivered by an unblocked orb. The donor's extra
                // pulse/passive timing changes are intentionally not copied because they alter the
                // visible P1 stream and are not part of the current OSRS invocation description.
                phaseOneEnergyPulseInterval = 2,
                phaseOnePassiveChargeInterval = 2,
                phaseOnePassiveChargeAmount = 2,
                phaseOneChargePerOrb = if (ancientHaste) 2 else 1,
                phaseTwoWardenAttackInterval = if (acceleration) 4 else 8,
                phaseTwoObeliskAttackInterval = if (acceleration) 30 else 40,
                penetration = penetration,
                penetrationDamageBonus = if (penetration) 5 else 0,
                phaseThreeFloorSlamInterval = slamInterval,
                resetsSlamAfterSiphons = !insanity,
                energySiphonBaseTicks = 3,
                energySiphonTicksPerTarget = if (insanity) 2 else 3,
                energySiphonFailureDamagePerTarget = if (insanity) 33 else 11,
                phaseThreePhantomAttackInterval = if (insanity) 3 else 8,
                phaseFourFirstLightningDelay = if (insanity) 1 else 5,
                phaseFourLightningWarningTicks = if (insanity) 2 else 3,
                phaseFourLightningInterval = if (insanity) 3 else 6,
                phaseFourPhantomAttackInterval = if (insanity) 3 else 5,
                phaseFourFirstFloorWarningTicks = if (insanity) 2 else 6,
                phaseFourCollapseIntervals =
                    if (insanity) listOf(8, 9, 8, 8, 8, 8, 9, 9) else List(8) { 18 },
            )
        }
    }
}

package org.rsmod.content.minigames.toa

/**
 * Pure Tombs of Amascut reward-point rules.
 *
 * This file deliberately has no dependency on [org.rsmod.game.entity.player.Player] or
 * [org.rsmod.game.entity.Npc]. The live raid bridge supplies the locked account hash, room, actual
 * post-mitigation damage and the target produced by [ToaRewardPointTargets].
 */
internal object ToaRewardPointRules {
    const val STARTING_POINTS = 5_000
    const val TOTAL_CAP = 64_000
    const val ORDINARY_ROOM_CAP = 20_000
    const val ZEBAK_ROOM_CAP = 10_000
    const val WARDENS_ROOM_CAP = 60_000
    const val MVP_POINTS_PER_PARTY_MEMBER = 300
    const val POINTED_WARDEN_PHASE_TWO_DOWNS = 3

    fun roomCap(room: ToaRewardPointRoom): Int =
        when (room) {
            ToaRewardPointRoom.ZEBAK -> ZEBAK_ROOM_CAP
            ToaRewardPointRoom.WARDENS -> WARDENS_ROOM_CAP
            else -> ORDINARY_ROOM_CAP
        }

    fun canAccrue(raidState: ToaRaidState): Boolean =
        raidState == ToaRaidState.ACTIVE || raidState == ToaRaidState.WARDENS

    fun pointsForDamage(actualDamage: Int, target: ToaRewardPointTarget): Int {
        if (actualDamage <= 0 || !target.isEligible) return 0
        return ((actualDamage.toLong() * target.kind.numerator) / target.kind.denominator)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
    }

    fun deathDeduction(totalPoints: Int): Int {
        val safeTotal = totalPoints.coerceAtLeast(0)
        return maxOf(safeTotal / 5, 1_000).coerceAtMost(safeTotal)
    }

    /** Path of Crondis grants two points for each water unit the Palm actually accepts. */
    fun crondisWaterPoints(acceptedWaterUnits: Int): Int =
        (acceptedWaterUnits.coerceAtLeast(0).toLong() * 2L)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
}

internal enum class ToaRewardPointRoom {
    SCABARAS,
    KEPHRI,
    APMEKEN,
    BABA,
    CRONDIS,
    ZEBAK,
    HET,
    AKKHA,
    WARDENS,
}

/** Revalidates delayed hits against the live encounter before they can add reward points. */
internal object ToaRewardPointImpactGuard {
    fun canAward(
        raidState: ToaRaidState,
        capturedRoom: ToaRewardPointRoom,
        currentRoom: ToaRewardPointRoom?,
        raidOwnsTarget: Boolean,
        scabarasOwnsTarget: Boolean,
        sameRaid: Boolean,
        sameTarget: Boolean,
        damage: Int,
    ): Boolean =
        ToaRewardPointRules.canAccrue(raidState) &&
            (raidOwnsTarget || scabarasOwnsTarget) &&
            currentRoom == capturedRoom &&
            sameRaid &&
            sameTarget &&
            damage > 0
}

/** Integer ratios avoid floating-point drift in the 0.5x, 1.2x, 1.5x, 2x and 2.5x rules. */
internal enum class ToaRewardPointTargetKind(val numerator: Int, val denominator: Int) {
    INELIGIBLE(0, 1),
    ORDINARY(1, 1),
    APMEKEN_BABOON(6, 5),
    BABA(2, 1),
    ZEBAK(3, 2),
    KEPHRI_SCARAB(1, 2),
    HET_SEAL(5, 2),
    WARDEN_OBELISK(3, 2),
    WARDEN_PHASE_TWO(2, 1),
    WARDEN_PHASE_THREE(5, 2),
}

internal data class ToaRewardPointTarget(
    val kind: ToaRewardPointTargetKind,
    /** Number of completed phase-two downs before this hit. Only meaningful for phase two. */
    val phaseTwoDownsCompleted: Int = 0,
) {
    val isEligible: Boolean
        get() =
            kind != ToaRewardPointTargetKind.INELIGIBLE &&
                (kind != ToaRewardPointTargetKind.WARDEN_PHASE_TWO ||
                    phaseTwoDownsCompleted in
                        0 until ToaRewardPointRules.POINTED_WARDEN_PHASE_TWO_DOWNS)
}

internal enum class ToaRewardPointWardenPhase {
    NONE,
    PHASE_TWO,
    PHASE_THREE,
}

internal data class ToaRewardPointNpcContext(
    val wardenPhase: ToaRewardPointWardenPhase = ToaRewardPointWardenPhase.NONE,
    /** Zero for the first down, one for the second and two for the third. */
    val phaseTwoDownsCompleted: Int = 0,
    /**
     * Unmapped NPCs are fail-closed unless the live raid has positively identified a real target.
     */
    val ordinaryDamageableTarget: Boolean = false,
)

/** Revision-240 NPC crosswalk plus explicit factories for non-NPC encounter bridges. */
internal object ToaRewardPointTargets {
    private val apmekenBaboons = 11709..11718
    private val babaForms = 11778..11780
    private val babaHazards = 11782..11788
    private val kephriScarabs = 11724..11726
    private val zebakLiving = setOf(11730, 11732)
    private val zebakJugs = setOf(11735, 11736)
    private val zebakCosmeticsAndHazards = (11730..11744).toSet() - zebakLiving - zebakJugs
    private val wardenPhaseTwoForms = setOf(11753, 11754, 11756, 11757)
    private val wardenPhaseThreeForms = setOf(11761, 11762)
    private val wardenEnergySiphons = setOf(11772, 11773)
    private val allWardenActors = (11746..11777).toSet()

    private val ordinaryActors = buildSet {
        // Path of Scabaras combat scarab and the real sequence obelisk.
        add(11697)
        add(11698)
        // Crondis crocodile.
        add(11705)
        // Kephri's three damageable boss forms; 11722 is the dead form.
        addAll(11719..11721)
        // Scarab swarm and eggs are ordinary 1.0x targets. Only combat scarabs are 0.5x.
        add(11723)
        add(11727)
        add(11728)
        add(11729)
        // The room baboon and damageable Zebak jugs use the default 1.0x factor.
        add(11781)
        addAll(zebakJugs)
        // Akkha's regular/final forms and damageable shadows.
        addAll(11790..11795)
        add(11797)
        add(11798)
    }

    private val explicitCosmeticsAndHazards = buildSet {
        // Scabaras' decoy/hit obelisk.
        add(11699)
        // Crondis palms are healed with water rather than damaged for points.
        addAll(11700..11704)
        // Kephri dead form.
        add(11722)
        addAll(babaHazards)
        addAll(zebakCosmeticsAndHazards)
        // Akkha dummy, trail-orb, enrage-orb and headbar actors.
        add(11789)
        add(11796)
        add(11799)
        addAll(11800..11805)
    }

    fun ordinary(): ToaRewardPointTarget = ToaRewardPointTarget(ToaRewardPointTargetKind.ORDINARY)

    fun ineligible(): ToaRewardPointTarget =
        ToaRewardPointTarget(ToaRewardPointTargetKind.INELIGIBLE)

    /** Het's Seal may be awarded by the object/mining bridge without constructing a fake NPC. */
    fun hetSeal(): ToaRewardPointTarget = ToaRewardPointTarget(ToaRewardPointTargetKind.HET_SEAL)

    fun wardenPhaseTwo(completedDownsBeforeHit: Int): ToaRewardPointTarget =
        ToaRewardPointTarget(
            kind = ToaRewardPointTargetKind.WARDEN_PHASE_TWO,
            phaseTwoDownsCompleted = completedDownsBeforeHit,
        )

    fun fromNpc(
        npcId: Int,
        context: ToaRewardPointNpcContext = ToaRewardPointNpcContext(),
    ): ToaRewardPointTarget =
        when {
            npcId in apmekenBaboons -> ToaRewardPointTarget(ToaRewardPointTargetKind.APMEKEN_BABOON)
            npcId in babaForms -> ToaRewardPointTarget(ToaRewardPointTargetKind.BABA)
            npcId in zebakLiving -> ToaRewardPointTarget(ToaRewardPointTargetKind.ZEBAK)
            npcId in kephriScarabs -> ToaRewardPointTarget(ToaRewardPointTargetKind.KEPHRI_SCARAB)
            npcId == 11706 || npcId == 11707 -> hetSeal()
            npcId in 11750..11752 -> ToaRewardPointTarget(ToaRewardPointTargetKind.WARDEN_OBELISK)
            npcId == 11770 || npcId == 11771 -> ineligible()
            npcId in wardenPhaseTwoForms ->
                if (context.wardenPhase == ToaRewardPointWardenPhase.PHASE_TWO) {
                    wardenPhaseTwo(context.phaseTwoDownsCompleted)
                } else {
                    ineligible()
                }
            npcId in wardenPhaseThreeForms ->
                if (context.wardenPhase == ToaRewardPointWardenPhase.PHASE_THREE) {
                    ToaRewardPointTarget(ToaRewardPointTargetKind.WARDEN_PHASE_THREE)
                } else {
                    ineligible()
                }
            npcId in wardenEnergySiphons -> ordinary()
            npcId in allWardenActors -> ineligible()
            npcId in explicitCosmeticsAndHazards -> ineligible()
            npcId in ordinaryActors || context.ordinaryDamageableTarget -> ordinary()
            else -> ineligible()
        }
}

internal data class ToaRewardPointAward(
    val requestedDamage: Int,
    val scaledPoints: Int,
    val awardedPoints: Int,
    val totalAfter: Int,
    val roomAfter: Int,
)

internal data class ToaRewardPointDeath(
    val applied: Boolean,
    val generation: Long,
    val deductedPoints: Int,
    val totalAfter: Int,
)

internal data class ToaRewardPointRoomSettlement(
    val applied: Boolean,
    val room: ToaRewardPointRoom,
    val generation: Long,
    val mvpAccountHash: Long?,
    val mvpBonusAwarded: Int,
    val roomScores: Map<Long, Int>,
)

internal data class ToaRewardPointSnapshot(
    val totalPoints: Map<Long, Int>,
    val roomPoints: Map<ToaRewardPointRoom, Map<Long, Int>>,
    val settledRooms: Set<ToaRewardPointRoom>,
)

/**
 * Raid-scoped mutable ledger. Its roster order is locked at construction and is the deterministic
 * tie-breaker for MVP. Generations make duplicate death callbacks harmless; a room may settle only
 * once, so delayed hits and replayed completion callbacks cannot mint points.
 */
internal class ToaRewardPointsLedger(
    participantAccountHashes: List<Long>,
    val lockedPartySize: Int = participantAccountHashes.size,
) {
    private val roster = participantAccountHashes.toList()
    private val totals = linkedMapOf<Long, Int>()
    private val roomScores = mutableMapOf<ToaRewardPointRoom, LinkedHashMap<Long, Int>>()
    private val lastDeathGeneration = mutableMapOf<Long, Long>()
    private val settledRooms = mutableSetOf<ToaRewardPointRoom>()

    init {
        require(lockedPartySize in 1..8) { "ToA party size must be between 1 and 8." }
        require(roster.size == lockedPartySize) { "The locked roster must match party size." }
        require(roster.distinct().size == roster.size) { "The locked roster contains duplicates." }
        roster.forEach { totals[it] = ToaRewardPointRules.STARTING_POINTS }
    }

    fun totalPoints(accountHash: Long): Int = participantTotal(accountHash)

    fun roomPoints(accountHash: Long, room: ToaRewardPointRoom): Int {
        requireParticipant(accountHash)
        return roomScores[room]?.get(accountHash) ?: 0
    }

    fun awardDamage(
        accountHash: Long,
        room: ToaRewardPointRoom,
        target: ToaRewardPointTarget,
        actualDamage: Int,
    ): ToaRewardPointAward {
        val totalBefore = participantTotal(accountHash)
        val roomBefore = roomPoints(accountHash, room)
        val scaled = ToaRewardPointRules.pointsForDamage(actualDamage, target)
        if (room in settledRooms || scaled == 0) {
            return ToaRewardPointAward(actualDamage, scaled, 0, totalBefore, roomBefore)
        }

        val roomRemaining = ToaRewardPointRules.roomCap(room) - roomBefore
        val awarded = minOf(scaled, roomRemaining).coerceAtLeast(0)
        if (awarded > 0) {
            scoresFor(room)[accountHash] = roomBefore + awarded
        }
        return ToaRewardPointAward(
            requestedDamage = actualDamage,
            scaledPoints = scaled,
            awardedPoints = awarded,
            totalAfter = totalBefore,
            roomAfter = roomBefore + awarded,
        )
    }

    /**
     * Adds already-calculated non-damage encounter points through the same caps and room ledger.
     */
    fun awardActionPoints(
        accountHash: Long,
        room: ToaRewardPointRoom,
        points: Int,
    ): ToaRewardPointAward =
        awardDamage(accountHash, room, ToaRewardPointTargets.ordinary(), points)

    fun applyDeath(accountHash: Long, generation: Long): ToaRewardPointDeath {
        val totalBefore = participantTotal(accountHash)
        val previousGeneration = lastDeathGeneration[accountHash]
        if (previousGeneration != null && generation <= previousGeneration) {
            return ToaRewardPointDeath(false, generation, 0, totalBefore)
        }
        lastDeathGeneration[accountHash] = generation
        val deducted = ToaRewardPointRules.deathDeduction(totalBefore)
        val totalAfter = totalBefore - deducted
        totals[accountHash] = totalAfter
        return ToaRewardPointDeath(true, generation, deducted, totalAfter)
    }

    fun settleRoom(room: ToaRewardPointRoom, generation: Long): ToaRewardPointRoomSettlement {
        if (!settledRooms.add(room)) {
            return ToaRewardPointRoomSettlement(false, room, generation, null, 0, emptyMap())
        }

        val scores = roster.associateWith { roomScores[room]?.get(it) ?: 0 }
        scores.forEach { (accountHash, roomScore) ->
            val totalBefore = participantTotal(accountHash)
            val transferred =
                minOf(roomScore, ToaRewardPointRules.TOTAL_CAP - totalBefore).coerceAtLeast(0)
            totals[accountHash] = totalBefore + transferred
        }

        val highest = scores.values.maxOrNull() ?: 0
        val mvp = roster.first { scores.getValue(it) == highest }
        val totalBefore = participantTotal(mvp)
        val requestedBonus = ToaRewardPointRules.MVP_POINTS_PER_PARTY_MEMBER * lockedPartySize
        val awardedBonus = minOf(requestedBonus, ToaRewardPointRules.TOTAL_CAP - totalBefore)
        totals[mvp] = totalBefore + awardedBonus
        roomScores.remove(room)

        return ToaRewardPointRoomSettlement(
            applied = true,
            room = room,
            generation = generation,
            mvpAccountHash = mvp,
            mvpBonusAwarded = awardedBonus,
            roomScores = scores,
        )
    }

    fun snapshot(): ToaRewardPointSnapshot =
        ToaRewardPointSnapshot(
            totalPoints = roster.associateWith(::participantTotal),
            roomPoints =
                roomScores.mapValues { (_, scores) -> roster.associateWith { scores[it] ?: 0 } },
            settledRooms = settledRooms.toSet(),
        )

    private fun participantTotal(accountHash: Long): Int =
        totals[accountHash]
            ?: throw IllegalArgumentException("Account is not in the locked roster.")

    private fun requireParticipant(accountHash: Long) {
        participantTotal(accountHash)
    }

    private fun scoresFor(room: ToaRewardPointRoom): LinkedHashMap<Long, Int> =
        roomScores.getOrPut(room) { roster.associateWithTo(linkedMapOf()) { 0 } }
}

package org.rsmod.content.minigames.toa

import kotlin.math.abs
import org.rsmod.game.hit.HitType

/**
 * Revision-240 Path of Apmeken NPCs.
 *
 * The roster IDs are verified by the existing OpenRune cache configuration. Thralls are spawned by
 * shamans and therefore never appear in [ApmekenWaves.roster].
 */
internal enum class ApmekenBaboon(val npcId: Int) {
    BRAWLER(11709),
    THROWER(11710),
    MAGE(11711),
    STRONGER_BRAWLER(11712),
    STRONGER_THROWER(11713),
    STRONGER_MAGE(11714),
    SHAMAN(11715),
    VOLATILE(11716),
    CURSED(11717),
    THRALL(11718),
}

internal data class ApmekenCombatDefinition(
    val style: ToaCombatStyle,
    val maxHit: Int,
    val attackRate: Int,
    val weakness: HitType?,
    val scripted: Boolean = false,
)

internal object ApmekenCombatRules {
    private val definitions =
        mapOf(
            ApmekenBaboon.BRAWLER to
                ApmekenCombatDefinition(ToaCombatStyle.MELEE, 5, 4, HitType.Magic),
            ApmekenBaboon.THROWER to
                ApmekenCombatDefinition(ToaCombatStyle.RANGED, 5, 4, HitType.Melee),
            ApmekenBaboon.MAGE to
                ApmekenCombatDefinition(ToaCombatStyle.MAGIC, 5, 4, HitType.Ranged),
            ApmekenBaboon.STRONGER_BRAWLER to
                ApmekenCombatDefinition(ToaCombatStyle.MELEE, 7, 4, HitType.Magic),
            ApmekenBaboon.STRONGER_THROWER to
                ApmekenCombatDefinition(ToaCombatStyle.RANGED, 7, 4, HitType.Melee),
            ApmekenBaboon.STRONGER_MAGE to
                ApmekenCombatDefinition(ToaCombatStyle.MAGIC, 7, 4, HitType.Ranged),
            ApmekenBaboon.SHAMAN to
                ApmekenCombatDefinition(
                    ToaCombatStyle.MAGIC,
                    7,
                    ApmekenLiveTuning.SHAMAN_ACTION_INTERVAL,
                    HitType.Ranged,
                    scripted = true,
                ),
            ApmekenBaboon.CURSED to
                ApmekenCombatDefinition(
                    ToaCombatStyle.MELEE,
                    0,
                    0,
                    HitType.Ranged,
                    scripted = true,
                ),
            ApmekenBaboon.VOLATILE to
                ApmekenCombatDefinition(ToaCombatStyle.MELEE, 0, 0, null, scripted = true),
            // Exact Thrall cadence/drain still requires a modern trace; this conservative profile
            // replaces the unrelated generic 15-max-hit fallback.
            ApmekenBaboon.THRALL to ApmekenCombatDefinition(ToaCombatStyle.MELEE, 5, 4, null),
        )

    fun type(npcId: Int): ApmekenBaboon? = ApmekenBaboon.entries.firstOrNull { it.npcId == npcId }

    fun definition(npcId: Int): ApmekenCombatDefinition? = type(npcId)?.let(definitions::get)

    fun receivesGuaranteedMaximum(npcId: Int, hitType: HitType): Boolean =
        definition(npcId)?.weakness == hitType
}

internal data class ApmekenWaveSpawn(val type: ApmekenBaboon, val tile: ApmekenTile)

/**
 * Current level-zero Path of Apmeken waves.
 *
 * OSRS removed the former two opening groups on 25 June 2025. The remaining eight waves use the
 * packet-captured spawn anchors from the older trace, while the current OSRS roster is
 * authoritative for which groups still spawn. Non-solo parties receive exactly one extra combat
 * baboon per wave.
 */
internal object ApmekenWaves {
    const val WAVE_COUNT = 8
    const val OVERLAP_REMAINING_BABOONS = 2

    private val specialCycle =
        listOf(ApmekenBaboon.SHAMAN, ApmekenBaboon.VOLATILE, ApmekenBaboon.CURSED)

    /** Null slots are independently seeded OSRS "special" baboons and may repeat. */
    private val soloWaveSlots =
        listOf(
            listOf(ApmekenBaboon.BRAWLER, ApmekenBaboon.BRAWLER, ApmekenBaboon.SHAMAN),
            listOf(ApmekenBaboon.THROWER, ApmekenBaboon.THROWER, ApmekenBaboon.VOLATILE),
            listOf(ApmekenBaboon.MAGE, ApmekenBaboon.CURSED, ApmekenBaboon.MAGE),
            listOf(
                null,
                null,
                null,
                ApmekenBaboon.STRONGER_THROWER,
                ApmekenBaboon.STRONGER_THROWER,
            ),
            listOf(
                ApmekenBaboon.SHAMAN,
                ApmekenBaboon.STRONGER_MAGE,
                null,
                ApmekenBaboon.STRONGER_MAGE,
                null,
            ),
            listOf(
                null,
                ApmekenBaboon.STRONGER_BRAWLER,
                ApmekenBaboon.STRONGER_BRAWLER,
                null,
                ApmekenBaboon.SHAMAN,
            ),
            listOf(
                ApmekenBaboon.STRONGER_THROWER,
                ApmekenBaboon.CURSED,
                ApmekenBaboon.SHAMAN,
                ApmekenBaboon.STRONGER_BRAWLER,
                ApmekenBaboon.CURSED,
            ),
            listOf(ApmekenBaboon.VOLATILE, ApmekenBaboon.SHAMAN, ApmekenBaboon.VOLATILE, null, null),
        )

    private val groupExtras =
        listOf(
            ApmekenBaboon.BRAWLER,
            ApmekenBaboon.THROWER,
            ApmekenBaboon.MAGE,
            ApmekenBaboon.STRONGER_THROWER,
            ApmekenBaboon.STRONGER_MAGE,
            ApmekenBaboon.STRONGER_BRAWLER,
            ApmekenBaboon.STRONGER_THROWER,
            ApmekenBaboon.STRONGER_MAGE,
        )

    fun spawns(wave: Int, partySize: Int, seed: Long): List<ApmekenWaveSpawn> {
        require(wave in 1..WAVE_COUNT) { "Apmeken wave must be in 1..$WAVE_COUNT: $wave" }
        require(partySize in 1..8) { "Apmeken party size must be in 1..8: $partySize" }
        val roster =
            soloWaveSlots[wave - 1]
                .mapIndexed { ordinal, fixed -> fixed ?: special(seed, wave, ordinal) }
                .toMutableList()
        if (partySize > 1) roster += groupExtras[wave - 1]
        val tiles = ApmekenPacketWaveLayout.tiles(wave, includeGroupExtra = partySize > 1)
        check(roster.size == tiles.size)
        return roster.zip(tiles, ::ApmekenWaveSpawn)
    }

    fun roster(wave: Int, partySize: Int, seed: Long): List<ApmekenBaboon> =
        spawns(wave, partySize, seed).map(ApmekenWaveSpawn::type)

    /** Waves one through seven overlap at the current foe threshold; wave eight clears the room. */
    fun remainingThreshold(wave: Int): Int {
        require(wave in 1..WAVE_COUNT)
        return if (wave < WAVE_COUNT) OVERLAP_REMAINING_BABOONS else 0
    }

    fun progression(wave: Int, population: ApmekenWavePopulation): ApmekenWaveProgression {
        require(wave in 1..WAVE_COUNT)
        return when {
            wave == WAVE_COUNT && population.totalAlive == 0 ->
                ApmekenWaveProgression.CHALLENGE_COMPLETE
            wave < WAVE_COUNT && population.aliveNonThrallBaboons <= remainingThreshold(wave) ->
                ApmekenWaveProgression.START_NEXT_WAVE
            else -> ApmekenWaveProgression.WAIT
        }
    }

    private fun special(seed: Long, wave: Int, ordinal: Int): ApmekenBaboon {
        var mixed = seed + GOLDEN_GAMMA * (wave * 16L + ordinal + 1L)
        mixed = (mixed xor (mixed ushr 30)) * MIX_MULTIPLIER_ONE
        mixed = (mixed xor (mixed ushr 27)) * MIX_MULTIPLIER_TWO
        mixed = mixed xor (mixed ushr 31)
        return specialCycle[mixed.floorMod(specialCycle.size.toLong()).toInt()]
    }

    private fun Long.floorMod(divisor: Long): Long = ((this % divisor) + divisor) % divisor

    private const val GOLDEN_GAMMA = -7046029254386353131L
    private const val MIX_MULTIPLIER_ONE = -4658895280553007687L
    private const val MIX_MULTIPLIER_TWO = -7723592293110705685L
}

internal data class ApmekenWavePopulation(val aliveNonThrallBaboons: Int, val aliveThralls: Int) {
    init {
        require(aliveNonThrallBaboons >= 0)
        require(aliveThralls >= 0)
    }

    val totalAlive: Int
        get() = aliveNonThrallBaboons + aliveThralls
}

internal enum class ApmekenWaveProgression {
    WAIT,
    START_NEXT_WAVE,
    CHALLENGE_COMPLETE,
}

internal enum class ApmekenQteType {
    ROOF_SUPPORTS,
    VENTS,
    CORRUPTION,
}

internal enum class ApmekenQteOutcome {
    PROGRESS,
    QTE_COMPLETED,
    ALREADY_DONE,
    FALSE_ACTION,
    RESOLVED,
    FAILED,
    NO_ACTIVE_QTE,
}

internal enum class ApmekenQtePenalty {
    FALSE_ACTION,
    MISSED_ACTIONS,
}

internal data class ApmekenQteSnapshot(
    val type: ApmekenQteType,
    val sightPlayer: Int,
    val requiredActions: Int,
    val completedActions: Int,
    val completedFixtures: Set<Int>,
    val corruptedPlayers: Set<Int>,
    val complete: Boolean,
)

internal data class ApmekenQteResult(
    val outcome: ApmekenQteOutcome,
    val snapshot: ApmekenQteSnapshot?,
    val penalty: ApmekenQtePenalty? = null,
    val missedActions: Int = 0,
    val affectedPlayers: Set<Int> = emptySet(),
    /** Unscaled level-zero damage. Runtime applies the locked raid/path damage multiplier. */
    val baseDamagePerPlayer: Int = 0,
)

internal data class ApmekenPlayerRetirement(
    val previousSight: Int?,
    val currentSight: Int?,
    val cancelledQte: ApmekenQteSnapshot?,
)

private data class MutableApmekenQte(
    val type: ApmekenQteType,
    val sightPlayer: Int,
    val requiredActions: Int,
    val completedFixtures: MutableSet<Int> = linkedSetOf(),
    val corruptedPlayers: MutableSet<Int> = linkedSetOf(),
    val initialCorruptedPlayers: Int = corruptedPlayers.size,
    var complete: Boolean = false,
) {
    val completedActions: Int
        get() =
            when (type) {
                ApmekenQteType.ROOF_SUPPORTS,
                ApmekenQteType.VENTS -> completedFixtures.size
                ApmekenQteType.CORRUPTION -> initialCorruptedPlayers - corruptedPlayers.size
            }

    fun snapshot(): ApmekenQteSnapshot =
        ApmekenQteSnapshot(
            type = type,
            sightPlayer = sightPlayer,
            requiredActions = requiredActions,
            completedActions = completedActions,
            completedFixtures = completedFixtures.toSet(),
            corruptedPlayers = corruptedPlayers.toSet(),
            complete = complete,
        )
}

/**
 * Pure level-zero controller for Apmeken's Sight and room quick-time events.
 *
 * Player IDs are party-slot indexes. The first entrant receives Sight; deterministic rotation never
 * chooses the current holder while another active player is available. Runtime cadence remains
 * outside this model.
 */
internal class ApmekenChallengeModel(private val seed: Long, val partySize: Int) {
    init {
        require(partySize in 1..8) { "Apmeken party size must be in 1..8: $partySize" }
    }

    private val partyPlayers = (0 until partySize).toSet()
    private val enteredPlayers = linkedSetOf<Int>()
    private val retiredPlayers = linkedSetOf<Int>()
    private var sightRng = ApmekenRng(seed xor SIGHT_SALT)
    private var qteRng = ApmekenRng(seed xor QTE_SALT)
    private var mutableActiveQte: MutableApmekenQte? = null

    var sightPlayer: Int? = null
        private set

    val activeQte: ApmekenQteSnapshot?
        get() = mutableActiveQte?.snapshot()

    private val activePartyPlayers: Set<Int>
        get() = partyPlayers - retiredPlayers

    fun enter(player: Int): Int {
        requirePlayer(player)
        enteredPlayers += player
        if (sightPlayer == null) sightPlayer = player
        return checkNotNull(sightPlayer)
    }

    /** Permanently removes a raid slot without treating an in-flight QTE as a failure. */
    fun retire(player: Int): ApmekenPlayerRetirement {
        require(player in partyPlayers) { "Unknown Apmeken player slot: $player" }
        if (!retiredPlayers.add(player)) {
            return ApmekenPlayerRetirement(sightPlayer, sightPlayer, null)
        }
        val previousSight = sightPlayer
        val cancelledQte = activeQte
        mutableActiveQte = null
        enteredPlayers.remove(player)
        if (previousSight == player) sightPlayer = enteredPlayers.firstOrNull()
        return ApmekenPlayerRetirement(previousSight, sightPlayer, cancelledQte)
    }

    fun rotateSight(activePlayers: Set<Int> = enteredPlayers): Int {
        require(activePlayers.isNotEmpty()) { "Apmeken Sight needs an active player." }
        require(activePlayers.all(partyPlayers::contains))
        require(activePlayers.all(enteredPlayers::contains))
        check(mutableActiveQte == null) { "Apmeken Sight cannot rotate during an active QTE." }

        val current = sightPlayer
        val candidates =
            if (activePlayers.size > 1 && current in activePlayers) {
                activePlayers.filterNot { it == current }.sorted()
            } else {
                activePlayers.sorted()
            }
        sightPlayer = candidates[sightRng.nextInt(candidates.size)]
        return checkNotNull(sightPlayer)
    }

    fun beginNextQte(): ApmekenQteSnapshot {
        val available =
            if (activePartyPlayers.size == 1) {
                listOf(ApmekenQteType.ROOF_SUPPORTS, ApmekenQteType.VENTS)
            } else {
                ApmekenQteType.entries
            }
        return beginQte(available[qteRng.nextInt(available.size)])
    }

    /**
     * Starts an explicit QTE for deterministic replay/restoration. Normal runtime flow should use
     * [beginNextQte].
     */
    fun beginQte(type: ApmekenQteType): ApmekenQteSnapshot {
        check(mutableActiveQte == null) { "An Apmeken QTE is already active." }
        val sight = requireNotNull(sightPlayer) { "Apmeken's Sight has not been assigned." }
        val activePlayers = activePartyPlayers
        require(type != ApmekenQteType.CORRUPTION || activePlayers.size > 1) {
            "Corruption is group-only."
        }
        val required =
            when (type) {
                ApmekenQteType.ROOF_SUPPORTS,
                ApmekenQteType.VENTS -> activePlayers.size.coerceAtMost(FIXTURE_COUNT)
                ApmekenQteType.CORRUPTION -> activePlayers.size - 1
            }
        val corrupted =
            if (type == ApmekenQteType.CORRUPTION) {
                (activePlayers - sight).toMutableSet()
            } else {
                linkedSetOf()
            }
        mutableActiveQte =
            MutableApmekenQte(
                type = type,
                sightPlayer = sight,
                requiredActions = required,
                corruptedPlayers = corrupted,
                initialCorruptedPlayers = corrupted.size,
            )
        return checkNotNull(activeQte)
    }

    fun actOnFixture(player: Int, type: ApmekenQteType, fixture: Int): ApmekenQteResult {
        requirePlayer(player)
        require(fixture in 0 until FIXTURE_COUNT)
        val state = mutableActiveQte ?: return falseAction(player, null)
        if (state.complete) return result(ApmekenQteOutcome.ALREADY_DONE, state)
        if (
            type == ApmekenQteType.CORRUPTION ||
                state.type != type ||
                state.type == ApmekenQteType.CORRUPTION
        ) {
            return falseAction(player, state)
        }
        if (!state.completedFixtures.add(fixture)) {
            return result(ApmekenQteOutcome.ALREADY_DONE, state)
        }
        if (state.completedFixtures.size >= state.requiredActions) state.complete = true
        return result(
            if (state.complete) {
                ApmekenQteOutcome.QTE_COMPLETED
            } else {
                ApmekenQteOutcome.PROGRESS
            },
            state,
        )
    }

    fun cleanseCorruption(player: Int, target: Int): ApmekenQteResult {
        requirePlayer(player)
        requirePlayer(target)
        val state = mutableActiveQte ?: return falseAction(player, null)
        if (state.complete) return result(ApmekenQteOutcome.ALREADY_DONE, state)
        if (state.type != ApmekenQteType.CORRUPTION || player != state.sightPlayer) {
            return falseAction(player, state)
        }
        if (!state.corruptedPlayers.remove(target)) {
            return result(ApmekenQteOutcome.ALREADY_DONE, state)
        }
        if (state.corruptedPlayers.isEmpty()) state.complete = true
        return result(
            if (state.complete) {
                ApmekenQteOutcome.QTE_COMPLETED
            } else {
                ApmekenQteOutcome.PROGRESS
            },
            state,
        )
    }

    /** Cleanses every corrupted teammate sharing the supplied tile with the Sight holder. */
    fun cleanseCorruptionStack(player: Int, playersOnTile: Set<Int>): ApmekenQteResult {
        requirePlayer(player)
        require(playersOnTile.all(partyPlayers::contains))
        require(player in playersOnTile) { "The Sight holder must share the cleansing tile." }
        val state = mutableActiveQte ?: return falseAction(player, null)
        if (state.complete) return result(ApmekenQteOutcome.ALREADY_DONE, state)
        if (state.type != ApmekenQteType.CORRUPTION || player != state.sightPlayer) {
            return falseAction(player, state)
        }
        val cleansed = state.corruptedPlayers.intersect(playersOnTile)
        if (cleansed.isEmpty()) return result(ApmekenQteOutcome.ALREADY_DONE, state)
        state.corruptedPlayers.removeAll(cleansed)
        if (state.corruptedPlayers.isEmpty()) state.complete = true
        return result(
            if (state.complete) {
                ApmekenQteOutcome.QTE_COMPLETED
            } else {
                ApmekenQteOutcome.PROGRESS
            },
            state,
        )
    }

    fun resolveQte(): ApmekenQteResult {
        val state =
            mutableActiveQte
                ?: return ApmekenQteResult(
                    outcome = ApmekenQteOutcome.NO_ACTIVE_QTE,
                    snapshot = null,
                )
        val snapshot = state.snapshot()
        mutableActiveQte = null
        if (state.complete) {
            return ApmekenQteResult(outcome = ApmekenQteOutcome.RESOLVED, snapshot = snapshot)
        }

        val missed = (state.requiredActions - state.completedActions).coerceAtLeast(0)
        val damage =
            (BASE_QTE_FAILURE_DAMAGE * missed + state.requiredActions - 1) / state.requiredActions
        return ApmekenQteResult(
            outcome = ApmekenQteOutcome.FAILED,
            snapshot = snapshot,
            penalty = ApmekenQtePenalty.MISSED_ACTIONS,
            missedActions = missed,
            affectedPlayers = activePartyPlayers,
            baseDamagePerPlayer = damage,
        )
    }

    fun reset() {
        enteredPlayers.clear()
        retiredPlayers.clear()
        sightPlayer = null
        mutableActiveQte = null
        sightRng = ApmekenRng(seed xor SIGHT_SALT)
        qteRng = ApmekenRng(seed xor QTE_SALT)
    }

    private fun falseAction(player: Int, state: MutableApmekenQte?): ApmekenQteResult =
        ApmekenQteResult(
            outcome = ApmekenQteOutcome.FALSE_ACTION,
            snapshot = state?.snapshot(),
            penalty = ApmekenQtePenalty.FALSE_ACTION,
            affectedPlayers = setOf(player),
            baseDamagePerPlayer = BASE_QTE_FAILURE_DAMAGE,
        )

    private fun result(outcome: ApmekenQteOutcome, state: MutableApmekenQte): ApmekenQteResult =
        ApmekenQteResult(outcome = outcome, snapshot = state.snapshot())

    private fun requirePlayer(player: Int) {
        require(player in partyPlayers && player !in retiredPlayers) {
            "Apmeken player slot is outside the active party: $player"
        }
    }

    private companion object {
        const val FIXTURE_COUNT = 4
        const val BASE_QTE_FAILURE_DAMAGE = 22
        const val SIGHT_SALT = 0x41504D01L
        const val QTE_SALT = 0x41504D02L
    }
}

internal enum class ApmekenShamanAction {
    SUMMON_THRALL,
    MAGIC_ATTACK,
}

/**
 * A shaman receives a deterministic four-or-five thrall lifetime cap. Poison or venom suppresses
 * summoning immediately; reaching the cap permanently changes future choices to magic attacks.
 */
internal class ApmekenShamanState(seed: Long, spawnOrdinal: Int) {
    init {
        require(spawnOrdinal >= 0)
    }

    val thrallLimit: Int =
        4 +
            ApmekenRng(seed xor SHAMAN_SALT xor (spawnOrdinal.toLong() * ApmekenRng.GAMMA))
                .nextInt(2)

    var thrallsSummoned: Int = 0
        private set

    fun nextAction(poisoned: Boolean, venomed: Boolean): ApmekenShamanAction =
        if (poisoned || venomed || thrallsSummoned >= thrallLimit) {
            ApmekenShamanAction.MAGIC_ATTACK
        } else {
            ApmekenShamanAction.SUMMON_THRALL
        }

    fun recordThrallSummon(): Boolean {
        if (thrallsSummoned >= thrallLimit) return false
        thrallsSummoned++
        return true
    }

    private companion object {
        const val SHAMAN_SALT = 0x41504D03L
    }
}

internal data class ApmekenTile(val x: Int, val z: Int)

internal enum class ApmekenVenomPoolPlacement {
    PLACED,
    DUPLICATE,
    SUPPRESSED,
}

/** Runtime-owned despawn callbacks remove pools from this authoritative logical set. */
internal class ApmekenVenomPools {
    private val mutablePools = linkedSetOf<ApmekenTile>()
    val pools: Set<ApmekenTile>
        get() = mutablePools.toSet()

    fun place(tile: ApmekenTile, protectedTile: Boolean): ApmekenVenomPoolPlacement {
        if (protectedTile) return ApmekenVenomPoolPlacement.SUPPRESSED
        return if (mutablePools.add(tile)) {
            ApmekenVenomPoolPlacement.PLACED
        } else {
            ApmekenVenomPoolPlacement.DUPLICATE
        }
    }

    fun remove(tile: ApmekenTile): Boolean = mutablePools.remove(tile)

    fun contains(tile: ApmekenTile): Boolean = tile in mutablePools

    fun clear() {
        mutablePools.clear()
    }

    companion object {
        const val MIN_CONTACT_DAMAGE = 6
        const val MAX_CONTACT_DAMAGE = 8

        fun contactDamage(roll: Int): Int {
            require(roll in 0..(MAX_CONTACT_DAMAGE - MIN_CONTACT_DAMAGE))
            return MIN_CONTACT_DAMAGE + roll
        }

        fun inflictsVenom(hasVenomImmunity: Boolean): Boolean = !hasVenomImmunity
    }
}

internal enum class ApmekenExplosionTarget {
    PLAYER,
    BABOON,
    PET,
    OTHER_NPC,
}

internal enum class ApmekenExplosionEffect {
    NONE,
    DAMAGE,
    INSTANT_KILL,
}

/**
 * A volatile explosion occupies its centre tile and every adjacent tile. Players take damage and
 * every other Apmeken baboon, including shamans, cursed baboons, volatiles, and thralls, is killed.
 * Pets and unrelated NPCs are not eligible.
 */
internal object ApmekenVolatileExplosion {
    const val RADIUS = 1

    fun effect(
        target: ApmekenExplosionTarget,
        deltaX: Int,
        deltaZ: Int,
        explosionSource: Boolean = false,
    ): ApmekenExplosionEffect {
        if (explosionSource || abs(deltaX) > RADIUS || abs(deltaZ) > RADIUS) {
            return ApmekenExplosionEffect.NONE
        }
        return when (target) {
            ApmekenExplosionTarget.PLAYER -> ApmekenExplosionEffect.DAMAGE
            ApmekenExplosionTarget.BABOON -> ApmekenExplosionEffect.INSTANT_KILL
            ApmekenExplosionTarget.PET,
            ApmekenExplosionTarget.OTHER_NPC -> ApmekenExplosionEffect.NONE
        }
    }
}

/** Local PRNG keeps raid replay stable across JVM and Kotlin random implementation changes. */
private class ApmekenRng(seed: Long) {
    private var state = seed

    fun nextInt(bound: Int): Int {
        require(bound > 0)
        return ((nextLong() ushr 1) % bound.toLong()).toInt()
    }

    private fun nextLong(): Long {
        state += GAMMA
        var value = state
        value = (value xor (value ushr 30)) * MIX_1
        value = (value xor (value ushr 27)) * MIX_2
        return value xor (value ushr 31)
    }

    companion object {
        const val GAMMA = -7046029254386353131L
        private const val MIX_1 = -4658895280553007687L
        private const val MIX_2 = -7723592293110705685L
    }
}

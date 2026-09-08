package org.rsmod.content.minigames.toa

internal enum class ApmekenLiveFacing {
    NORTH,
    SOUTH,
}

internal enum class ApmekenLiveAttackStyle {
    MAGIC
}

internal enum class ApmekenLiveDamageReason {
    FALSE_QTE_ACTION,
    MISSED_QTE,
    VENOM_POOL,
    VOLATILE_EXPLOSION,
}

internal enum class ApmekenLiveFeedbackCode {
    NEED_HAMMER,
    NEED_NEUTRALISING_POTION,
    STAND_ON_VENT,
    FIXTURE_ALREADY_COMPLETE,
    QTE_PROGRESS,
    QTE_COMPLETE,
    QTE_FAILED,
    NOTHING_TO_NEUTRALISE,
    SHAMAN_SUPPRESSED,
}

internal data class ApmekenNpcSpawnRequest(
    val npcKey: Long,
    val npcId: Int,
    val tile: ApmekenTile,
    val facing: ApmekenLiveFacing,
    val targetPlayer: Int?,
    val spawnSequence: Int,
    val spawnSpotanim: Int? = null,
)

internal data class ApmekenObjectInteraction(
    val player: Int,
    val locId: Int,
    /** Room-local source coordinate, not an absolute instance coordinate. */
    val tile: ApmekenTile,
    val hasHammer: Boolean = false,
    val neutralisingPotions: Int = 0,
)

internal data class ApmekenPotionInteraction(
    val player: Int,
    val target: Int,
    val neutralisingPotions: Int,
)

internal data class ApmekenPotionPourInteraction(
    val player: Int,
    val tile: ApmekenTile,
    val playersOnTile: Set<Int>,
    val neutralisingPotions: Int,
)

internal data class ApmekenNpcHitContext(
    val npcKey: Long,
    val tile: ApmekenTile,
    val remainingHitpoints: Int,
    val poisoned: Boolean = false,
    val venomed: Boolean = false,
) {
    init {
        require(remainingHitpoints >= 0)
    }
}

internal data class ApmekenLiveTickInput(
    val tick: Int,
    val activePlayers: Set<Int>,
    val playerTiles: Map<Int, ApmekenTile>,
    val npcTiles: Map<Long, ApmekenTile> = emptyMap(),
)

internal sealed interface ApmekenLiveEvent {
    data class Initialized(val partySize: Int, val seed: Long) : ApmekenLiveEvent

    data class WaveScheduled(val wave: Int, val startsAt: Int) : ApmekenLiveEvent

    data class WaveStarted(val wave: Int, val npcKeys: List<Long>) : ApmekenLiveEvent

    data class SpawnNpc(val request: ApmekenNpcSpawnRequest) : ApmekenLiveEvent

    data class KillNpcRequest(val npcKey: Long, val volatileExplosion: Boolean) : ApmekenLiveEvent

    data class DespawnNpcRequest(val npcKey: Long) : ApmekenLiveEvent

    data class SightChanged(val previousPlayer: Int?, val currentPlayer: Int?) : ApmekenLiveEvent

    data class PlayerGraphic(val player: Int, val spotanimId: Int) : ApmekenLiveEvent

    data class MapGraphic(
        val tile: ApmekenTile,
        val spotanimId: Int,
        /** Null broadcasts the graphic to the whole party. */
        val visibleTo: Int?,
        val height: Int = 0,
    ) : ApmekenLiveEvent

    data class PlayerAnimation(val player: Int, val sequenceId: Int) : ApmekenLiveEvent

    data class NpcAnimation(val npcKey: Long, val sequenceId: Int) : ApmekenLiveEvent

    data class NpcAttackRequest(
        val npcKey: Long,
        val targetPlayer: Int,
        val style: ApmekenLiveAttackStyle,
        val sequenceId: Int,
        val projectileId: Int,
        val drainPrayerByLandedDamage: Boolean,
    ) : ApmekenLiveEvent

    data class QteStarted(val snapshot: ApmekenQteSnapshot, val resolvesAt: Int) : ApmekenLiveEvent

    data class QteFeedback(
        val player: Int?,
        val type: ApmekenQteType,
        val outcome: ApmekenQteOutcome,
        val code: ApmekenLiveFeedbackCode,
    ) : ApmekenLiveEvent

    data class QteResolved(val type: ApmekenQteType, val success: Boolean, val missedActions: Int) :
        ApmekenLiveEvent

    data class QteCancelled(val type: ApmekenQteType, val retiredPlayer: Int) : ApmekenLiveEvent

    data class DamagePlayer(
        val player: Int,
        val baseDamage: Int,
        val reason: ApmekenLiveDamageReason,
        val sourceNpcKey: Long? = null,
    ) : ApmekenLiveEvent

    data class ApplyVenom(val player: Int, val severity: Int) : ApmekenLiveEvent

    data class ChangeObject(
        val tile: ApmekenTile,
        val fromLocId: Int,
        val toLocId: Int,
        val animationId: Int,
    ) : ApmekenLiveEvent

    data class SupplyTransfer(
        val player: Int,
        val itemId: Int,
        /** Positive grants an item; negative consumes it. */
        val amount: Int,
    ) : ApmekenLiveEvent

    data class Feedback(val player: Int?, val code: ApmekenLiveFeedbackCode) : ApmekenLiveEvent

    data class ShamanSuppressionChanged(
        val npcKey: Long,
        val poisoned: Boolean,
        val venomed: Boolean,
    ) : ApmekenLiveEvent

    data class SpawnVenomPool(
        val tile: ApmekenTile,
        val locId: Int,
        val angle: Int,
        val expiresAt: Int,
    ) : ApmekenLiveEvent

    data class RemoveVenomPool(val tile: ApmekenTile, val locId: Int) : ApmekenLiveEvent

    data class ChallengeComplete(val tick: Int) : ApmekenLiveEvent

    data class CleanupComplete(val npcCount: Int, val poolCount: Int) : ApmekenLiveEvent
}

internal data class ApmekenLiveActorSnapshot(
    val npcKey: Long,
    val type: ApmekenBaboon,
    val wave: Int,
    val tile: ApmekenTile,
    val alive: Boolean,
    val targetPlayer: Int?,
    val poisoned: Boolean,
    val venomed: Boolean,
    val thrallsSummoned: Int,
    val thrallLimit: Int?,
    val volatileCharging: Boolean,
)

internal data class ApmekenLiveSnapshot(
    val partySize: Int,
    val currentWave: Int,
    val pendingWaveAt: Int?,
    val sightPlayer: Int?,
    val activeQte: ApmekenQteSnapshot?,
    val qteResolvesAt: Int?,
    val venomPools: Set<ApmekenTile>,
    val actors: List<ApmekenLiveActorSnapshot>,
    val complete: Boolean,
)

/**
 * Packet-captured level-zero wave anchors from RsProx capture 14, source region 15186. Absolute
 * source tiles 3800/3808/3816 by 5272/5288 map to these six room-local coordinates.
 */
internal object ApmekenPacketWaveLayout {
    val anchors =
        listOf(
            ApmekenTile(24, 24),
            ApmekenTile(24, 40),
            ApmekenTile(32, 24),
            ApmekenTile(32, 40),
            ApmekenTile(40, 24),
            ApmekenTile(40, 40),
        )

    private val capturedSoloTiles =
        listOf(
            listOf(ApmekenTile(24, 24), ApmekenTile(40, 24), ApmekenTile(40, 40)),
            listOf(ApmekenTile(24, 24), ApmekenTile(40, 24), ApmekenTile(40, 40)),
            listOf(ApmekenTile(24, 40), ApmekenTile(32, 40), ApmekenTile(40, 40)),
            listOf(
                ApmekenTile(24, 40),
                ApmekenTile(32, 24),
                ApmekenTile(32, 40),
                ApmekenTile(40, 24),
                ApmekenTile(40, 40),
            ),
            listOf(
                ApmekenTile(24, 24),
                ApmekenTile(24, 40),
                ApmekenTile(32, 24),
                ApmekenTile(40, 24),
                ApmekenTile(40, 40),
            ),
            listOf(
                ApmekenTile(24, 40),
                ApmekenTile(32, 24),
                ApmekenTile(32, 40),
                ApmekenTile(40, 24),
                ApmekenTile(40, 40),
            ),
            listOf(
                ApmekenTile(24, 24),
                ApmekenTile(24, 40),
                ApmekenTile(32, 24),
                ApmekenTile(32, 40),
                ApmekenTile(40, 24),
            ),
            listOf(
                ApmekenTile(24, 24),
                ApmekenTile(24, 40),
                ApmekenTile(32, 24),
                ApmekenTile(40, 24),
                ApmekenTile(40, 40),
            ),
        )

    fun tiles(wave: Int, includeGroupExtra: Boolean): List<ApmekenTile> {
        require(wave in 1..ApmekenWaves.WAVE_COUNT)
        val captured = capturedSoloTiles[wave - 1]
        if (!includeGroupExtra) return captured
        return captured + checkNotNull(anchors.firstOrNull { it !in captured })
    }

    init {
        check(anchors.size == anchors.toSet().size)
        check(anchors.all(ApmekenRevision240RuntimeLayout::isWalkableFloor))
        check(capturedSoloTiles.size == ApmekenWaves.WAVE_COUNT)
        check(capturedSoloTiles.flatten().all(ApmekenRevision240RuntimeLayout::isWalkableFloor))
    }
}

internal object ApmekenLiveTuning {
    const val RAID_LEVEL = 0
    const val PATH_LEVEL = 0
    const val FIRST_WAVE_DELAY = 15
    const val NEXT_WAVE_DELAY = 4
    const val QTE_CYCLE_TICKS = 30
    const val QTE_WINDOW_TICKS = 30
    const val SHAMAN_ACTION_INTERVAL = 8
    const val CURSED_POOL_START_DELAY = 2
    const val CURSED_POOL_LIFETIME = 33
    const val VOLATILE_CHARGE_TICKS = 4
    const val VOLATILE_EXPLOSION_TICK = 3
    const val VOLATILE_BASE_DAMAGE = 14
    const val VOLATILE_DAMAGE_VARIANCE = 2
    const val VENOM_SEVERITY = 14
    const val HAMMER_ITEM = 2347
    const val NEUTRALISING_POTION_ITEM = 27297
    const val NEUTRALISING_POTION_SUPPLY_AMOUNT = 100
    const val QTE_WARNING_HEIGHT = 46
}

/**
 * Live movement rules for the Path of Apmeken actors.
 *
 * The encounter model deliberately owns wave/QTE timing, while OpenRune owns physical NPC movement.
 * Keeping the movement policy here makes the live bridge deterministic and prevents actors from
 * being left with a one-shot AP interaction or a direction that changes every tick.
 */
internal object ApmekenLiveMovementPolicy {
    const val MELEE_ATTACK_RANGE = 1
    const val PROJECTILE_ATTACK_RANGE = 8
    const val ROOM_CHASE_RANGE = 64
    const val CURSED_DIRECTION_TICKS = 6

    private val wanderDirections =
        listOf(1 to 0, 1 to 1, 0 to 1, -1 to 1, -1 to 0, -1 to -1, 0 to -1, 1 to -1)

    fun attackRange(type: ApmekenBaboon): Int =
        when (type) {
            ApmekenBaboon.BRAWLER,
            ApmekenBaboon.STRONGER_BRAWLER,
            ApmekenBaboon.THRALL -> MELEE_ATTACK_RANGE
            ApmekenBaboon.THROWER,
            ApmekenBaboon.MAGE,
            ApmekenBaboon.STRONGER_THROWER,
            ApmekenBaboon.STRONGER_MAGE,
            ApmekenBaboon.SHAMAN -> PROJECTILE_ATTACK_RANGE
            ApmekenBaboon.VOLATILE,
            ApmekenBaboon.CURSED -> 0
        }

    fun usesAutoCombat(type: ApmekenBaboon): Boolean =
        type !in setOf(ApmekenBaboon.SHAMAN, ApmekenBaboon.VOLATILE, ApmekenBaboon.CURSED)

    fun shouldRearm(
        isApPlayerMode: Boolean,
        retainedTarget: Boolean,
        retainedRange: Boolean,
    ): Boolean = !isApPlayerMode || !retainedTarget || !retainedRange

    fun pursuitCandidates(current: ApmekenTile, target: ApmekenTile): List<ApmekenTile> {
        val dx = target.x.compareTo(current.x)
        val dz = target.z.compareTo(current.z)
        if (dx == 0 && dz == 0) return emptyList()
        val xFirst = kotlin.math.abs(target.x - current.x) >= kotlin.math.abs(target.z - current.z)
        return buildList {
                if (dx != 0 && dz != 0) add(ApmekenTile(current.x + dx, current.z + dz))
                if (xFirst) {
                    if (dx != 0) add(ApmekenTile(current.x + dx, current.z))
                    if (dz != 0) add(ApmekenTile(current.x, current.z + dz))
                } else {
                    if (dz != 0) add(ApmekenTile(current.x, current.z + dz))
                    if (dx != 0) add(ApmekenTile(current.x + dx, current.z))
                }
                if (dx != 0 && dz != 0) {
                    add(ApmekenTile(current.x + dx, current.z - dz))
                    add(ApmekenTile(current.x - dx, current.z + dz))
                }
            }
            .distinct()
    }

    fun cursedWanderCandidates(current: ApmekenTile, npcKey: Long, tick: Int): List<ApmekenTile> {
        val epoch = Math.floorDiv(tick, CURSED_DIRECTION_TICKS)
        val start = Math.floorMod(npcKey + epoch.toLong(), wanderDirections.size.toLong()).toInt()
        return wanderDirections.indices.map { offset ->
            val (dx, dz) = wanderDirections[(start + offset) % wanderDirections.size]
            ApmekenTile(current.x + dx, current.z + dz)
        }
    }
}

private data class MutableApmekenLiveActor(
    val npcKey: Long,
    val type: ApmekenBaboon,
    val wave: Int,
    var tile: ApmekenTile,
    var targetPlayer: Int?,
    var alive: Boolean = true,
    var poisoned: Boolean = false,
    var venomed: Boolean = false,
    var remainingHitpoints: Int = baseHitpoints(type),
    val shaman: ApmekenShamanState? = null,
    var nextShamanActionAt: Int = Int.MAX_VALUE,
    var nextCursedPoolAt: Int = Int.MAX_VALUE,
    var volatileChargeStartedAt: Int? = null,
    var volatileAnimationSent: Boolean = false,
    var volatileExplosionSent: Boolean = false,
) {
    fun snapshot(): ApmekenLiveActorSnapshot =
        ApmekenLiveActorSnapshot(
            npcKey = npcKey,
            type = type,
            wave = wave,
            tile = tile,
            alive = alive,
            targetPlayer = targetPlayer,
            poisoned = poisoned,
            venomed = venomed,
            thrallsSummoned = shaman?.thrallsSummoned ?: 0,
            thrallLimit = shaman?.thrallLimit,
            volatileCharging = volatileChargeStartedAt != null,
        )

    companion object {
        private fun baseHitpoints(type: ApmekenBaboon): Int =
            when (type) {
                ApmekenBaboon.BRAWLER -> 25
                ApmekenBaboon.THROWER -> 30
                ApmekenBaboon.MAGE -> 20
                ApmekenBaboon.STRONGER_BRAWLER -> 30
                ApmekenBaboon.STRONGER_THROWER -> 35
                ApmekenBaboon.STRONGER_MAGE -> 25
                ApmekenBaboon.SHAMAN -> 40
                ApmekenBaboon.VOLATILE -> 8
                ApmekenBaboon.CURSED -> 20
                ApmekenBaboon.THRALL -> 9
            }
    }
}

private data class MutableApmekenLiveState(
    val seed: Long,
    val partySize: Int,
    val challenge: ApmekenChallengeModel,
    val pools: ApmekenVenomPools = ApmekenVenomPools(),
    val actors: LinkedHashMap<Long, MutableApmekenLiveActor> = linkedMapOf(),
    val enteredPlayers: MutableSet<Int> = linkedSetOf(),
    val poolExpiresAt: MutableMap<ApmekenTile, Int> = linkedMapOf(),
    val retiredPlayers: MutableSet<Int> = linkedSetOf(),
    var currentTick: Int,
    var currentWave: Int = 0,
    var pendingWaveAt: Int? = currentTick + ApmekenLiveTuning.FIRST_WAVE_DELAY,
    var nextNpcKey: Long = 1L,
    var nextQteCycleAt: Int? = null,
    var qteResolvesAt: Int? = null,
    var qtesForCurrentSight: Int = 0,
    var rotateBeforeNextQte: Boolean = false,
    var complete: Boolean = false,
) {
    fun snapshot(): ApmekenLiveSnapshot =
        ApmekenLiveSnapshot(
            partySize = partySize,
            currentWave = currentWave,
            pendingWaveAt = pendingWaveAt,
            sightPlayer = challenge.sightPlayer,
            activeQte = challenge.activeQte,
            qteResolvesAt = qteResolvesAt,
            venomPools = pools.pools,
            actors = actors.values.map { it.snapshot() },
            complete = complete,
        )
}

/**
 * Pure manager-facing Path of Apmeken controller.
 *
 * This class owns logical state only. It never adds/removes an OpenRune NPC, changes an object,
 * queues a hit, consumes an item, or sends a graphic directly. The raid manager applies returned
 * events and remains authoritative for entity ownership, routing, combat and inventory changes.
 */
internal class ToaApmekenLiveRuntime {
    private val states = mutableMapOf<Any, MutableApmekenLiveState>()

    fun initialize(
        instanceKey: Any,
        seed: Long,
        partySize: Int,
        startTick: Int,
        raidLevel: Int = ApmekenLiveTuning.RAID_LEVEL,
        pathLevel: Int = ApmekenLiveTuning.PATH_LEVEL,
    ): List<ApmekenLiveEvent> {
        require(partySize in 1..8)
        require(startTick >= 0)
        require(raidLevel in 0..600)
        require(pathLevel in 0..6)
        states.remove(instanceKey)
        val state =
            MutableApmekenLiveState(
                seed = seed,
                partySize = partySize,
                challenge = ApmekenChallengeModel(seed, partySize),
                currentTick = startTick,
            )
        states[instanceKey] = state
        return listOf(
            ApmekenLiveEvent.Initialized(partySize, seed),
            ApmekenLiveEvent.WaveScheduled(1, checkNotNull(state.pendingWaveAt)),
        )
    }

    fun enterPlayer(instanceKey: Any, player: Int, tick: Int): List<ApmekenLiveEvent> {
        val state = state(instanceKey)
        advanceClock(state, tick)
        require(player in 0 until state.partySize)
        val previous = state.challenge.sightPlayer
        val sight = state.challenge.enter(player)
        state.enteredPlayers += player
        if (
            state.enteredPlayers.isNotEmpty() &&
                state.enteredPlayers.size + state.retiredPlayers.size == state.partySize &&
                state.nextQteCycleAt == null
        ) {
            state.nextQteCycleAt = tick + ApmekenLiveTuning.QTE_CYCLE_TICKS
        }
        if (previous != null) return emptyList()
        return listOf(
            ApmekenLiveEvent.SightChanged(null, sight),
            ApmekenLiveEvent.PlayerGraphic(sight, ApmekenRevision240Assets.sightGranted.id),
        )
    }

    /**
     * Permanently retires a stable raid slot. Any active QTE is cancelled without damage, and Sight
     * transfers before the manager removes the retired player's slot mapping.
     */
    fun retirePlayer(instanceKey: Any, player: Int, tick: Int): List<ApmekenLiveEvent> {
        val state = states[instanceKey] ?: return emptyList()
        advanceClock(state, tick)
        require(player in 0 until state.partySize)
        if (!state.retiredPlayers.add(player)) return emptyList()

        val retirement = state.challenge.retire(player)
        state.enteredPlayers.remove(player)
        state.qteResolvesAt = null
        state.qtesForCurrentSight = 0
        state.rotateBeforeNextQte = false
        state.nextQteCycleAt =
            if (
                state.enteredPlayers.isNotEmpty() &&
                    state.enteredPlayers.size + state.retiredPlayers.size == state.partySize
            ) {
                tick + ApmekenLiveTuning.QTE_CYCLE_TICKS
            } else {
                null
            }

        return buildList {
            retirement.cancelledQte?.let { cancelled ->
                add(ApmekenLiveEvent.QteCancelled(cancelled.type, player))
                if (cancelled.type == ApmekenQteType.ROOF_SUPPORTS) {
                    ApmekenRevision240RuntimeLayout.roofSupports.forEach {
                        add(
                            ApmekenLiveEvent.ChangeObject(
                                it.tile,
                                ApmekenRevision240RuntimeLayout.roofSupportAlternateLoc.locId,
                                ApmekenRevision240RuntimeLayout.roofSupportLoc.locId,
                                ApmekenRevision240RuntimeLayout.roofSupportLoc.animationId,
                            )
                        )
                    }
                }
            }
            if (retirement.previousSight != retirement.currentSight) {
                retirement.previousSight?.let {
                    add(ApmekenLiveEvent.PlayerGraphic(it, ApmekenRevision240Assets.sightLost.id))
                }
                retirement.currentSight?.let {
                    add(
                        ApmekenLiveEvent.PlayerGraphic(it, ApmekenRevision240Assets.sightGranted.id)
                    )
                }
                add(
                    ApmekenLiveEvent.SightChanged(retirement.previousSight, retirement.currentSight)
                )
            }
        }
    }

    fun tick(instanceKey: Any, input: ApmekenLiveTickInput): List<ApmekenLiveEvent> {
        val state = state(instanceKey)
        advanceClock(state, input.tick)
        if (state.complete) return expirePools(state, input.tick)
        require(input.activePlayers.all { it in state.enteredPlayers })
        require(input.playerTiles.keys.all { it in state.enteredPlayers })
        input.npcTiles.forEach { (key, tile) ->
            state.actors[key]?.takeIf { it.alive }?.tile = tile
        }

        val events = mutableListOf<ApmekenLiveEvent>()
        events += expirePools(state, input.tick)
        val pending = state.pendingWaveAt
        if (pending != null && input.tick >= pending) events += startNextWave(state, input.tick)
        events += processShamans(state, input)
        events += processCursedBaboons(state, input)
        events += processVolatiles(state, input)
        events += processPoolContact(state, input)
        events += processQteCycle(state, input)
        return events
    }

    fun interactObject(
        instanceKey: Any,
        interaction: ApmekenObjectInteraction,
        tick: Int,
    ): List<ApmekenLiveEvent> {
        val state = state(instanceKey)
        advanceClock(state, tick)
        require(interaction.player in state.enteredPlayers)
        if (state.complete) return emptyList()

        return when (interaction.locId) {
            ApmekenRevision240RuntimeLayout.hammerSupplyLoc.locId ->
                listOf(
                    ApmekenLiveEvent.SupplyTransfer(
                        interaction.player,
                        ApmekenLiveTuning.HAMMER_ITEM,
                        1,
                    )
                )
            ApmekenRevision240RuntimeLayout.potionSupplyLoc.locId ->
                listOf(
                    ApmekenLiveEvent.SupplyTransfer(
                        interaction.player,
                        ApmekenLiveTuning.NEUTRALISING_POTION_ITEM,
                        ApmekenLiveTuning.NEUTRALISING_POTION_SUPPLY_AMOUNT,
                    )
                )
            ApmekenRevision240RuntimeLayout.roofSupportLoc.locId,
            ApmekenRevision240RuntimeLayout.roofSupportAlternateLoc.locId ->
                repairRoof(state, interaction)
            ApmekenRevision240RuntimeLayout.ventLoc.locId -> neutraliseVent(state, interaction)
            else -> emptyList()
        }
    }

    fun useNeutralisingPotion(
        instanceKey: Any,
        interaction: ApmekenPotionInteraction,
        tick: Int,
    ): List<ApmekenLiveEvent> {
        val state = state(instanceKey)
        advanceClock(state, tick)
        require(interaction.player in state.enteredPlayers)
        require(interaction.target in state.enteredPlayers)
        if (interaction.neutralisingPotions <= 0) {
            return listOf(
                ApmekenLiveEvent.Feedback(
                    interaction.player,
                    ApmekenLiveFeedbackCode.NEED_NEUTRALISING_POTION,
                )
            )
        }
        val events =
            mutableListOf<ApmekenLiveEvent>(
                ApmekenLiveEvent.PlayerAnimation(
                    interaction.player,
                    ApmekenRevision240Assets.POUR_PLAYER_SEQUENCE,
                ),
                ApmekenLiveEvent.SupplyTransfer(
                    interaction.player,
                    ApmekenLiveTuning.NEUTRALISING_POTION_ITEM,
                    -1,
                ),
            )
        val result = state.challenge.cleanseCorruption(interaction.player, interaction.target)
        events += qteResultEvents(state, interaction.player, ApmekenQteType.CORRUPTION, result)
        return events
    }

    /** Handles OSRS's primary potion action: click the potion while standing on the target tile. */
    fun pourNeutralisingPotion(
        instanceKey: Any,
        interaction: ApmekenPotionPourInteraction,
        tick: Int,
    ): List<ApmekenLiveEvent> {
        val state = state(instanceKey)
        advanceClock(state, tick)
        require(interaction.player in state.enteredPlayers)
        require(interaction.playersOnTile.all(state.enteredPlayers::contains))
        require(interaction.player in interaction.playersOnTile)
        if (interaction.neutralisingPotions <= 0) {
            return listOf(
                ApmekenLiveEvent.Feedback(
                    interaction.player,
                    ApmekenLiveFeedbackCode.NEED_NEUTRALISING_POTION,
                )
            )
        }

        return when (state.challenge.activeQte?.type) {
            ApmekenQteType.VENTS -> {
                val vent =
                    ApmekenRevision240RuntimeLayout.vents.firstOrNull {
                        it.tile == interaction.tile
                    }
                        ?: return listOf(
                            ApmekenLiveEvent.Feedback(
                                interaction.player,
                                ApmekenLiveFeedbackCode.STAND_ON_VENT,
                            )
                        )
                neutraliseVent(
                    state,
                    ApmekenObjectInteraction(
                        player = interaction.player,
                        locId = vent.locId,
                        tile = vent.tile,
                        neutralisingPotions = interaction.neutralisingPotions,
                    ),
                )
            }
            ApmekenQteType.CORRUPTION -> {
                val result =
                    state.challenge.cleanseCorruptionStack(
                        interaction.player,
                        interaction.playersOnTile,
                    )
                buildList {
                    add(
                        ApmekenLiveEvent.PlayerAnimation(
                            interaction.player,
                            ApmekenRevision240Assets.POUR_PLAYER_SEQUENCE,
                        )
                    )
                    add(
                        ApmekenLiveEvent.SupplyTransfer(
                            interaction.player,
                            ApmekenLiveTuning.NEUTRALISING_POTION_ITEM,
                            -1,
                        )
                    )
                    addAll(
                        qteResultEvents(
                            state,
                            interaction.player,
                            ApmekenQteType.CORRUPTION,
                            result,
                        )
                    )
                }
            }
            else ->
                listOf(
                    ApmekenLiveEvent.Feedback(
                        interaction.player,
                        ApmekenLiveFeedbackCode.NOTHING_TO_NEUTRALISE,
                    )
                )
        }
    }

    fun onNpcHit(instanceKey: Any, hit: ApmekenNpcHitContext, tick: Int): List<ApmekenLiveEvent> {
        val state = state(instanceKey)
        advanceClock(state, tick)
        val actor = state.actors[hit.npcKey] ?: return emptyList()
        if (!actor.alive) return emptyList()
        actor.tile = hit.tile
        actor.remainingHitpoints = hit.remainingHitpoints
        val changed = actor.poisoned != hit.poisoned || actor.venomed != hit.venomed
        actor.poisoned = hit.poisoned
        actor.venomed = hit.venomed
        if (actor.type != ApmekenBaboon.SHAMAN || !changed) return emptyList()
        return listOf(
            ApmekenLiveEvent.ShamanSuppressionChanged(actor.npcKey, actor.poisoned, actor.venomed),
            ApmekenLiveEvent.Feedback(null, ApmekenLiveFeedbackCode.SHAMAN_SUPPRESSED),
        )
    }

    fun onNpcKilled(instanceKey: Any, npcKey: Long, tick: Int): List<ApmekenLiveEvent> {
        val state = state(instanceKey)
        advanceClock(state, tick)
        val actor = state.actors[npcKey] ?: return emptyList()
        if (!actor.alive) return emptyList()
        actor.alive = false
        return evaluateWaveProgress(state, tick)
    }

    fun cleanup(instanceKey: Any): List<ApmekenLiveEvent> {
        val state = states.remove(instanceKey) ?: return emptyList()
        val alive = state.actors.values.filter { it.alive }
        val pools = state.pools.pools
        val events = mutableListOf<ApmekenLiveEvent>()
        alive.forEach { events += ApmekenLiveEvent.DespawnNpcRequest(it.npcKey) }
        pools.forEach {
            events +=
                ApmekenLiveEvent.RemoveVenomPool(
                    it,
                    ApmekenRevision240RuntimeLayout.venomPoolLoc.locId,
                )
        }
        ApmekenRevision240RuntimeLayout.roofSupports.forEach {
            events +=
                ApmekenLiveEvent.ChangeObject(
                    it.tile,
                    ApmekenRevision240RuntimeLayout.roofSupportAlternateLoc.locId,
                    ApmekenRevision240RuntimeLayout.roofSupportLoc.locId,
                    ApmekenRevision240RuntimeLayout.roofSupportLoc.animationId,
                )
        }
        state.challenge.sightPlayer?.let { sight ->
            events += ApmekenLiveEvent.PlayerGraphic(sight, ApmekenRevision240Assets.sightLost.id)
            events += ApmekenLiveEvent.SightChanged(sight, null)
        }
        state.pools.clear()
        state.challenge.reset()
        events += ApmekenLiveEvent.CleanupComplete(alive.size, pools.size)
        return events
    }

    fun snapshot(instanceKey: Any): ApmekenLiveSnapshot? = states[instanceKey]?.snapshot()

    fun contains(instanceKey: Any): Boolean = instanceKey in states

    private fun startNextWave(state: MutableApmekenLiveState, tick: Int): List<ApmekenLiveEvent> {
        if (state.currentWave >= ApmekenWaves.WAVE_COUNT) return emptyList()
        val wave = state.currentWave + 1
        val spawns = ApmekenWaves.spawns(wave, state.partySize, state.seed)
        state.currentWave = wave
        state.pendingWaveAt = null
        val events = mutableListOf<ApmekenLiveEvent>()
        val keys = mutableListOf<Long>()
        spawns.forEachIndexed { index, spawn ->
            val actor = createActor(state, spawn.type, wave, spawn.tile, tick, index)
            keys += actor.npcKey
            events += ApmekenLiveEvent.SpawnNpc(spawnRequest(actor, index < 3))
        }
        events.add(0, ApmekenLiveEvent.WaveStarted(wave, keys))
        return events
    }

    private fun createActor(
        state: MutableApmekenLiveState,
        type: ApmekenBaboon,
        wave: Int,
        tile: ApmekenTile,
        tick: Int,
        spawnOrdinal: Int,
        targetOverride: Int? = null,
    ): MutableApmekenLiveActor {
        val key = state.nextNpcKey++
        val actor =
            MutableApmekenLiveActor(
                npcKey = key,
                type = type,
                wave = wave,
                tile = tile,
                targetPlayer = targetOverride ?: selectTarget(state, key),
                shaman =
                    if (type == ApmekenBaboon.SHAMAN) {
                        ApmekenShamanState(state.seed, wave * 16 + spawnOrdinal)
                    } else {
                        null
                    },
                nextShamanActionAt =
                    if (type == ApmekenBaboon.SHAMAN) {
                        tick + ApmekenLiveTuning.SHAMAN_ACTION_INTERVAL
                    } else {
                        Int.MAX_VALUE
                    },
                nextCursedPoolAt =
                    if (type == ApmekenBaboon.CURSED) {
                        tick + ApmekenLiveTuning.CURSED_POOL_START_DELAY
                    } else {
                        Int.MAX_VALUE
                    },
            )
        state.actors[key] = actor
        return actor
    }

    private fun spawnRequest(
        actor: MutableApmekenLiveActor,
        facesSouth: Boolean,
        spawnSpotanim: Int? = null,
    ): ApmekenNpcSpawnRequest =
        ApmekenNpcSpawnRequest(
            npcKey = actor.npcKey,
            npcId = actor.type.npcId,
            tile = actor.tile,
            facing = if (facesSouth) ApmekenLiveFacing.SOUTH else ApmekenLiveFacing.NORTH,
            targetPlayer = actor.targetPlayer,
            spawnSequence = ApmekenRevision240Assets.SPAWN_SEQUENCE,
            spawnSpotanim = spawnSpotanim,
        )

    private fun processShamans(
        state: MutableApmekenLiveState,
        input: ApmekenLiveTickInput,
    ): List<ApmekenLiveEvent> {
        val events = mutableListOf<ApmekenLiveEvent>()
        state.actors.values.toList().forEach { actor ->
            if (
                !actor.alive ||
                    actor.type != ApmekenBaboon.SHAMAN ||
                    input.tick < actor.nextShamanActionAt
            ) {
                return@forEach
            }
            val shaman = checkNotNull(actor.shaman)
            actor.targetPlayer = selectTarget(state, actor.npcKey, input.activePlayers)
            when (shaman.nextAction(actor.poisoned, actor.venomed)) {
                ApmekenShamanAction.SUMMON_THRALL -> {
                    if (shaman.recordThrallSummon()) {
                        events +=
                            ApmekenLiveEvent.NpcAnimation(
                                actor.npcKey,
                                ApmekenRevision240Assets.SHAMAN_SUMMON_SEQUENCE,
                            )
                        val tile = selectThrallTile(state, actor)
                        val thrall =
                            createActor(
                                state,
                                ApmekenBaboon.THRALL,
                                actor.wave,
                                tile,
                                input.tick,
                                shaman.thrallsSummoned,
                                actor.targetPlayer,
                            )
                        events +=
                            ApmekenLiveEvent.SpawnNpc(
                                spawnRequest(
                                    thrall,
                                    facesSouth = false,
                                    spawnSpotanim = ApmekenRevision240Assets.thrallSummon.id,
                                )
                            )
                    }
                }
                ApmekenShamanAction.MAGIC_ATTACK -> {
                    val target = actor.targetPlayer
                    if (target != null) {
                        events +=
                            ApmekenLiveEvent.NpcAttackRequest(
                                npcKey = actor.npcKey,
                                targetPlayer = target,
                                style = ApmekenLiveAttackStyle.MAGIC,
                                sequenceId = ApmekenRevision240Assets.MAGIC_SEQUENCE,
                                projectileId = ApmekenRevision240Assets.magicProjectile.id,
                                drainPrayerByLandedDamage = true,
                            )
                    }
                }
            }
            actor.nextShamanActionAt = input.tick + ApmekenLiveTuning.SHAMAN_ACTION_INTERVAL
        }
        return events
    }

    private fun processCursedBaboons(
        state: MutableApmekenLiveState,
        input: ApmekenLiveTickInput,
    ): List<ApmekenLiveEvent> {
        val events = mutableListOf<ApmekenLiveEvent>()
        state.actors.values.forEach { actor ->
            if (
                !actor.alive ||
                    actor.type != ApmekenBaboon.CURSED ||
                    input.tick < actor.nextCursedPoolAt
            ) {
                return@forEach
            }
            val protected = !ApmekenRevision240RuntimeLayout.isVenomPlacementTile(actor.tile)
            if (state.pools.place(actor.tile, protected) == ApmekenVenomPoolPlacement.PLACED) {
                val expiresAt = input.tick + ApmekenLiveTuning.CURSED_POOL_LIFETIME
                state.poolExpiresAt[actor.tile] = expiresAt
                events +=
                    ApmekenLiveEvent.SpawnVenomPool(
                        tile = actor.tile,
                        locId = ApmekenRevision240RuntimeLayout.venomPoolLoc.locId,
                        angle =
                            deterministicInt(
                                state.seed xor actor.npcKey xor input.tick.toLong(),
                                4,
                            ),
                        expiresAt = expiresAt,
                    )
            }
            actor.nextCursedPoolAt = input.tick + 1
        }
        return events
    }

    private fun processVolatiles(
        state: MutableApmekenLiveState,
        input: ApmekenLiveTickInput,
    ): List<ApmekenLiveEvent> {
        val events = mutableListOf<ApmekenLiveEvent>()
        var killedAny = false
        state.actors.values.toList().forEach { actor ->
            if (!actor.alive || actor.type != ApmekenBaboon.VOLATILE) return@forEach
            actor.targetPlayer = selectTarget(state, actor.npcKey, input.activePlayers)
            val targetTile = actor.targetPlayer?.let(input.playerTiles::get)
            if (
                actor.volatileChargeStartedAt == null &&
                    targetTile != null &&
                    chebyshev(actor.tile, targetTile) <= 1
            ) {
                actor.volatileChargeStartedAt = input.tick
            }
            val started = actor.volatileChargeStartedAt ?: return@forEach
            val elapsed = input.tick - started
            if (elapsed >= 1 && !actor.volatileAnimationSent) {
                actor.volatileAnimationSent = true
                events +=
                    ApmekenLiveEvent.NpcAnimation(
                        actor.npcKey,
                        ApmekenRevision240Assets.VOLATILE_EXPLODE_SEQUENCE,
                    )
            }
            if (
                elapsed >= ApmekenLiveTuning.VOLATILE_EXPLOSION_TICK && !actor.volatileExplosionSent
            ) {
                actor.volatileExplosionSent = true
                events +=
                    ApmekenLiveEvent.MapGraphic(
                        actor.tile,
                        ApmekenRevision240Assets.fire.id,
                        visibleTo = null,
                    )
                events +=
                    ApmekenLiveEvent.MapGraphic(
                        actor.tile,
                        ApmekenRevision240Assets.volatileBody.id,
                        visibleTo = null,
                    )
                input.playerTiles.forEach { (player, tile) ->
                    if (
                        ApmekenVolatileExplosion.effect(
                            ApmekenExplosionTarget.PLAYER,
                            tile.x - actor.tile.x,
                            tile.z - actor.tile.z,
                        ) == ApmekenExplosionEffect.DAMAGE
                    ) {
                        events +=
                            ApmekenLiveEvent.DamagePlayer(
                                player,
                                ApmekenLiveTuning.VOLATILE_BASE_DAMAGE +
                                    deterministicInt(
                                        state.seed xor actor.npcKey xor player.toLong(),
                                        ApmekenLiveTuning.VOLATILE_DAMAGE_VARIANCE + 1,
                                    ),
                                ApmekenLiveDamageReason.VOLATILE_EXPLOSION,
                                actor.npcKey,
                            )
                    }
                }
                state.actors.values.toList().forEach { other ->
                    if (!other.alive || other.npcKey == actor.npcKey) return@forEach
                    val effect =
                        ApmekenVolatileExplosion.effect(
                            ApmekenExplosionTarget.BABOON,
                            other.tile.x - actor.tile.x,
                            other.tile.z - actor.tile.z,
                        )
                    if (effect == ApmekenExplosionEffect.INSTANT_KILL) {
                        other.alive = false
                        killedAny = true
                        events += ApmekenLiveEvent.KillNpcRequest(other.npcKey, true)
                    }
                }
            }
            if (elapsed >= ApmekenLiveTuning.VOLATILE_CHARGE_TICKS) {
                actor.alive = false
                killedAny = true
                events += ApmekenLiveEvent.KillNpcRequest(actor.npcKey, true)
            }
        }
        if (killedAny) events += evaluateWaveProgress(state, input.tick)
        return events
    }

    private fun processPoolContact(
        state: MutableApmekenLiveState,
        input: ApmekenLiveTickInput,
    ): List<ApmekenLiveEvent> {
        val events = mutableListOf<ApmekenLiveEvent>()
        input.playerTiles.forEach { (player, tile) ->
            if (!state.pools.contains(tile)) return@forEach
            val damage =
                ApmekenVenomPools.contactDamage(
                    deterministicInt(state.seed xor input.tick.toLong() xor player.toLong(), 3)
                )
            events +=
                ApmekenLiveEvent.DamagePlayer(player, damage, ApmekenLiveDamageReason.VENOM_POOL)
            events += ApmekenLiveEvent.ApplyVenom(player, ApmekenLiveTuning.VENOM_SEVERITY)
        }
        return events
    }

    private fun processQteCycle(
        state: MutableApmekenLiveState,
        input: ApmekenLiveTickInput,
    ): List<ApmekenLiveEvent> {
        if (
            state.enteredPlayers.isEmpty() ||
                state.enteredPlayers.size + state.retiredPlayers.size != state.partySize
        )
            return emptyList()
        val events = mutableListOf<ApmekenLiveEvent>()
        val activeQte = state.challenge.activeQte
        val resolvesAt = state.qteResolvesAt
        if (activeQte != null && resolvesAt != null && input.tick >= resolvesAt) {
            val result = state.challenge.resolveQte()
            events += resolveQteEvents(state, activeQte, result)
            state.qteResolvesAt = null
            state.qtesForCurrentSight++
            val rotateAfter = if (state.enteredPlayers.size == 2) 3 else 2
            state.rotateBeforeNextQte = state.qtesForCurrentSight >= rotateAfter
            state.nextQteCycleAt = input.tick + ApmekenLiveTuning.QTE_CYCLE_TICKS
            return events
        }
        val cycleAt = state.nextQteCycleAt ?: return events
        if (
            state.challenge.activeQte != null ||
                input.tick < cycleAt ||
                input.activePlayers.isEmpty()
        ) {
            return events
        }
        if (state.rotateBeforeNextQte || state.challenge.sightPlayer !in input.activePlayers) {
            val previous = state.challenge.sightPlayer
            val current = state.challenge.rotateSight(input.activePlayers)
            state.rotateBeforeNextQte = false
            state.qtesForCurrentSight = 0
            state.nextQteCycleAt = input.tick + ApmekenLiveTuning.QTE_CYCLE_TICKS
            if (previous != current) {
                previous?.let {
                    events +=
                        ApmekenLiveEvent.PlayerGraphic(it, ApmekenRevision240Assets.sightLost.id)
                }
                events +=
                    ApmekenLiveEvent.PlayerGraphic(
                        current,
                        ApmekenRevision240Assets.sightGranted.id,
                    )
                events += ApmekenLiveEvent.SightChanged(previous, current)
            }
            return events
        }
        val snapshot = state.challenge.beginNextQte()
        state.qteResolvesAt = input.tick + ApmekenLiveTuning.QTE_WINDOW_TICKS
        state.nextQteCycleAt = null
        events += ApmekenLiveEvent.QteStarted(snapshot, checkNotNull(state.qteResolvesAt))
        events += qteStartVisuals(snapshot)
        return events
    }

    private fun qteStartVisuals(snapshot: ApmekenQteSnapshot): List<ApmekenLiveEvent> =
        when (snapshot.type) {
            ApmekenQteType.ROOF_SUPPORTS ->
                buildList {
                    ApmekenRevision240RuntimeLayout.roofSupports.forEachIndexed { index, support ->
                        add(
                            ApmekenLiveEvent.ChangeObject(
                                support.tile,
                                ApmekenRevision240RuntimeLayout.roofSupportLoc.locId,
                                ApmekenRevision240RuntimeLayout.roofSupportAlternateLoc.locId,
                                ApmekenRevision240RuntimeLayout.roofSupportAlternateLoc.animationId,
                            )
                        )
                        add(
                            ApmekenLiveEvent.MapGraphic(
                                ApmekenRevision240RuntimeLayout.roofInteractionTiles[index],
                                if (index < 2) {
                                    ApmekenRevision240Assets.roofWarning.id
                                } else {
                                    ApmekenRevision240Assets.roofWarningAlternate.id
                                },
                                snapshot.sightPlayer,
                                height = ApmekenLiveTuning.QTE_WARNING_HEIGHT,
                            )
                        )
                    }
                }
            ApmekenQteType.VENTS ->
                ApmekenRevision240RuntimeLayout.vents.mapIndexed { index, vent ->
                    ApmekenLiveEvent.MapGraphic(
                        vent.tile,
                        if (index < 2) {
                            ApmekenRevision240Assets.roofWarning.id
                        } else {
                            ApmekenRevision240Assets.roofWarningAlternate.id
                        },
                        snapshot.sightPlayer,
                        height = ApmekenLiveTuning.QTE_WARNING_HEIGHT,
                    )
                }
            ApmekenQteType.CORRUPTION ->
                snapshot.corruptedPlayers.map {
                    ApmekenLiveEvent.PlayerGraphic(it, ApmekenRevision240Assets.corruption.id)
                }
        }

    private fun repairRoof(
        state: MutableApmekenLiveState,
        interaction: ApmekenObjectInteraction,
    ): List<ApmekenLiveEvent> {
        val fixture =
            ApmekenRevision240RuntimeLayout.roofSupports.indexOfFirst {
                it.tile == interaction.tile
            }
        if (fixture < 0) return emptyList()
        if (!interaction.hasHammer) {
            return listOf(
                ApmekenLiveEvent.Feedback(interaction.player, ApmekenLiveFeedbackCode.NEED_HAMMER)
            )
        }
        val result =
            state.challenge.actOnFixture(interaction.player, ApmekenQteType.ROOF_SUPPORTS, fixture)
        val events =
            mutableListOf<ApmekenLiveEvent>(
                ApmekenLiveEvent.PlayerAnimation(
                    interaction.player,
                    ApmekenRevision240Assets.REPAIR_PLAYER_SEQUENCE,
                )
            )
        if (result.outcome in setOf(ApmekenQteOutcome.PROGRESS, ApmekenQteOutcome.QTE_COMPLETED)) {
            events +=
                ApmekenLiveEvent.ChangeObject(
                    interaction.tile,
                    ApmekenRevision240RuntimeLayout.roofSupportAlternateLoc.locId,
                    ApmekenRevision240RuntimeLayout.roofSupportLoc.locId,
                    ApmekenRevision240RuntimeLayout.roofSupportLoc.animationId,
                )
        } else if (result.outcome == ApmekenQteOutcome.FALSE_ACTION) {
            events +=
                ApmekenLiveEvent.MapGraphic(
                    ApmekenRevision240RuntimeLayout.roofInteractionTiles[fixture],
                    ApmekenRevision240Assets.roofCollapse.id,
                    visibleTo = null,
                )
        }
        events += qteResultEvents(state, interaction.player, ApmekenQteType.ROOF_SUPPORTS, result)
        return events
    }

    private fun neutraliseVent(
        state: MutableApmekenLiveState,
        interaction: ApmekenObjectInteraction,
    ): List<ApmekenLiveEvent> {
        val fixture =
            ApmekenRevision240RuntimeLayout.vents.indexOfFirst { it.tile == interaction.tile }
        if (fixture < 0) return emptyList()
        if (interaction.neutralisingPotions <= 0) {
            return listOf(
                ApmekenLiveEvent.Feedback(
                    interaction.player,
                    ApmekenLiveFeedbackCode.NEED_NEUTRALISING_POTION,
                )
            )
        }
        val result = state.challenge.actOnFixture(interaction.player, ApmekenQteType.VENTS, fixture)
        val events =
            mutableListOf<ApmekenLiveEvent>(
                ApmekenLiveEvent.PlayerAnimation(
                    interaction.player,
                    ApmekenRevision240Assets.POUR_PLAYER_SEQUENCE,
                ),
                ApmekenLiveEvent.SupplyTransfer(
                    interaction.player,
                    ApmekenLiveTuning.NEUTRALISING_POTION_ITEM,
                    -1,
                ),
            )
        if (result.outcome == ApmekenQteOutcome.FALSE_ACTION) {
            events +=
                ApmekenLiveEvent.MapGraphic(
                    interaction.tile,
                    ApmekenRevision240Assets.ventWarning.id,
                    visibleTo = null,
                )
        }
        events += qteResultEvents(state, interaction.player, ApmekenQteType.VENTS, result)
        return events
    }

    private fun qteResultEvents(
        state: MutableApmekenLiveState,
        player: Int,
        type: ApmekenQteType,
        result: ApmekenQteResult,
    ): List<ApmekenLiveEvent> {
        val code =
            when (result.outcome) {
                ApmekenQteOutcome.PROGRESS -> ApmekenLiveFeedbackCode.QTE_PROGRESS
                ApmekenQteOutcome.QTE_COMPLETED -> ApmekenLiveFeedbackCode.QTE_COMPLETE
                ApmekenQteOutcome.ALREADY_DONE -> ApmekenLiveFeedbackCode.FIXTURE_ALREADY_COMPLETE
                else -> ApmekenLiveFeedbackCode.NOTHING_TO_NEUTRALISE
            }
        return buildList {
            val activeType = state.challenge.activeQte?.type ?: type
            add(ApmekenLiveEvent.QteFeedback(player, activeType, result.outcome, code))
            if (result.baseDamagePerPlayer > 0) {
                result.affectedPlayers.forEach {
                    add(
                        ApmekenLiveEvent.DamagePlayer(
                            it,
                            result.baseDamagePerPlayer,
                            ApmekenLiveDamageReason.FALSE_QTE_ACTION,
                        )
                    )
                }
            }
        }
    }

    private fun resolveQteEvents(
        state: MutableApmekenLiveState,
        snapshot: ApmekenQteSnapshot,
        result: ApmekenQteResult,
    ): List<ApmekenLiveEvent> = buildList {
        val success = result.outcome == ApmekenQteOutcome.RESOLVED
        add(ApmekenLiveEvent.QteResolved(snapshot.type, success, result.missedActions))
        if (snapshot.type == ApmekenQteType.ROOF_SUPPORTS) {
            ApmekenRevision240RuntimeLayout.roofSupports.forEach {
                add(
                    ApmekenLiveEvent.ChangeObject(
                        it.tile,
                        ApmekenRevision240RuntimeLayout.roofSupportAlternateLoc.locId,
                        ApmekenRevision240RuntimeLayout.roofSupportLoc.locId,
                        ApmekenRevision240RuntimeLayout.roofSupportLoc.animationId,
                    )
                )
            }
            if (!success) {
                ApmekenRevision240RuntimeLayout.roofInteractionTiles.forEach {
                    add(
                        ApmekenLiveEvent.MapGraphic(
                            it,
                            ApmekenRevision240Assets.roofCollapse.id,
                            visibleTo = null,
                        )
                    )
                }
            }
        }
        if (snapshot.type == ApmekenQteType.VENTS && !success) {
            ApmekenRevision240RuntimeLayout.vents.forEach {
                add(
                    ApmekenLiveEvent.MapGraphic(
                        it.tile,
                        ApmekenRevision240Assets.ventWarning.id,
                        visibleTo = null,
                    )
                )
            }
        }
        if (!success) {
            result.affectedPlayers.forEach {
                add(
                    ApmekenLiveEvent.DamagePlayer(
                        it,
                        result.baseDamagePerPlayer,
                        ApmekenLiveDamageReason.MISSED_QTE,
                    )
                )
            }
            add(ApmekenLiveEvent.Feedback(player = null, code = ApmekenLiveFeedbackCode.QTE_FAILED))
        }
    }

    private fun evaluateWaveProgress(
        state: MutableApmekenLiveState,
        tick: Int,
    ): List<ApmekenLiveEvent> {
        if (state.complete || state.currentWave == 0) return emptyList()
        val alive = state.actors.values.filter { it.alive }
        val population =
            ApmekenWavePopulation(
                aliveNonThrallBaboons = alive.count { it.type != ApmekenBaboon.THRALL },
                aliveThralls = alive.count { it.type == ApmekenBaboon.THRALL },
            )
        return when (ApmekenWaves.progression(state.currentWave, population)) {
            ApmekenWaveProgression.WAIT -> emptyList()
            ApmekenWaveProgression.START_NEXT_WAVE -> {
                if (state.pendingWaveAt != null) return emptyList()
                state.pendingWaveAt = tick + ApmekenLiveTuning.NEXT_WAVE_DELAY
                listOf(
                    ApmekenLiveEvent.WaveScheduled(
                        state.currentWave + 1,
                        checkNotNull(state.pendingWaveAt),
                    )
                )
            }
            ApmekenWaveProgression.CHALLENGE_COMPLETE -> {
                state.complete = true
                state.pendingWaveAt = null
                state.nextQteCycleAt = null
                state.qteResolvesAt = null
                val previous = state.challenge.sightPlayer
                state.challenge.reset()
                buildList {
                    previous?.let {
                        add(
                            ApmekenLiveEvent.PlayerGraphic(
                                it,
                                ApmekenRevision240Assets.sightLost.id,
                            )
                        )
                        add(ApmekenLiveEvent.SightChanged(it, null))
                    }
                    add(ApmekenLiveEvent.ChallengeComplete(tick))
                }
            }
        }
    }

    private fun expirePools(state: MutableApmekenLiveState, tick: Int): List<ApmekenLiveEvent> {
        val expired = state.poolExpiresAt.filterValues { tick >= it }.keys.toList()
        expired.forEach {
            state.poolExpiresAt.remove(it)
            state.pools.remove(it)
        }
        return expired.map {
            ApmekenLiveEvent.RemoveVenomPool(it, ApmekenRevision240RuntimeLayout.venomPoolLoc.locId)
        }
    }

    private fun selectThrallTile(
        state: MutableApmekenLiveState,
        shaman: MutableApmekenLiveActor,
    ): ApmekenTile {
        val occupied = state.actors.values.filter { it.alive }.mapTo(linkedSetOf()) { it.tile }
        val offsets =
            listOf(
                0 to 1,
                1 to 0,
                0 to -1,
                -1 to 0,
                1 to 1,
                1 to -1,
                -1 to -1,
                -1 to 1,
                0 to 2,
                2 to 0,
                0 to -2,
                -2 to 0,
            )
        val start = deterministicInt(state.seed xor shaman.npcKey, offsets.size)
        repeat(offsets.size) { ordinal ->
            val offset = offsets[(start + ordinal) % offsets.size]
            val tile = ApmekenTile(shaman.tile.x + offset.first, shaman.tile.z + offset.second)
            if (tile !in occupied && ApmekenRevision240RuntimeLayout.isWalkableFloor(tile)) {
                return tile
            }
        }
        return shaman.tile
    }

    private fun selectTarget(
        state: MutableApmekenLiveState,
        npcKey: Long,
        preferred: Set<Int> = state.enteredPlayers,
    ): Int? {
        val candidates = preferred.ifEmpty { state.enteredPlayers }.sorted()
        if (candidates.isEmpty()) return null
        return candidates[deterministicInt(state.seed xor npcKey, candidates.size)]
    }

    private fun advanceClock(state: MutableApmekenLiveState, tick: Int) {
        require(tick >= state.currentTick) {
            "Apmeken runtime tick moved backwards: ${state.currentTick} -> $tick"
        }
        state.currentTick = tick
    }

    private fun state(instanceKey: Any): MutableApmekenLiveState =
        requireNotNull(states[instanceKey]) { "Apmeken instance is not initialized: $instanceKey" }

    private fun chebyshev(first: ApmekenTile, second: ApmekenTile): Int =
        maxOf(kotlin.math.abs(first.x - second.x), kotlin.math.abs(first.z - second.z))

    private fun deterministicInt(value: Long, bound: Int): Int {
        require(bound > 0)
        return Math.floorMod(value, bound.toLong()).toInt()
    }
}

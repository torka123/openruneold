package org.rsmod.content.minigames.toa

import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.combatClearQueue
import org.rsmod.api.player.hit.modifier.NpcBypassProtectionPrayerPlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid

internal object ScabarasWorldBindings {
    val primaryActions: Map<Int, ScabarasLiveObjectAction> = buildMap {
        put(ScabarasRevision240RuntimeLayout.sequenceCache.buttonLoc, ScabarasLiveObjectAction.PUSH)
        put(ScabarasRevision240RuntimeLayout.ADDITION_TABLET_LOC, ScabarasLiveObjectAction.INSPECT)
        ScabarasRevision240RuntimeLayout.matchingTiles.forEach { tile ->
            put(tile.hiddenButtonLoc, ScabarasLiveObjectAction.ACTIVATE)
        }
    }

    val fifthActions: Map<Int, ScabarasLiveObjectAction> =
        mapOf(
            ScabarasRevision240RuntimeLayout.lightsCache.tileOffLoc to ScabarasLiveObjectAction.FLIP
        )
}

internal data class ScabarasManagerDispatch(
    val handled: Boolean,
    val cancelNpcDamage: Boolean = false,
    val challengeCompleted: Boolean = false,
)

/**
 * Authoritative manager adapter for the deterministic Scabaras runtime.
 *
 * Puzzle state, dynamic locs and dynamic NPCs remain owned by [ToaScabarasLiveRuntime]. This
 * adapter only translates its explicit events into raid-wide presentation, damage, points and the
 * one-shot challenge transition consumed by [ToaRaidManager].
 */
internal class ToaScabarasIntegration
@Inject
constructor(
    private val runtime: ToaScabarasLiveRuntime,
    private val clock: MapClock,
    private val players: PlayerList,
    private val instances: InstanceManager,
    private val interactions: AiPlayerInteractions,
    private val world: WorldRepository,
) {
    private val lastPlayerTiles =
        mutableMapOf<org.rsmod.api.instances.InstanceId, MutableMap<PlayerUid, CoordGrid>>()
    private val pendingRockfalls =
        mutableMapOf<org.rsmod.api.instances.InstanceId, MutableList<PendingRockfall>>()

    fun initialize(raid: ToaRaidSession): ScabarasManagerDispatch {
        check(raid.challenge?.stage == ToaChallengeStage.ACTIVE) {
            "Scabaras initialized before its red barrier was crossed."
        }
        cleanup(raid)
        val result =
            runtime.initialize(
                instanceId = raid.instance.id,
                seed = scabarasSeed(raid),
                partySize = raid.startSnapshot.partySize,
            )
        lastPlayerTiles[raid.instance.id] = mutableMapOf()
        return consume(raid, actor = null, result)
    }

    fun tick(raid: ToaRaidSession): Boolean {
        var completed =
            consume(raid, actor = null, runtime.tick(raid.instance.id)).challengeCompleted
        processRockfalls(raid)

        val active = activePlayers(raid)
        val activeUids = active.mapTo(hashSetOf()) { it.uid }
        val previousTiles = lastPlayerTiles.getOrPut(raid.instance.id) { mutableMapOf() }
        previousTiles.keys.retainAll(activeUids)
        active.forEach { player ->
            val current = player.coords
            val previous = previousTiles.put(player.uid, current) ?: return@forEach
            if (previous == current) return@forEach
            val traversed = buildList {
                val intermediate = player.lastProcessedCoord
                if (
                    player.pendingStepCount > 1 &&
                        intermediate != CoordGrid.NULL &&
                        intermediate != previous &&
                        intermediate != current
                ) {
                    add(intermediate)
                }
                add(current)
            }
            traversed.distinct().forEach { tile ->
                val dispatch = consume(raid, player, runtime.onTileStep(raid.instance.id, tile))
                completed = completed || dispatch.challengeCompleted
            }
        }
        return completed
    }

    fun interact(
        raid: ToaRaidSession,
        player: Player,
        action: ScabarasLiveObjectAction,
        loc: BoundLocInfo,
    ): ScabarasManagerDispatch =
        consume(
            raid,
            player,
            runtime.onObjectInteraction(raid.instance.id, action, loc.id, loc.coords),
        )

    fun onNpcHit(raid: ToaRaidSession, player: Player, npc: Npc): ScabarasManagerDispatch {
        val result = consume(raid, player, runtime.onNpcHit(raid.instance.id, npc))
        if (result.handled && result.cancelNpcDamage) {
            player.combatClearQueue()
            player.clearInteraction()
        }
        return result
    }

    fun onNpcKill(raid: ToaRaidSession, player: Player, npc: Npc): ScabarasManagerDispatch {
        return consume(raid, player, runtime.onNpcKill(raid.instance.id, npc))
    }

    fun owns(raid: ToaRaidSession, npc: Npc): Boolean = runtime.owns(raid.instance.id, npc)

    fun isPlayerAttackable(raid: ToaRaidSession, npc: Npc): Boolean =
        owns(raid, npc) &&
            npc.id in
                setOf(
                    ScabarasRevision240RuntimeLayout.RANGED_SCARAB_NPC,
                    ScabarasRevision240RuntimeLayout.obelisks.attackableNpc,
                )

    fun isCombatNpc(raid: ToaRaidSession, npc: Npc): Boolean =
        owns(raid, npc) && npc.id == ScabarasRevision240RuntimeLayout.RANGED_SCARAB_NPC

    fun cleanup(raid: ToaRaidSession) {
        lastPlayerTiles.remove(raid.instance.id)
        pendingRockfalls.remove(raid.instance.id)
        runtime.cleanup(raid.instance.id)
    }

    fun rebindPlayer(raid: ToaRaidSession, oldUid: PlayerUid, newUid: PlayerUid) {
        lastPlayerTiles[raid.instance.id]?.rebindKey(oldUid, newUid)
        pendingRockfalls[raid.instance.id]?.replaceAll { rockfall ->
            if (rockfall.player == oldUid) rockfall.copy(player = newUid) else rockfall
        }
    }

    fun retirePlayer(raid: ToaRaidSession, uid: PlayerUid) {
        lastPlayerTiles[raid.instance.id]?.remove(uid)
        pendingRockfalls[raid.instance.id]?.removeAll { it.player == uid }
    }

    private fun consume(
        raid: ToaRaidSession,
        actor: Player?,
        result: ScabarasLiveResult,
    ): ScabarasManagerDispatch {
        result.events.forEach { event -> applyEvent(raid, actor, event) }
        return ScabarasManagerDispatch(
            handled = result.handled,
            cancelNpcDamage = result.cancelNpcDamage,
            challengeCompleted =
                result.events.any { it.kind == ScabarasLiveEventKind.CHALLENGE_COMPLETED },
        )
    }

    private fun applyEvent(raid: ToaRaidSession, actor: Player?, event: ScabarasLiveEvent) {
        when (event.kind) {
            ScabarasLiveEventKind.SEQUENCE_REQUESTED -> {
                actor?.anim(BUTTON_PUSH_SEQUENCE)
                actor?.mes("The ancient button displays a sequence. Watch it carefully.")
            }
            ScabarasLiveEventKind.ADDITION_TARGET ->
                actor?.mes(
                    "The tablet displays ${event.value}. Your current total is ${event.secondaryValue}."
                )
            ScabarasLiveEventKind.ACTION_RESULT -> applyActionFeedback(actor, event.actionResult)
            ScabarasLiveEventKind.PENALTY -> applyPenalty(raid, actor, event)
            ScabarasLiveEventKind.PUZZLE_COMPLETED ->
                message(raid, "${puzzleLabel(raid, event.puzzle)} has been completed!")
            ScabarasLiveEventKind.CHALLENGE_COMPLETED ->
                message(raid, "The Path of Scabaras challenge is complete.")
            ScabarasLiveEventKind.OBELISKS_RESET ->
                message(raid, "The obelisks reset before the sequence was completed.")
            ScabarasLiveEventKind.NPC_SPAWNED -> configureSpawnedNpc(raid, event)
            ScabarasLiveEventKind.INITIALIZED,
            ScabarasLiveEventKind.SEQUENCE_FLASH,
            ScabarasLiveEventKind.BARRIER_OPENED,
            ScabarasLiveEventKind.NPC_KILLED,
            ScabarasLiveEventKind.CLEANED_UP -> Unit
        }
    }

    private fun applyActionFeedback(actor: Player?, result: ScabarasActionResult?) {
        if (actor == null || result == null) return
        when (result.outcome) {
            ScabarasActionOutcome.NOT_READY -> actor.mes("This device won't function yet.")
            ScabarasActionOutcome.ALREADY_COMPLETED ->
                actor.mes("You have already completed this puzzle.")
            ScabarasActionOutcome.FAILURE ->
                if (result.puzzle == ScabarasPuzzle.MATCHING) {
                    actor.mes("The symbols do not match.")
                }
            else -> Unit
        }
    }

    private fun applyPenalty(raid: ToaRaidSession, actor: Player?, event: ScabarasLiveEvent) {
        when (event.actionResult?.penalty) {
            ScabarasPenalty.SMALL_FALLING_ROCKS -> {
                if (actor == null) return
                world.spotanimMap(SpotanimType(SMALL_ROCKS_SPOTANIM), actor.coords)
                queueEnvironmentalHit(actor, scaledDamage(raid, SMALL_ROCKS_BASE_DAMAGE))
            }
            ScabarasPenalty.FORCED_LIGHT_FLIP -> {
                if (actor == null) return
                actor.anim(LIGHT_FLIP_SEQUENCE)
                queueEnvironmentalHit(actor, event.value ?: FORCED_FLIP_DAMAGE)
            }
            ScabarasPenalty.OBELISK_ROCKFALL -> scheduleRockfalls(raid)
            null -> Unit
        }
    }

    private fun scheduleRockfalls(raid: ToaRaidSession) {
        val landsAt = clock.cycle + ROCKFALL_DELAY
        val pending = pendingRockfalls.getOrPut(raid.instance.id) { mutableListOf() }
        activePlayers(raid).forEach { player ->
            world.spotanimMap(SpotanimType(ROCK_FALL_SPOTANIM), player.coords)
            pending += PendingRockfall(player.uid, player.coords, landsAt)
        }
    }

    private fun processRockfalls(raid: ToaRaidSession) {
        val pending = pendingRockfalls[raid.instance.id] ?: return
        val iterator = pending.iterator()
        while (iterator.hasNext()) {
            val rockfall = iterator.next()
            if (clock.cycle < rockfall.landsAt) continue
            iterator.remove()
            val player = rockfall.player.resolve(players) ?: continue
            if (player !in activePlayers(raid) || player.coords != rockfall.tile) continue
            queueEnvironmentalHit(player, rockfallDamage(raid, rockfall))
        }
        if (pending.isEmpty()) pendingRockfalls.remove(raid.instance.id)
    }

    private fun configureSpawnedNpc(raid: ToaRaidSession, event: ScabarasLiveEvent) {
        if (event.npcKind != ScabarasLiveNpcKind.RANGED_SCARAB) return
        val npc = event.npc ?: return
        val pathLevel = raid.pathLevels[ToaPath.SCABARAS] ?: 0
        val hitpoints =
            ToaRules.hitpoints(
                base = npc.baseHitpointsLvl.coerceAtLeast(SCARAB_BASE_HITPOINTS),
                partySize = raid.startSnapshot.partySize,
                raidLevel = raid.raidLevel,
                pathLevel = pathLevel,
            )
        npc.baseHitpointsLvl = hitpoints
        npc.hitpoints = hitpoints
        npc.suppressHitpointsRegen = true
        npc.apRangeOverride = SCARAB_ATTACK_RANGE
        npc.maxOpRangeOverride = SCARAB_HUNT_RANGE
        npc.anim(SCARAB_SPAWN_SEQUENCE)
        npc.mode = null
        activePlayers(raid)
            .minByOrNull { it.coords.chebyshevDistance(npc.coords) }
            ?.let { npc.apPlayer2(it, interactions) }
    }

    private fun queueEnvironmentalHit(player: Player, damage: Int) {
        player.queueHit(
            delay = 1,
            type = HitType.Typeless,
            damage = damage.coerceAtMost(player.hitpoints),
            modifier = NpcBypassProtectionPrayerPlayerHitModifier,
        )
    }

    private fun rockfallDamage(raid: ToaRaidSession, rockfall: PendingRockfall): Int {
        val mixed =
            raid.party.id.mostSignificantBits xor
                raid.party.id.leastSignificantBits xor
                rockfall.player.packed.toLong() xor
                rockfall.landsAt.toLong()
        val variance = ((mixed xor (mixed ushr 33)) and Long.MAX_VALUE).rem(7L).toInt()
        return scaledDamage(raid, ROCKFALL_BASE_DAMAGE) + variance
    }

    private fun scaledDamage(raid: ToaRaidSession, base: Int): Int =
        ToaRules.maxHit(base, raid.raidLevel, raid.pathLevels[ToaPath.SCABARAS] ?: 0)

    private fun activePlayers(raid: ToaRaidSession): List<Player> =
        if (raid.challenge?.stage != ToaChallengeStage.ACTIVE) {
            emptyList()
        } else {
            raid.participants.values
                .filter { it.connected && !it.spectating && it.currentRoom == ToaRoom.SCABARAS }
                .mapNotNull { it.uid.resolve(players) }
                .filter {
                    ToaChallengeRoomRules.isInside(raid, instances, ToaRoom.SCABARAS, it.coords)
                }
        }

    private fun message(raid: ToaRaidSession, text: String) {
        raid.participants.keys.mapNotNull { it.resolve(players) }.forEach { it.mes(text) }
    }

    private fun puzzleLabel(raid: ToaRaidSession, puzzle: ScabarasPuzzle?): String {
        if (puzzle == ScabarasPuzzle.MATCHING) return "Puzzle 5"
        val sideRooms = runtime.snapshot(raid.instance.id)?.solutions?.sideRooms.orEmpty()
        val index = sideRooms.indexOf(puzzle)
        return if (index >= 0) "Puzzle ${index + 1}" else "A puzzle"
    }

    private fun scabarasSeed(raid: ToaRaidSession): Long =
        raid.party.id.mostSignificantBits xor
            raid.party.id.leastSignificantBits xor
            SCABARAS_SEED_SALT

    private data class PendingRockfall(
        val player: PlayerUid,
        val tile: CoordGrid,
        val landsAt: Int,
    )

    private companion object {
        const val SCABARAS_SEED_SALT = 0x5343414241524153L
        const val SCARAB_BASE_HITPOINTS = 25
        const val SCARAB_ATTACK_RANGE = 10
        const val SCARAB_HUNT_RANGE = 48
        const val SCARAB_SPAWN_SEQUENCE = 9589
        const val BUTTON_PUSH_SEQUENCE = 832
        const val LIGHT_FLIP_SEQUENCE = 9490
        const val SMALL_ROCKS_SPOTANIM = 302
        const val ROCK_FALL_SPOTANIM = 317
        const val SMALL_ROCKS_BASE_DAMAGE = 6
        const val ROCKFALL_BASE_DAMAGE = 7
        const val ROCKFALL_DELAY = 5
        const val FORCED_FLIP_DAMAGE = 20
    }
}

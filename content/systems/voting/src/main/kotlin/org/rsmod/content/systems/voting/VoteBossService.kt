@file:OptIn(dev.openrune.types.util.UncheckedType::class)

package org.rsmod.content.systems.voting

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.MoveRestrict
import dev.openrune.types.NpcMode
import dev.openrune.types.ProjAnimType
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.security.SecureRandom
import java.util.Collections
import java.util.UUID
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.collectionlog.CollectionLogAwardKind
import org.rsmod.api.collectionlog.CollectionLogAwardSource
import org.rsmod.api.collectionlog.CollectionLogService
import org.rsmod.api.config.refs.params
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.invtx.add
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.hit.modifier.NpcBypassProtectionPrayerPlayerHitModifier
import org.rsmod.api.player.hit.modifier.NpcPartialProtectionPrayerPlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.game.damage.DamageContributor
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.game.map.collision.isZoneValid
import org.rsmod.map.CoordGrid

@Singleton
internal class VoteBossService
@Inject
constructor(
    private val config: VoteSystemConfig,
    private val store: VoteStateStore,
    private val deps: BossDeps,
    private val objRepo: ObjRepository,
    private val locRepo: LocRepository,
    private val collectionLog: CollectionLogService,
) {
    private val log = InlineLogger()
    private val secureRandom = SecureRandom()
    private val state: VoteGlobalState = recover(store.load())
    private val pendingEasternBridgeRemovals = EASTERN_ZUK_BRIDGE_REMOVALS.toMutableList()
    private var activeNpc: Npc? = null
    private var nextAttackTick = 0
    private var attackNumber = 0
    private var bridgeRemovalStalledTicks = 0

    init {
        if (state.nextRaffleDrawAt <= 0) {
            state.nextRaffleDrawAt = System.currentTimeMillis() + config.raffleIntervalMillis
            save()
        }
    }

    fun tick() {
        removeEasternZukBridge()
        val now = System.currentTimeMillis()
        drawRaffleIfDue(now)
        val event = state.active
        if (event == null) {
            startNext(now)
            return
        }
        when (event.state) {
            VoteBossEventState.WARNING -> if (now >= event.warningEndsAt) spawn(event, now)
            VoteBossEventState.ACTIVE -> tickActive(event, now)
            VoteBossEventState.DEAD,
            VoteBossEventState.TIMED_OUT,
            VoteBossEventState.CLEANUP -> cleanup(event)
            VoteBossEventState.QUEUED -> {
                event.state = VoteBossEventState.WARNING
                event.warningEndsAt = now + config.warningMillis
                announceWarning(event)
                save()
            }
        }
    }

    fun onlinePlayer(accountId: Int): Player? =
        deps.playerList.firstOrNull { it.accountId == accountId }

    fun filterUnclaimed(ids: List<String>, player: Player): List<String> {
        val playerClaims = player.attr[VOTE_CLAIM_IDS].orEmpty()
        return ids.distinct()
            .filterNot(state.claimedVoteIds::contains)
            .filterNot(playerClaims::contains)
            .filterNot {
                state.pendingClaims.values.any { pending ->
                    it in pending.voteIds && pending.accountId != player.accountId
                }
            }
    }

    fun storePendingClaim(accountId: Int, username: String, voteIds: List<String>) {
        if (voteIds.isEmpty()) return
        val accepted = voteIds.distinct().filterNot(state.claimedVoteIds::contains)
        if (accepted.isEmpty()) return
        val existing = state.pendingClaims[accountId]
        state.pendingClaims[accountId] =
            PendingVoteClaim(
                accountId,
                username,
                ((existing?.voteIds ?: emptyList()) + accepted).distinct().toMutableList(),
                existing?.createdAt ?: System.currentTimeMillis(),
            )
        save()
    }

    fun pendingClaim(accountId: Int): PendingVoteClaim? = state.pendingClaims[accountId]

    fun completePendingClaim(player: Player, pending: PendingVoteClaim) {
        val current = state.pendingClaims[player.accountId] ?: return
        if (current.voteIds != pending.voteIds) return
        state.pendingClaims.remove(player.accountId)
        val newlyCommitted = pending.voteIds.filter(state.claimedVoteIds::add)
        newlyCommitted.forEach {
            state.raffleEntries +=
                RaffleEntry(
                    accountId = player.accountId,
                    username = player.username.ifBlank { player.displayName },
                )
        }
        state.lifetimeClaimedVotes += newlyCommitted.size
        addVotesInternal(newlyCommitted.size, "claim:${player.accountId}")
        store.audit(
            "claim account=${player.accountId} username=${player.username} votes=${newlyCommitted.size} " +
                "lifetime=${state.lifetimeClaimedVotes} counter=${state.pendingVoteCounter}"
        )
        save()
    }

    fun addTestVotes(amount: Int, admin: Player): String {
        if (amount !in 1..100_000) return "Use an amount from 1 to 100000."
        state.lifetimeClaimedVotes += amount
        repeat(amount) {
            state.raffleEntries +=
                RaffleEntry(accountId = admin.accountId, username = admin.username)
        }
        addVotesInternal(amount, "staff:${admin.accountId}")
        save()
        return "Added $amount test votes; counter=${state.pendingVoteCounter}, queue=${state.queue.size}."
    }

    fun force(kind: VoteBossKind, admin: Player): String {
        enqueue(kind, forced = true)
        store.audit("forced boss=$kind admin=${admin.accountId}")
        save()
        return "$kind was added to the Vote Boss queue."
    }

    fun status(): String {
        val event = state.active
        return "Votes: counter=${state.pendingVoteCounter}/${config.threshold}, lifetime=${state.lifetimeClaimedVotes}; " +
            "active=${event?.boss ?: "none"}/${event?.state ?: "IDLE"}; queued=${state.queue.size}; " +
            "raffleEntries=${state.raffleEntries.size}."
    }

    fun queueStatus(): List<String> = buildList {
        state.active?.let { add("ACTIVE ${it.boss} ${it.state} id=${it.id}") }
        state.queue.forEachIndexed { index, event ->
            add("${index + 1}. ${event.boss} ${event.state} id=${event.id}")
        }
        if (isEmpty()) add("The Vote Boss queue is empty.")
    }

    fun clear(admin: Player, clearQueue: Boolean): String {
        val current = state.active
        activeNpc?.let(::deleteNpc)
        activeNpc = null
        if (current != null)
            store.audit("cleared event=${current.id} boss=${current.boss} admin=${admin.accountId}")
        state.active = null
        if (clearQueue) state.queue.clear()
        save()
        return if (clearQueue) "The active Vote Boss and full queue were safely cleared."
        else "The active Vote Boss was safely cleared."
    }

    fun teleport(player: Player): String {
        val event = state.active ?: return "There is no announced or active Vote Boss."
        if (event.state != VoteBossEventState.WARNING && event.state != VoteBossEventState.ACTIVE) {
            return "There is no announced or active Vote Boss."
        }
        val arena = config.arena(event.boss, event.arenaIndex)
        PathingEntityCommon.teleport(player, deps.collision, arena.teleport)
        return "You teleport to ${event.boss.displayName}'s level ${arena.wildernessLevel} Wilderness arena."
    }

    fun onKill(context: NpcDeathKillContext) {
        val npc = context.npc
        val event = state.active ?: return
        if (event.state != VoteBossEventState.ACTIVE || activeNpc !== npc) return
        event.state = VoteBossEventState.DEAD
        event.endedAt = System.currentTimeMillis()
        save()
        val arena = config.arena(event.boss, event.arenaIndex)
        val participants =
            npc.damageContributions
                .sortedByDamageDescending()
                .filterIsInstance<DamageContributor.ByPlayer>()
                .filter { it.damage >= config.minimumDamage }
        participants.forEach { contribution ->
            val player = contribution.resolve(deps.playerList) ?: return@forEach
            if (!event.awardedAccounts.add(player.accountId)) return@forEach
            rewardParticipant(player, event, arena, contribution.damage)
            save()
        }
        broadcast(
            "${event.boss.displayName} has been defeated by ${event.awardedAccounts.size} eligible participant(s)!"
        )
        store.audit(
            "death event=${event.id} boss=${event.boss} participants=${participants.size} " +
                "awarded=${event.awardedAccounts.size} damage=${participants.joinToString { "${it.uuid}:${it.damage}" }}"
        )
    }

    fun claimRafflePrize(player: Player): Boolean {
        val prizes = state.unclaimedRafflePrizes[player.accountId]?.toList() ?: return false
        val configured =
            prizes.map { prize ->
                val type = ServerCacheManager.getItem(prize.itemId)
                if (type == null) {
                    log.error {
                        "[Vote] Missing raffle prize item ${prize.itemId}; all prizes retained."
                    }
                    return false
                }
                prize to type
            }
        val result =
            player.invTransaction(player.inv) {
                configured.forEach { (prize, type) ->
                    add(select(player.inv), type.id, prize.quantity)
                }
            }
        if (!result.success) {
            player.mes(
                "You have vote-raffle prizes waiting. Free inventory space and relog or use ::claimvote."
            )
            return false
        }
        state.unclaimedRafflePrizes.remove(player.accountId)
        save()
        val total = prizes.sumOf(RafflePrize::quantity)
        player.mes("You claim $total vote-raffle prize box(es) from ${prizes.size} draw(s)!")
        store.audit(
            "raffle-claimed draws=${prizes.joinToString { it.drawId }} account=${player.accountId}"
        )
        return true
    }

    private fun addVotesInternal(amount: Int, source: String) {
        repeat(amount) {
            state.pendingVoteCounter++
            if (state.pendingVoteCounter in config.progressMilestones) {
                broadcast(
                    "Vote Boss progress: ${state.pendingVoteCounter}/${config.threshold} votes!"
                )
            }
            if (state.pendingVoteCounter >= config.threshold) {
                state.pendingVoteCounter -= config.threshold
                val kind = bossFromSecureRoll(secureRandom.nextBoolean())
                enqueue(kind, forced = false)
                store.audit(
                    "threshold source=$source serial=${state.nextThresholdSerial - 1} selected=$kind"
                )
            }
        }
    }

    private fun enqueue(kind: VoteBossKind, forced: Boolean) {
        val serial = state.nextThresholdSerial++
        val arenaIndex = secureRandom.nextInt(config.arenas(kind).size)
        val event =
            VoteBossEventRecord(thresholdSerial = serial, boss = kind, arenaIndex = arenaIndex)
        state.queue += event
        val arena = config.arena(kind, arenaIndex)
        broadcast(
            "${if (forced) "Staff queued" else "The vote threshold selected"} ${kind.displayName} " +
                "for ${arena.name}."
        )
    }

    private fun startNext(now: Long) {
        val next = state.queue.removeFirstOrNull() ?: return
        next.state = VoteBossEventState.WARNING
        next.warningEndsAt = now + config.warningMillis
        state.active = next
        announceWarning(next)
        save()
    }

    private fun announceWarning(event: VoteBossEventRecord) {
        val arena = config.arena(event.boss, event.arenaIndex)
        broadcast(
            "${event.boss.displayName} will appear at ${arena.name} in ${config.warningMillis / 1_000} seconds! Use ::vb."
        )
        store.audit("warning event=${event.id} boss=${event.boss} ends=${event.warningEndsAt}")
    }

    private fun spawn(event: VoteBossEventRecord, now: Long) {
        if (activeNpc?.isSlotAssigned == true) return
        val arena = config.arena(event.boss, event.arenaIndex)
        val base =
            (listOf(arena.npcId) + arena.fallbackNpcIds)
                .asSequence()
                .mapNotNull(ServerCacheManager::getNpc)
                .firstOrNull { it.combatLevel > 0 }
        if (base == null) {
            event.state = VoteBossEventState.QUEUED
            state.active = null
            state.queue.add(0, event)
            save()
            log.error {
                "[Vote] Spawn failed: no combat NPC for ${event.boss}; selection retained and requeued."
            }
            return
        }
        val tile =
            if (event.boss == VoteBossKind.TZKAL_ZUK) {
                centeredNpcAnchor(arena.spawn, base.size).takeIf { anchor ->
                    arena.contains(anchor) &&
                        arena.contains(anchor.translate(base.size - 1, base.size - 1))
                }
            } else {
                findSafeSpawn(arena, base.size)
            }
        if (tile == null) {
            event.state = VoteBossEventState.QUEUED
            state.active = null
            state.queue.add(0, event)
            save()
            log.error {
                "[Vote] Spawn failed: no safe footprint for ${event.boss}; event retained."
            }
            return
        }
        val type = base.copy(hitpoints = arena.hitpoints, regenRate = Int.MAX_VALUE)
        activeNpc =
            Npc(type, tile).also {
                it.mode = NpcMode.None
                if (event.boss == VoteBossKind.TZKAL_ZUK) {
                    it.moveRestrict = MoveRestrict.NoMove
                    it.clearInteraction()
                    it.abortRoute()
                }
                it.respawns = false
                deps.npcRepo.add(it, Int.MAX_VALUE)
            }
        event.state = VoteBossEventState.ACTIVE
        event.spawnedAt = now
        nextAttackTick = deps.mapClock.cycle + 2
        attackNumber = 0
        save()
        broadcast(
            "${event.boss.displayName} has spawned at ${arena.name}! Use ::vb to join the public multi-combat fight."
        )
        store.audit(
            "spawn event=${event.id} boss=${event.boss} tile=$tile npc=${type.id} hp=${type.hitpoints}"
        )
    }

    private fun tickActive(event: VoteBossEventRecord, now: Long) {
        val npc = activeNpc
        if (npc == null || !npc.isSlotAssigned) {
            event.state = VoteBossEventState.QUEUED
            state.active = null
            state.queue.add(0, event)
            activeNpc = null
            save()
            store.audit("active-lost event=${event.id}; selected boss requeued")
            return
        }
        if (npc.hitpoints <= 0) return
        if (now - event.spawnedAt >= config.despawnMillis) {
            event.state = VoteBossEventState.TIMED_OUT
            event.endedAt = now
            save()
            broadcast("${event.boss.displayName} has escaped after the event timed out.")
            store.audit("timeout event=${event.id} boss=${event.boss}")
            return
        }
        if (deps.mapClock.cycle < nextAttackTick) return
        attack(event, npc)
        val arena = config.arena(event.boss, event.arenaIndex)
        val enraged =
            event.boss != VoteBossKind.TZKAL_ZUK &&
                npc.hitpoints * 100 <= npc.baseHitpointsLvl * arena.enragePercent
        nextAttackTick =
            deps.mapClock.cycle +
                (if (enraged) (arena.attackDelayTicks - 2).coerceAtLeast(3)
                else arena.attackDelayTicks)
    }

    private fun attack(event: VoteBossEventRecord, npc: Npc) {
        val arena = config.arena(event.boss, event.arenaIndex)
        val players = validPlayers(arena)
        if (players.isEmpty()) return
        Collections.shuffle(players, secureRandom)
        val targets = players.take(arena.targetCap)
        val primary = targets[attackNumber % targets.size]
        npc.facePlayer(primary)
        if (event.boss == VoteBossKind.TZKAL_ZUK) {
            npc.anim(ZUK_ATTACK_SEQUENCE)
        } else {
            val configured = npc.visType.paramOrNull(params.attack_anim)?.id
            (configured?.takeIf { ServerCacheManager.getAnim(it) != null }
                    ?: npc.visType.standAnim.takeIf { ServerCacheManager.getAnim(it) != null })
                ?.let(npc::anim)
        }
        val enraged = npc.hitpoints * 100 <= npc.baseHitpointsLvl * arena.enragePercent
        when (event.boss) {
            VoteBossKind.TZKAL_ZUK -> attackZuk(event, npc, targets, arena)
            VoteBossKind.SOL_HEREDIT -> attackSol(event, npc, targets, primary, arena, enraged)
        }
        attackNumber++
    }

    private fun attackZuk(
        event: VoteBossEventRecord,
        npc: Npc,
        targets: List<Player>,
        arena: VoteBossArena,
    ) {
        val profile = zukAttackProfile(secureRandom.nextBoolean(), arena.maxHit)
        projectileVolley(
            event,
            npc,
            targets,
            profile.hitType,
            profile.maxHit,
            ZUK_TRAVEL,
            ZUK_IMPACT,
            bypassPrayer = true,
        )
    }

    private fun attackSol(
        event: VoteBossEventRecord,
        npc: Npc,
        targets: List<Player>,
        primary: Player,
        arena: VoteBossArena,
        enraged: Boolean,
    ) {
        when (attackNumber % 4) {
            0 ->
                projectileVolley(
                    event,
                    npc,
                    targets,
                    HitType.Magic,
                    arena.maxHit + if (enraged) 8 else 0,
                    SOL_TRAVEL,
                    SOL_IMPACT,
                )
            1 ->
                delayedTiles(
                    event,
                    npc,
                    targets.map(Player::coords),
                    arena,
                    arena.maxHit,
                    SOL_IMPACT,
                    radius = 1,
                )
            2 -> {
                val switched = targets.drop(1) + primary
                projectileVolley(
                    event,
                    npc,
                    switched,
                    HitType.Magic,
                    arena.maxHit + 5,
                    SOL_TRAVEL,
                    SOL_IMPACT,
                )
            }
            else ->
                directHits(
                    npc,
                    validPlayers(arena).take(arena.targetCap),
                    HitType.Typeless,
                    arena.maxHit + 10,
                    bypassPrayer = true,
                )
        }
    }

    private fun projectileVolley(
        event: VoteBossEventRecord,
        npc: Npc,
        targets: List<Player>,
        hitType: HitType,
        maxHit: Int,
        travel: String,
        impact: String,
        bypassPrayer: Boolean = false,
    ) {
        targets.forEach { target ->
            val projectile =
                deps.worldRepo.projAnimSourced(
                    npc,
                    target,
                    SpotanimType(travel.asRSCM(RSCMType.SPOTANIM)),
                    VOTE_PROJECTILE,
                )
            val delay = projectile.serverCycles.coerceAtLeast(1)
            deps.worldQueues.add(delay) {
                if (
                    !isCurrent(event, npc) ||
                        !validTarget(target, config.arena(event.boss, event.arenaIndex))
                )
                    return@add
                target.spotanim(impact)
                target.queueHit(
                    npc,
                    1,
                    hitType,
                    secureRandom.nextInt(maxHit + 1),
                    modifier =
                        if (bypassPrayer) NpcBypassProtectionPrayerPlayerHitModifier
                        else NpcPartialProtectionPrayerPlayerHitModifier(50),
                )
            }
        }
    }

    private fun delayedTiles(
        event: VoteBossEventRecord,
        npc: Npc,
        tiles: List<CoordGrid>,
        arena: VoteBossArena,
        maxHit: Int,
        graphic: String,
        radius: Int,
    ) {
        tiles.distinct().forEach {
            deps.worldRepo.spotanimMap(SpotanimType(graphic.asRSCM(RSCMType.SPOTANIM)), it)
        }
        deps.worldQueues.add(3) {
            if (!isCurrent(event, npc)) return@add
            tiles.distinct().forEach { tile ->
                validPlayers(arena)
                    .filter { it.coords.chebyshevDistance(tile) <= radius }
                    .forEach { player ->
                        player.spotanim(graphic)
                        player.queueHit(
                            npc,
                            1,
                            HitType.Typeless,
                            secureRandom.nextInt(maxHit + 1),
                            modifier = NpcBypassProtectionPrayerPlayerHitModifier,
                        )
                    }
            }
        }
    }

    private fun directHits(
        npc: Npc,
        players: List<Player>,
        type: HitType,
        maxHit: Int,
        bypassPrayer: Boolean,
    ) {
        players.forEach { player ->
            player.queueHit(
                npc,
                1,
                type,
                secureRandom.nextInt(maxHit + 1),
                modifier =
                    if (bypassPrayer) NpcBypassProtectionPrayerPlayerHitModifier
                    else NpcPartialProtectionPrayerPlayerHitModifier(50),
            )
        }
    }

    private fun rewardParticipant(
        player: Player,
        event: VoteBossEventRecord,
        arena: VoteBossArena,
        damage: Int,
    ) {
        val entry = weightedLoot(arena.loot)
        val quantity =
            if (entry.minimum == entry.maximum) entry.minimum
            else secureRandom.nextInt(entry.maximum - entry.minimum + 1) + entry.minimum
        val type = ServerCacheManager.getItem(entry.itemId)
        if (type != null) {
            val result =
                player.invTransaction(player.inv) { add(select(player.inv), type.id, quantity) }
            if (!result.success) {
                val duration = player.lootDropDuration ?: 200
                objRepo.add(type, player.coords, duration, player, quantity)
                player.mes("Your Vote Boss loot was placed beneath you as an owner-only drop.")
            }
            collectionLog.record(
                player = player,
                itemId = type.id,
                amount = quantity,
                source =
                    CollectionLogAwardSource(
                        CollectionLogAwardKind.SpecialContainer,
                        "vote-boss:${event.boss.name.lowercase()}",
                    ),
                eventId = "${event.id}:${player.accountId}",
            )
        } else {
            log.error {
                "[Vote] Missing loot item ${entry.itemId} for ${event.boss}; points still awarded."
            }
        }
        player.attr[VOTE_BOSS_POINTS] = (player.attr[VOTE_BOSS_POINTS] ?: 0) + config.voteBossPoints
        player.attr[VOTE_BOSS_KILLS] = (player.attr[VOTE_BOSS_KILLS] ?: 0) + 1
        player.mes(
            "Your $damage damage earned $quantity x ${type?.name ?: "configured item ${entry.itemId}"} and ${config.voteBossPoints} Vote Boss points."
        )
        if (entry.rare && type != null)
            broadcast(
                "${player.displayName} received ${quantity} x ${type.name} from ${event.boss.displayName}!"
            )
        store.audit(
            "reward event=${event.id} account=${player.accountId} damage=$damage item=${entry.itemId}:$quantity rare=${entry.rare}"
        )
    }

    private fun weightedLoot(entries: List<VoteLootEntry>): VoteLootEntry {
        var roll = secureRandom.nextInt(entries.sumOf(VoteLootEntry::weight))
        for (entry in entries) {
            if (roll < entry.weight) return entry
            roll -= entry.weight
        }
        return entries.last()
    }

    private fun drawRaffleIfDue(now: Long) {
        if (now < state.nextRaffleDrawAt) return
        if (state.raffleEntries.isNotEmpty()) {
            val winner = state.raffleEntries[secureRandom.nextInt(state.raffleEntries.size)]
            val drawId = UUID.randomUUID().toString()
            state.unclaimedRafflePrizes.getOrPut(winner.accountId, ::mutableListOf) +=
                RafflePrize(
                    drawId,
                    winner.accountId,
                    winner.username,
                    config.rafflePrizeItemId,
                    config.rafflePrizeQuantity,
                    now,
                )
            state.completedRaffleDraws +=
                CompletedRaffleDraw(drawId, winner.username, state.raffleEntries.size, now)
            if (state.completedRaffleDraws.size > 100) state.completedRaffleDraws.removeAt(0)
            broadcast(
                "${winner.username} won the vote raffle from ${state.raffleEntries.size} ticket(s)!"
            )
            store.audit(
                "raffle draw=$drawId winner=${winner.accountId} entries=${state.raffleEntries.size}"
            )
            onlinePlayer(winner.accountId)?.let(::claimRafflePrize)
        } else {
            store.audit("raffle draw skipped: no entries")
        }
        state.raffleEntries.clear()
        do {
            state.nextRaffleDrawAt += config.raffleIntervalMillis
        } while (state.nextRaffleDrawAt <= now)
        save()
    }

    private fun cleanup(event: VoteBossEventRecord) {
        activeNpc?.let(::deleteNpc)
        activeNpc = null
        event.state = VoteBossEventState.CLEANUP
        store.audit("cleanup event=${event.id} boss=${event.boss} state=${event.state}")
        state.active = null
        save()
    }

    private fun deleteNpc(npc: Npc) {
        if (npc.isSlotAssigned) deps.npcRepo.del(npc, Int.MAX_VALUE)
    }

    private fun validPlayers(arena: VoteBossArena): MutableList<Player> =
        deps.playerList.filterTo(mutableListOf()) { validTarget(it, arena) }

    private fun validTarget(player: Player, arena: VoteBossArena): Boolean =
        player.isSlotAssigned &&
            player.hitpoints > 0 &&
            player.isValidTarget() &&
            arena.contains(player.coords)

    private fun isCurrent(event: VoteBossEventRecord, npc: Npc): Boolean =
        state.active?.id == event.id &&
            state.active?.state == VoteBossEventState.ACTIVE &&
            activeNpc === npc &&
            npc.isSlotAssigned &&
            npc.hitpoints > 0

    private fun removeEasternZukBridge() {
        if (pendingEasternBridgeRemovals.isEmpty()) return
        var removed = 0
        val iterator = pendingEasternBridgeRemovals.iterator()
        while (iterator.hasNext() && removed < BRIDGE_REMOVALS_PER_TICK) {
            val ref = iterator.next()
            val id = ref.internal.asRSCM(RSCMType.LOC)
            val loc =
                locRepo.findAll(ref.coords).firstOrNull {
                    it.id == id && it.shape == ref.shape && it.angle == ref.angle
                } ?: continue
            if (locRepo.del(loc, Int.MAX_VALUE)) {
                iterator.remove()
                removed++
            }
        }
        if (removed > 0) {
            bridgeRemovalStalledTicks = 0
        } else {
            bridgeRemovalStalledTicks++
        }
        if (pendingEasternBridgeRemovals.isEmpty()) {
            store.audit(
                "removed ${EASTERN_ZUK_BRIDGE_REMOVALS.size} synchronized bridge locs from the eastern Zuk basin"
            )
        } else if (bridgeRemovalStalledTicks == 5) {
            log.error {
                "[Vote] Unable to remove ${pendingEasternBridgeRemovals.size} eastern Zuk bridge loc(s): " +
                    pendingEasternBridgeRemovals.joinToString { "${it.internal}@${it.coords}" }
            }
        }
    }

    private fun findSafeSpawn(arena: VoteBossArena, size: Int): CoordGrid? {
        val candidates = sequence {
            yield(arena.spawn)
            for (radius in 1..8) {
                for (dx in -radius..radius) {
                    yield(arena.spawn.translate(dx, -radius))
                    yield(arena.spawn.translate(dx, radius))
                }
                for (dz in (-radius + 1) until radius) {
                    yield(arena.spawn.translate(-radius, dz))
                    yield(arena.spawn.translate(radius, dz))
                }
            }
        }
        return candidates.firstOrNull { tile ->
            arena.contains(tile) &&
                arena.contains(tile.translate(size - 1, size - 1)) &&
                (0 until size).all { dx ->
                    (0 until size).all { dz ->
                        val footprint = tile.translate(dx, dz)
                        deps.collision.isZoneValid(footprint) &&
                            !deps.collision.isWalkBlocked(footprint)
                    }
                }
        }
    }

    private fun recover(loaded: VoteGlobalState): VoteGlobalState {
        val interrupted = loaded.active
        if (interrupted != null) {
            loaded.queue.removeAll { it.id == interrupted.id }
            if (shouldRequeueAfterRestart(interrupted.state)) {
                interrupted.state = VoteBossEventState.QUEUED
                interrupted.warningEndsAt = 0
                interrupted.spawnedAt = 0
                loaded.queue.add(0, interrupted)
                store.audit(
                    "restart recovered event=${interrupted.id} boss=${interrupted.boss}; selection retained"
                )
            } else {
                store.audit(
                    "restart finalized terminal event=${interrupted.id} boss=${interrupted.boss} state=${interrupted.state}"
                )
            }
            loaded.active = null
            store.save(loaded)
        }
        loaded.pendingVoteCounter = loaded.pendingVoteCounter.coerceIn(0, config.threshold - 1)
        return loaded
    }

    private fun save() = store.save(state)

    private fun broadcast(message: String) {
        deps.playerList.forEach { it.mes(message, ChatType.Broadcast) }
    }
}

internal val VoteBossKind.displayName: String
    get() = if (this == VoteBossKind.TZKAL_ZUK) "TzKal-Zuk" else "Sol Heredit"

internal const val ZUK_MAX_HIT: Int = 28
internal const val ZUK_MAGIC_MAX_HIT: Int = 23

internal data class ZukAttackProfile(val hitType: HitType, val maxHit: Int)

internal fun zukAttackProfile(rangedRoll: Boolean, configuredMaxHit: Int): ZukAttackProfile {
    val hitType = if (rangedRoll) HitType.Ranged else HitType.Magic
    return ZukAttackProfile(hitType, zukAttackMaxHit(configuredMaxHit, hitType))
}

internal fun zukAttackMaxHit(configuredMaxHit: Int, hitType: HitType): Int =
    configuredMaxHit.coerceIn(0, if (hitType == HitType.Ranged) ZUK_MAX_HIT else ZUK_MAGIC_MAX_HIT)

internal fun centeredNpcAnchor(center: CoordGrid, size: Int): CoordGrid {
    require(size > 0)
    val offset = size / 2
    return center.translate(-offset, -offset)
}

private const val BRIDGE_REMOVALS_PER_TICK: Int = 5

internal data class StaticLocRemoval(
    val internal: String,
    val coords: CoordGrid,
    val shape: LocShape,
    val angle: LocAngle,
)

internal val EASTERN_ZUK_BRIDGE_REMOVALS: List<StaticLocRemoval> =
    listOf(
        StaticLocRemoval(
            "loc.wild_falloff_bridge_railing",
            CoordGrid(3360, 3935, 0),
            LocShape.WallSquareCorner,
            LocAngle.East,
        ),
        StaticLocRemoval(
            "loc.wild_falloff_bridge_railing",
            CoordGrid(3360, 3936, 0),
            LocShape.WallSquareCorner,
            LocAngle.North,
        ),
        StaticLocRemoval(
            "loc.wild_falloff_bridge_pier_hillskew",
            CoordGrid(3361, 3935, 0),
            LocShape.GroundDecor,
            LocAngle.North,
        ),
        StaticLocRemoval(
            "loc.wild_falloff_bridge_pier_hillskew",
            CoordGrid(3361, 3936, 0),
            LocShape.GroundDecor,
            LocAngle.North,
        ),
        StaticLocRemoval(
            "loc.wild_falloff_bridge_broken",
            CoordGrid(3362, 3935, 0),
            LocShape.GroundDecor,
            LocAngle.South,
        ),
        StaticLocRemoval(
            "loc.wild_falloff_bridge_broken",
            CoordGrid(3362, 3936, 0),
            LocShape.GroundDecor,
            LocAngle.North,
        ),
        StaticLocRemoval(
            "loc.wild_falloff_bridge_pier_hillskew",
            CoordGrid(3363, 3935, 0),
            LocShape.GroundDecor,
            LocAngle.North,
        ),
        StaticLocRemoval(
            "loc.wild_falloff_bridge_broken",
            CoordGrid(3363, 3936, 0),
            LocShape.GroundDecor,
            LocAngle.North,
        ),
        StaticLocRemoval(
            "loc.wild_falloff_bridge_pier_hillskew",
            CoordGrid(3364, 3935, 0),
            LocShape.GroundDecor,
            LocAngle.North,
        ),
        StaticLocRemoval(
            "loc.wild_falloff_bridge_pier_hillskew",
            CoordGrid(3364, 3936, 0),
            LocShape.GroundDecor,
            LocAngle.North,
        ),
        StaticLocRemoval(
            "loc.wild_falloff_bridge_broken",
            CoordGrid(3365, 3935, 0),
            LocShape.GroundDecor,
            LocAngle.South,
        ),
        StaticLocRemoval(
            "loc.wild_falloff_bridge_pier_hillskew",
            CoordGrid(3365, 3936, 0),
            LocShape.GroundDecor,
            LocAngle.North,
        ),
        StaticLocRemoval(
            "loc.wild_falloff_bridge_broken_mirror",
            CoordGrid(3366, 3935, 0),
            LocShape.GroundDecor,
            LocAngle.South,
        ),
        StaticLocRemoval(
            "loc.wild_falloff_bridge_pier_hillskew",
            CoordGrid(3366, 3936, 0),
            LocShape.GroundDecor,
            LocAngle.North,
        ),
        StaticLocRemoval(
            "loc.wild_falloff_bridge_pier_hillskew",
            CoordGrid(3367, 3935, 0),
            LocShape.GroundDecor,
            LocAngle.North,
        ),
        StaticLocRemoval(
            "loc.wild_falloff_bridge_broken",
            CoordGrid(3367, 3936, 0),
            LocShape.GroundDecor,
            LocAngle.North,
        ),
        StaticLocRemoval(
            "loc.wild_falloff_bridge_broken",
            CoordGrid(3368, 3935, 0),
            LocShape.GroundDecor,
            LocAngle.South,
        ),
        StaticLocRemoval(
            "loc.wild_falloff_bridge_pier_hillskew",
            CoordGrid(3368, 3936, 0),
            LocShape.GroundDecor,
            LocAngle.North,
        ),
        StaticLocRemoval(
            "loc.wild_falloff_bridge_pier_hillskew",
            CoordGrid(3369, 3935, 0),
            LocShape.GroundDecor,
            LocAngle.North,
        ),
        StaticLocRemoval(
            "loc.wild_falloff_bridge_pier_hillskew",
            CoordGrid(3369, 3936, 0),
            LocShape.GroundDecor,
            LocAngle.North,
        ),
        StaticLocRemoval(
            "loc.wild_falloff_bridge_pier_hillskew",
            CoordGrid(3370, 3935, 0),
            LocShape.GroundDecor,
            LocAngle.North,
        ),
        StaticLocRemoval(
            "loc.wild_falloff_bridge_broken_mirror",
            CoordGrid(3370, 3936, 0),
            LocShape.GroundDecor,
            LocAngle.North,
        ),
        StaticLocRemoval(
            "loc.wild_falloff_bridge_pier_hillskew",
            CoordGrid(3371, 3935, 0),
            LocShape.GroundDecor,
            LocAngle.North,
        ),
        StaticLocRemoval(
            "loc.wild_falloff_bridge_broken",
            CoordGrid(3371, 3936, 0),
            LocShape.GroundDecor,
            LocAngle.North,
        ),
        StaticLocRemoval(
            "loc.wild_falloff_bridge_broken",
            CoordGrid(3372, 3935, 0),
            LocShape.GroundDecor,
            LocAngle.South,
        ),
        StaticLocRemoval(
            "loc.wild_falloff_bridge_pier_hillskew",
            CoordGrid(3372, 3936, 0),
            LocShape.GroundDecor,
            LocAngle.South,
        ),
        StaticLocRemoval(
            "loc.wild_falloff_bridge_pier_hillskew",
            CoordGrid(3373, 3935, 0),
            LocShape.GroundDecor,
            LocAngle.North,
        ),
        StaticLocRemoval(
            "loc.wild_falloff_bridge_pier_hillskew",
            CoordGrid(3373, 3936, 0),
            LocShape.GroundDecor,
            LocAngle.North,
        ),
        StaticLocRemoval(
            "loc.wild_falloff_bridge_railing",
            CoordGrid(3374, 3935, 0),
            LocShape.WallSquareCorner,
            LocAngle.South,
        ),
        StaticLocRemoval(
            "loc.wild_falloff_bridge_railing",
            CoordGrid(3374, 3936, 0),
            LocShape.WallSquareCorner,
            LocAngle.West,
        ),
    )

private val VOTE_PROJECTILE =
    ProjAnimType(
        startHeight = 64,
        endHeight = 32,
        delay = 0,
        angle = 16,
        lengthAdjustment = 10,
        progress = 0,
        stepMultiplier = 1,
    )
private const val ZUK_ATTACK_SEQUENCE = "seq.zuk_attack"
private const val ZUK_TRAVEL = "spotanim.inferno_zuk_projectile"
private const val ZUK_IMPACT = "spotanim.tzhaar_fire_launch_impact"
private const val SOL_TRAVEL = "spotanim.revenant_magic_travel"
private const val SOL_IMPACT = "spotanim.revenant_magic_impact"

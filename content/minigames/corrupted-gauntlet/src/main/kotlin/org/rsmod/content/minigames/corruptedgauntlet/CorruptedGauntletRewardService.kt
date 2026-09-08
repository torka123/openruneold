@file:OptIn(dev.openrune.types.util.UncheckedType::class)

package org.rsmod.content.minigames.corruptedgauntlet

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.InvScope
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.UUID
import org.rsmod.api.account.saver.AccountSavingService
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.collectionlog.CollectionLogAwardKind
import org.rsmod.api.collectionlog.CollectionLogAwardSource
import org.rsmod.api.collectionlog.CollectionLogService
import org.rsmod.api.collectionlog.PlayerCollectionLogLoad
import org.rsmod.api.invtx.add
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.output.mes
import org.rsmod.api.random.GameRandom
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.inv.InvObj
import org.rsmod.map.CoordGrid

internal val CORRUPTED_GAUNTLET_PENDING_REWARD: AttributeKey<MutableList<Int>> =
    AttributeKey(persistenceKey = "corrupted_gauntlet_pending_reward_v1")
internal val CORRUPTED_GAUNTLET_LAST_RESULT: AttributeKey<MutableList<Int>> =
    AttributeKey(persistenceKey = "corrupted_gauntlet_last_result_v1")
internal val CORRUPTED_GAUNTLET_COMPLETIONS: AttributeKey<Int> =
    AttributeKey(persistenceKey = "corrupted_gauntlet_completions_v1")
internal val CORRUPTED_GAUNTLET_DEATHS: AttributeKey<Int> =
    AttributeKey(persistenceKey = "corrupted_gauntlet_deaths_v1")
internal val CORRUPTED_GAUNTLET_BEST_PREP_TICKS: AttributeKey<Int> =
    AttributeKey(persistenceKey = "corrupted_gauntlet_best_prep_ticks_v1")
internal val CORRUPTED_GAUNTLET_BEST_KILL_TICKS: AttributeKey<Int> =
    AttributeKey(persistenceKey = "corrupted_gauntlet_best_kill_ticks_v1")
internal val CORRUPTED_GAUNTLET_BEST_COMPLETION_TICKS: AttributeKey<Int> =
    AttributeKey(persistenceKey = "corrupted_gauntlet_best_completion_ticks_v1")

internal enum class CorruptedGauntletRewardOutcome {
    SUCCESS,
    PARTIAL_FAILURE,
    MINIMAL_FAILURE,
}

internal data class CorruptedGauntletReward(
    val objId: Int,
    val count: Int,
    val noted: Boolean = false,
) {
    init {
        require(objId >= 0)
        require(count > 0)
    }
}

internal data class CorruptedGauntletPendingReward(
    val runId: UUID,
    val transactionId: UUID,
    val outcome: CorruptedGauntletRewardOutcome,
    val preparationTicks: Int,
    val killTicks: Int,
    val rewards: List<CorruptedGauntletReward>,
) {
    init {
        require(preparationTicks >= 0)
        require(killTicks >= 0)
        require(rewards.isNotEmpty())
    }
}

internal enum class CorruptedGauntletRewardPreparation {
    NEW,
    EXISTING_FOR_RUN,
    ALREADY_FINALIZED,
    BLOCKED_BY_OTHER_REWARD,
}

internal object CorruptedGauntletPendingRewardRules {
    fun preparation(
        requestedRunId: UUID,
        pendingRunId: UUID?,
        lastFinalizedRunId: UUID?,
    ): CorruptedGauntletRewardPreparation =
        when {
            pendingRunId == requestedRunId -> CorruptedGauntletRewardPreparation.EXISTING_FOR_RUN
            pendingRunId != null -> CorruptedGauntletRewardPreparation.BLOCKED_BY_OTHER_REWARD
            lastFinalizedRunId == requestedRunId ->
                CorruptedGauntletRewardPreparation.ALREADY_FINALIZED
            else -> CorruptedGauntletRewardPreparation.NEW
        }
}

/** OSRS Corrupted Gauntlet reward table: three main rolls plus independent tertiaries. */
internal object CorruptedGauntletChestTable {
    private const val TOTAL_COMMON_WEIGHT = 48
    const val CRYSTAL_SHARD = 23_866
    const val GAUNTLET_CAPE = 23_859
    const val CRYSTAL_WEAPON_SEED = 4_207
    const val CRYSTAL_ARMOUR_SEED = 23_956
    const val ENHANCED_CRYSTAL_WEAPON_SEED = 25_859
    const val YOUNGLLEF = 23_757
    const val ELITE_CLUE_SCROLL = 12_073

    private data class Entry(
        val internalName: String,
        val minimum: Int,
        val maximum: Int,
        val weight: Int = 2,
        val noted: Boolean = false,
    )

    private val common =
        listOf(
            Entry("obj.rune_full_helm", 3, 5, noted = true),
            Entry("obj.rune_chainbody", 2, 3, noted = true),
            Entry("obj.rune_platebody", 2, 2, noted = true),
            Entry("obj.rune_platelegs", 2, 3, noted = true),
            Entry("obj.rune_plateskirt", 2, 3, noted = true),
            Entry("obj.rune_halberd", 2, 3, noted = true),
            Entry("obj.rune_pickaxe", 2, 3, noted = true),
            Entry("obj.dragon_halberd", 1, 2, weight = 1, noted = true),
            Entry("obj.cosmicrune", 175, 250),
            Entry("obj.naturerune", 125, 150),
            Entry("obj.lawrune", 100, 150),
            Entry("obj.chaosrune", 200, 350),
            Entry("obj.deathrune", 125, 175),
            Entry("obj.bloodrune", 100, 150),
            Entry("obj.mithril_arrow", 1_000, 1_500),
            Entry("obj.adamant_arrow", 500, 750),
            Entry("obj.rune_arrow", 250, 450),
            Entry("obj.dragon_arrow", 50, 100),
            Entry("obj.uncut_sapphire", 25, 65, noted = true),
            Entry("obj.uncut_emerald", 15, 60, noted = true),
            Entry("obj.uncut_ruby", 10, 40, noted = true),
            Entry("obj.uncut_diamond", 5, 15, noted = true),
            Entry("obj.coins", 75_000, 150_000, weight = 3),
            Entry("obj.battlestaff", 8, 12, noted = true),
        )

    private val partialFailure =
        listOf(
            Entry("obj.adamant_dagger", 1, 1),
            Entry("obj.adamant_full_helm", 1, 1),
            Entry("obj.adamant_mace", 2, 3, noted = true),
            Entry("obj.adamant_pickaxe", 1, 1),
            Entry("obj.adamant_platebody", 1, 1),
            Entry("obj.adamant_platelegs", 1, 1),
            Entry("obj.adamant_plateskirt", 1, 1),
            Entry("obj.adamant_scimitar", 1, 1),
            Entry("obj.maple_longbow", 7, 13, noted = true),
            Entry("obj.maple_shortbow", 8, 11, noted = true),
            Entry("obj.mithril_full_helm", 1, 1),
            Entry("obj.mithril_mace", 2, 5, noted = true),
            Entry("obj.mithril_platebody", 1, 1),
            Entry("obj.mithril_platelegs", 1, 1),
            Entry("obj.mithril_plateskirt", 1, 1),
            Entry("obj.airrune", 200, 300),
            Entry("obj.bodyrune", 250, 350),
            Entry("obj.earthrune", 200, 300),
            Entry("obj.firerune", 200, 300),
            Entry("obj.mindrune", 300, 400),
            Entry("obj.waterrune", 200, 300),
            Entry("obj.cake", 10, 20, noted = true),
            Entry("obj.cod", 75, 125, noted = true),
            Entry("obj.trout", 50, 100, noted = true),
            Entry("obj.eye_of_newt", 300, 500, noted = true),
            Entry("obj.silver_bar", 15, 30, noted = true),
            Entry("obj.uncut_sapphire", 1, 3, noted = true),
        )

    private val minimalFailure = intArrayOf(956, 195, 2_518)

    init {
        require(common.sumOf(Entry::weight) == TOTAL_COMMON_WEIGHT)
        require(partialFailure.size == 27)
    }

    fun verifyDefinitions() {
        val ids =
            common.map { itemId(it.internalName) } +
                partialFailure.map { itemId(it.internalName) } +
                minimalFailure.toList() +
                listOf(
                    CRYSTAL_SHARD,
                    GAUNTLET_CAPE,
                    CRYSTAL_WEAPON_SEED,
                    CRYSTAL_ARMOUR_SEED,
                    ENHANCED_CRYSTAL_WEAPON_SEED,
                    YOUNGLLEF,
                    ELITE_CLUE_SCROLL,
                )
        ids.distinct().forEach {
            requireNotNull(ServerCacheManager.getItem(it)) {
                "Missing Corrupted Gauntlet reward item $it"
            }
        }
    }

    fun success(nextInt: (Int) -> Int, ownsCape: Boolean): List<CorruptedGauntletReward> {
        val rewards = mutableListOf<CorruptedGauntletReward>()
        rewards += CorruptedGauntletReward(CRYSTAL_SHARD, between(nextInt, 7, 12))
        if (!ownsCape) rewards += CorruptedGauntletReward(GAUNTLET_CAPE, 1)
        repeat(CorruptedGauntletConfig.COMPLETION_COMMON_ROLLS) { rewards += rollCommon(nextInt) }
        if (oneIn(nextInt, 50)) rewards += CorruptedGauntletReward(CRYSTAL_WEAPON_SEED, 1)
        if (oneIn(nextInt, 50)) rewards += CorruptedGauntletReward(CRYSTAL_ARMOUR_SEED, 1)
        if (oneIn(nextInt, 400)) {
            rewards += CorruptedGauntletReward(ENHANCED_CRYSTAL_WEAPON_SEED, 1)
        }
        if (oneIn(nextInt, 800)) rewards += CorruptedGauntletReward(YOUNGLLEF, 1)
        if (oneIn(nextInt, 20)) rewards += CorruptedGauntletReward(ELITE_CLUE_SCROLL, 1)
        return aggregate(rewards)
    }

    fun failure(nextInt: (Int) -> Int, bossDamaged: Boolean): List<CorruptedGauntletReward> {
        if (!bossDamaged) {
            return listOf(
                CorruptedGauntletReward(minimalFailure[index(nextInt, minimalFailure.size)], 1)
            )
        }
        val entry = partialFailure[index(nextInt, partialFailure.size)]
        return listOf(entry.reward(nextInt))
    }

    private fun rollCommon(nextInt: (Int) -> Int): CorruptedGauntletReward {
        var ticket = index(nextInt, TOTAL_COMMON_WEIGHT)
        for (entry in common) {
            if (ticket < entry.weight) return entry.reward(nextInt)
            ticket -= entry.weight
        }
        error("Corrupted Gauntlet reward weight escaped its table")
    }

    private fun Entry.reward(nextInt: (Int) -> Int) =
        CorruptedGauntletReward(itemId(internalName), between(nextInt, minimum, maximum), noted)

    private fun itemId(internalName: String): Int = internalName.asRSCM(RSCMType.OBJ)

    private fun oneIn(nextInt: (Int) -> Int, denominator: Int): Boolean =
        index(nextInt, denominator) == 0

    private fun between(nextInt: (Int) -> Int, minimum: Int, maximum: Int): Int =
        minimum + index(nextInt, maximum - minimum + 1)

    private fun index(nextInt: (Int) -> Int, bound: Int): Int {
        require(bound > 0)
        return Math.floorMod(nextInt(bound), bound)
    }

    private fun aggregate(rewards: List<CorruptedGauntletReward>): List<CorruptedGauntletReward> =
        rewards
            .groupBy { it.objId to it.noted }
            .map { (key, entries) ->
                CorruptedGauntletReward(key.first, entries.sumOf { it.count }, key.second)
            }
}

@Singleton
internal class CorruptedGauntletRewardService
@Inject
constructor(
    private val random: GameRandom,
    private val collectionLog: CollectionLogService,
    private val accountSaving: AccountSavingService,
    private val snapshots: CorruptedGauntletSnapshotService,
) {
    private val claiming = mutableSetOf<PlayerUid>()

    fun verifyDefinitions() {
        CorruptedGauntletChestTable.verifyDefinitions()
        listOf(
                CorruptedGauntletChestTable.YOUNGLLEF,
                CorruptedGauntletChestTable.CRYSTAL_ARMOUR_SEED,
                CorruptedGauntletChestTable.CRYSTAL_WEAPON_SEED,
                CorruptedGauntletChestTable.ENHANCED_CRYSTAL_WEAPON_SEED,
                CorruptedGauntletChestTable.GAUNTLET_CAPE,
            )
            .forEach { id ->
                require(collectionLog.catalog.slotsFor(id).isNotEmpty()) {
                    "Collection Log is missing Gauntlet item $id"
                }
            }
    }

    fun hasPending(player: Player): Boolean = player.attr.has(CORRUPTED_GAUNTLET_PENDING_REWARD)

    fun pending(player: Player): CorruptedGauntletPendingReward? =
        CorruptedGauntletPendingRewardCodec.decode(player.attr[CORRUPTED_GAUNTLET_PENDING_REWARD])

    fun completions(player: Player): Int =
        player.attr.getOrDefault(CORRUPTED_GAUNTLET_COMPLETIONS, 0)

    fun scoreboard(player: Player): CorruptedGauntletScoreboardSnapshot =
        CorruptedGauntletScoreboardSnapshot(
            completions = completions(player),
            deaths = player.attr.getOrDefault(CORRUPTED_GAUNTLET_DEATHS, 0),
            bestCompletionTicks = player.attr[CORRUPTED_GAUNTLET_BEST_COMPLETION_TICKS],
        )

    @Synchronized
    fun prepareSuccess(
        player: Player,
        runId: UUID,
        preparationTicks: Int,
        killTicks: Int,
    ): CorruptedGauntletPendingReward? =
        prepare(
            player,
            runId,
            CorruptedGauntletRewardOutcome.SUCCESS,
            preparationTicks,
            killTicks,
            countDeath = false,
        )

    @Synchronized
    fun prepareFailure(
        player: Player,
        runId: UUID,
        bossDamaged: Boolean,
        preparationTicks: Int,
        killTicks: Int,
        died: Boolean = false,
    ): CorruptedGauntletPendingReward? =
        prepare(
            player,
            runId,
            if (bossDamaged) CorruptedGauntletRewardOutcome.PARTIAL_FAILURE
            else CorruptedGauntletRewardOutcome.MINIMAL_FAILURE,
            preparationTicks,
            killTicks,
            countDeath = died,
        )

    fun claim(player: Player, chest: CoordGrid = player.coords): List<CorruptedGauntletReward>? {
        if (!claiming.add(player.uid)) return null
        try {
            val pending = pending(player)
            if (pending == null) {
                player.mes("You do not have a Corrupted Gauntlet reward waiting.")
                return null
            }
            if (collectionLog.load(player) !is PlayerCollectionLogLoad.Ready) {
                player.mes(
                    "Your Collection Log could not be loaded. Your reward remains safely stored."
                )
                return null
            }
            val rollback = claimRollback(player)
            val committed =
                runCatching {
                        accountSaving.commitPlayerMutationBlocking(
                            player,
                            mutation = {
                                val transaction =
                                    player.invTransaction(player.inv) {
                                        val destination = select(player.inv)
                                        pending.rewards.forEach { reward ->
                                            add(
                                                inv = destination,
                                                obj = reward.objId,
                                                count = reward.count,
                                                strict = true,
                                                cert = reward.noted,
                                            )
                                        }
                                    }
                                if (transaction.failure) {
                                    false
                                } else {
                                    recordCollectionLog(player, pending)
                                    player.attr.remove(CORRUPTED_GAUNTLET_PENDING_REWARD)
                                    true
                                }
                            },
                            rollback = { restoreClaimRollback(player, rollback) },
                        )
                    }
                    .getOrElse {
                        player.mes(
                            "Your reward could not be saved safely. Nothing was claimed; try again."
                        )
                        return null
                    }
            if (!committed) {
                player.mes("Make enough inventory space, then search the reward chest again.")
                return null
            }
            pending.rewards.forEach { reward ->
                val name = ServerCacheManager.getItem(reward.objId)?.name ?: "item"
                player.mes("<col=ff9900>${reward.count} x $name</col>")
            }
            player.mes("You claim your Corrupted Gauntlet reward.")
            return pending.rewards
        } finally {
            claiming.remove(player.uid)
        }
    }

    private fun prepare(
        player: Player,
        runId: UUID,
        outcome: CorruptedGauntletRewardOutcome,
        preparationTicks: Int,
        killTicks: Int,
        countDeath: Boolean,
    ): CorruptedGauntletPendingReward? {
        require(preparationTicks >= 0)
        require(killTicks >= 0)
        if (player.attr.has(CORRUPTED_GAUNTLET_PENDING_REWARD) && pending(player) == null) {
            player.mes("Your stored Gauntlet reward is corrupt and has not been replaced.")
            return null
        }
        val existing = pending(player)
        val lastRun = decodeUuid(player.attr[CORRUPTED_GAUNTLET_LAST_RESULT])
        return when (
            CorruptedGauntletPendingRewardRules.preparation(runId, existing?.runId, lastRun)
        ) {
            CorruptedGauntletRewardPreparation.EXISTING_FOR_RUN -> existing
            CorruptedGauntletRewardPreparation.ALREADY_FINALIZED -> null
            CorruptedGauntletRewardPreparation.BLOCKED_BY_OTHER_REWARD -> {
                player.mes("Claim your previous Gauntlet reward before starting another run.")
                existing
            }
            CorruptedGauntletRewardPreparation.NEW -> {
                val rewards =
                    when (outcome) {
                        CorruptedGauntletRewardOutcome.SUCCESS ->
                            CorruptedGauntletChestTable.success(
                                nextInt = { bound -> random.of(bound) },
                                ownsCape = ownsCape(player),
                            )
                        CorruptedGauntletRewardOutcome.PARTIAL_FAILURE ->
                            CorruptedGauntletChestTable.failure(
                                nextInt = { bound -> random.of(bound) },
                                bossDamaged = true,
                            )
                        CorruptedGauntletRewardOutcome.MINIMAL_FAILURE ->
                            CorruptedGauntletChestTable.failure(
                                nextInt = { bound -> random.of(bound) },
                                bossDamaged = false,
                            )
                    }
                val created =
                    CorruptedGauntletPendingReward(
                        runId,
                        UUID.randomUUID(),
                        outcome,
                        preparationTicks,
                        killTicks,
                        rewards,
                    )
                val rollback = preparationRollback(player)
                val committed =
                    runCatching {
                            accountSaving.commitPlayerMutationBlocking(
                                player,
                                mutation = {
                                    player.attr[CORRUPTED_GAUNTLET_PENDING_REWARD] =
                                        CorruptedGauntletPendingRewardCodec.encode(created)
                                    player.attr[CORRUPTED_GAUNTLET_LAST_RESULT] = encodeUuid(runId)
                                    if (outcome == CorruptedGauntletRewardOutcome.SUCCESS) {
                                        player.attr[CORRUPTED_GAUNTLET_COMPLETIONS] =
                                            completions(player) + 1
                                        updateBest(
                                            player,
                                            CORRUPTED_GAUNTLET_BEST_PREP_TICKS,
                                            preparationTicks,
                                        )
                                        updateBest(
                                            player,
                                            CORRUPTED_GAUNTLET_BEST_KILL_TICKS,
                                            killTicks,
                                        )
                                        updateBest(
                                            player,
                                            CORRUPTED_GAUNTLET_BEST_COMPLETION_TICKS,
                                            preparationTicks + killTicks,
                                        )
                                    } else if (countDeath) {
                                        player.attr[CORRUPTED_GAUNTLET_DEATHS] =
                                            player.attr.getOrDefault(CORRUPTED_GAUNTLET_DEATHS, 0) +
                                                1
                                    }
                                    true
                                },
                                rollback = { restorePreparationRollback(player, rollback) },
                            )
                        }
                        .getOrDefault(false)
                if (committed) created else null
            }
        }
    }

    private fun ownsCape(player: Player): Boolean =
        snapshots.ownsStoredItem(player, CorruptedGauntletChestTable.GAUNTLET_CAPE) ||
            player.invMap.values
                .asSequence()
                .filter { it.type.scope == InvScope.Perm }
                .flatMap { it.objs.asSequence() }
                .any { it?.id == CorruptedGauntletChestTable.GAUNTLET_CAPE }

    private fun recordCollectionLog(player: Player, pending: CorruptedGauntletPendingReward) {
        val tracked =
            pending.rewards.filter { reward ->
                reward.objId != CorruptedGauntletChestTable.YOUNGLLEF &&
                    collectionLog.catalog.slotsFor(reward.objId).isNotEmpty()
            }
        if (tracked.isNotEmpty()) {
            collectionLog.recordBatch(
                player = player,
                awards = tracked.map { it.objId to it.count },
                source =
                    CollectionLogAwardSource(
                        CollectionLogAwardKind.MinigameReward,
                        "corrupted-gauntlet",
                    ),
                eventId = "${pending.runId}:${pending.transactionId}:claim",
            )
        }
        pending.rewards
            .firstOrNull { it.objId == CorruptedGauntletChestTable.YOUNGLLEF }
            ?.let {
                collectionLog.record(
                    player = player,
                    itemId = it.objId,
                    amount = it.count,
                    source = CollectionLogAwardSource(CollectionLogAwardKind.PetGrant, "youngllef"),
                    eventId = "${pending.runId}:${pending.transactionId}:pet",
                )
            }
    }

    private fun updateBest(player: Player, key: AttributeKey<Int>, ticks: Int) {
        val current = player.attr[key]
        if (current == null || ticks < current) player.attr[key] = ticks
    }

    private fun preparationRollback(player: Player) =
        PreparationRollback(
            pending = player.attr[CORRUPTED_GAUNTLET_PENDING_REWARD]?.toMutableList(),
            lastResult = player.attr[CORRUPTED_GAUNTLET_LAST_RESULT]?.toMutableList(),
            completions = player.attr[CORRUPTED_GAUNTLET_COMPLETIONS],
            deaths = player.attr[CORRUPTED_GAUNTLET_DEATHS],
            bestPrep = player.attr[CORRUPTED_GAUNTLET_BEST_PREP_TICKS],
            bestKill = player.attr[CORRUPTED_GAUNTLET_BEST_KILL_TICKS],
            bestCompletion = player.attr[CORRUPTED_GAUNTLET_BEST_COMPLETION_TICKS],
        )

    private fun restorePreparationRollback(player: Player, rollback: PreparationRollback) {
        restore(player, CORRUPTED_GAUNTLET_PENDING_REWARD, rollback.pending)
        restore(player, CORRUPTED_GAUNTLET_LAST_RESULT, rollback.lastResult)
        restore(player, CORRUPTED_GAUNTLET_COMPLETIONS, rollback.completions)
        restore(player, CORRUPTED_GAUNTLET_DEATHS, rollback.deaths)
        restore(player, CORRUPTED_GAUNTLET_BEST_PREP_TICKS, rollback.bestPrep)
        restore(player, CORRUPTED_GAUNTLET_BEST_KILL_TICKS, rollback.bestKill)
        restore(player, CORRUPTED_GAUNTLET_BEST_COMPLETION_TICKS, rollback.bestCompletion)
    }

    private fun claimRollback(player: Player) =
        ClaimRollback(
            inventory = Array(player.inv.size) { slot -> player.inv[slot]?.let(::InvObj) },
            pending = player.attr[CORRUPTED_GAUNTLET_PENDING_REWARD]?.toMutableList(),
            collectionLog = copyPersistenceValue(player.attr[CollectionLogService.STATE]),
            hadCollectionLog = player.attr.has(CollectionLogService.STATE),
        )

    private fun restoreClaimRollback(player: Player, rollback: ClaimRollback) {
        rollback.inventory.forEachIndexed { slot, obj -> player.inv[slot] = obj?.let(::InvObj) }
        restore(player, CORRUPTED_GAUNTLET_PENDING_REWARD, rollback.pending)
        if (rollback.hadCollectionLog) {
            player.attr[CollectionLogService.STATE] = requireNotNull(rollback.collectionLog)
        } else {
            player.attr.remove(CollectionLogService.STATE)
        }
    }

    private fun copyPersistenceValue(value: Any?): Any? =
        when (value) {
            is MutableList<*> -> value.toMutableList()
            is List<*> -> value.toList()
            is MutableMap<*, *> -> value.toMutableMap()
            is Map<*, *> -> value.toMap()
            else -> value
        }

    private fun <T> restore(player: Player, key: AttributeKey<T>, value: T?) {
        if (value == null) player.attr.remove(key) else player.attr[key] = value
    }

    private data class PreparationRollback(
        val pending: MutableList<Int>?,
        val lastResult: MutableList<Int>?,
        val completions: Int?,
        val deaths: Int?,
        val bestPrep: Int?,
        val bestKill: Int?,
        val bestCompletion: Int?,
    )

    private data class ClaimRollback(
        val inventory: Array<InvObj?>,
        val pending: MutableList<Int>?,
        val collectionLog: Any?,
        val hadCollectionLog: Boolean,
    )
}

internal object CorruptedGauntletPendingRewardCodec {
    private const val MAGIC = 0x4347_5201
    private const val MAX_REWARDS = 16

    fun encode(reward: CorruptedGauntletPendingReward): MutableList<Int> =
        buildList {
                add(MAGIC)
                addUuid(reward.runId)
                addUuid(reward.transactionId)
                add(reward.outcome.ordinal)
                add(reward.preparationTicks)
                add(reward.killTicks)
                add(reward.rewards.size)
                reward.rewards.forEach { item ->
                    add(item.objId)
                    add(item.count)
                    add(if (item.noted) 1 else 0)
                }
            }
            .toMutableList()

    fun decode(values: List<Int>?): CorruptedGauntletPendingReward? {
        if (values.isNullOrEmpty()) return null
        return runCatching {
                val reader = IntReader(values)
                require(reader.next() == MAGIC)
                val runId = reader.uuid()
                val transactionId = reader.uuid()
                val outcome = CorruptedGauntletRewardOutcome.entries[reader.next()]
                val prepTicks = reader.next().also { require(it >= 0) }
                val killTicks = reader.next().also { require(it >= 0) }
                val count = reader.next().also { require(it in 1..MAX_REWARDS) }
                val rewards =
                    List(count) {
                        CorruptedGauntletReward(
                            objId = reader.next(),
                            count = reader.next(),
                            noted = reader.next().also { require(it in 0..1) } == 1,
                        )
                    }
                require(reader.finished())
                CorruptedGauntletPendingReward(
                    runId,
                    transactionId,
                    outcome,
                    prepTicks,
                    killTicks,
                    rewards,
                )
            }
            .getOrNull()
    }

    private class IntReader(private val values: List<Int>) {
        private var index = 0

        fun next(): Int = values[index++]

        fun long(): Long = (next().toLong() shl 32) or (next().toLong() and 0xffff_ffffL)

        fun uuid(): UUID = UUID(long(), long())

        fun finished(): Boolean = index == values.size
    }
}

private fun MutableList<Int>.addUuid(value: UUID) {
    add((value.mostSignificantBits ushr 32).toInt())
    add(value.mostSignificantBits.toInt())
    add((value.leastSignificantBits ushr 32).toInt())
    add(value.leastSignificantBits.toInt())
}

private fun encodeUuid(value: UUID): MutableList<Int> =
    mutableListOf<Int>().apply { addUuid(value) }

private fun decodeUuid(values: List<Int>?): UUID? {
    if (values?.size != 4) return null
    fun long(high: Int, low: Int) = (high.toLong() shl 32) or (low.toLong() and 0xffff_ffffL)
    return UUID(long(values[0], values[1]), long(values[2], values[3]))
}

package org.rsmod.content.bosses.doom

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.collectionlog.CollectionLogAwardKind
import org.rsmod.api.collectionlog.CollectionLogAwardSource
import org.rsmod.api.collectionlog.CollectionLogService
import org.rsmod.api.collectionlog.PlayerCollectionLogLoad
import org.rsmod.api.invtx.add
import org.rsmod.api.invtx.invAddOrDrop
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.output.mes
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.Inventory
import org.rsmod.map.CoordGrid

internal data class DoomReward(val objId: Int, val count: Int)

internal data class DoomPendingRewardState(
    val runId: String,
    val resultIds: Set<String>,
    val rewards: List<DoomReward>,
    val uniqueRisk: Boolean,
    val claiming: Boolean = false,
)

internal data class DoomRewardRoll(val rewards: List<DoomReward>, val containsUniqueRisk: Boolean)

internal data class DoomLootEntry(
    val internal: String,
    val weight: Int,
    val count: IntRange = 1..1,
)

internal object DoomPendingRewardCodec {
    private const val VERSION = "v2"

    fun encode(state: DoomPendingRewardState): String =
        listOf(
                VERSION,
                state.runId,
                state.resultIds.sorted().joinToString(","),
                if (state.uniqueRisk) "1" else "0",
                if (state.claiming) "1" else "0",
                state.rewards
                    .filter { it.count > 0 }
                    .sortedBy { it.objId }
                    .joinToString(";") { "${it.objId}:${it.count}" },
            )
            .joinToString("|")

    fun decode(encoded: String?): DoomPendingRewardState {
        if (encoded.isNullOrBlank()) {
            return DoomPendingRewardState("", emptySet(), emptyList(), uniqueRisk = false)
        }
        if (!encoded.startsWith("$VERSION|")) {
            return DoomPendingRewardState(
                runId = "legacy",
                resultIds = emptySet(),
                rewards = decodeRewards(encoded),
                uniqueRisk = false,
            )
        }
        val parts = encoded.split('|', limit = 6)
        require(parts.size == 6) { "Malformed Doom pending-reward state." }
        return DoomPendingRewardState(
            runId = parts[1],
            resultIds = parts[2].split(',').filter(String::isNotBlank).toSet(),
            uniqueRisk = parts[3] == "1",
            claiming = parts[4] == "1",
            rewards = decodeRewards(parts[5]),
        )
    }

    private fun decodeRewards(encoded: String): List<DoomReward> =
        encoded.split(';').mapNotNull {
            val pair = it.split(':')
            val id = pair.getOrNull(0)?.toIntOrNull()
            val count = pair.getOrNull(1)?.toIntOrNull()
            if (id == null || count == null || count <= 0) null else DoomReward(id, count)
        }
}

internal object DoomLootTable {
    const val TOTAL_COMMON_WEIGHT = 104

    val common =
        listOf(
            DoomLootEntry("obj.cert_dragon_med_helm", 5),
            DoomLootEntry("obj.cert_dragon_platelegs", 1, 2..4),
            DoomLootEntry("obj.cert_mystic_earth_staff", 5),
            DoomLootEntry("obj.cert_rune_pickaxe", 5, 1..3),
            DoomLootEntry("obj.deathrune", 5, 50..70),
            DoomLootEntry("obj.chaosrune", 5, 50..70),
            DoomLootEntry("obj.earthrune", 5, 500..1000),
            DoomLootEntry("obj.firerune", 5, 500..1000),
            DoomLootEntry("obj.mcannonball", 5, 200..600),
            DoomLootEntry("obj.xbows_crossbow_bolts_runite_tipped_onyx", 5, 5..15),
            DoomLootEntry("obj.cert_coal", 5, 15..50),
            DoomLootEntry("obj.cert_gold_ore", 5, 20..60),
            DoomLootEntry("obj.cert_runite_ore", 5, 3..6),
            DoomLootEntry("obj.celastrus_tree_seed", 3),
            DoomLootEntry("obj.ranarr_seed", 2, 1..3),
            DoomLootEntry("obj.spirit_tree_seed", 3),
            DoomLootEntry("obj.cosmic_soul_catalyst", 5, 151..400),
            DoomLootEntry("obj.dragon_dart_tip", 5, 30..90),
            DoomLootEntry("obj.cert_raw_shark", 3, 20..35),
            DoomLootEntry("obj.shark_lure", 2, 40..70),
            DoomLootEntry("obj.sun_kissed_bone", 5, 25..75),
            DoomLootEntry("obj.varlamore_key_half_1", 1),
            DoomLootEntry("obj.demon_tear", 7, 100..300),
            DoomLootEntry("obj.dom_teleport_item", 7, 1..2),
        )

    init {
        require(common.sumOf(DoomLootEntry::weight) == TOTAL_COMMON_WEIGHT)
    }
}

@Singleton
internal class DoomRewardService
@Inject
constructor(
    private val random: GameRandom,
    private val objects: ObjRepository,
    private val collectionLog: CollectionLogService,
) {
    private val log = InlineLogger()

    fun validateAssets() {
        allRewardInternals.forEach { internal ->
            val id = itemId(internal)
            requireNotNull(ServerCacheManager.getItem(id)) { "Missing Doom reward $internal ($id)" }
        }
    }

    @Synchronized fun hasPending(player: Player) = state(player).rewards.isNotEmpty()

    @Synchronized fun hasUniqueRisk(player: Player) = state(player).uniqueRisk

    @Synchronized fun pending(player: Player) = state(player).rewards

    @Synchronized
    fun pendingValue(player: Player): Long =
        pending(player).sumOf { reward ->
            (ServerCacheManager.getItem(reward.objId)?.cost ?: 0).toLong() * reward.count
        }

    @Synchronized
    fun clear(player: Player) {
        player.attr.remove(DOOM_PENDING_REWARDS)
    }

    @Synchronized
    fun accumulate(
        player: Player,
        delve: Int,
        runId: String,
        resultId: String,
    ): DoomPendingRewardState {
        val previous = state(player)
        if (resultId in previous.resultIds) return previous
        require(previous.runId.isBlank() || previous.runId == "legacy" || previous.runId == runId) {
            "Pending Doom rewards belong to another run."
        }

        DoomEyeState.addPassiveCharges(player.inv, random.of(10..20))
        val roll = roll(player, delve)
        val merged = linkedMapOf<Int, Int>()
        previous.rewards.forEach { merged[it.objId] = (merged[it.objId] ?: 0) + it.count }
        roll.rewards.forEach { merged[it.objId] = (merged[it.objId] ?: 0) + it.count }
        val updated =
            DoomPendingRewardState(
                runId = runId,
                resultIds = previous.resultIds + resultId,
                rewards = merged.map { DoomReward(it.key, it.value) },
                uniqueRisk = previous.uniqueRisk || roll.containsUniqueRisk,
            )
        player.attr[DOOM_PENDING_REWARDS] = DoomPendingRewardCodec.encode(updated)
        log.info {
            "[Doom] player=${player.displayName} delve=$delve result=$resultId rewards=${roll.rewards}"
        }
        return updated
    }

    @Synchronized
    fun claim(player: Player, chest: CoordGrid = player.coords): Boolean {
        val current = beginClaim(player) ?: return false
        current.rewards.forEach {
            val internal = RSCM.getReverseMapping(RSCMType.OBJ, it.objId)
            player.invAddOrDrop(objects, internal, it.count, coords = chest)
            player.mes(
                "<col=ff9900>${it.count} x ${ServerCacheManager.getItem(it.objId)?.name ?: internal}</col>"
            )
        }
        finishClaim(player, current, "inventory")
        return true
    }

    @Synchronized
    fun claimToBank(player: Player, bank: Inventory): Boolean {
        val current = beginClaim(player) ?: return false
        val transaction =
            player.invTransaction(bank) {
                val destination = select(bank)
                current.rewards.forEach {
                    add(
                        inv = destination,
                        obj = it.objId,
                        count = it.count,
                        strict = true,
                        uncert = true,
                    )
                }
            }
        if (transaction.failure) {
            player.attr[DOOM_PENDING_REWARDS] =
                DoomPendingRewardCodec.encode(current.copy(claiming = false))
            player.mes("Your bank does not have enough room for all of these rewards.")
            return false
        }
        finishClaim(player, current, "bank")
        return true
    }

    fun summary(player: Player): String {
        val rewards = pending(player)
        if (rewards.isEmpty()) return "No rewards are currently stored."
        return rewards.joinToString(prefix = "Stored rewards: ", separator = ", ") {
            "${it.count} x ${ServerCacheManager.getItem(it.objId)?.name ?: it.objId}"
        }
    }

    private fun beginClaim(player: Player): DoomPendingRewardState? {
        val current = state(player)
        if (current.rewards.isEmpty()) {
            player.mes("You do not have any Mokhaiotl rewards waiting.")
            return null
        }
        if (current.claiming) {
            clear(player)
            player.mes("A previous reward claim was already committed; no items were duplicated.")
            return null
        }
        player.attr[DOOM_PENDING_REWARDS] =
            DoomPendingRewardCodec.encode(current.copy(claiming = true))
        return current
    }

    private fun finishClaim(player: Player, state: DoomPendingRewardState, destination: String) {
        val domId = itemId("obj.dompet")
        val normalAwards = state.rewards.filter { it.objId != domId }.map { it.objId to it.count }
        if (normalAwards.isNotEmpty()) {
            collectionLog.recordBatch(
                player = player,
                awards = normalAwards,
                source =
                    CollectionLogAwardSource(CollectionLogAwardKind.BossChest, "doom-of-mokhaiotl"),
                eventId = "${state.runId}:${state.resultIds.sorted().joinToString(",")}:claim",
            )
        }
        if (state.rewards.any { it.objId == domId }) {
            collectionLog.record(
                player = player,
                itemId = domId,
                amount = 1,
                source = CollectionLogAwardSource(CollectionLogAwardKind.PetGrant, "dom"),
                eventId = "${state.runId}:dom",
            )
        }
        clear(player)
        player.mes("You claim the rewards accumulated during your delve to your $destination.")
    }

    internal fun roll(player: Player, delve: Int): DoomRewardRoll {
        val rewards = mutableListOf<DoomReward>()
        val tears = DoomRules.guaranteedTears(delve)
        if (tears > 0) rewards += DoomReward(itemId("obj.demon_tear"), tears)

        val denominator = DoomRules.uniqueDenominator(delve)
        var uniqueRisk = false
        if (denominator != null) {
            if (delve >= 2 && oneIn(denominator)) {
                rewards += DoomReward(itemId("obj.mokhaiotl_cloth"), 1)
                uniqueRisk = true
            }
            if (delve >= 3 && oneIn(denominator)) {
                rewards += DoomReward(itemId("obj.eye_of_ayak_uncharged"), 1)
                uniqueRisk = true
            }
            if (delve >= 4 && oneIn(denominator)) {
                rewards += DoomReward(itemId("obj.avernic_treads"), 1)
                uniqueRisk = true
            }
        }

        DoomRules.domDenominator(delve)?.let { domRate ->
            if (oneIn(domRate)) {
                rewards += DoomReward(itemId("obj.dompet"), 1)
                uniqueRisk = uniqueRisk || !hasLoggedDom(player)
            }
        }

        val common = weightedCommon()
        val baseQuantity =
            if (common.internal == "obj.dom_teleport_item" && delve == 1) 1
            else random.of(common.count)
        rewards +=
            DoomReward(
                itemId(common.internal),
                DoomRules.scaleRewardQuantity(delve, baseQuantity).coerceAtLeast(1),
            )

        if (oneIn(DoomRules.eliteClueDenominator(delve))) {
            rewards += DoomReward(itemId("obj.trail_elite_emote_exp1"), 1)
        }
        return DoomRewardRoll(rewards, uniqueRisk)
    }

    private fun weightedCommon(): DoomLootEntry {
        var roll = random.of(DoomLootTable.TOTAL_COMMON_WEIGHT)
        for (entry in DoomLootTable.common) {
            if (roll < entry.weight) return entry
            roll -= entry.weight
        }
        error("Doom common loot weight selection escaped its table.")
    }

    private fun oneIn(denominator: Int): Boolean {
        require(denominator > 0)
        return random.of(denominator) == 0
    }

    private fun hasLoggedDom(player: Player): Boolean =
        when (val loaded = collectionLog.load(player)) {
            is PlayerCollectionLogLoad.Ready ->
                collectionLog.engine.quantity(loaded.state, itemId("obj.dompet")) > 0
            PlayerCollectionLogLoad.CorruptOrUnsupported -> false
        }

    private fun state(player: Player): DoomPendingRewardState =
        DoomPendingRewardCodec.decode(player.attr[DOOM_PENDING_REWARDS])

    private fun itemId(internal: String) = RSCM.run { internal.asRSCM(RSCMType.OBJ) }

    private companion object {
        val uniqueInternals =
            listOf(
                "obj.mokhaiotl_cloth",
                "obj.eye_of_ayak_uncharged",
                "obj.avernic_treads",
                "obj.dompet",
                "obj.trail_elite_emote_exp1",
                "obj.demon_tear",
            )
        val allRewardInternals =
            (DoomLootTable.common.map(DoomLootEntry::internal) + uniqueInternals).distinct()
    }
}

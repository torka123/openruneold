package org.rsmod.content.minigames.tob

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.collectionlog.CollectionLogAwardKind
import org.rsmod.api.collectionlog.CollectionLogAwardSource
import org.rsmod.api.collectionlog.CollectionLogService
import org.rsmod.api.instances.RaidRewardClaim
import org.rsmod.api.instances.RaidRewardClaimResult
import org.rsmod.api.invtx.add
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.output.mes
import org.rsmod.api.random.GameRandom
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.PlayerUid

@Singleton
internal class TobRewardService
@Inject
constructor(
    private val random: GameRandom,
    private val collectionLog: CollectionLogService,
) {
    private val log = InlineLogger()
    private val claiming = mutableSetOf<PlayerUid>()

    fun verifyDefinitions() =
        (TobRewardRules.normalUniques.map(WeightedTobUnique::objId) +
                COMMON.map(Common::objId) +
                TERTIARY)
            .distinct()
            .forEach {
                requireNotNull(ServerCacheManager.getItem(it)) {
                    "Missing ToB reward definition $it"
                }
                RSCM.getReverseMapping(RSCMType.OBJ, it)
            }

    fun prepare(raid: TobRaidSession, cycle: Int, forceUniqueFor: PlayerUid? = null) {
        if (raid.participants.values.any { it.pendingReward != null }) return
        val participants = raid.participants.values.filter(TobParticipant::entered)
        val score = participants.sumOf(TobParticipant::score)
        val chance = TobRewardRules.teamUniqueChance(raid.mode, score, participants.size)
        val forcedOwner = participants.firstOrNull { it.uid == forceUniqueFor && it.score > 0 }
        val uniqueRolled =
            TobRewardRules.isUniqueRoll(
                chance,
                random.of(1..TobRewardRules.PURPLE_CHEST_DENOMINATOR),
                forceUnique = forcedOwner != null,
            )
        val owner =
            forcedOwner
                ?: if (uniqueRolled && score > 0)
                    TobRewardRules.ownerTicket(participants, random.of(0 until score))
                else null
        val unique =
            owner?.let {
                val table =
                    if (raid.mode == TobMode.HARD) TobRewardRules.hardUniques
                    else TobRewardRules.normalUniques
                TobRewardRules.uniqueAtTicket(
                    raid.mode,
                    random.of(0 until table.sumOf(WeightedTobUnique::weight)),
                )
            }
        val inTime =
            raid.mode == TobMode.HARD &&
                cycle - raid.startedAt <= TobRewardRules.hardTimeTicks(participants.size)
        participants.forEach { participant ->
            val rewards =
                if (participant === owner && unique != null) {
                    mutableListOf(TobReward(unique.objId, 1, true))
                } else {
                    MutableList(3) { rollCommon(raid.mode, inTime) }
                }
            if (
                raid.mode != TobMode.ENTRY &&
                    unique != null &&
                    participant === owner &&
                    random.of(1..650) == 1
            )
                rewards += TobReward(LIL_ZIK, 1, true)
            if (raid.mode == TobMode.HARD && inTime) {
                if (random.of(1..275) == 1) rewards += TobReward(SANGUINE_DUST, 1, true)
                if (random.of(1..100) == 1)
                    rewards +=
                        TobReward(if (random.of(0..1) == 0) HOLY_KIT else SANGUINE_KIT, 1, true)
            }
            participant.pendingReward =
                TobRewardRoll(rewards, participant === owner && unique != null)
        }
        log.info {
            "[ToB] rewards party=${raid.party.id} mode=${raid.mode} score=$score chance=$chance unique=${unique?.objId} inTime=$inTime"
        }
    }

    fun claim(player: Player, raid: TobRaidSession): TobRewardRoll? =
        claimAttempt(player, raid).reward

    fun claimForLeave(player: Player, raid: TobRaidSession): RaidRewardClaimResult =
        claimAttempt(player, raid).result

    private fun claimAttempt(
        player: Player,
        raid: TobRaidSession,
    ): RaidRewardClaim<TobRewardRoll> {
        val participant =
            raid.participant(player.uid)
                ?: return RaidRewardClaim(RaidRewardClaimResult.NothingToClaim)
        if (!claiming.add(player.uid)) {
            player.mes("Your Theatre reward is already being processed.")
            return RaidRewardClaim(RaidRewardClaimResult.Busy)
        }
        try {
            if (participant.rewardClaimed) {
                player.mes("You have already searched your monumental chest.")
                return RaidRewardClaim(RaidRewardClaimResult.AlreadyClaimed)
            }
            val roll =
                participant.pendingReward
                    ?: run {
                        player.mes("There is no Theatre reward waiting for you.")
                        return RaidRewardClaim(RaidRewardClaimResult.NothingToClaim)
                    }
            val transaction =
                player.invTransaction(player.inv) {
                    val destination = select(player.inv)
                    roll.rewards.forEach { reward ->
                        add(
                            inv = destination,
                            obj = reward.objId,
                            count = reward.count,
                            strict = true,
                            uncert = true,
                        )
                    }
                }
            if (transaction.failure) {
                player.mes(
                    "You need enough inventory space for your entire Theatre reward. " +
                        "Free some space and try again."
                )
                return RaidRewardClaim(RaidRewardClaimResult.InventoryFull)
            }
            roll.rewards.forEach { reward ->
                if (reward.objId == LIL_ZIK) player.attr[TOB_LIL_ZIK_UNLOCKED] = true
            }
            participant.rewardClaimed = true
            participant.pendingReward = null
            collectionLog.recordBatch(
                player = player,
                awards = roll.rewards.map { reward -> reward.objId to reward.count },
                source =
                    CollectionLogAwardSource(
                        CollectionLogAwardKind.RaidReward,
                        "theatre-of-blood",
                    ),
                eventId = "${raid.party.id}:${raid.startedAt}:${player.uid}",
            )
            return RaidRewardClaim(RaidRewardClaimResult.Claimed, roll)
        } finally {
            claiming.remove(player.uid)
        }
    }

    private fun rollCommon(mode: TobMode, inTime: Boolean): TobReward {
        val total = COMMON.sumOf(Common::weight)
        val ticket = random.of(0 until total)
        var cursor = 0
        val entry =
            COMMON.first {
                cursor += it.weight
                ticket < cursor
            }
        val multiplier = TobRewardRules.commonMultiplier(mode, inTime)
        val count = (random.of(entry.min..entry.max) * multiplier).toInt().coerceAtLeast(1)
        return TobReward(entry.objId, count, false)
    }

    private data class Common(val objId: Int, val min: Int, val max: Int, val weight: Int)

    private companion object {
        const val LIL_ZIK = 22473
        const val HOLY_KIT = 25742
        const val SANGUINE_KIT = 25744
        const val SANGUINE_DUST = 25746
        val TERTIARY = listOf(LIL_ZIK, HOLY_KIT, SANGUINE_KIT, SANGUINE_DUST)
        val COMMON =
            listOf(
                Common(565, 100, 600, 10),
                Common(560, 100, 500, 9),
                Common(995, 10_000, 100_000, 8),
                Common(454, 100, 500, 7),
                Common(450, 20, 100, 6),
                Common(452, 5, 30, 4),
                Common(22446, 10, 50, 4),
                Common(386, 10, 35, 5),
                Common(392, 10, 30, 5),
            )
    }
}

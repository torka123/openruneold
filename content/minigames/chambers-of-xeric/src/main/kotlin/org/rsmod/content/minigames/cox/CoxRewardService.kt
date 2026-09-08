package org.rsmod.content.minigames.cox

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
internal class CoxRewardService
@Inject
constructor(
    private val random: GameRandom,
    private val collectionLog: CollectionLogService,
) {
    private val log = InlineLogger()
    private val claiming = mutableSetOf<PlayerUid>()

    fun verifyDefinitions() {
        (CoxRewardRules.donorUniqueTable(false).map(WeightedUnique::objId) +
                DONOR_COMMON.map(DonorCommon::objId) +
                listOf(OLMLET))
            .distinct()
            .forEach { objId ->
                requireNotNull(ServerCacheManager.getItem(objId)) {
                    "Missing donor CoX reward definition: obj=$objId"
                }
                RSCM.getReverseMapping(RSCMType.OBJ, objId)
            }
    }

    /** Exact reward shape used by the donor: one personal purple or two common rolls. */
    fun prepare(session: CoxRaidSession, forceUniqueFor: PlayerUid? = null) {
        if (session.participants.values.any { it.pendingReward != null }) return
        var uniqueCount = 0
        for (participant in session.participants.values) {
            val points = participant.personalPoints
            val rewards = mutableListOf<CoxReward>()
            val purpleChance = CoxRewardRules.donorPurpleChance(points, session.olmOnly)
            val purple =
                participant.uid == forceUniqueFor ||
                    (points > DONOR_MINIMUM_REWARD_POINTS &&
                        random.of(0..999_999) < (purpleChance * 1_000_000.0).toInt())
            if (purple) {
                val table = CoxRewardRules.donorUniqueTable(session.olmOnly)
                val weight = table.sumOf(WeightedUnique::weight)
                val unique = CoxRewardRules.uniqueAtTicket(table, random.of(0 until weight))
                rewards += CoxReward(unique.objId, 1, unique = true)
                uniqueCount++
                if (random.of(1..DONOR_OLMLET_RATE) == 1) {
                    rewards += CoxReward(OLMLET, 1, unique = true)
                }
            } else {
                repeat(2) {
                    val common = DONOR_COMMON[random.of(DONOR_COMMON.indices)]
                    rewards +=
                        CoxReward(
                            common.objId,
                            CoxRewardRules.donorCommonAmount(points, common.pointsPerItem),
                            unique = false,
                        )
                }
            }
            participant.pendingReward =
                CoxRewardRoll(
                    rewards = rewards,
                    uniqueAwarded = purple,
                    uniqueOwner = if (purple) participant.uid else null,
                )
        }
        log.info {
            "[CoX] donor rewards party=${session.party.id} olmOnly=${session.olmOnly} " +
                "players=${session.participants.size} uniques=$uniqueCount"
        }
    }

    fun claim(player: Player, session: CoxRaidSession): CoxRewardRoll? =
        claimAttempt(player, session).reward

    fun claimForLeave(player: Player, session: CoxRaidSession): RaidRewardClaimResult =
        claimAttempt(player, session).result

    private fun claimAttempt(
        player: Player,
        session: CoxRaidSession,
    ): RaidRewardClaim<CoxRewardRoll> {
        val participant =
            session.participant(player.uid)
                ?: return RaidRewardClaim(RaidRewardClaimResult.NothingToClaim)
        if (!claiming.add(player.uid)) {
            player.mes("Your Chambers reward is already being processed.")
            return RaidRewardClaim(RaidRewardClaimResult.Busy)
        }
        try {
            if (participant.rewardClaimed) {
                player.mes("You have already claimed your Chambers reward.")
                return RaidRewardClaim(RaidRewardClaimResult.AlreadyClaimed)
            }
            val roll =
                participant.pendingReward
                    ?: run {
                        player.mes("There is no reward waiting for you.")
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
                    "You need enough inventory space for your entire Chambers reward. " +
                        "Free some space and try again."
                )
                return RaidRewardClaim(RaidRewardClaimResult.InventoryFull)
            }
            participant.rewardClaimed = true
            participant.pendingReward = null
            collectionLog.recordBatch(
                player = player,
                awards = roll.rewards.map { reward -> reward.objId to reward.count },
                source =
                    CollectionLogAwardSource(
                        CollectionLogAwardKind.RaidReward,
                        "chambers-of-xeric",
                    ),
                eventId = "${session.party.id}:${session.startedAt}:${player.uid}",
            )
            for (reward in roll.rewards) {
                val internalName = RSCM.getReverseMapping(RSCMType.OBJ, reward.objId)
                if (reward.objId == OLMLET) player.attr[COX_OLMLET_UNLOCKED] = true
                val name = ServerCacheManager.getItem(reward.objId)?.name ?: internalName
                player.mes(
                    "${if (reward.unique) "<col=ff0000>" else "<col=ff9900>"}" +
                        "${reward.count} x $name</col>"
                )
            }
            player.mes("You claim your Chambers of Xeric reward.")
            return RaidRewardClaim(RaidRewardClaimResult.Claimed, roll)
        } finally {
            claiming.remove(player.uid)
        }
    }

    private data class DonorCommon(val objId: Int, val pointsPerItem: Int)

    private companion object {
        const val OLMLET = 20_851
        const val DONOR_OLMLET_RATE = 40
        const val DONOR_MINIMUM_REWARD_POINTS = 1_000

        val DONOR_COMMON =
            listOf(
                DonorCommon(560, 20),
                DonorCommon(565, 16),
                DonorCommon(566, 10),
                DonorCommon(892, 7),
                DonorCommon(11_212, 70),
                DonorCommon(3_050, 185),
                DonorCommon(208, 400),
                DonorCommon(210, 98),
                DonorCommon(212, 185),
                DonorCommon(214, 205),
                DonorCommon(3_052, 500),
                DonorCommon(216, 200),
                DonorCommon(2_486, 150),
                DonorCommon(218, 106),
                DonorCommon(220, 428),
                DonorCommon(443, 20),
                DonorCommon(454, 20),
                DonorCommon(445, 45),
                DonorCommon(448, 45),
                DonorCommon(450, 100),
                DonorCommon(452, 750),
                DonorCommon(1_624, 100),
                DonorCommon(1_622, 170),
                DonorCommon(1_620, 125),
                DonorCommon(1_618, 260),
                DonorCommon(7_937, 200),
                DonorCommon(8_781, 100),
                DonorCommon(8_783, 240),
                DonorCommon(21_047, 131_071),
                DonorCommon(21_027, 131_071),
            )
    }
}

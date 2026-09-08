@file:OptIn(dev.openrune.types.util.UncheckedType::class)

package org.rsmod.content.minigames.clanmayhem

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.UUID
import kotlin.random.Random
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.invtx.add
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.output.mes
import org.rsmod.api.random.GameRandom
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj

internal val MAYHEM_POINTS: AttributeKey<Int> =
    AttributeKey(persistenceKey = "clan_mayhem_points_v1")
internal val MAYHEM_WINS: AttributeKey<Int> = AttributeKey(persistenceKey = "clan_mayhem_wins_v1")
internal val MAYHEM_FIRST_WIN_DAY: AttributeKey<Int> =
    AttributeKey(persistenceKey = "clan_mayhem_first_win_day_v1")
internal val MAYHEM_LAST_POINT_MATCH: AttributeKey<MutableList<Int>> =
    AttributeKey(persistenceKey = "clan_mayhem_last_point_match_v1")
internal val MAYHEM_PENDING_REWARD: AttributeKey<MutableList<Int>> =
    AttributeKey(persistenceKey = "clan_mayhem_pending_reward_v1")

internal data class ClanMayhemPendingReward(
    val matchId: UUID,
    val clanId: UUID,
    val transactionId: UUID,
    val totalValue: Int,
    val items: List<InvObj>,
)

/**
 * Central, reviewed Clan Mayhem economy table. Values are server-side event values and are never
 * derived from client examine or high-alchemy prices. Keeping the values here makes quantity-aware
 * reward validation auditable and prevents scattered hard-coded prices.
 */
internal object ClanMayhemRewardEconomy {
    val table =
        listOf(
            MayhemRewardItem("obj.coins", unitValue = 1, quantity = 40_000..80_000),
            MayhemRewardItem("obj.deathrune", unitValue = 250, quantity = 160..300),
            MayhemRewardItem("obj.bloodrune", unitValue = 350, quantity = 120..230),
            MayhemRewardItem("obj.rune_arrow", unitValue = 200, quantity = 200..400),
            MayhemRewardItem("obj.dragon_javelin", unitValue = 300, quantity = 150..260),
        )

    val fallback =
        MayhemRewardPackage(
            listOf(MayhemRewardStack("obj.coins", quantity = 140_000, unitValue = 1))
        )

    val generator = ClanMayhemRewardGenerator(table, fallback, maximumAttempts = 64)
}

@Singleton
internal class ClanMayhemRewardService @Inject constructor(private val random: GameRandom) {
    fun points(player: Player): Int = player.attr[MAYHEM_POINTS] ?: 0

    fun wins(player: Player): Int = player.attr[MAYHEM_WINS] ?: 0

    fun hasPendingReward(player: Player): Boolean = pending(player) != null

    fun pending(player: Player): ClanMayhemPendingReward? =
        ClanMayhemPendingRewardCodec.decode(player.attr[MAYHEM_PENDING_REWARD])

    fun awardPoints(
        player: Player,
        matchId: UUID,
        placement: Int,
        participant: ClanMayhemParticipant,
        bossOwningClan: Boolean,
    ): Int {
        if (decodeUuid(player.attr[MAYHEM_LAST_POINT_MATCH]) == matchId) return 0
        val firstWin =
            placement == 1 && (player.attr[MAYHEM_FIRST_WIN_DAY] ?: Int.MIN_VALUE) != epochDay()
        val active = meaningfulParticipation(participant)
        val amount =
            ClanMayhemPointRules.award(
                placement,
                participant.score.kills,
                bossOwningClan,
                firstWin,
                active,
            )
        player.attr[MAYHEM_LAST_POINT_MATCH] = encodeUuid(matchId)
        if (amount > 0) player.attr[MAYHEM_POINTS] = points(player) + amount
        if (placement == 1 && active) {
            player.attr[MAYHEM_WINS] = wins(player) + 1
            if (firstWin) player.attr[MAYHEM_FIRST_WIN_DAY] = epochDay()
        }
        return amount
    }

    fun prepareWinnerReward(
        player: Player,
        matchId: UUID,
        clanId: UUID,
        participant: ClanMayhemParticipant,
    ): ClanMayhemPendingReward? {
        pending(player)?.let {
            return it
        }
        if (!meaningfulParticipation(participant)) return null
        val packageRoll =
            ClanMayhemRewardEconomy.generator.generate(Random(random.of(Int.MAX_VALUE)))
        check(
            packageRoll.totalValue in
                ClanMayhemConfig.rewardMinimumValue..ClanMayhemConfig.rewardMaximumValue
        )
        val reward =
            ClanMayhemPendingReward(
                matchId = matchId,
                clanId = clanId,
                transactionId = UUID.randomUUID(),
                totalValue = packageRoll.totalValue,
                items =
                    packageRoll.stacks.map { stack ->
                        InvObj(stack.internalName.asRSCM(RSCMType.OBJ), stack.quantity)
                    },
            )
        player.attr[MAYHEM_PENDING_REWARD] = ClanMayhemPendingRewardCodec.encode(reward)
        return reward
    }

    fun claim(player: Player): List<InvObj>? {
        val reward = pending(player)
        if (reward == null) {
            player.mes("You do not have a Clan Mayhem reward waiting.")
            return null
        }
        val result =
            player.invTransaction(player.inv) {
                val inv = select(player.inv)
                reward.items.forEach { add(inv, it.id, it.count, it.vars) }
            }
        if (!result.success) {
            player.mes("Make enough inventory space, then search the chest again.")
            return null
        }
        player.attr.remove(MAYHEM_PENDING_REWARD)
        player.mes("You claim a Clan Mayhem reward worth ${reward.totalValue} coins.")
        return reward.items
    }

    fun spendPoints(player: Player, amount: Int): Boolean {
        require(amount > 0)
        val balance = points(player)
        if (balance < amount) return false
        player.attr[MAYHEM_POINTS] = balance - amount
        return true
    }

    internal fun meaningfulParticipation(participant: ClanMayhemParticipant): Boolean {
        val score = participant.score
        return score.activityActions > 0 ||
            score.enemyDamage > 0 ||
            score.bossDamage > 0 ||
            score.kills > 0 ||
            score.assists > 0 ||
            score.cratesClaimed > 0 ||
            score.keysOpened > 0
    }

    private fun epochDay(): Int = (System.currentTimeMillis() / 86_400_000L).toInt()
}

internal object ClanMayhemPendingRewardCodec {
    private const val MAGIC = 0x434D_5201

    fun encode(reward: ClanMayhemPendingReward): MutableList<Int> =
        buildList {
                add(MAGIC)
                addUuid(reward.matchId)
                addUuid(reward.clanId)
                addUuid(reward.transactionId)
                add(reward.totalValue)
                add(reward.items.size)
                reward.items.forEach {
                    add(it.id)
                    add(it.count)
                    add(it.vars)
                }
            }
            .toMutableList()

    fun decode(values: List<Int>?): ClanMayhemPendingReward? =
        runCatching {
                if (values == null) return null
                var index = 0
                fun next() = values[index++]
                fun nextLong() = (next().toLong() shl 32) or (next().toLong() and 0xffff_ffffL)
                fun nextUuid() = UUID(nextLong(), nextLong())
                require(next() == MAGIC)
                val match = nextUuid()
                val clan = nextUuid()
                val transaction = nextUuid()
                val total = next()
                require(
                    total in
                        ClanMayhemConfig.rewardMinimumValue..ClanMayhemConfig.rewardMaximumValue
                )
                val count = next().also { require(it in 1..10) }
                val items = List(count) { InvObj(next(), next(), next()) }
                require(index == values.size)
                require(items.all { it.count > 0 })
                ClanMayhemPendingReward(match, clan, transaction, total, items)
            }
            .getOrNull()
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
    fun join(high: Int, low: Int) = (high.toLong() shl 32) or (low.toLong() and 0xffff_ffffL)
    return UUID(join(values[0], values[1]), join(values[2], values[3]))
}

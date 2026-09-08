@file:OptIn(dev.openrune.types.util.UncheckedType::class)

package org.rsmod.content.bosses.dt2

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.config.refs.BaseParams
import org.rsmod.api.death.PlayerDeathContext
import org.rsmod.api.death.PlayerDeathDrops
import org.rsmod.api.death.PlayerDeathStorageHook
import org.rsmod.api.invtx.add
import org.rsmod.api.invtx.delete
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.market.MarketPrices
import org.rsmod.api.player.output.mes
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj

private val LEVIATHAN_DEATH_STORAGE =
    AttributeKey<MutableList<Int>>(persistenceKey = "leviathan_death_storage_v1")
private val LEVIATHAN_DEATH_FEE = AttributeKey<Int>(persistenceKey = "leviathan_death_fee_v1")

/**
 * Keeps death items out of the disposable private map. The exterior rowboat is the recovery point.
 */
@Singleton
internal class LeviathanDeathStorage
@Inject
constructor(private val encounters: Dt2EncounterManager, private val marketPrices: MarketPrices) :
    PlayerDeathStorageHook {
    override fun store(
        context: PlayerDeathContext,
        result: PlayerDeathDrops.DeathDropResult,
    ): Boolean {
        val player = context.player
        if (!encounters.isLeviathanActive(player) || !encounters.rewardEligible(player)) {
            if (!encounters.isLeviathanActive(player) && hasItems(player)) {
                clear(player)
                player.mes(
                    "Your items held beside The Leviathan were lost because you died an unsafe death."
                )
            }
            return false
        }
        val lost = result.supplyPile + result.lostTradeable + result.lostUntradeable
        if (lost.isEmpty()) return false
        val combined = LeviathanStorageCodec.decode(player.attr[LEVIATHAN_DEATH_STORAGE]) + lost
        player.attr[LEVIATHAN_DEATH_STORAGE] = LeviathanStorageCodec.encode(combined)
        player.attr[LEVIATHAN_DEATH_FEE] = calculateFee(player, combined)
        player.mes("Your lost items can be reclaimed from the rowboat outside The Leviathan.")
        return true
    }

    fun hasItems(player: Player): Boolean =
        LeviathanStorageCodec.decode(player.attr[LEVIATHAN_DEATH_STORAGE]).isNotEmpty()

    fun reclaim(player: Player): Boolean {
        val items = LeviathanStorageCodec.decode(player.attr[LEVIATHAN_DEATH_STORAGE])
        if (items.isEmpty()) {
            player.mes("The rowboat is not holding any items for you.")
            return false
        }
        val fee = player.attr[LEVIATHAN_DEATH_FEE] ?: 0
        if (player.inv.count("obj.coins") < fee) {
            player.mes("You need $fee coins and enough inventory space to reclaim your items.")
            return false
        }
        val query =
            player.invTransaction(player.inv) {
                val inventory = select(player.inv)
                if (fee > 0) delete(inventory, "obj.coins".asRSCM(RSCMType.OBJ), fee)
                items.forEach { item -> add(inventory, item.id, item.count, item.vars) }
            }
        if (!query.success) {
            player.mes("You need $fee coins and enough inventory space to reclaim your items.")
            return false
        }
        clear(player)
        player.mes("You reclaim your items from the rowboat.")
        return true
    }

    private fun calculateFee(player: Player, items: List<InvObj>): Int {
        var baseFee = 0L
        for (item in items) {
            val type = requireNotNull(ServerCacheManager.getItem(item.id))
            val value = (marketPrices[type] ?: type.cost).toLong().coerceAtLeast(1L)
            val fee = LeviathanDeathRules.graveItemFee(value).toLong() * item.count.toLong()
            baseFee = (baseFee + fee).coerceAtMost(LeviathanDeathRules.GRAVE_FEE_CAP.toLong())
        }
        val npc = requireNotNull(ServerCacheManager.getNpc(Dt2Boss.LEVIATHAN.normalNpcId))
        val killCountVarp = npc.paramOrNull(BaseParams.killcount_varp)
        val killCount = killCountVarp?.let { player.vars[it] } ?: 0
        return LeviathanDeathRules.discountedGraveFee(baseFee, killCount)
    }

    private fun clear(player: Player) {
        player.attr.remove(LEVIATHAN_DEATH_STORAGE)
        player.attr.remove(LEVIATHAN_DEATH_FEE)
    }
}

internal object LeviathanDeathRules {
    const val GRAVE_FEE_CAP = 500_000
    const val DISCOUNTED_KILLCOUNT_LIMIT = 5

    fun graveItemFee(value: Long): Int =
        when {
            value < 100_000L -> 0
            value < 1_000_000L -> 1_000
            value < 10_000_000L -> 10_000
            else -> 100_000
        }

    fun discountedGraveFee(baseFee: Long, killCount: Int): Int {
        val capped = baseFee.coerceIn(0L, GRAVE_FEE_CAP.toLong())
        return if (killCount < DISCOUNTED_KILLCOUNT_LIMIT) (capped / 4L).toInt() else capped.toInt()
    }
}

internal object LeviathanStorageCodec {
    fun encode(items: List<InvObj>): MutableList<Int> =
        items.flatMapTo(mutableListOf()) { listOf(it.id, it.count, it.vars) }

    fun decode(values: List<Int>?): List<InvObj> =
        values.orEmpty().chunked(3).mapNotNull { triple ->
            if (triple.size != 3 || triple[0] < 0 || triple[1] <= 0) null
            else InvObj(triple[0], triple[1], triple[2])
        }
}

@file:OptIn(dev.openrune.types.util.UncheckedType::class)

package org.rsmod.content.bosses.yama

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
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
import org.rsmod.game.inv.Inventory

@Singleton
internal class YamaDeathStorage
@Inject
constructor(private val encounters: YamaEncounterManager, private val marketPrices: MarketPrices) :
    PlayerDeathStorageHook {
    override fun store(
        context: PlayerDeathContext,
        result: PlayerDeathDrops.DeathDropResult,
    ): Boolean {
        val player = context.player
        if (!encounters.isActive(player)) {
            if (hasItems(player)) {
                clear(player)
                player.mes(
                    "Your items held by the Voice of Yama were lost because you died an unsafe death."
                )
            }
            return false
        }
        val lost = result.supplyPile + result.lostTradeable + result.lostUntradeable
        if (lost.isEmpty()) return false
        val combined = YamaStorageCodec.decode(player.attr[YAMA_DEATH_STORAGE]) + lost
        player.attr[YAMA_DEATH_STORAGE] = YamaStorageCodec.encode(combined)
        player.attr[YAMA_DEATH_FEE] = calculateFee(context, combined)
        player.attr.remove(YAMA_STORAGE_REMINDER_SHOWN)
        player.mes("The Voice of Yama has collected the items you lost in the Domain.")
        return true
    }

    fun hasItems(player: Player): Boolean =
        YamaStorageCodec.decode(player.attr[YAMA_DEATH_STORAGE]).isNotEmpty()

    fun count(player: Player): Int = YamaStorageCodec.decode(player.attr[YAMA_DEATH_STORAGE]).size

    fun fee(player: Player): Int = player.attr[YAMA_DEATH_FEE] ?: 0

    fun reclaim(player: Player, bank: Inventory? = null): Boolean {
        val items = YamaStorageCodec.decode(player.attr[YAMA_DEATH_STORAGE])
        if (items.isEmpty()) {
            player.mes("The Voice of Yama is not guarding any items for you.")
            return false
        }
        val reclaimFee = fee(player)
        val payment =
            YamaReclaimRules.payment(
                fee = reclaimFee,
                inventoryCoins = player.inv.count("obj.coins"),
                bankCoins = bank?.count("obj.coins") ?: 0,
            )
        if (payment == null) {
            player.mes(
                "You need $reclaimFee coins in your inventory or bank to reclaim these items."
            )
            return false
        }
        val query =
            if (bank == null) {
                player.invTransaction(player.inv) {
                    val inventory = select(player.inv)
                    if (payment.inventoryCoins > 0) {
                        delete(inventory, "obj.coins".asRSCM(RSCMType.OBJ), payment.inventoryCoins)
                    }
                    items.forEach { item -> add(inventory, item.id, item.count, item.vars) }
                }
            } else {
                player.invTransaction(player.inv, bank) {
                    val inventory = select(player.inv)
                    val bankInventory = select(bank)
                    if (payment.inventoryCoins > 0) {
                        delete(inventory, "obj.coins".asRSCM(RSCMType.OBJ), payment.inventoryCoins)
                    }
                    if (payment.bankCoins > 0) {
                        delete(bankInventory, "obj.coins".asRSCM(RSCMType.OBJ), payment.bankCoins)
                    }
                    items.forEach { item -> add(inventory, item.id, item.count, item.vars) }
                }
            }
        if (!query.success) {
            player.mes(
                if (reclaimFee > 0) {
                    "You need $reclaimFee coins in your inventory or bank and enough inventory space for every stored item."
                } else {
                    "You need enough inventory space for every stored item."
                }
            )
            return false
        }
        clear(player)
        player.mes("The Voice of Yama returns all of your stored items.")
        return true
    }

    private fun calculateFee(context: PlayerDeathContext, items: List<InvObj>): Int {
        var baseFee = 0L
        for (item in items) {
            val type = requireNotNull(ServerCacheManager.getItem(item.id))
            val value = (marketPrices[type] ?: type.cost).toLong().coerceAtLeast(1L)
            val itemFee = YamaRules.graveItemFee(value).toLong() * item.count.toLong()
            baseFee = (baseFee + itemFee).coerceAtMost(YamaRules.GRAVE_FEE_CAP.toLong())
        }
        val killCount = context.player.attr[YAMA_KILL_COUNT] ?: 0
        return YamaRules.discountedGraveFee(baseFee, killCount)
    }

    private fun clear(player: Player) {
        player.attr.remove(YAMA_DEATH_STORAGE)
        player.attr.remove(YAMA_DEATH_FEE)
        player.attr.remove(YAMA_STORAGE_REMINDER_SHOWN)
    }
}

internal data class YamaReclaimPayment(val inventoryCoins: Int, val bankCoins: Int)

internal object YamaReclaimRules {
    fun payment(fee: Int, inventoryCoins: Int, bankCoins: Int): YamaReclaimPayment? {
        require(fee >= 0) { "Reclaim fee must not be negative." }
        val availableInventory = inventoryCoins.coerceAtLeast(0)
        val availableBank = bankCoins.coerceAtLeast(0)
        if (availableInventory.toLong() + availableBank.toLong() < fee.toLong()) {
            return null
        }
        val fromInventory = availableInventory.coerceAtMost(fee)
        return YamaReclaimPayment(inventoryCoins = fromInventory, bankCoins = fee - fromInventory)
    }
}

internal object YamaStorageCodec {
    fun encode(items: List<InvObj>): MutableList<Int> =
        items.flatMapTo(mutableListOf()) { listOf(it.id, it.count, it.vars) }

    fun decode(values: List<Int>?): List<InvObj> =
        values.orEmpty().chunked(3).mapNotNull { triple ->
            if (triple.size != 3 || triple[0] < 0 || triple[1] <= 0) null
            else InvObj(triple[0], triple[1], triple[2])
        }
}

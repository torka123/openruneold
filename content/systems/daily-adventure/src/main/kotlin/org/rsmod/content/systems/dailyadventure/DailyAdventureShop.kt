package org.rsmod.content.systems.dailyadventure

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.market.MarketPrices
import org.rsmod.api.player.output.mes
import org.rsmod.api.shops.Shops
import org.rsmod.api.shops.currency.ShopCurrency
import org.rsmod.api.shops.operation.ShopOperationMap
import org.rsmod.api.shops.restock.ShopRestockProcess
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.Inventory

@Singleton
public class DailyAdventureShop
@Inject
constructor(
    private val service: DailyAdventureService,
    private val shops: Shops,
    private val operations: ShopOperationMap,
    private val restock: ShopRestockProcess,
    private val marketPrices: MarketPrices,
) {
    private var registered = false

    public fun startup() {
        if (registered) return
        operations.registerCurrency(
            key = CURRENCY,
            currency = TaskPointCurrency(service),
            restockProcess = restock,
            marketPrices = marketPrices,
            costOf = ::costOf,
        )
        registered = true
    }

    public fun open(player: Player) {
        startup()
        shops.open(
            player = player,
            title = "Daily Adventure Task Shop",
            shopInv = SHOP_INV,
            buyPercentage = 0.0,
            sellPercentage = 100.0,
            changePercentage = 0.0,
            currency = CURRENCY,
            subtext = "Spend account-wide Task Points. Rerolls are never sold here.",
        )
    }

    private fun costOf(type: ItemServerType): Int =
        when (RSCM.getReverseMapping(RSCMType.OBJ, type.id)) {
            PROTECTION_SCROLL -> PROTECTION_SCROLL_COST
            else -> Int.MAX_VALUE
        }

    private class TaskPointCurrency(private val service: DailyAdventureService) : ShopCurrency {
        override val singularName: String = "Task Point"
        override val pluralName: String = "Task Points"
        override val invObj: ItemServerType? = null

        override fun balance(player: Player, sideInv: Inventory): Int =
            service.points(player).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

        override fun receiveCap(player: Player, sideInv: Inventory): Int = 0

        override fun deduct(player: Player, amount: Int) {
            if (!service.deductPoints(player, amount.toLong())) {
                player.mes("Your account no longer has enough Task Points.")
            }
        }

        override fun credit(player: Player, amount: Int) {
            if (amount > 0) service.addPoints(player, amount.toLong())
        }
    }

    private companion object {
        const val CURRENCY = "currency.daily_adventure"
        const val SHOP_INV = "inv.daily_adventure_shop"
        const val PROTECTION_SCROLL = "obj.streak_protection_scroll"
        const val PROTECTION_SCROLL_COST = 3_000
    }
}

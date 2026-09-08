package org.rsmod.api.shops.operation

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import java.util.concurrent.ConcurrentHashMap
import org.rsmod.api.market.MarketPrices
import org.rsmod.api.shops.currency.ShopCurrency
import org.rsmod.api.shops.currency.ShopCurrencyRow
import org.rsmod.api.shops.currency.ShopCurrencyRows
import org.rsmod.api.shops.restock.ShopRestockProcess
import org.rsmod.game.entity.Player

public class ShopOperationMap(
    private val map: MutableMap<Int, ShopOperations> = Int2ObjectOpenHashMap()
) {
    private val logger = InlineLogger()
    private val costOfOverrides = ConcurrentHashMap<String, (ItemServerType) -> Int>()
    private val syncOverrides = ConcurrentHashMap<String, (Player) -> Unit>()
    private val purchaseOverrides =
        ConcurrentHashMap<String, (Player, ItemServerType, Int) -> Unit>()

    public fun register(key: String, value: ShopOperations) {
        val id = key.asRSCM(RSCMType.CURRENCY)
        if (map.containsKey(id)) {
            throw IllegalStateException("CurrencyType already mapped to shop operations: $key")
        }
        map[id] = value
    }

    /**
     * Registers [currency] under a `currency.rscm` [key] backed by
     * [StandardCurrencyShopOperations].
     *
     * [costOf] overrides per-item base cost when the shop does not use the obj's default cost.
     */
    public fun registerCurrency(
        key: String,
        currency: ShopCurrency,
        restockProcess: ShopRestockProcess,
        marketPrices: MarketPrices,
        costOf: (ItemServerType) -> Int = { it.cost },
        onPurchase: (Player, ItemServerType, Int) -> Unit = { _, _, _ -> },
    ) {
        register(
            key,
            StandardCurrencyShopOperations(
                currency = currency,
                restockProcess = restockProcess,
                marketPrices = marketPrices,
                costOf = costOf,
                onPurchase = onPurchase,
            ),
        )
    }

    /**
     * Registers every row in `dbtable.shop_currency`.
     *
     * Optional Kotlin hooks: [costOf], [sync], and [onPurchase] for currencies that need custom
     * pricing, varbit client sync, or post-transaction award handling.
     */
    public fun registerCurrenciesFromDb(
        restockProcess: ShopRestockProcess,
        marketPrices: MarketPrices,
    ): Int {
        val rows =
            runCatching { ShopCurrencyRows.all() }
                .onFailure { logger.error(it) { "Failed to load dbtable.shop_currency" } }
                .getOrDefault(emptyList())

        for (row in rows) {
            registerCurrencyRow(row, restockProcess, marketPrices)
        }
        return rows.size
    }

    public fun costOf(key: String, costOf: (ItemServerType) -> Int) {
        costOfOverrides[key] = costOf
    }

    public fun sync(key: String, sync: (Player) -> Unit) {
        syncOverrides[key] = sync
    }

    public fun onPurchase(key: String, onPurchase: (Player, ItemServerType, Int) -> Unit) {
        purchaseOverrides[key] = onPurchase
    }

    private fun registerCurrencyRow(
        row: ShopCurrencyRow,
        restockProcess: ShopRestockProcess,
        marketPrices: MarketPrices,
    ) {
        val key = row.key
        val currency =
            ShopCurrencyRows.toShopCurrency(row) { player -> syncOverrides[key]?.invoke(player) }
        registerCurrency(
            key = key,
            currency = currency,
            restockProcess = restockProcess,
            marketPrices = marketPrices,
            costOf = { type -> costOfOverrides[key]?.invoke(type) ?: type.cost },
            onPurchase = { player, type, count ->
                purchaseOverrides[key]?.invoke(player, type, count)
            },
        )
    }

    public operator fun get(key: String): ShopOperations? = map[key.asRSCM(RSCMType.CURRENCY)]

    public operator fun set(key: String, value: ShopOperations) {
        map[key.asRSCM(RSCMType.CURRENCY)] = value
    }
}

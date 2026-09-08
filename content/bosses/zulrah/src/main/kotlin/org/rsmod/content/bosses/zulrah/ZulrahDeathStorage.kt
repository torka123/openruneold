@file:OptIn(dev.openrune.types.util.UncheckedType::class)

package org.rsmod.content.bosses.zulrah

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.death.PlayerDeathContext
import org.rsmod.api.death.PlayerDeathDrops
import org.rsmod.api.death.PlayerDeathStorageHook
import org.rsmod.api.invtx.add
import org.rsmod.api.invtx.delete
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.output.mes
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj

internal val ZULRAH_DEATH_STORAGE: AttributeKey<MutableList<Int>> =
    AttributeKey(persistenceKey = "zulrah_death_storage_v1")

@Singleton
internal class ZulrahDeathStorage
@Inject
constructor(private val encounters: ZulrahEncounterManager) : PlayerDeathStorageHook {
    override fun store(
        context: PlayerDeathContext,
        result: PlayerDeathDrops.DeathDropResult,
    ): Boolean {
        val player = context.player
        if (!encounters.isActive(player)) {
            if (player.attr.has(ZULRAH_DEATH_STORAGE)) {
                player.attr.remove(ZULRAH_DEATH_STORAGE)
                player.mes(
                    "Your items held by Priestess Zul-Gwenwynig were lost because you died an unsafe death."
                )
            }
            return false
        }
        val lost = result.supplyPile + result.lostTradeable + result.lostUntradeable
        if (lost.isEmpty()) return false
        player.attr[ZULRAH_DEATH_STORAGE] = ZulrahStorageCodec.encode(lost)
        player.attr.remove(ZULRAH_STORAGE_REMINDER_SHOWN)
        val fee = fee(player)
        player.mes(
            if (fee == 0) "Priestess Zul-Gwenwynig has collected your items free of charge."
            else
                "Priestess Zul-Gwenwynig has collected your items. Reclaiming them costs 100,000 coins."
        )
        return true
    }

    fun fee(player: Player): Int =
        if (player.vars["varp.total_snakeboss_kills"] < ZULRAH_FREE_RECLAIM_KILLS) 0
        else ZULRAH_DEATH_FEE

    fun hasItems(player: Player): Boolean =
        ZulrahStorageCodec.decode(player.attr[ZULRAH_DEATH_STORAGE]).isNotEmpty()

    fun count(player: Player): Int =
        ZulrahStorageCodec.decode(player.attr[ZULRAH_DEATH_STORAGE]).size

    fun reclaim(player: Player): Boolean {
        val items = ZulrahStorageCodec.decode(player.attr[ZULRAH_DEATH_STORAGE])
        if (items.isEmpty()) {
            player.mes("Priestess Zul-Gwenwynig is not holding any items for you.")
            return false
        }
        val fee = fee(player)
        val query =
            player.invTransaction(player.inv) {
                val inv = select(player.inv)
                if (fee > 0) delete(inv, "obj.coins".asRSCM(RSCMType.OBJ), fee)
                for (item in items) add(inv, item.id, item.count, item.vars)
            }
        if (!query.success) {
            player.mes(
                if (fee > 0)
                    "You need 100,000 coins and enough inventory space for every stored item."
                else "You need enough inventory space for every stored item."
            )
            return false
        }
        player.attr.remove(ZULRAH_DEATH_STORAGE)
        player.attr.remove(ZULRAH_STORAGE_REMINDER_SHOWN)
        player.mes("Priestess Zul-Gwenwynig returns all of your stored items.")
        return true
    }
}

internal object ZulrahStorageCodec {
    internal fun encode(items: List<InvObj>): MutableList<Int> =
        items.flatMapTo(mutableListOf()) { listOf(it.id, it.count, it.vars) }

    internal fun decode(values: List<Int>?): List<InvObj> =
        values.orEmpty().chunked(3).mapNotNull { triple ->
            if (triple.size != 3 || triple[1] <= 0) null
            else InvObj(triple[0], triple[1], triple[2])
        }
}

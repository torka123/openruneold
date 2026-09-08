package org.rsmod.content.minigames.tob

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import net.rsprot.protocol.game.outgoing.interfaces.IfSetText
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.invtx.invDel
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.ui.ifOpenMainModal
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.PlayerUid

@Singleton
internal class TobSupplies @Inject constructor(private val eventBus: EventBus) {
    private val purchased = mutableMapOf<PlayerUid, MutableMap<Int, Int>>()

    fun verifyDefinitions() {
        OFFERS.values.forEach {
            requireNotNull(ServerCacheManager.getItem(it.objId)) {
                "Missing ToB supply item ${it.objId}"
            }
        }
        requireNotNull(ServerCacheManager.getInterface(SUPPLY_INTERFACE)) {
            "Missing ToB supply interface $SUPPLY_INTERFACE"
        }
        RSCM.getReverseMapping(RSCMType.COMPONENT, SUPPLY_ITEMS_COMPONENT)
    }

    fun awardPoints(raid: TobRaidSession) {
        raid.participants.values.filter(TobParticipant::entered).forEach { participant ->
            val points =
                when {
                    participant.spectating -> 6
                    participant.mvpPoints > 0 -> 11
                    else -> 8
                }
            participant.supplyPoints += points
        }
    }

    fun open(player: Player, raid: TobRaidSession): Boolean {
        val participant = raid.participant(player.uid) ?: return false
        val room = raid.rooms[participant.roomIndex]
        if (
            room.room !in setOf(TobRoom.BLOAT, TobRoom.SOTETSEG) ||
                room.state != TobRoomState.COMPLETE
        ) {
            player.mes("This supply chest is unavailable until the room is complete.")
            return false
        }
        player.ifOpenMainModal(SUPPLY_INTERFACE, eventBus)
        player.ifSetEvents(
            RSCM.getReverseMapping(RSCMType.COMPONENT, SUPPLY_ITEMS_COMPONENT),
            OFFERS_BY_SLOT.indices,
            IfEvent.Op1,
        )
        refreshPoints(player, participant)
        return true
    }

    fun buy(player: Player, raid: TobRaidSession, key: String): Boolean {
        val offer =
            OFFERS[key.lowercase()]
                ?: run {
                    player.mes(
                        "Unknown supply. Use ::tobsupply stamina|prayer|brew|restore|potato|shark|turtle|manta."
                    )
                    return false
                }
        return buyOffer(player, raid, offer)
    }

    fun buySlot(player: Player, raid: TobRaidSession, slot: Int): Boolean {
        val offer = OFFERS_BY_SLOT.getOrNull(slot) ?: return false
        return buyOffer(player, raid, offer)
    }

    private fun buyOffer(player: Player, raid: TobRaidSession, offer: Offer): Boolean {
        val participant = raid.participant(player.uid) ?: return false
        if (participant.supplyPoints < offer.price) {
            player.mes("You need ${offer.price} supply points.")
            refreshPoints(player, participant)
            return false
        }
        if (!player.invAdd(player.inv, offer.objId, 1).success) {
            player.mes("Your inventory is full.")
            return false
        }
        participant.supplyPoints -= offer.price
        purchased.getOrPut(player.uid) { mutableMapOf() }.merge(offer.objId, 1, Int::plus)
        player.mes(
            "You buy ${ServerCacheManager.getItem(offer.objId)?.name} for ${offer.price} point${if (offer.price == 1) "" else "s"}."
        )
        refreshPoints(player, participant)
        return true
    }

    fun cleanup(player: Player) {
        val items = purchased.remove(player.uid) ?: return
        items.forEach { (id, count) ->
            player.invDel(player.inv, id, count, strict = false)
            player.invDel(player.worn, id, count, strict = false)
        }
    }

    private fun refreshPoints(player: Player, participant: TobParticipant) {
        player.client.write(
            IfSetText(
                SUPPLY_INTERFACE,
                SUPPLY_POINTS_COMPONENT,
                "Points Available: <col=ffffff>${participant.supplyPoints}</col>",
            )
        )
    }

    private data class Offer(val objId: Int, val price: Int)

    companion object {
        private const val SUPPLY_INTERFACE = 405
        private const val SUPPLY_POINTS_COMPONENT = 4
        const val SUPPLY_ITEMS_COMPONENT = (SUPPLY_INTERFACE shl 16) or 8
        private val OFFERS =
            linkedMapOf(
                "stamina" to Offer(12625, 1),
                "prayer" to Offer(2434, 2),
                "brew" to Offer(6685, 3),
                "restore" to Offer(3024, 3),
                "potato" to Offer(7058, 1),
                "shark" to Offer(385, 1),
                "turtle" to Offer(397, 2),
                "manta" to Offer(391, 2),
            )
        private val OFFERS_BY_SLOT = OFFERS.values.toList()
    }
}

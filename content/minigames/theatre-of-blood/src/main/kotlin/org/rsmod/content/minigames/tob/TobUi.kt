package org.rsmod.content.minigames.tob

import jakarta.inject.Inject
import jakarta.inject.Singleton
import net.rsprot.protocol.game.outgoing.interfaces.IfSetText
import org.rsmod.api.player.ui.ifCloseOverlay
import org.rsmod.api.player.ui.ifOpenOverlay
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player

@Singleton
internal class TobUi @Inject constructor(private val eventBus: EventBus) {
    fun open(player: Player, raid: TobRaidSession, cycle: Int) {
        player.ifOpenOverlay(28, eventBus)
        sync(player, raid, cycle)
    }

    fun sync(player: Player, raid: TobRaidSession, cycle: Int) {
        val participant = raid.participant(player.uid)
        val members =
            raid.participants.values.joinToString("<br>") { member ->
                val marker =
                    when {
                        member.spectating -> "<col=666666>"
                        member.uid == raid.party.leader -> "<col=ffff00>"
                        else -> "<col=ffffff>"
                    }
                "$marker${member.uid}</col>  ${member.score} pts${if (member.deaths > 0) " (${member.deaths} deaths)" else ""}"
            }
        val elapsed = (cycle - raid.startedAt).coerceAtLeast(0)
        val roomIndex = participant?.roomIndex ?: raid.currentRoomIndex
        val room = raid.rooms[roomIndex].room
        val status =
            "<col=ff0000>${raid.mode.name} Theatre</col><br>${room.displayName} " +
                "${roomIndex + 1}/${raid.rooms.size}<br>Time ${elapsed / 100}:${((elapsed % 100) * 3 / 5).toString().padStart(2, '0')} " +
                "Score ${participant?.score ?: 0}<br>$members"
        player.client.write(IfSetText(28, 12, status))
    }

    fun close(player: Player) = player.ifCloseOverlay(28, eventBus)
}

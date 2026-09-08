package org.rsmod.content.minigames.corruptedgauntlet

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.ui.ifCloseOverlay
import org.rsmod.api.player.ui.ifOpenOverlay
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player

@Singleton
internal class CorruptedGauntletUi @Inject constructor(private val eventBus: EventBus) {
    fun open(player: Player, layout: CorruptedGauntletLayout) {
        setVarp(player, 2_322, 2)
        setVarbit(player, CorruptedGauntletConfig.VarBit.BOSS, 0)
        setVarbit(player, CorruptedGauntletConfig.VarBit.MAP, 1)
        setVarbit(player, CorruptedGauntletConfig.VarBit.ROOM_TYPE, 1)
        setVarbit(
            player,
            CorruptedGauntletConfig.VarBit.START_ROOM,
            layout.start.z * CorruptedGauntletConfig.GRID_SIZE + layout.start.x,
        )
        for (id in
            CorruptedGauntletConfig.VarBit.FIRST_ROOM_REVEAL..CorruptedGauntletConfig.VarBit
                    .LAST_ROOM_REVEAL) {
            setVarbit(player, id, 0)
        }
        player.ifOpenOverlay(interfaceName(CorruptedGauntletConfig.Interface.OVERLAY), eventBus)
        updateTimer(player, CorruptedGauntletConfig.PREPARATION_TICKS, boss = false)
        revealRoom(player, layout.start)
        revealRoom(player, layout.boss)
        setCurrentRoom(player, layout.start)
    }

    fun updateTimer(player: Player, ticks: Int, boss: Boolean) {
        if (!boss) {
            player.runClientScript(
                CorruptedGauntletConfig.ClientScript.TIMER_UPDATE,
                ticks.coerceAtLeast(0),
            )
        }
    }

    fun revealRoom(player: Player, room: CorruptedGauntletGridPoint) {
        val index = room.z * CorruptedGauntletConfig.GRID_SIZE + room.x
        setVarbit(player, CorruptedGauntletConfig.VarBit.FIRST_ROOM_REVEAL + index, 1)
    }

    fun setCurrentRoom(player: Player, room: CorruptedGauntletGridPoint) {
        setVarbit(player, CorruptedGauntletConfig.VarBit.CURRENT_ROOM_X, room.x)
        setVarbit(player, CorruptedGauntletConfig.VarBit.CURRENT_ROOM_Z, room.z)
    }

    fun beginBoss(player: Player) {
        setVarp(player, 2_322, 3)
        setVarbit(player, CorruptedGauntletConfig.VarBit.BOSS, 1)
        player.runClientScript(CorruptedGauntletConfig.ClientScript.TIMER_END)
    }

    fun close(player: Player) {
        setVarp(player, 2_322, 0)
        setVarbit(player, CorruptedGauntletConfig.VarBit.BOSS, 0)
        setVarbit(player, CorruptedGauntletConfig.VarBit.MAP, 0)
        player.ifCloseOverlay(interfaceName(CorruptedGauntletConfig.Interface.OVERLAY), eventBus)
    }

    fun setRewardAvailable(player: Player, available: Boolean) {
        setVarbit(player, CorruptedGauntletConfig.VarBit.PENDING, if (available) 1 else 0)
    }

    private fun setVarbit(player: Player, id: Int, value: Int) {
        val type =
            requireNotNull(ServerCacheManager.getVarbit(id)) { "Missing revision-240 varbit $id" }
        VarPlayerIntMapSetter.set(player, type, value)
    }

    private fun setVarp(player: Player, id: Int, value: Int) {
        val type =
            requireNotNull(ServerCacheManager.getVarp(id)) { "Missing revision-240 varp $id" }
        VarPlayerIntMapSetter.set(player, type, value)
    }

    private fun interfaceName(id: Int): String = RSCM.getReverseMapping(RSCMType.INTERFACE, id)
}

package org.rsmod.content.minigames.cox

import dev.openrune.ServerCacheManager
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.ui.ifCloseOverlay
import org.rsmod.api.player.ui.ifOpenOverlay
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player

/** Uses the stock revision-240 Chambers points overlay (interface 513). */
@Singleton
internal class CoxUi @Inject constructor(private val eventBus: EventBus) {
    fun open(player: Player, raid: CoxRaidSession, cycle: Int) {
        player.ifOpenOverlay(COX_POINTS_INTERFACE, eventBus)
        sync(player, raid, cycle)
    }

    fun sync(player: Player, raid: CoxRaidSession, cycle: Int) {
        val personal = raid.participant(player.uid)?.personalPoints ?: 0
        set(player, RAID_TAB_ICON_VARBIT, 1)
        set(player, RAID_PARTY_POINTS_VARBIT, raid.teamPoints.coerceAtLeast(0))
        set(player, RAID_PERSONAL_POINTS_VARBIT, personal.coerceAtLeast(0))
        set(player, RAID_TIMER_VARBIT, (cycle - raid.startedAt).coerceAtLeast(0))
    }

    fun close(player: Player) {
        player.ifCloseOverlay(COX_POINTS_INTERFACE, eventBus)
        set(player, RAID_TAB_ICON_VARBIT, 0)
        set(player, RAID_PARTY_POINTS_VARBIT, 0)
        set(player, RAID_PERSONAL_POINTS_VARBIT, 0)
        set(player, RAID_TIMER_VARBIT, 0)
    }

    private fun set(player: Player, id: Int, value: Int) {
        val type = requireNotNull(ServerCacheManager.getVarbit(id)) { "Missing CoX varbit $id" }
        VarPlayerIntMapSetter.set(player, type, value)
    }

    private companion object {
        const val COX_POINTS_INTERFACE = 513
        const val RAID_TAB_ICON_VARBIT = 5_432
        const val RAID_PARTY_POINTS_VARBIT = 5_431
        const val RAID_PERSONAL_POINTS_VARBIT = 5_422
        const val RAID_TIMER_VARBIT = 6_386
    }
}

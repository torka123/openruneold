package org.rsmod.api.combat.commons.magic

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.player.output.VarpSync
import org.rsmod.game.entity.Player

/** Arceuus Home has no recharge; other books retain their shared, persisted home timer. */
public object HomeTeleportCooldown {
    public const val TIMER: String = "varp.aide_tele_timer"

    public fun requiresRecharge(player: Player): Boolean =
        player.vars["varbit.spellbook"] != Spellbook.Arceuus.varValue

    public fun syncClient(player: Player) {
        val timer = requireNotNull(ServerCacheManager.getVarp(TIMER.asRSCM(RSCMType.VARP)))
        val value = if (requiresRecharge(player)) player.vars[TIMER] else 0
        VarpSync.writeVarp(player, timer, value)
    }
}

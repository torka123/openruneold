package org.rsmod.api.combat.commons.magic

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.player.output.VarpSync
import org.rsmod.game.entity.Player

/** OpenRune Home is cooldown-free on every book; saved OSRS timestamps must not grey its button. */
public object HomeTeleportCooldown {
    public const val TIMER: String = "varp.aide_tele_timer"

    public fun syncClient(player: Player) {
        val timer = requireNotNull(ServerCacheManager.getVarp(TIMER.asRSCM(RSCMType.VARP)))
        VarpSync.writeVarp(player, timer, 0)
    }
}

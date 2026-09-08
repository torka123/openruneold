package org.rsmod.content.minigames.clanmayhem

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.player.hook.PlayerTeleportValidateHook
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.game.entity.Player

/** Blocks every normal teleport route from snapshot capture until restoration commits. */
@Singleton
internal class ClanMayhemTeleportHook
@Inject
constructor(
    private val manager: ClanMayhemManager,
    private val snapshots: ClanMayhemSnapshotService,
) : PlayerTeleportValidateHook {
    override fun validate(player: Player, type: TeleportType, areaChecker: AreaChecker): String? =
        if (manager.matchFor(player) != null || snapshots.hasUnresolved(player)) {
            "Teleporting is disabled during Clan Mayhem. Use Return to Team from the respawn box."
        } else {
            null
        }
}

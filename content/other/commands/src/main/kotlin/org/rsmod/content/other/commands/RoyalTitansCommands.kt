package org.rsmod.content.other.commands

import jakarta.inject.Inject
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.Cheat
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Administrator teleports to the live revision-240 Royal Titans entrance. */
public class RoyalTitansCommands
@Inject
constructor(
    private val protectedAccess: ProtectedAccessLauncher,
    private val instances: InstanceManager,
    private val clock: MapClock,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onCommand("royaltitans", DESCRIPTION, ::teleport)
        onCommand("royaltitan", DESCRIPTION, ::teleport)
        onCommand("titans", DESCRIPTION, ::teleport)
        onCommand("rt", DESCRIPTION, ::teleport)
        onCommand("branda", DESCRIPTION, ::teleport)
        onCommand("eldric", DESCRIPTION, ::teleport)
    }

    private fun teleport(cheat: Cheat) =
        with(cheat) {
            protectedAccess.launch(player) {
                instances.leaveForExternalTeleport(player, ENTRANCE_TILE, clock.cycle)
                telejump(ENTRANCE_TILE, TeleportType.Exempt)
                player.mes("Teleported outside the Royal Titans entrance. Enter the tunnel to begin.")
            }
        }

    public companion object {
        /** Walkable dungeon-side tile immediately east of revision-240 entrance loc 55986. */
        public val ENTRANCE_TILE: CoordGrid = CoordGrid(2951, 9573, 0)

        private const val DESCRIPTION = "Teleport outside the Royal Titans entrance"
    }
}

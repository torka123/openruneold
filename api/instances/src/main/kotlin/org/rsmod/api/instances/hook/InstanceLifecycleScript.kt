package org.rsmod.api.instances.hook

import jakarta.inject.Inject
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.player.forceDisconnect
import org.rsmod.api.player.hook.PlayerPostTickHook
import org.rsmod.api.script.onPlayerCoordsChanged
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class InstanceLifecycleScript
@Inject
constructor(private val manager: InstanceManager, private val worldClock: MapClock) :
    PluginScript(), PlayerPostTickHook {
    override fun ScriptContext.startup() {
        onPlayerCoordsChanged {
            manager.leaveForExternalTeleport(player, player.coords, worldClock.cycle)
        }
        onPlayerLogout { manager.handleLogout(player, worldClock.cycle) }
    }

    override fun onPostTick(player: Player) {
        if (!player.clientDisconnected.get()) return
        if (manager.handleDisconnect(player, worldClock.cycle)) {
            player.forceDisconnect()
        }
    }
}

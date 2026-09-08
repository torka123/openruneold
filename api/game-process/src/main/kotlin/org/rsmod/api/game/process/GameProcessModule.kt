package org.rsmod.api.game.process

import org.rsmod.api.game.process.npc.NpcPlayerChaseValidateHook
import org.rsmod.api.game.process.npc.hunt.NpcPlayerHuntProfileOverride
import org.rsmod.api.game.process.npc.hunt.NpcPlayerHuntValidateHook
import org.rsmod.api.game.process.player.PlayerNpcApLineOfSightHook
import org.rsmod.plugin.module.PluginModule

/** Declares extension points used by the core game-process pipeline. */
public class GameProcessModule : PluginModule() {
    override fun bind() {
        newSetBinding<NpcPlayerChaseValidateHook>()
        newSetBinding<NpcPlayerHuntProfileOverride>()
        newSetBinding<NpcPlayerHuntValidateHook>()
        newSetBinding<PlayerNpcApLineOfSightHook>()
    }
}

package org.rsmod.content.drops

import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.content.slayer.core.SlayerTaskManager
import org.rsmod.content.slayer.dialogue.SlayerMasters
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

public fun Player.shouldDropBrimstoneKey(npc: Npc, areaChecker: AreaChecker): Boolean {
    if (vars["varbit.slayer_master"] != SlayerMasters.TASK_KONAR) {
        return false
    }
    if (vars["varp.slayer_target"] == 0 || vars["varp.slayer_count"] <= 0) {
        return false
    }
    if (!SlayerTaskManager.countsAsCurrentTaskKill(this, npc)) {
        return false
    }
    return SlayerTaskManager.countsKillTowardTask(this, npc, areaChecker)
}

package org.rsmod.content.generic.killcount

import jakarta.inject.Inject
import org.rsmod.api.config.refs.BaseParams
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.plugin.module.PluginModule

public class KillcountModule : PluginModule() {
    override fun bind() {
        addSetBinding<NpcDeathKillHook>(KillcountNpcKillHook::class.java)
    }
}

/** Increments the player's killcount varp for the killed npc. */
public class KillcountNpcKillHook @Inject constructor() : NpcDeathKillHook {
    override fun onKill(context: NpcDeathKillContext) {
        if (context.killcountHandled) return
        if (context.npc.type.internalName in CHEST_AWARDED_KILLCOUNT_NPCS) {
            return
        }
        val varp = context.npc.paramOrNull(BaseParams.killcount_varp) ?: return
        val notify = context.npc.paramOrNull(BaseParams.killcount_notify) ?: true
        val count = context.hero.vars[varp] + 1
        VarPlayerIntMapSetter.set(context.hero, varp, count)
        if (notify) {
            context.hero.mes("Your ${context.npc.name} kill count is: <col=ff0000>$count</col>")
        }
    }

    private companion object {
        private val CHEST_AWARDED_KILLCOUNT_NPCS =
            setOf("npc.hillgiant_boss", "npc.gb_mossgiant", "npc.vorkath")
    }
}

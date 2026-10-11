package org.rsmod.api.bosses.runtime

import org.rsmod.api.bosses.spec.BossSpec
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

abstract class BossPluginScript(protected val deps: BossDeps) : PluginScript() {

    abstract val spec: BossSpec
    open val bossSlug: String
        get() = spec.npcTypes.first().removePrefix("npc.").replace('_', '-')

    override fun ScriptContext.startup() {
        BossCombat.register(this, spec, deps, ownerSlug = bossSlug)
    }
}

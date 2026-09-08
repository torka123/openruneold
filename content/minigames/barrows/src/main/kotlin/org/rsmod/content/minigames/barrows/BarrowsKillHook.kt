package org.rsmod.content.minigames.barrows

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.death.NpcDeathKillHook

@Singleton
internal class BarrowsKillHook @Inject constructor(
    private val runs: BarrowsRunManager,
) : NpcDeathKillHook {
    override fun onKill(context: NpcDeathKillContext) {
        runs.handleKill(context.hero, context.npc)
    }
}

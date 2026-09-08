package org.rsmod.content.minigames.barrows

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.api.script.onCommand
import org.rsmod.game.cheat.Cheat
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class BarrowsLootPreviewScript
@Inject
constructor(
    private val runs: BarrowsRunManager,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onCommand("barrowsloot") {
            desc = "Preview 20 full-potential Barrows chests without awarding items"
            invalidArgs = "Use as ::barrowsloot"
            cheat {
                previewLoot()
            }
        }
    }

    private fun Cheat.previewLoot() {
        if (args.isNotEmpty()) {
            player.mes("Use as ::barrowsloot")
            return
        }

        player.mes("<col=00ff00>Barrows preview: 20 full-potential chests. No items were awarded.</col>")
        runs.previewFullChestRewards(CHEST_COUNT).forEachIndexed { index, rewards ->
            player.mes(
                "<col=00ff00>Loot ${index + 1}:</col> " +
                    rewards.joinToString(" | ") { it.displayText() },
            )
        }
    }

    private fun BarrowsReward.displayText(): String {
        val name = ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ))?.name ?: obj
        return "$name x$count"
    }

    private companion object {
        private const val CHEST_COUNT = 20
    }
}

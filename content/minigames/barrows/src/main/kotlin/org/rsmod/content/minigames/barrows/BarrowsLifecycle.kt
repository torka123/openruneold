package org.rsmod.content.minigames.barrows

import jakarta.inject.Inject
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.api.player.hook.PlayerPostTickHook
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/** Keeps shared-world Barrows brother spawns private to their owner without creating an instance. */
internal class BarrowsAttackHook
@Inject
constructor(
    private val runs: BarrowsRunManager,
) : NpcAttackValidateHook {
    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult =
        when {
            !runs.isOwnedBarrowsNpc(npc) -> NpcAttackValidateResult.Pass
            runs.isOwnedBarrowsNpc(player, npc) -> NpcAttackValidateResult.BypassSingleWayPvnRestriction
            else -> NpcAttackValidateResult.Deny("That Barrows brother belongs to another adventurer.")
        }
}

/** Death removes any in-memory spawn while leaving persisted run progress available after recovery. */
internal class BarrowsDeathCleanupHook
@Inject
constructor(
    private val runs: BarrowsRunManager,
) : PlayerDeathCleanupHook {
    override fun cleanup(player: Player) {
        runs.cleanupSpawnedBrothers(player)
    }
}

internal class BarrowsLifecycleEvents
@Inject
constructor(
    private val runs: BarrowsRunManager,
    private val collision: CollisionFlagMap,
) : PluginScript(), PlayerPostTickHook {
    override fun ScriptContext.startup() {
        onPlayerLogout {
            runs.cleanupSpawnedBrothers(player)
        }
    }

    override fun onPostTick(player: Player) {
        runs.tickOwnedBrothers(player)
        runs.tickOwnedTunnelMonsters(player)
    }
}

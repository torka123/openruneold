package org.rsmod.content.areas.wilderness

import jakarta.inject.Inject
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.areas.wilderness.interactions.REVENANT_CAVE_FEE_AT_RISK_VARP
import org.rsmod.content.areas.wilderness.interactions.REVENANT_CAVE_FEE_PAID_VARP
import org.rsmod.content.areas.wilderness.interactions.clearAllCourseState
import org.rsmod.game.entity.Player

public class WildernessDeathCleanupHook @Inject constructor() : PlayerDeathCleanupHook {
    override fun cleanup(player: Player) {
        if (player.isSkulled()) {
            player.clearSkull(force = true)
        }
        clearEntranceFeeIfAtRisk(
            player,
            REVENANT_CAVE_FEE_AT_RISK_VARP,
            REVENANT_CAVE_FEE_PAID_VARP,
        )
        clearEntranceFeeIfAtRisk(
            player,
            "varp.wilderness_boss_fee_at_risk",
            "varp.wilderness_boss_fee_paid",
        )
        player.clearAllCourseState()
    }

    private fun clearEntranceFeeIfAtRisk(player: Player, riskVarp: String, paidVarp: String) {
        if (player.vars[riskVarp] == 0) return
        VarPlayerIntMapSetter.set(player, riskVarp, 0)
        VarPlayerIntMapSetter.set(player, paidVarp, 0)
    }
}

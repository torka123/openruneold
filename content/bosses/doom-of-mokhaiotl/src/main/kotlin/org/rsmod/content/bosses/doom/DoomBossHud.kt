package org.rsmod.content.bosses.doom

import java.awt.Color
import org.rsmod.api.bossbar.plugin.BossHpBarStyle

/** Current-live Doom name and visibility rules for the normal boss-health HUD. */
internal object DoomBossHud {
    /** Exact shield colours emitted by the live revision-235 Doom capture. */
    val SHIELD_STYLE =
        BossHpBarStyle(
            inner = Color(0, 32, 32),
            back = Color(0, 152, 120),
            sliding = Color(0, 208, 168),
        )

    fun label(delve: Int): String {
        require(delve >= 1) { "Doom delve must be positive: $delve" }
        return "Doom of Mokhaiotl (Delve $delve)"
    }

    fun shieldLabel(): String = "Doom of Mokhaiotl (Shielded)"

    fun showsNormalHealth(state: DoomState): Boolean =
        when (state) {
            DoomState.SHIELD_TRANSITION,
            DoomState.SHIELDED -> false
            else -> true
        }
}

package org.rsmod.content.interfaces.prayer.tab

import dev.openrune.definition.type.VarBitType
import dev.openrune.types.aconverted.SynthType
import org.rsmod.api.player.stat.baseDefenceLvl
import org.rsmod.api.player.stat.basePrayerLvl
import org.rsmod.game.entity.Player

data class Prayer(
    val id: Int,
    val name: String,
    val level: Int,
    val sound: Int,
    val enabled: String,
    val drainEffect: Int,
    val overhead: Int?,
    val unlocked: VarBitType?,
    val unlockState: Int,
    val defenceReq: Int?,
    val lockedMessage: String?,
) {
    val plainLockedMessage: String? =
        lockedMessage?.replace("<col=000080>", "")?.replace("</col>", "")?.replace("<br>", " ")

    fun hasAllRequirements(player: Player): Boolean {
        if (requiresUnlock(player)) {
            return false
        } else if (defenceReq != null && player.baseDefenceLvl < defenceReq) {
            return false
        }
        return hasBaseRequirement(player)
    }

    fun hasBaseRequirement(player: Player): Boolean = player.basePrayerLvl >= level

    private fun requiresUnlock(player: Player): Boolean =
        unlocked != null &&
            !isQuestRequirementFreePrayer(enabled) &&
            player.vars[unlocked] < unlockState
}

internal fun isQuestRequirementFreePrayer(enabled: String): Boolean =
    enabled in QUEST_REQUIREMENT_FREE_PRAYERS

private val QUEST_REQUIREMENT_FREE_PRAYERS =
    setOf("varbit.prayer_chivalry", "varbit.prayer_piety")

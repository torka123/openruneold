package org.rsmod.api.combat.formulas

import dev.openrune.types.NpcServerType
import org.rsmod.api.config.refs.BaseParams
import org.rsmod.api.config.refs.CanonicalSlayerCategoryMembership
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

/**
 * Hit chance formulas internally use decimals (e.g., `1%` = `0.01`, `100%` = `1.0`). To maintain
 * consistency with other combat formulas that use whole integers, we scale them using this
 * constant.
 */
internal const val HIT_CHANCE_SCALE: Int = 10_000

internal fun scale(base: Int, multiplier: Int, divisor: Int): Int = (base * multiplier) / divisor

/**
 * Returns whether this NPC definition belongs to the player's current Slayer assignment. Boss
 * content should use this instead of treating any non-zero Slayer target as a matching task.
 */
public fun NpcServerType.isSlayerTask(player: Player): Boolean {
    val canonicalCategory = player.vars[CanonicalSlayerCategoryMembership.ASSIGNMENT_CATEGORY_VARP]
    if (canonicalCategory > 0) {
        return CanonicalSlayerCategoryMembership.matches(canonicalCategory, id)
    }

    val taskId = player.vars["varp.slayer_target"]
    if (taskId <= 0) return false

    if (slayerTaskIds.isNotEmpty()) {
        return taskId in slayerTaskIds
    }

    val category = paramOrNull(BaseParams.slayer_task_id)
    if (category != null && category > 0 && category == taskId) {
        return true
    }

    return false
}

internal fun Npc.allowsSlayerTaskBonuses(assignedTask: Boolean): Boolean =
    assignedTask && !combatImmunities.slayerTaskBonuses

internal fun Npc.isSlayerTask(player: Player): Boolean =
    allowsSlayerTaskBonuses(visType.isSlayerTask(player))

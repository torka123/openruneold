package org.rsmod.api.player.stat

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.StatType
import org.rsmod.game.entity.Player

/**
 * Cache-backed mastery checks shared by skillcape and Max cape content.
 *
 * Hidden future stats must not affect live eligibility. Conversely, once a stat is released, it is
 * included automatically instead of relying on a hand-maintained list.
 */
public object ReleasedSkillMastery {
    public fun releasedStats(): List<StatType> =
        ServerCacheManager.getStats().values.filterNot { it.unreleased }.sortedBy { it.id }

    public fun masteredReleasedSkillCount(player: Player): Int =
        releasedStats().count { player.hasMastered(it) }

    public fun allReleasedSkillsMastered(player: Player): Boolean {
        val released = releasedStats()
        return released.isNotEmpty() && released.all { player.hasMastered(it) }
    }

    public fun isReleased(stat: StatType): Boolean = !stat.unreleased

    public fun isMastered(player: Player, stat: StatType): Boolean =
        isReleased(stat) && player.hasMastered(stat)

    private fun Player.hasMastered(stat: StatType): Boolean {
        val internal = RSCM.getReverseMapping(RSCMType.STAT, stat.id)
        return statBase(internal) >= stat.maxLevel
    }
}

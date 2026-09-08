package org.rsmod.game.queue

import dev.openrune.types.StatType

/** One real XP award that crossed at least one configurable post-99 million-XP boundary. */
public data class StatXpMilestone(
    public val stat: StatType,
    public val previousXp: Int,
    public val currentXp: Int,
)

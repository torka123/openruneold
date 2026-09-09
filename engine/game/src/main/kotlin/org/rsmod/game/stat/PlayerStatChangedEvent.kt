package org.rsmod.game.stat

import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitScope

public enum class StatLevelKind {
    Current,
    Base,
}

/**
 * Synchronous observation of an actual level mutation, not a deferred request to update the UI.
 * Snapshot fields remain unchanged if a subscriber restores the stat or ends an encounter.
 */
public data class PlayerStatChangedEvent(
    public val player: Player,
    public val statId: Int,
    public val kind: StatLevelKind,
    public val previousLevel: Int,
    public val currentLevel: Int,
    public val mutationCycle: Int,
    public val membership: HitScope.Token?,
) : UnboundEvent

package org.rsmod.api.combat.commons.magic

import org.rsmod.game.entity.Player

/**
 * The single access decision for Ancient Magicks on this server.
 *
 * OpenRune intentionally allows Ancient Magicks without Desert Treasure I. Keeping that decision
 * here prevents spellbook switching, manual casting, autocasting, and teleports from drifting into
 * separate quest checks. This policy never mutates quest state.
 */
public object AncientMagicksAccessPolicy {
    public const val REQUIRES_DESERT_TREASURE: Boolean = false
    public const val CLIENT_UNLOCK_STAGE: Int = 15

    @Suppress("UNUSED_PARAMETER")
    public fun canAccess(player: Player): Boolean = !REQUIRES_DESERT_TREASURE
}

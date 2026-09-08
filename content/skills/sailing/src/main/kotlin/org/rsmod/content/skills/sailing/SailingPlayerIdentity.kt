package org.rsmod.content.skills.sailing

import org.rsmod.game.entity.Player

/** Character ownership is keyed by the durable account-character id, never a player-list slot. */
internal object SailingPlayerIdentity {
    fun idOrNull(player: Player): Long? =
        runCatching { player.characterId.toLong() }.getOrNull()?.takeIf { it > 0 }

    fun debugBoatId(playerId: Long): SailingBoatId = SailingBoatIds.debug(playerId)
}

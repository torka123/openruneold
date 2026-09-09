package org.rsmod.game.damage

import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.hit.Hit

public fun Hit.recordDamageOn(
    target: PathingEntity,
    damage: Int,
    playerList: PlayerList,
    npcList: NpcList,
) {
    if (damage <= 0) {
        return
    }
    val attribution = context.attribution
    if (attribution.ownerUuid != null) {
        target.recordDamage(attribution.ownerUuid, damage, attribution.origin)
        return
    }
    when {
        isFromPlayer -> {
            val source = resolvePlayerSource(playerList) ?: return
            target.recordDamage(source, damage, attribution.origin)
        }
        isFromNpc -> {
            val source = resolveNpcSource(npcList) ?: return
            target.recordDamage(source, damage, attribution.origin)
        }
    }
}

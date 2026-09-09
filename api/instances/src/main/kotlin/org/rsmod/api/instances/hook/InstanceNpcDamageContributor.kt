package org.rsmod.api.instances.hook

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.currentInstanceId
import org.rsmod.api.npc.hit.NpcDamageContributor
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitAttribution

@Singleton
internal class InstanceNpcDamageContributor
@Inject
constructor(private val instances: InstanceManager) : NpcDamageContributor {
    override fun onPlayerDamageNpc(npc: Npc, source: Player, damage: Int) {
        onPlayerDamageNpc(npc, source, damage, HitAttribution(ownerUuid = source.uuid))
    }

    override fun onPlayerDamageNpc(
        npc: Npc,
        source: Player?,
        damage: Int,
        attribution: HitAttribution,
    ) {
        val instanceId = instances.instanceForNpc(npc) ?: source?.currentInstanceId() ?: return
        val owner = attribution.ownerUuid ?: source?.uuid ?: return
        instances.contributionsFor(instanceId)?.recordPlayer(owner, damage, attribution.origin)
    }
}

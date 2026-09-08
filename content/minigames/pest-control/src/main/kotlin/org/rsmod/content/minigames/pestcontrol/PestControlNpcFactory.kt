package org.rsmod.content.minigames.pestcontrol

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.game.entity.Npc
import org.rsmod.map.CoordGrid

@Singleton
internal class PestControlNpcFactory
@Inject
constructor(private val npcs: NpcRepository, private val instances: InstanceManager) {
    fun firstMissingPortalDefinition(): String? =
        PortalColor.entries.firstNotNullOfOrNull { color ->
            val type =
                runCatching { ServerCacheManager.getNpc(color.activeNpc.asRSCM(RSCMType.NPC)) }
                    .getOrNull()
            color.activeNpc.takeIf { type == null }
        }

    fun spawnPortal(match: PestControlMatch, color: PortalColor): PestControlPortal {
        val npc =
            spawn(match, color.activeNpc, color.sourceCoord, PestControlLander.portalHitpoints)
        npc.movementLocked = true
        npc.ignoreCombatInteractions = true
        return PestControlPortal(color, npc, PestControlLander.portalHitpoints)
    }

    fun delete(npc: Npc) {
        if (npc.isSlotAssigned) npcs.del(npc, Int.MAX_VALUE)
    }

    private fun spawn(
        match: PestControlMatch,
        internal: String,
        source: CoordGrid,
        hitpoints: Int,
    ): Npc {
        val npc = Npc(internal, match.mapSourceCoord(source))
        npc.mode = null
        npc.baseHitpointsLvl = hitpoints
        npc.hitpoints = hitpoints
        npcs.add(npc, PC_MATCH_TICKS + 100)
        instances.attachNpc(match.session.id, npc)
        return npc
    }
}

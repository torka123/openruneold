package org.rsmod.content.skills.construction

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.skills.construction.data.DonorConstructionData
import org.rsmod.game.entity.Npc
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class ConstructionServantScript
@Inject
constructor(private val npcs: NpcRepository, private val servants: ConstructionServantService) :
    PluginScript() {
    override fun ScriptContext.startup() {
        ensureTemporaryDemonButler()
        ConstructionServant.entries.forEach { definition ->
            onOpNpc1(definition.npcGameval) { servants.talk(this, it.npc, definition) }
        }
        val bellPullIds =
            DonorConstructionData.buildables
                .asSequence()
                .filter { it.name in BELL_PULL_BUILDABLES }
                .flatMap { it.builtLocIds.asSequence() }
                .filter { it > 0 }
                .distinct()
        bellPullIds.forEach { id ->
            val type = ServerCacheManager.getObject(id) ?: return@forEach
            onOpLoc1(type) { servants.call(this) }
        }
    }

    private fun ensureTemporaryDemonButler() {
        val typeId = ConstructionServant.DEMON_BUTLER.npcGameval.asRSCM(RSCMType.NPC)
        val existing =
            npcs
                .findAll(ZoneKey.from(TEMP_CONSTRUCTION_SERVANT_TEST_SPAWN), zoneRadius = 0)
                .firstOrNull {
                    it.isSlotAssigned &&
                        it.type.id == typeId &&
                        it.spawnCoords == TEMP_CONSTRUCTION_SERVANT_TEST_SPAWN
                }
        if (existing != null) {
            existing.mode = NpcMode.None
            existing.respawns = true
            return
        }
        val type =
            requireNotNull(ServerCacheManager.getNpc(typeId)) {
                "Missing revision-240 Demon butler ${ConstructionServant.DEMON_BUTLER.npcGameval}"
            }
        val npc =
            Npc(type, TEMP_CONSTRUCTION_SERVANT_TEST_SPAWN).also {
                it.mode = NpcMode.None
                it.respawns = true
            }
        npcs.add(npc, Int.MAX_VALUE)
    }

    private companion object {
        val BELL_PULL_BUILDABLES = setOf("ROPE_BELL_PULL", "BELL_PULL", "POSH_BELL_PULL")
    }
}

package org.rsmod.api.bosses.runtime

import jakarta.inject.Singleton
import org.rsmod.api.bosses.spec.BossSpec
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.npc.NpcUid

@Singleton
class EncounterRegistry {
    private val encounters = mutableMapOf<NpcUid, BossEncounter>()
    private val specs = mutableMapOf<Int, BossSpec>()
    private val owners = linkedMapOf<String, BossSpec>()

    fun register(npcTypeId: Int, spec: BossSpec, ownerSlug: String) {
        specs[npcTypeId] = spec
        val previous = owners.putIfAbsent(ownerSlug, spec)
        require(previous == null || previous === spec) {
            "Duplicate boss owner slug registered: $ownerSlug"
        }
    }

    fun registeredBosses(): Map<String, BossSpec> = owners.toMap()

    fun registeredBoss(slug: String): BossSpec? = owners[slug]

    fun registeredSpec(npc: Npc): BossSpec? = specs[npc.type.id] ?: specs[npc.visType.id]

    fun of(npc: Npc): BossEncounter {
        return encounters.getOrPut(npc.uid) {
            val spec =
                requireNotNull(registeredSpec(npc)) {
                    "NPC type ${npc.type.id}/${npc.visType.id} has no registered boss combat owner."
                }
            BossEncounter(npc, spec)
        }
    }

    fun isCurrent(npc: Npc, encounter: BossEncounter): Boolean =
        npc.isSlotAssigned && npc.hitpoints > 0 && encounters[npc.uid] === encounter

    fun existing(npc: Npc): BossEncounter? = encounters[npc.uid]

    fun remove(npc: Npc) {
        encounters.remove(npc.uid)
    }
}

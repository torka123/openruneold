package org.rsmod.api.bosses.runtime

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.bosses.spec.BossSpec
import org.rsmod.api.random.GameRandom
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.npc.NpcUid

@Singleton
class EncounterRegistry @Inject constructor(private val random: GameRandom) {
    private val encounters = mutableMapOf<NpcUid, BossEncounter>()
    private val specs = mutableMapOf<Int, BossSpec>()
    private val owners = linkedMapOf<String, BossSpec>()

    fun register(npcTypeId: Int, spec: BossSpec, ownerSlug: String) {
        val previous = owners[ownerSlug]
        require(previous == null || previous === spec) {
            "Duplicate boss owner slug registered: $ownerSlug"
        }
        val previousSpec = specs[npcTypeId]
        require(previousSpec == null || previousSpec === spec) {
            "Duplicate boss NPC type registered: $npcTypeId"
        }
        specs[npcTypeId] = spec
        owners[ownerSlug] = spec
    }

    fun registeredBosses(): Map<String, BossSpec> = owners.toMap()

    fun registeredBoss(slug: String): BossSpec? = owners[slug]

    fun registeredSpec(npc: Npc): BossSpec? = specs[npc.type.id] ?: specs[npc.visType.id]

    /** Selection RNG belongs to first creation; lookups never reseed a running encounter. */
    fun of(npc: Npc, selectionRandom: GameRandom = random): BossEncounter {
        return encounters.getOrPut(npc.uid) {
            val spec =
                requireNotNull(registeredSpec(npc)) {
                    "NPC type ${npc.type.id}/${npc.visType.id} has no registered boss combat owner."
                }
            BossEncounter(npc, spec, selectionRandom)
        }
    }

    fun isCurrent(npc: Npc, encounter: BossEncounter): Boolean =
        npc.hitpoints > 0 && isAttached(npc, encounter)

    /**
     * Identity/lifecycle check for already-launched hits whose declared lifetime includes death.
     */
    fun isAttached(npc: Npc, encounter: BossEncounter): Boolean =
        npc.isSlotAssigned && !encounter.isEnded && encounters[npc.uid] === encounter

    fun existing(npc: Npc): BossEncounter? = encounters[npc.uid]

    fun remove(npc: Npc) {
        encounters.remove(npc.uid)?.end()
    }
}

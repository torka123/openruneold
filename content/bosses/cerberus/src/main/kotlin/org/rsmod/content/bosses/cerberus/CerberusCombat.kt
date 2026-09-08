package org.rsmod.content.bosses.cerberus

import dev.openrune.ServerCacheManager
import jakarta.inject.Inject
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.npc.events.NpcQueueEvents
import org.rsmod.api.npc.events.interact.AiPlayerEvents
import org.rsmod.api.script.onAiApPlayer2
import org.rsmod.api.script.onNpcHit
import org.rsmod.api.script.onNpcQueue
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class CerberusCombat
@Inject
constructor(private val encounters: CerberusEncounterManager, private val death: NpcDeath) :
    PluginScript() {
    override fun ScriptContext.startup() {
        registerCerberusCombat(
            onAttack = { encounters.attack(this, it.target) },
            onHit = { encounters.defend(npc) },
            onDeath = {
                if (encounters.beginBossDeath(npc)) {
                    death.deathWithDrops(this, deathAnimationId = CerberusAssets.DEATH_ANIMATION)
                    encounters.finishBossDeath(npc)
                } else {
                    death.deathNoDrops(this, CerberusAssets.DEATH_ANIMATION)
                }
            },
        )
    }
}

internal fun ScriptContext.registerCerberusCombat(
    onAttack: suspend StandardNpcAccess.(AiPlayerEvents.Ap2) -> Unit,
    onHit: NpcHitEvents.Impact.() -> Unit,
    onDeath: suspend StandardNpcAccess.(NpcQueueEvents.Type<Nothing>) -> Unit,
) {
    // AI and death queues dispatch by immutable Npc.id; incoming hits use visType.id.
    // The encounter spawns sitting and uses transmog to wake/reset, so all forms must
    // resolve to the same callbacks. Encounter ownership and phase checks gate execution.
    for (id in listOf(CerberusAssets.SITTING_NPC_ID, CerberusAssets.ACTIVE_NPC_ID,
        CerberusAssets.RESETTING_NPC_ID)) {
        val boss = requireNotNull(ServerCacheManager.getNpc(id)) { "Missing Cerberus NPC definition: $id" }
        onAiApPlayer2(boss, onAttack)
        onNpcHit(boss, onHit)
        onNpcQueue(boss, "queue.death", onDeath)
    }
}

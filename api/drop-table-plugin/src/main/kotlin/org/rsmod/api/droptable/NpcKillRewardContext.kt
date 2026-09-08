package org.rsmod.api.droptable

import java.util.Collections
import java.util.WeakHashMap
import org.rsmod.game.entity.Npc

/**
 * Server-authoritative reward metadata for one NPC life. The default is intentionally absent, so
 * debug spawns cannot become reward-eligible without their encounter owner opting in.
 */
public data class NpcKillRewardContext(
    public val rewardEligible: Boolean,
    public val awakened: Boolean = false,
    public val perfectKill: Boolean = false,
    public val eventId: String? = null,
)

/**
 * Weak identity association used between an encounter lifecycle and the synchronous death/drop
 * pipeline. Context must be cleared when an NPC life is abandoned or reset.
 */
public object NpcKillRewardContexts {
    private val contexts: MutableMap<Npc, NpcKillRewardContext> =
        Collections.synchronizedMap(WeakHashMap())

    public operator fun get(npc: Npc): NpcKillRewardContext? = contexts[npc]

    public fun set(npc: Npc, context: NpcKillRewardContext) {
        contexts[npc] = context
    }

    public fun clear(npc: Npc) {
        contexts.remove(npc)
    }
}

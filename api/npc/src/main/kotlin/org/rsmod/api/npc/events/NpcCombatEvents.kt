package org.rsmod.api.npc.events

import org.rsmod.events.KeyedEvent
import org.rsmod.game.entity.Npc
import org.rsmod.game.hit.Hit
import org.rsmod.game.hit.HitBuilder

public class NpcHitEvents {
    public data class Modify(
        public val npc: Npc,
        public val hit: HitBuilder,
        override val id: Long = npc.visType.id.toLong(),
    ) : KeyedEvent

    /**
     * Final damage policy at impact, after administrative modifiers and before HP loss, hitmarks,
     * contribution or death. [hit] is the immutable queued hit; [damage] initially includes any
     * administrative adjustment. The processor clamps the result to zero through current HP.
     *
     * Use this for dynamic immunity and HP floors. Observe accepted damage in [Impact], not here;
     * do not mutate HP, grant credit or start death from a pre-resolution rule.
     */
    public data class Resolve(
        public val npc: Npc,
        public val hit: Hit,
        public var damage: Int = hit.damage,
        override val id: Long = npc.visType.id.toLong(),
    ) : KeyedEvent

    public data class Impact(
        public val npc: Npc,
        public val hit: Hit,
        override val id: Long = npc.visType.id.toLong(),
    ) : KeyedEvent
}

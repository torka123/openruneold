package org.rsmod.game.entity

import org.rsmod.game.entity.npc.NoopNpcInfo
import org.rsmod.game.entity.npc.NpcInfoProtocol

public class NpcAvatar(size: Int, infoProtocol: NpcInfoProtocol = NoopNpcInfo) :
    PathingEntityAvatar(size) {
    public var infoProtocol: NpcInfoProtocol = infoProtocol
        set(value) {
            field = value
            bodyModels?.let(value::setBodyModels)
        }

    /** Null uses the current NPC type's models. Non-null lists are immutable snapshots. */
    public var bodyModels: List<Int>? = null
        private set

    public fun setBodyModels(models: List<Int>) {
        require(models.size in 1..255) { "NPC body model count must be in 1..255" }
        require(models.all { it >= 0 }) { "NPC body model ids must be non-negative" }
        val snapshot = java.util.List.copyOf(models)
        if (bodyModels == snapshot) return
        bodyModels = snapshot
        infoProtocol.setBodyModels(snapshot)
    }

    public fun resetBodyModels() {
        if (bodyModels == null) return
        bodyModels = null
        infoProtocol.resetBodyModels()
    }
}

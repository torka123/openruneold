package org.rsmod.api.bosses.runtime

import jakarta.inject.Singleton
import org.rsmod.api.combat.NpcAttackOverride
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

fun interface BossExtensionHandler {
    suspend fun invoke(access: StandardNpcAccess, npc: Npc, target: Player, params: Any?)
}

/** Computes per-launch geometry/equipment snapshots; must not launch or schedule an attack. */
fun interface BossAttackParameters {
    fun resolve(npc: Npc, target: Player, params: Any?): NpcAttackOverride
}

@Singleton
class BossExtensionRegistry {
    private val handlers = mutableMapOf<String, BossExtensionHandler>()
    private val attackParameters = mutableMapOf<String, BossAttackParameters>()

    fun registerAttackParameters(name: String, resolver: BossAttackParameters) {
        require(attackParameters.putIfAbsent(name, resolver) == null) {
            "Duplicate boss attack parameters registered: $name"
        }
    }

    fun attackParameters(name: String, npc: Npc, target: Player, params: Any?): NpcAttackOverride =
        (attackParameters[name] ?: error("No boss attack parameters registered: $name")).resolve(
            npc,
            target,
            params,
        )

    fun containsAttackParameters(name: String): Boolean = name in attackParameters

    fun register(name: String, handler: BossExtensionHandler) {
        require(handlers.putIfAbsent(name, handler) == null) {
            "Duplicate boss extension registered: $name"
        }
    }

    suspend fun invoke(
        name: String,
        access: StandardNpcAccess,
        npc: Npc,
        target: Player,
        params: Any?,
    ) {
        val handler = handlers[name] ?: error("No boss extension registered: $name")
        handler.invoke(access, npc, target, params)
    }

    fun contains(name: String): Boolean = name in handlers
}

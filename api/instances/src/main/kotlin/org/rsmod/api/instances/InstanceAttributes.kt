package org.rsmod.api.instances

import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Player

public object InstanceAttributes {
    public val CURRENT_INSTANCE_ID: AttributeKey<Long> = AttributeKey(temp = true)

    /**
     * Packed [CoordGrid] the player should be placed at on their next login. This is persisted
     * while the player occupies an instance so a disconnect, process crash, or restart can never
     * save an allocated instance tile as the character's permanent location.
     */
    public val LOGIN_EXIT_COORD: AttributeKey<Int> =
        AttributeKey(persistenceKey = "instance_exit_coord")

    /**
     * Best encounter durations keyed by stable instance content key. This is account-backed so boss
     * personal bests survive logout and process restart.
     */
    internal val KILL_BEST_TICKS: AttributeKey<MutableMap<String, Int>> =
        AttributeKey(persistenceKey = "instance_kill_best_ticks_v1")
}

public fun Player.currentInstanceId(): InstanceId? =
    attr[InstanceAttributes.CURRENT_INSTANCE_ID]?.let(::InstanceId)

public fun Player.assignInstance(id: InstanceId) {
    attr[InstanceAttributes.CURRENT_INSTANCE_ID] = id.value
    VarPlayerIntMapSetter.set(this, "varbit.player_in_instance", 1)
}

public fun Player.clearInstance() {
    attr.remove(InstanceAttributes.CURRENT_INSTANCE_ID)
    VarPlayerIntMapSetter.set(this, "varbit.player_in_instance", 0)
}

internal fun Player.instanceKillBest(instanceKey: String): Int? =
    attr[InstanceAttributes.KILL_BEST_TICKS]?.get(instanceKey)

internal fun Player.recordInstanceKillBest(instanceKey: String, ticks: Int): Boolean {
    val bests = attr.getOrPut(InstanceAttributes.KILL_BEST_TICKS) { mutableMapOf() }
    val previous = bests[instanceKey]

    if (previous != null && ticks >= previous) {
        return false
    }

    bests[instanceKey] = ticks
    return true
}

public fun Player.inInstance(id: InstanceId): Boolean = currentInstanceId() == id

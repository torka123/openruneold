package org.rsmod.api.attr

/** Cross-module lock used while a minigame owns every item carried by a player. */
public object ItemSandboxAttributes {
    /**
     * While set, persistent storage interfaces must remain closed. The marker is persisted so a
     * reconnect cannot expose temporary minigame items before recovery restores the player.
     */
    public val active: AttributeKey<Boolean> =
        AttributeKey(persistenceKey = "item_sandbox_active_v1")
}

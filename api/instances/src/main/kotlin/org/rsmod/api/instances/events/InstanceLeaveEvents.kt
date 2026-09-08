package org.rsmod.api.instances.events

import org.rsmod.api.instances.InstanceId
import org.rsmod.api.instances.RaidLeaveResult
import org.rsmod.events.KeyedEvent
import org.rsmod.game.entity.Player

/** Leave request for opted-in non-raid instances using the shared logout-tab interface. */
public data class InstanceLeaveRequestEvent(
    public val player: Player,
    public val key: String,
    public val instanceId: InstanceId,
    public var result: RaidLeaveResult = RaidLeaveResult.Unhandled,
    override val id: Long = instanceEventId(key),
) : KeyedEvent

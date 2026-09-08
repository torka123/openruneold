package org.rsmod.api.collectionlog

import org.rsmod.game.entity.Player

/** Observes one authoritative, idempotent Collection Log record attempt. */
public data class CollectionLogRecordContext(
    public val player: Player,
    public val source: CollectionLogAwardSource,
    public val stableEventId: String,
    public val result: CollectionLogRecordResult,
)

public fun interface CollectionLogRecordHook {
    public fun onRecord(context: CollectionLogRecordContext)
}

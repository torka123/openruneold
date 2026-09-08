package org.rsmod.api.net.social

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.collectionlog.CollectionLogAwardKind
import org.rsmod.api.collectionlog.CollectionLogCatalogRepository
import org.rsmod.api.collectionlog.CollectionLogRecordContext
import org.rsmod.api.collectionlog.CollectionLogRecordHook
import org.rsmod.api.death.PlayerDeathContext
import org.rsmod.api.death.PlayerDeathNoticeHook
import org.rsmod.api.player.ironman.hasSafeDeathMark
import org.rsmod.game.MapClock

@Singleton
public class ClanCollectionLogBroadcastHook
@Inject
constructor(
    private val clans: ClanChatService,
    private val catalog: CollectionLogCatalogRepository,
) : CollectionLogRecordHook {
    override fun onRecord(context: CollectionLogRecordContext) {
        val result = context.result
        if (!result.newlyObtained) return
        val itemId = result.canonicalItemId ?: return
        val itemName = catalog.slotsFor(itemId).firstOrNull()?.name ?: return
        clans.publishCollectionLogBroadcast(
            player = context.player,
            itemName = itemName,
            completedSlots = result.completedSlots,
            isPet = context.source.kind == CollectionLogAwardKind.PetGrant,
            stableEventId = context.stableEventId,
        )
    }
}

@Singleton
public class ClanHardcoreStatusBroadcastHook
@Inject
constructor(private val clans: ClanChatService, private val mapClock: MapClock) :
    PlayerDeathNoticeHook {
    override fun onDeath(context: PlayerDeathContext) {
        if (!context.isHardcoreIronman || context.player.hasSafeDeathMark()) return
        clans.publishBroadcast(
            player = context.player,
            key = ClanBroadcastKey.HardcoreStatus,
            eventToken = "${mapClock.cycle}:${context.coords.packed}",
            message = "has died and lost Hardcore Ironman status.",
        )
    }
}

@Singleton
public class ClanPlayerDeathBroadcastHook
@Inject
constructor(private val clans: ClanChatService, private val mapClock: MapClock) :
    PlayerDeathNoticeHook {
    override fun onDeath(context: PlayerDeathContext) {
        val killer = context.killer ?: return
        val killerName = killer.displayName.ifBlank { killer.username }
        clans.publishPlayerDefeatBroadcast(
            player = context.player,
            killerName = killerName,
            stableEventId = "${mapClock.cycle}:${context.coords.packed}:${killer.characterId}",
        )
    }
}

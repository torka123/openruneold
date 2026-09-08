package org.rsmod.api.collectionlog

import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.concurrent.CopyOnWriteArraySet
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.output.mes
import org.rsmod.game.entity.Player

public enum class CollectionLogAwardKind {
    NpcDrop,
    BossChest,
    RaidReward,
    ClueReward,
    MinigameReward,
    SkillingReward,
    PetGrant,
    SpecialContainer,
    AdminTest,
}

public data class CollectionLogAwardSource(
    public val kind: CollectionLogAwardKind,
    public val reference: String,
)

internal fun CollectionLogAwardSource.stableEventId(eventId: String): String {
    require(eventId.isNotBlank()) { "Collection Log award event IDs must not be blank" }
    return "$kind:$reference:$eventId"
}

public sealed interface PlayerCollectionLogLoad {
    public data class Ready(public val state: CollectionLogState) : PlayerCollectionLogLoad

    public data object CorruptOrUnsupported : PlayerCollectionLogLoad
}

@Singleton
public class CollectionLogService
@Inject
constructor(
    public val catalog: CollectionLogCatalogRepository,
    initialRecordHooks: Set<CollectionLogRecordHook> = emptySet(),
) {
    public val engine: CollectionLogStateEngine = CollectionLogStateEngine(catalog)
    private val recordHooks: MutableSet<CollectionLogRecordHook> =
        CopyOnWriteArraySet(initialRecordHooks)

    public fun addRecordHook(hook: CollectionLogRecordHook): Boolean = recordHooks.add(hook)

    public fun load(player: Player): PlayerCollectionLogLoad {
        val raw = player.attr[STATE]
        val state =
            if (raw == null) {
                CollectionLogState()
            } else {
                CollectionLogStateCodec.decode(raw)
                    ?: return PlayerCollectionLogLoad.CorruptOrUnsupported
            }
        if (!state.legacyMigrationComplete) {
            val legacy =
                LEGACY_KEYS.mapNotNull { key -> player.attr[key] }
                    .map(CollectionLogStateCodec::legacyQuantities)
            engine.migrateLegacy(state, legacy)
            save(player, state)
        }
        return PlayerCollectionLogLoad.Ready(state)
    }

    public fun record(
        player: Player,
        itemId: Int,
        amount: Int,
        source: CollectionLogAwardSource,
        eventId: String,
    ): CollectionLogRecordResult {
        val stableEventId = source.stableEventId(eventId)
        val loaded = load(player)
        if (loaded !is PlayerCollectionLogLoad.Ready) {
            player.mes("Your Collection Log data could not be loaded; this award was not recorded.")
            return CollectionLogRecordResult(CollectionLogRecordStatus.UntrackedItem)
        }
        val result = engine.record(loaded.state, itemId, amount, stableEventId)
        if (
            result.status == CollectionLogRecordStatus.Recorded ||
                result.status == CollectionLogRecordStatus.AtCap
        ) {
            save(player, loaded.state)
        }
        val notificationMode = player.vars["varbit.option_collection_new_item"]
        val chatNotificationEnabled = notificationMode and COLLECTION_LOG_CHAT_NOTIFICATION != 0
        if (result.newlyObtained && chatNotificationEnabled) {
            val name =
                catalog.slotsFor(requireNotNull(result.canonicalItemId)).firstOrNull()?.name
                    ?: "item"
            player.mes("New item added to your collection log: <col=ff0000>$name</col>")
        }
        val context = CollectionLogRecordContext(player, source, stableEventId, result)
        recordHooks.forEach { it.onRecord(context) }
        return result
    }

    public fun recordBatch(
        player: Player,
        awards: Iterable<Pair<Int, Int>>,
        source: CollectionLogAwardSource,
        eventId: String,
    ): List<CollectionLogRecordResult> =
        awards.mapIndexed { index, (itemId, amount) ->
            record(player, itemId, amount, source, "$eventId:$index")
        }

    public fun save(player: Player, state: CollectionLogState) {
        player.attr[STATE] = CollectionLogStateCodec.encode(state)
    }

    public companion object {
        private const val COLLECTION_LOG_CHAT_NOTIFICATION: Int = 1

        public val STATE: AttributeKey<Any> =
            AttributeKey(persistenceKey = "collection_log_state_v1")

        /** Compatible legacy shapes are retained after migration; they are never erased. */
        public val LEGACY_KEYS: List<AttributeKey<Any>> =
            listOf(
                AttributeKey(persistenceKey = "collection_log"),
                AttributeKey(persistenceKey = "collection_log_collected"),
                AttributeKey(persistenceKey = "unique_drops"),
            )
    }
}

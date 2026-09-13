package org.rsmod.api.account.character.rewards

import java.util.Collections
import java.util.Objects
import java.util.UUID
import org.rsmod.api.account.persistence.GamePersistenceRscmKeys
import org.rsmod.game.inv.InvObj
import org.rsmod.map.CoordGrid

/**
 * Immutable personal-ground reward intent for one NPC life and its complete recipient set.
 *
 * Prepare this value, including native item-key conversion, before entering a database transaction.
 * Persisting it allocates items only; it does not grant ground objects or make pickup crash-safe.
 */
public class NpcRewardAllocation(
    public val lifeId: UUID,
    public val sourceNpcKey: String,
    recipients: List<Recipient>,
) {
    public val recipients: List<Recipient> =
        Collections.unmodifiableList(recipients.sortedBy { it.characterId })

    init {
        require(sourceNpcKey.isNotBlank()) { "NPC reward source key must not be blank" }
        require(this.recipients.map { it.characterId }.distinct().size == this.recipients.size) {
            "An NPC life must have at most one allocation per character"
        }
    }

    override fun equals(other: Any?): Boolean =
        other is NpcRewardAllocation &&
            lifeId == other.lifeId &&
            sourceNpcKey == other.sourceNpcKey &&
            recipients == other.recipients

    override fun hashCode(): Int = Objects.hash(lifeId, sourceNpcKey, recipients)

    public class Recipient(
        public val accountId: Int,
        public val characterId: Int,
        public val coords: CoordGrid,
        public val duration: Int,
        items: List<Item>,
    ) {
        /** List index is the stable item ordinal; equal items occupy separate lines. */
        public val items: List<Item> = Collections.unmodifiableList(ArrayList(items))

        init {
            require(accountId > 0 && characterId > 0) { "Native recipient IDs must be positive" }
            require(duration >= 0) { "NPC reward duration must be nonnegative" }
        }

        override fun equals(other: Any?): Boolean =
            other is Recipient &&
                accountId == other.accountId &&
                characterId == other.characterId &&
                coords == other.coords &&
                duration == other.duration &&
                items == other.items

        override fun hashCode(): Int = Objects.hash(accountId, characterId, coords, duration, items)
    }

    public data class Item(
        public val objKey: String,
        public val count: Int,
        public val vars: Int = 0,
    ) {
        init {
            require(objKey.isNotBlank()) { "NPC reward item key must not be blank" }
            require(count > 0) { "NPC reward quantity must be positive" }
        }

        /** Uses the same final-item RSCM encoding as the native account inventory pipeline. */
        public constructor(
            item: InvObj
        ) : this(GamePersistenceRscmKeys.encodeObjKey(item.id), item.count, item.vars)
    }
}

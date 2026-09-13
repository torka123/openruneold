package org.rsmod.api.account.character.rewards

import java.util.Collections
import java.util.Objects

/**
 * Detached allocation minus recorded inventory-claim consumption in one SQL statement snapshot.
 * Unclaimed quantities are observations, not reservations or ground eligibility/lifetime proof.
 * Another backend may still commit a claim; the caller's own staged claims remain provisional.
 * Non-pickup retirement and restart/reveal/expiry policy are not represented by this value.
 */
public class NpcRewardAllocationBalance
internal constructor(
    public val allocation: NpcRewardAllocation,
    claimed: Map<Pair<Int, Int>, Long>,
) {
    public val items: List<Item>

    init {
        val expected =
            allocation.recipients
                .flatMap { recipient ->
                    recipient.items.indices.map { recipient.characterId to it }
                }
                .toSet()
        require(claimed.keys == expected) {
            "Claim balance must cover exactly the allocated item ordinals"
        }
        items =
            Collections.unmodifiableList(
                allocation.recipients.flatMap { recipient ->
                    recipient.items.mapIndexed { ordinal, allocated ->
                        val count = claimed.getValue(recipient.characterId to ordinal)
                        require(count in 0..allocated.count.toLong()) {
                            "Claim consumption exceeds its allocation"
                        }
                        Item(recipient.characterId, ordinal, allocated, count.toInt())
                    }
                }
            )
    }

    override fun equals(other: Any?): Boolean =
        other is NpcRewardAllocationBalance &&
            allocation == other.allocation &&
            items == other.items

    override fun hashCode(): Int = Objects.hash(allocation, items)

    public data class Item(
        public val characterId: Int,
        public val ordinal: Int,
        public val allocated: NpcRewardAllocation.Item,
        public val claimedCount: Int,
    ) {
        public val unclaimedCount: Int
            get() = allocated.count - claimedCount

        init {
            require(characterId > 0 && ordinal >= 0) { "Invalid allocation item identity" }
            require(claimedCount in 0..allocated.count) { "Invalid claimed allocation quantity" }
        }
    }
}

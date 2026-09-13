package org.rsmod.api.account.character.rewards

import java.util.Collections
import java.util.Objects

/**
 * Detached allocation, inventory claims and non-pickup retirement in one SQL statement snapshot.
 * Remaining quantities are observations, not reservations or ground eligibility/lifetime proof.
 * Another backend may still commit consumption; the caller's own staged rows remain provisional.
 * Restart/reveal/expiry policy is not represented by this value.
 */
public class NpcRewardAllocationBalance
internal constructor(
    public val allocation: NpcRewardAllocation,
    claimed: Map<Pair<Int, Int>, Long>,
    retired: Map<Pair<Int, Int>, Long> = claimed.mapValues { 0L },
) {
    public val items: List<Item>

    init {
        val expected =
            allocation.recipients
                .flatMap { recipient ->
                    recipient.items.indices.map { recipient.characterId to it }
                }
                .toSet()
        require(claimed.keys == expected && retired.keys == expected) {
            "Claim and retirement balances must cover exactly the allocated item ordinals"
        }
        items =
            Collections.unmodifiableList(
                allocation.recipients.flatMap { recipient ->
                    recipient.items.mapIndexed { ordinal, allocated ->
                        val count = claimed.getValue(recipient.characterId to ordinal)
                        val retiredCount = retired.getValue(recipient.characterId to ordinal)
                        require(
                            count in 0..allocated.count.toLong() &&
                                retiredCount in 0..(allocated.count.toLong() - count)
                        ) {
                            "Claim and retirement consumption exceeds its allocation"
                        }
                        Item(
                            recipient.characterId,
                            ordinal,
                            allocated,
                            count.toInt(),
                            retiredCount.toInt(),
                        )
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
        public val retiredCount: Int = 0,
    ) {
        /** Inventory-unclaimed quantity, including retired units. See [remainingCount]. */
        public val unclaimedCount: Int
            get() = allocated.count - claimedCount

        /** Quantity not yet consumed by either journal; not a reservation or live-ground proof. */
        public val remainingCount: Int
            get() = allocated.count - claimedCount - retiredCount

        init {
            require(characterId > 0 && ordinal >= 0) { "Invalid allocation item identity" }
            require(claimedCount in 0..allocated.count) { "Invalid claimed allocation quantity" }
            require(retiredCount in 0..(allocated.count - claimedCount)) {
                "Invalid retired allocation quantity"
            }
        }
    }
}

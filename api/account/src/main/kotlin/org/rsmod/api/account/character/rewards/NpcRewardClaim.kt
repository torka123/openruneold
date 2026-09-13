package org.rsmod.api.account.character.rewards

import java.util.Collections
import java.util.Objects
import java.util.UUID

/**
 * Detached quantity request prepared before SQL. The picker can differ from the allocated
 * recipient. This identity records source consumption, not ground eligibility or transformed
 * inventory output.
 */
public class NpcRewardClaim(
    public val attemptId: UUID,
    public val pickerAccountId: Int,
    public val pickerCharacterId: Int,
    fragments: List<Fragment>,
) {
    public val fragments: List<Fragment> =
        Collections.unmodifiableList(
            fragments.sortedWith(
                compareBy<Fragment> { it.lifeId.toString() }
                    .thenBy { it.allocatedCharacterId }
                    .thenBy { it.itemOrdinal }
            )
        )

    init {
        require(pickerAccountId > 0 && pickerCharacterId > 0) {
            "Native picker IDs must be positive"
        }
        require(this.fragments.isNotEmpty()) {
            "An NPC reward claim must consume at least one fragment"
        }
        require(
            this.fragments
                .map { Triple(it.lifeId, it.allocatedCharacterId, it.itemOrdinal) }
                .distinct()
                .size == this.fragments.size
        ) {
            "An NPC reward claim cannot repeat a source fragment"
        }
        require(this.fragments.sumOf { it.count.toLong() } <= Int.MAX_VALUE) {
            "NPC reward claim quantity exceeds the native inventory count limit"
        }
    }

    override fun equals(other: Any?): Boolean =
        other is NpcRewardClaim &&
            attemptId == other.attemptId &&
            pickerAccountId == other.pickerAccountId &&
            pickerCharacterId == other.pickerCharacterId &&
            fragments == other.fragments

    override fun hashCode(): Int =
        Objects.hash(attemptId, pickerAccountId, pickerCharacterId, fragments)

    public data class Fragment(
        public val lifeId: UUID,
        public val allocatedCharacterId: Int,
        public val itemOrdinal: Int,
        public val count: Int,
    ) {
        init {
            require(allocatedCharacterId > 0) { "Allocated character ID must be positive" }
            require(itemOrdinal >= 0) { "Allocated item ordinal must be nonnegative" }
            require(count > 0) { "Claimed quantity must be positive" }
        }
    }
}

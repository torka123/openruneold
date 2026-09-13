package org.rsmod.api.account.character.rewards

import java.util.Collections
import java.util.Objects
import java.util.UUID

/**
 * Detached non-pickup source consumption. The caller supplies the reason; this value does not
 * establish that a live object expired or was destroyed, nor choose a restart or expiry policy.
 * Counts are bounded per source item, not as one inventory stack: retirement grants no inventory.
 */
public class NpcRewardRetirement(
    public val attemptId: UUID,
    public val reason: NpcRewardRetirementReason,
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
        require(this.fragments.isNotEmpty()) {
            "An NPC reward retirement must consume at least one fragment"
        }
        require(
            this.fragments
                .map { Triple(it.lifeId, it.allocatedCharacterId, it.itemOrdinal) }
                .distinct()
                .size == this.fragments.size
        ) {
            "An NPC reward retirement cannot repeat a source fragment"
        }
    }

    override fun equals(other: Any?): Boolean =
        other is NpcRewardRetirement &&
            attemptId == other.attemptId &&
            reason == other.reason &&
            fragments == other.fragments

    override fun hashCode(): Int = Objects.hash(attemptId, reason, fragments)

    public data class Fragment(
        public val lifeId: UUID,
        public val allocatedCharacterId: Int,
        public val itemOrdinal: Int,
        public val count: Int,
    ) {
        init {
            require(allocatedCharacterId > 0) { "Allocated character ID must be positive" }
            require(itemOrdinal >= 0) { "Allocated item ordinal must be nonnegative" }
            require(count > 0) { "Retired quantity must be positive" }
        }
    }
}

public enum class NpcRewardRetirementReason {
    EXPIRED,
    DESTROYED,
}

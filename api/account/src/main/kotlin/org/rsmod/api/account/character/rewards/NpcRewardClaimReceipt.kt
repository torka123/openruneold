package org.rsmod.api.account.character.rewards

/** The outcome represented by a committed row, not proof that the caller has committed its SQL. */
public enum class NpcRewardClaimOutcome {
    COMMITTED,
    ABORTED,
}

/**
 * A detached observation. A receipt read from the caller's own transaction can still be
 * provisional.
 */
public data class NpcRewardClaimReceipt(
    public val request: NpcRewardClaim,
    public val outcome: NpcRewardClaimOutcome,
)

/**
 * Distinguishes newly staged SQL from a replay. Only a [Staged] COMMITTED proposal may be paired
 * with a new account snapshot in the same transaction. [Existing] must never replay that
 * mutation/save. Both variants require the caller to finish its transaction; neither grants an
 * inventory item.
 */
public sealed interface NpcRewardClaimWrite {
    public val receipt: NpcRewardClaimReceipt

    public data class Staged(override val receipt: NpcRewardClaimReceipt) : NpcRewardClaimWrite

    public data class Existing(override val receipt: NpcRewardClaimReceipt) : NpcRewardClaimWrite
}

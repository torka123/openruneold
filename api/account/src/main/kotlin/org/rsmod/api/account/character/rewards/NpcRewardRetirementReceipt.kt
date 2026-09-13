package org.rsmod.api.account.character.rewards

/** A committed row's outcome, not proof that the caller has committed its SQL. */
public enum class NpcRewardRetirementOutcome {
    COMMITTED,
    ABORTED,
}

/** Detached observation; a receipt in the caller's own transaction can still be provisional. */
public data class NpcRewardRetirementReceipt(
    public val request: NpcRewardRetirement,
    public val outcome: NpcRewardRetirementOutcome,
)

/**
 * New provisional SQL versus exact replay. The caller must finish its transaction and reconcile its
 * own operation; neither variant removes a ground object or proves its live eligibility. [Existing]
 * must never repeat consumption or a gameplay mutation.
 */
public sealed interface NpcRewardRetirementWrite {
    public val receipt: NpcRewardRetirementReceipt

    public data class Staged(override val receipt: NpcRewardRetirementReceipt) :
        NpcRewardRetirementWrite

    public data class Existing(override val receipt: NpcRewardRetirementReceipt) :
        NpcRewardRetirementWrite
}

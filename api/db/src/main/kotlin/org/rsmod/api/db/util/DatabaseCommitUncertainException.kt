package org.rsmod.api.db.util

/**
 * Commit was attempted but did not return successfully. Neither this error nor a successful
 * best-effort rollback proves the transaction was aborted. Reconcile the operation's durable
 * receipt before retrying gameplay or allowing an account snapshot to overwrite its outcome.
 */
public class DatabaseCommitUncertainException(
    commitCause: Throwable,
    public val rollbackCause: Throwable? = null,
) :
    RuntimeException(
        "Database commit outcome is uncertain; durable receipt reconciliation is required.",
        commitCause,
    ) {
    init {
        if (rollbackCause != null) addSuppressed(rollbackCause)
    }
}

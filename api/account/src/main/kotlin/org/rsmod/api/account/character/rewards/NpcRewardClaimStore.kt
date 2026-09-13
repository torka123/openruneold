package org.rsmod.api.account.character.rewards

import dev.or2.sql.OpenRuneSql
import java.sql.PreparedStatement
import java.util.UUID
import org.rsmod.api.db.DatabaseConnection

/** SQL only. On any failure the caller must roll back its entire transaction. */
internal object NpcRewardClaimStore {
    fun stage(db: DatabaseConnection, request: NpcRewardClaim, timeout: Int): NpcRewardClaimWrite {
        val session = Session(db, timeout)
        session.validateTransaction()
        val result = session.reserve(request, NpcRewardClaimOutcome.COMMITTED)
        if (result is NpcRewardClaimWrite.Existing) return result
        session.lockPicker(request)
        session.consume(request)
        return result
    }

    fun settle(db: DatabaseConnection, request: NpcRewardClaim, timeout: Int): NpcRewardClaimWrite {
        val session = Session(db, timeout)
        session.validateTransaction()
        // Arbitration precedes every live-parent lookup: even a missing-parent attempt is fenced.
        return session.reserve(request, NpcRewardClaimOutcome.ABORTED)
    }

    fun find(db: DatabaseConnection, attemptId: UUID, timeout: Int): NpcRewardClaimReceipt? {
        val session = Session(db, timeout)
        session.validateTransaction()
        return session.find(attemptId)
    }

    private class Session(private val db: DatabaseConnection, private val timeout: Int) {
        init {
            require(timeout > 0) { "NPC reward claim statement timeout must be positive" }
        }

        fun validateTransaction() {
            statement("claim_transaction_isolation").use {
                check(!it.connection.autoCommit) {
                    "NPC reward claims require a caller-owned transaction"
                }
                it.executeQuery().use { rows ->
                    check(rows.next() && rows.getString(1) == "read committed") {
                        "NPC reward claims require READ COMMITTED statement snapshots"
                    }
                }
            }
        }

        fun reserve(request: NpcRewardClaim, outcome: NpcRewardClaimOutcome): NpcRewardClaimWrite {
            val inserted =
                statement("insert_claim_attempt").use {
                    it.setObject(1, request.attemptId)
                    it.setInt(2, request.pickerAccountId)
                    it.setInt(3, request.pickerCharacterId)
                    it.setString(4, outcome.name)
                    it.executeUpdate() == 1
                }
            if (!inserted) {
                // This must be a separate statement: the unique conflict may have just committed.
                val receipt =
                    checkNotNull(find(request.attemptId)) { "NPC reward claim disappeared" }
                check(receipt.request == request) {
                    "NPC reward claim payload changed for ${request.attemptId}"
                }
                return NpcRewardClaimWrite.Existing(receipt)
            }
            statement("insert_claim_fragment").use {
                for (fragment in request.fragments) {
                    it.setObject(1, request.attemptId)
                    it.bindFragment(fragment, 2)
                    it.setInt(5, fragment.count)
                    check(it.executeUpdate() == 1)
                }
            }
            return NpcRewardClaimWrite.Staged(NpcRewardClaimReceipt(request, outcome))
        }

        fun lockPicker(request: NpcRewardClaim) {
            // Reuse native allocation/save lock order; an exclusive account lock can deadlock
            // against native saves which update their character before account-attribute FK checks.
            statement("lock_account").use {
                it.setInt(1, request.pickerAccountId)
                it.executeQuery().use { rows ->
                    check(rows.next()) {
                        "Missing NPC reward picker account ${request.pickerAccountId}"
                    }
                }
            }
            statement("lock_character").use {
                it.setInt(1, request.pickerCharacterId)
                it.executeQuery().use { rows ->
                    check(rows.next() && rows.getInt(1) == request.pickerAccountId) {
                        "NPC reward picker character ${request.pickerCharacterId} does not belong to account ${request.pickerAccountId}"
                    }
                }
            }
        }

        fun consume(request: NpcRewardClaim) {
            val originalCounts =
                statement("lock_claim_item").use { lock ->
                    request.fragments.map { fragment ->
                        lock.bindFragment(fragment)
                        lock.executeQuery().use { rows ->
                            check(rows.next()) { "Missing NPC reward source fragment $fragment" }
                            rows.getInt(1).toLong()
                        }
                    }
                }
            // All source rows are now locked. A fresh statement snapshot includes consumption
            // committed by a contender we waited for; a locking/aggregate CTE would be stale.
            statement("select_claim_consumed").use { sum ->
                for ((index, fragment) in request.fragments.withIndex()) {
                    sum.bindFragment(fragment)
                    val consumed =
                        sum.executeQuery().use { rows ->
                            check(rows.next())
                            rows.getLong(1)
                        }
                    val original = originalCounts[index]
                    check(
                        consumed in 0..original && fragment.count.toLong() <= original - consumed
                    ) {
                        "NPC reward fragment quantity is unavailable: $fragment"
                    }
                }
            }
            statement("insert_claim_consumption").use {
                it.setObject(1, request.attemptId)
                check(it.executeUpdate() == request.fragments.size)
            }
        }

        fun find(attemptId: UUID): NpcRewardClaimReceipt? =
            statement("select_claim").use {
                it.setObject(1, attemptId)
                it.executeQuery().use { rows ->
                    if (!rows.next()) return null
                    check(
                        rows.getInt("request_version") == 1 &&
                            rows.getString("destination") == "inventory"
                    ) {
                        "Unsupported stored NPC reward claim format"
                    }
                    val account = rows.getInt("picker_account_id")
                    val character = rows.getInt("picker_character_id")
                    val outcome = NpcRewardClaimOutcome.valueOf(rows.getString("outcome"))
                    val fragments = buildList {
                        do {
                            val lifeId =
                                checkNotNull(rows.getObject("life_id", UUID::class.java)) {
                                    "Stored NPC reward claim has no fragments"
                                }
                            add(
                                NpcRewardClaim.Fragment(
                                    lifeId,
                                    rows.getInt("allocated_character_id"),
                                    rows.getInt("item_ordinal"),
                                    rows.getInt("count"),
                                )
                            )
                        } while (rows.next())
                    }
                    NpcRewardClaimReceipt(
                        NpcRewardClaim(attemptId, account, character, fragments),
                        outcome,
                    )
                }
            }

        private fun statement(name: String): PreparedStatement =
            db.prepareStatement(OpenRuneSql.text("game/npc_rewards/$name.sql")).apply {
                // PostgreSQL cancellation/error leaves the outcome unresolved until same-key
                // settlement.
                queryTimeout = timeout
            }

        private fun PreparedStatement.bindFragment(
            fragment: NpcRewardClaim.Fragment,
            start: Int = 1,
        ) {
            setObject(start, fragment.lifeId)
            setInt(start + 1, fragment.allocatedCharacterId)
            setInt(start + 2, fragment.itemOrdinal)
        }
    }
}

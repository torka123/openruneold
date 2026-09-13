package org.rsmod.api.account.character.rewards

import dev.or2.sql.OpenRuneSql
import java.sql.PreparedStatement
import java.util.UUID
import org.rsmod.api.db.DatabaseConnection

/** SQL only. On any failure the caller must roll back its entire transaction. */
internal object NpcRewardRetirementStore {
    fun stage(
        db: DatabaseConnection,
        request: NpcRewardRetirement,
        timeout: Int,
    ): NpcRewardRetirementWrite {
        val session = Session(db, timeout)
        session.validateTransaction()
        val result = session.reserve(request, NpcRewardRetirementOutcome.COMMITTED)
        if (result is NpcRewardRetirementWrite.Existing) return result
        session.consume(request)
        return result
    }

    fun settle(
        db: DatabaseConnection,
        request: NpcRewardRetirement,
        timeout: Int,
    ): NpcRewardRetirementWrite {
        val session = Session(db, timeout)
        session.validateTransaction()
        // Reserve the original unique key before looking up any live source dependency.
        return session.reserve(request, NpcRewardRetirementOutcome.ABORTED)
    }

    fun find(db: DatabaseConnection, attemptId: UUID, timeout: Int): NpcRewardRetirementReceipt? {
        val session = Session(db, timeout)
        session.validateTransaction()
        return session.find(attemptId)
    }

    private class Session(private val db: DatabaseConnection, private val timeout: Int) {
        init {
            require(timeout > 0) { "NPC reward retirement statement timeout must be positive" }
        }

        fun validateTransaction() {
            statement("claim_transaction_isolation").use {
                check(!it.connection.autoCommit) {
                    "NPC reward retirements require a caller-owned transaction"
                }
                it.executeQuery().use { rows ->
                    check(rows.next() && rows.getString(1) == "read committed") {
                        "NPC reward retirements require READ COMMITTED statement snapshots"
                    }
                }
            }
        }

        fun reserve(
            request: NpcRewardRetirement,
            outcome: NpcRewardRetirementOutcome,
        ): NpcRewardRetirementWrite {
            val inserted =
                statement("insert_retirement_attempt").use {
                    it.setObject(1, request.attemptId)
                    it.setString(2, request.reason.name)
                    it.setString(3, outcome.name)
                    it.executeUpdate() == 1
                }
            if (!inserted) {
                // The winner may have been invisible before this unique-key wait completed.
                val receipt =
                    checkNotNull(find(request.attemptId)) { "NPC reward retirement disappeared" }
                check(receipt.request == request) {
                    "NPC reward retirement payload changed for ${request.attemptId}"
                }
                return NpcRewardRetirementWrite.Existing(receipt)
            }
            statement("insert_retirement_fragment").use {
                for (fragment in request.fragments) {
                    it.setObject(1, request.attemptId)
                    it.bindFragment(fragment, 2)
                    it.setInt(5, fragment.count)
                    check(it.executeUpdate() == 1)
                }
            }
            return NpcRewardRetirementWrite.Staged(NpcRewardRetirementReceipt(request, outcome))
        }

        fun consume(request: NpcRewardRetirement) {
            // Identical source rows/order to inventory claims. Retirement needs no picker lock.
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
            // A new statement AFTER every source lock includes the claim/retirement contender
            // we waited for. Combining lock acquisition and aggregation would use a stale view.
            statement("select_reward_consumed").use { sum ->
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
            statement("insert_retirement_consumption").use {
                it.setObject(1, request.attemptId)
                check(it.executeUpdate() == request.fragments.size)
            }
        }

        fun find(attemptId: UUID): NpcRewardRetirementReceipt? =
            statement("select_retirement").use {
                it.setObject(1, attemptId)
                it.executeQuery().use { rows ->
                    if (!rows.next()) return null
                    check(rows.getInt("request_version") == 1) {
                        "Unsupported stored NPC reward retirement format"
                    }
                    val reason = NpcRewardRetirementReason.valueOf(rows.getString("reason"))
                    val outcome = NpcRewardRetirementOutcome.valueOf(rows.getString("outcome"))
                    val fragments = buildList {
                        do {
                            val lifeId =
                                checkNotNull(rows.getObject("life_id", UUID::class.java)) {
                                    "Stored NPC reward retirement has no fragments"
                                }
                            add(
                                NpcRewardRetirement.Fragment(
                                    lifeId,
                                    rows.getInt("allocated_character_id"),
                                    rows.getInt("item_ordinal"),
                                    rows.getInt("count"),
                                )
                            )
                        } while (rows.next())
                    }
                    NpcRewardRetirementReceipt(
                        NpcRewardRetirement(attemptId, reason, fragments),
                        outcome,
                    )
                }
            }

        private fun statement(name: String): PreparedStatement =
            db.prepareStatement(OpenRuneSql.text("game/npc_rewards/$name.sql")).apply {
                // Timeout/cancellation does not settle an unknown commit outcome.
                queryTimeout = timeout
            }

        private fun PreparedStatement.bindFragment(
            fragment: NpcRewardRetirement.Fragment,
            start: Int = 1,
        ) {
            setObject(start, fragment.lifeId)
            setInt(start + 1, fragment.allocatedCharacterId)
            setInt(start + 2, fragment.itemOrdinal)
        }
    }
}

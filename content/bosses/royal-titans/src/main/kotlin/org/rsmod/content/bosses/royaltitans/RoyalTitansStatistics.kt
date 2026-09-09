package org.rsmod.content.bosses.royaltitans

import jakarta.inject.Inject
import java.util.UUID
import org.rsmod.api.db.DatabaseConnection
import org.rsmod.api.db.jdbc.GameDatabase

/** Content persistence through the existing public transaction API; no character-attribute SQL. */
internal class RoyalTitansStatistics @Inject constructor(private val database: GameDatabase) {
    fun complete(completion: RoyalTitansCompletion): Map<Int, RoyalTitansCompletionReceipt> =
        database.withTransactionBlocking { RoyalTitansStatisticsStore.complete(it, completion) }

    fun death(event: UUID, accountId: Int, encounterId: UUID) {
        database.withTransactionBlocking {
            RoyalTitansStatisticsStore.death(it, event, accountId, encounterId)
        }
    }

    fun snapshot(accountId: Int): RoyalTitansStatisticsSnapshot =
        database.withTransactionBlocking { RoyalTitansStatisticsStore.snapshot(it, accountId) }
}

internal data class RoyalTitansCompletion(
    val encounterId: UUID,
    val elapsedTicks: Int,
    val participants: List<RoyalTitansFinisher>,
) {
    init {
        require(elapsedTicks > 0)
        require(participants.all { it.accountId > 0 })
        require(participants.map { it.accountId }.distinct().size == participants.size)
    }
}

internal data class RoyalTitansFinisher(
    val accountId: Int,
    val displayName: String,
    val personalBestEligible: Boolean,
)

internal data class RoyalTitansCompletionReceipt(
    val snapshot: RoyalTitansStatisticsSnapshot,
    val newPersonalBest: Boolean,
)

internal data class RoyalTitansStatisticsSnapshot(
    val personalKills: Int = 0,
    val personalDeaths: Int = 0,
    val personalBestTicks: Int? = null,
    val globalKills: Int = 0,
    val globalDeaths: Int = 0,
    val globalBestTicks: Int? = null,
    val globalRecordHolder: String? = null,
)

/** SQL functions receive the native connection; transaction ownership remains with GameDatabase. */
internal object RoyalTitansStatisticsStore {
    fun complete(
        db: DatabaseConnection,
        completion: RoyalTitansCompletion,
    ): Map<Int, RoyalTitansCompletionReceipt> {
        val finishers = completion.participants.sortedBy { it.accountId }
        // Account row locks serialize simultaneous PB decisions, in stable order to avoid deadlock.
        for (player in finishers) db.prepareStatement(
                "SELECT id FROM accounts WHERE id = ? FOR UPDATE"
            )
            .use {
                it.setInt(1, player.accountId)
                it.executeQuery().use { rows ->
                    check(rows.next()) { "Missing Royal Titans account ${player.accountId}" }
                }
            }
        val inserted =
            db.prepareStatement(
                    """
            INSERT INTO royal_titan_completions(encounter_id, elapsed_ticks) VALUES (?, ?)
            ON CONFLICT DO NOTHING RETURNING encounter_id
        """
                )
                .use {
                    it.setObject(1, completion.encounterId)
                    it.setInt(2, completion.elapsedTicks)
                    it.executeQuery().use { rows -> rows.next() }
                }
        if (inserted) {
            for (player in finishers) {
                val previous = snapshot(db, player.accountId).personalBestTicks
                db.prepareStatement(
                        """
                    INSERT INTO royal_titan_participants
                    (encounter_id, account_id, display_name, personal_best_eligible, previous_best_ticks)
                    VALUES (?, ?, ?, ?, ?)
                """
                    )
                    .use {
                        it.setObject(1, completion.encounterId)
                        it.setInt(2, player.accountId)
                        it.setString(3, player.displayName)
                        it.setBoolean(4, player.personalBestEligible)
                        it.setObject(5, previous)
                        check(it.executeUpdate() == 1)
                    }
            }
        }
        // Retried callbacks must have the same immutable payload, not merely the same UUID.
        val storedTicks =
            db.prepareStatement(
                    "SELECT elapsed_ticks FROM royal_titan_completions WHERE encounter_id = ?"
                )
                .use {
                    it.setObject(1, completion.encounterId)
                    it.executeQuery().use { rows ->
                        check(rows.next())
                        rows.getInt(1)
                    }
                }
        check(storedTicks == completion.elapsedTicks) { "Royal Titans completion payload changed" }
        val stored =
            db.prepareStatement(
                    """
            SELECT account_id, display_name, personal_best_eligible, previous_best_ticks
            FROM royal_titan_participants WHERE encounter_id = ? ORDER BY account_id
        """
                )
                .use {
                    it.setObject(1, completion.encounterId)
                    it.executeQuery().use { rows ->
                        buildList {
                            while (rows.next()) add(
                                RoyalTitansFinisher(
                                    rows.getInt(1),
                                    rows.getString(2),
                                    rows.getBoolean(3),
                                ) to rows.getObject(4, Integer::class.java)?.toInt()
                            )
                        }
                    }
                }
        check(stored.map { it.first } == finishers) { "Royal Titans participant payload changed" }
        return stored.associate { (player, previous) ->
            player.accountId to
                RoyalTitansCompletionReceipt(
                    snapshot(db, player.accountId),
                    player.personalBestEligible &&
                        (previous == null || completion.elapsedTicks < previous),
                )
        }
    }

    fun death(db: DatabaseConnection, event: UUID, accountId: Int, encounterId: UUID) {
        db.prepareStatement(
                """
            INSERT INTO royal_titan_deaths(death_event_id, account_id, encounter_id) VALUES (?, ?, ?)
            ON CONFLICT DO NOTHING
        """
            )
            .use {
                it.setObject(1, event)
                it.setInt(2, accountId)
                it.setObject(3, encounterId)
                it.executeUpdate()
            }
        db.prepareStatement(
                "SELECT account_id, encounter_id FROM royal_titan_deaths WHERE death_event_id = ?"
            )
            .use {
                it.setObject(1, event)
                it.executeQuery().use { rows ->
                    check(
                        rows.next() &&
                            rows.getInt(1) == accountId &&
                            rows.getObject(2, UUID::class.java) == encounterId
                    ) {
                        "Royal Titans death payload changed"
                    }
                }
            }
    }

    /** One statement gives personal and global values the same PostgreSQL snapshot. */
    fun snapshot(db: DatabaseConnection, accountId: Int): RoyalTitansStatisticsSnapshot =
        db.prepareStatement(
                """
            WITH personal AS (
                SELECT COUNT(*) AS kills, MIN(c.elapsed_ticks) FILTER (WHERE p.personal_best_eligible) AS best
                FROM royal_titan_participants p JOIN royal_titan_completions c USING (encounter_id)
                WHERE p.account_id = ?
            ), global_stats AS (
                SELECT COUNT(*) AS kills, MIN(elapsed_ticks) AS best FROM royal_titan_completions
            ), deaths AS (
                SELECT COUNT(*) AS total, COUNT(*) FILTER (WHERE account_id = ?) AS personal FROM royal_titan_deaths
            ), record AS (
                SELECT p.display_name FROM royal_titan_completions c
                JOIN royal_titan_participants p USING (encounter_id)
                WHERE c.elapsed_ticks = (SELECT best FROM global_stats)
                ORDER BY c.elapsed_ticks, c.completed_at, c.encounter_id, p.account_id LIMIT 1
            )
            SELECT personal.kills, deaths.personal, personal.best, global_stats.kills, deaths.total,
                   global_stats.best, (SELECT display_name FROM record)
            FROM personal CROSS JOIN global_stats CROSS JOIN deaths
        """
            )
            .use {
                it.setInt(1, accountId)
                it.setInt(2, accountId)
                it.executeQuery().use { rows ->
                    check(rows.next())
                    RoyalTitansStatisticsSnapshot(
                        rows.getInt(1),
                        rows.getInt(2),
                        rows.getObject(3, Integer::class.java)?.toInt(),
                        rows.getInt(4),
                        rows.getInt(5),
                        rows.getObject(6, Integer::class.java)?.toInt(),
                        rows.getString(7),
                    )
                }
            }
}

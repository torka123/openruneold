package org.rsmod.api.account.character.rewards

import com.fasterxml.jackson.databind.ObjectMapper
import dev.or2.central.db.FlywayMigrator
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.*
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.rsmod.api.account.character.main.CharacterAccountApplier
import org.rsmod.api.account.character.main.CharacterAccountRepository
import org.rsmod.api.db.DatabaseConnection
import org.rsmod.map.CoordGrid

/**
 * S04 typed quantity observation from docs/bosses/nex/NEX_OSRS_AUDIT.md. Native Flyway runs before
 * fixture DDL/rows in an isolated logs PostgreSQL cluster. The public repository reads real V43
 * allocations and V44 claims through one observed native statement with its explicit timeout. Own
 * uncommitted consumption is provisional; another backend observes only its SQL snapshot. Neither
 * an unclaimed count nor an empty result establishes ground existence, eligibility, expiration,
 * reservation, inventory delivery or gameplay reconciliation.
 */
class NpcRewardAllocationBalanceTest {
    @Test
    fun `balance detaches exact source keys and validates Long quantities before narrowing`() {
        val duplicate = NpcRewardAllocation.Item("obj.coins", 10, 12345)
        val maximum = NpcRewardAllocation.Item("obj.airrune", Int.MAX_VALUE, 77)
        val allocation =
            NpcRewardAllocation(
                UUID.randomUUID(),
                "npc.nex",
                listOf(
                    recipient(1, 12, listOf(maximum), Int.MAX_VALUE),
                    recipient(1, 11, listOf(duplicate, duplicate), 100),
                    recipient(2, 13, emptyList(), 0),
                ),
            )
        val claimed =
            mutableMapOf((12 to 0) to Int.MAX_VALUE.toLong(), (11 to 1) to 0L, (11 to 0) to 3L)
        val balance = NpcRewardAllocationBalance(allocation, claimed)
        val equivalent = NpcRewardAllocationBalance(allocation, claimed.toMap())
        claimed.clear()
        assertEquals(equivalent, balance)
        assertEquals(equivalent.hashCode(), balance.hashCode())
        assertEquals(allocation, balance.allocation)
        assertEquals(
            listOf(11 to 0, 11 to 1, 12 to 0),
            balance.items.map { it.characterId to it.ordinal },
        )
        assertEquals(listOf(duplicate, duplicate, maximum), balance.items.map { it.allocated })
        assertEquals(listOf(3, 0, Int.MAX_VALUE), balance.items.map { it.claimedCount })
        assertEquals(listOf(7, 10, 0), balance.items.map { it.unclaimedCount })
        assertTrue(balance.allocation.recipients.last().items.isEmpty())
        assertFailsWith<UnsupportedOperationException> {
            (balance.items as MutableList<NpcRewardAllocationBalance.Item>).clear()
        }
        val valid = mapOf((11 to 0) to 0L, (11 to 1) to 0L, (12 to 0) to 0L)
        val invalid =
            listOf(
                emptyMap(),
                valid - (11 to 1),
                valid + ((99 to 0) to 0L),
                valid + ((13 to 0) to 0L),
                valid + ((11 to 0) to -1L),
                valid + ((11 to 0) to 11L),
                valid + ((12 to 0) to (Int.MAX_VALUE.toLong() + 1)),
                valid + ((12 to 0) to Long.MAX_VALUE),
            )
        for (counts in invalid) assertFailsWith<IllegalArgumentException> {
            NpcRewardAllocationBalance(allocation, counts)
        }
        val empty = NpcRewardAllocation(UUID.randomUUID(), "npc.nex", emptyList())
        val emptyBalance = NpcRewardAllocationBalance(empty, emptyMap())
        assertEquals(empty, emptyBalance.allocation)
        assertTrue(emptyBalance.items.isEmpty())
        assertFailsWith<IllegalArgumentException> {
            NpcRewardAllocationBalance(empty, mapOf((11 to 0) to 0L))
        }
    }

    @Test
    fun `native one-statement balance preserves metadata and observes committed and provisional quantities`() {
        val logs = Files.createDirectories(Path.of("logs")).toAbsolutePath().normalize()
        val directory = Files.createTempDirectory(logs, "npc-reward-balance-test-").normalize()
        check(directory.startsWith(logs))
        val mapper = mock(ObjectMapper::class.java)
        val applier = mock(CharacterAccountApplier::class.java)
        val repository = CharacterAccountRepository(mapper, applier)
        EmbeddedPostgres.builder()
            .setDataDirectory(directory.resolve("data"))
            .setOverrideWorkingDirectory(directory.resolve("binaries").toFile())
            .setCleanDataDirectory(false)
            .setPort(0)
            .start()
            .use { postgres ->
                val source = postgres.postgresDatabase
                FlywayMigrator.migrate(source)
                val people = seedCharacters(source)
                val duplicate = NpcRewardAllocation.Item("obj.coins", 10, 12345)
                val rune = NpcRewardAllocation.Item("obj.airrune", 4, 9876)
                val other = NpcRewardAllocation.Item("obj.waterrune", 6, 4321)
                val allocation =
                    NpcRewardAllocation(
                        UUID.randomUUID(),
                        "npc.nex",
                        listOf(
                            recipient(
                                people.other.account,
                                people.other.character,
                                listOf(other),
                                Int.MAX_VALUE,
                            ),
                            recipient(
                                people.first.account,
                                people.second.character,
                                emptyList(),
                                0,
                            ),
                            recipient(
                                people.first.account,
                                people.first.character,
                                listOf(duplicate, duplicate, rune),
                                100,
                            ),
                        ),
                    )
                assertNull(read(source, repository, allocation.lifeId))
                source.tx { repository.recordNpcRewardAllocation(it, allocation) }
                val keys =
                    listOf(
                        people.first.character to 0,
                        people.first.character to 1,
                        people.first.character to 2,
                        people.other.character to 0,
                    )
                val original = assertNotNull(read(source, repository, allocation.lifeId))
                assertBalance(original, allocation, keys, listOf(0, 0, 0, 0), listOf(10, 10, 4, 6))
                assertEquals(
                    listOf(duplicate, duplicate, rune, other),
                    original.items.map { it.allocated },
                )
                assertTrue(original.allocation.recipients[1].items.isEmpty())

                fun request(vararg fragments: NpcRewardClaim.Fragment) =
                    NpcRewardClaim(
                        UUID.randomUUID(),
                        people.other.account,
                        people.other.character,
                        fragments.toList(),
                    )
                fun fragment(character: Int, ordinal: Int, count: Int) =
                    NpcRewardClaim.Fragment(allocation.lifeId, character, ordinal, count)
                val first =
                    request(
                        fragment(people.first.character, 0, 3),
                        fragment(people.first.character, 2, 2),
                        fragment(people.other.character, 0, 1),
                    )
                val committed = source.tx { repository.stageNpcRewardClaim(it, first, TIMEOUT) }
                assertIs<NpcRewardClaimWrite.Staged>(committed)
                assertEquals(NpcRewardClaimOutcome.COMMITTED, committed.receipt.outcome)
                val partial = assertNotNull(read(source, repository, allocation.lifeId))
                assertBalance(partial, allocation, keys, listOf(3, 0, 2, 1), listOf(7, 10, 2, 5))
                repeat(2) {
                    assertEquals(
                        committed.receipt,
                        assertIs<NpcRewardClaimWrite.Existing>(
                                source.tx { repository.stageNpcRewardClaim(it, first, TIMEOUT) }
                            )
                            .receipt,
                    )
                    assertEquals(
                        committed.receipt,
                        assertIs<NpcRewardClaimWrite.Existing>(
                                source.tx { repository.settleNpcRewardClaim(it, first, TIMEOUT) }
                            )
                            .receipt,
                    )
                    assertBalance(
                        assertNotNull(read(source, repository, allocation.lifeId)),
                        allocation,
                        keys,
                        listOf(3, 0, 2, 1),
                        listOf(7, 10, 2, 5),
                    )
                }
                val aborted =
                    request(
                        fragment(people.first.character, 1, 8),
                        fragment(people.other.character, 0, 2),
                    )
                val settled = source.tx { repository.settleNpcRewardClaim(it, aborted, TIMEOUT) }
                assertEquals(
                    NpcRewardClaimOutcome.ABORTED,
                    assertIs<NpcRewardClaimWrite.Staged>(settled).receipt.outcome,
                )
                assertEquals(
                    settled.receipt,
                    assertIs<NpcRewardClaimWrite.Existing>(
                            source.tx { repository.stageNpcRewardClaim(it, aborted, TIMEOUT) }
                        )
                        .receipt,
                )
                assertBalance(
                    assertNotNull(read(source, repository, allocation.lifeId)),
                    allocation,
                    keys,
                    listOf(3, 0, 2, 1),
                    listOf(7, 10, 2, 5),
                )

                val inflight =
                    request(
                        fragment(people.first.character, 1, 4),
                        fragment(people.first.character, 2, 2),
                        fragment(people.other.character, 0, 5),
                    )
                source.connection.use { retainedSnapshot ->
                    retainedSnapshot.autoCommit = false
                    retainedSnapshot.transactionIsolation = Connection.TRANSACTION_REPEATABLE_READ
                    assertBalance(
                        assertNotNull(read(retainedSnapshot, repository, allocation.lifeId)),
                        allocation,
                        keys,
                        listOf(3, 0, 2, 1),
                        listOf(7, 10, 2, 5),
                    )
                    try {
                        for (commit in listOf(false, true)) {
                            source.connection.use { writer ->
                                writer.begin()
                                try {
                                    assertIs<NpcRewardClaimWrite.Staged>(
                                        repository.stageNpcRewardClaim(
                                            DatabaseConnection(writer),
                                            inflight,
                                            TIMEOUT,
                                        )
                                    )
                                    assertBalance(
                                        assertNotNull(read(writer, repository, allocation.lifeId)),
                                        allocation,
                                        keys,
                                        listOf(3, 4, 4, 6),
                                        listOf(7, 6, 0, 0),
                                    )
                                    assertBalance(
                                        assertNotNull(read(source, repository, allocation.lifeId)),
                                        allocation,
                                        keys,
                                        listOf(3, 0, 2, 1),
                                        listOf(7, 10, 2, 5),
                                    )
                                    if (commit) writer.commit() else writer.rollback()
                                } finally {
                                    writer.rollback()
                                }
                            }
                            if (!commit)
                                assertBalance(
                                    assertNotNull(read(source, repository, allocation.lifeId)),
                                    allocation,
                                    keys,
                                    listOf(3, 0, 2, 1),
                                    listOf(7, 10, 2, 5),
                                )
                        }
                        // This read is an observation of its caller's snapshot, not a fresh
                        // reservation.
                        assertBalance(
                            assertNotNull(read(retainedSnapshot, repository, allocation.lifeId)),
                            allocation,
                            keys,
                            listOf(3, 0, 2, 1),
                            listOf(7, 10, 2, 5),
                        )
                    } finally {
                        retainedSnapshot.rollback()
                    }
                }
                assertBalance(
                    assertNotNull(read(source, repository, allocation.lifeId)),
                    allocation,
                    keys,
                    listOf(3, 4, 4, 6),
                    listOf(7, 6, 0, 0),
                )
                val completion =
                    request(
                        fragment(people.first.character, 0, 7),
                        fragment(people.first.character, 1, 6),
                    )
                assertIs<NpcRewardClaimWrite.Staged>(
                    source.tx { repository.stageNpcRewardClaim(it, completion, TIMEOUT) }
                )
                assertBalance(
                    assertNotNull(read(source, repository, allocation.lifeId)),
                    allocation,
                    keys,
                    listOf(10, 10, 4, 6),
                    listOf(0, 0, 0, 0),
                )
                assertEquals(
                    allocation,
                    source.tx { repository.findNpcRewardAllocation(it, allocation.lifeId) },
                )
                assertBalance(original, allocation, keys, listOf(0, 0, 0, 0), listOf(10, 10, 4, 6))
                assertBalance(partial, allocation, keys, listOf(3, 0, 2, 1), listOf(7, 10, 2, 5))

                val empty = NpcRewardAllocation(UUID.randomUUID(), "npc.nex_smokemage", emptyList())
                source.tx { repository.recordNpcRewardAllocation(it, empty) }
                val emptyBalance = assertNotNull(read(source, repository, empty.lifeId))
                assertEquals(empty, emptyBalance.allocation)
                assertTrue(emptyBalance.items.isEmpty())
                assertNull(read(source, repository, UUID.randomUUID()))
                source.connection.use { jdbc ->
                    for (timeout in listOf(0, -1)) {
                        assertFailsWith<IllegalArgumentException> {
                            repository.findNpcRewardAllocationBalance(
                                DatabaseConnection(jdbc),
                                allocation.lifeId,
                                timeout,
                            )
                        }
                    }
                }
            }
        verifyNoInteractions(mapper, applier)
    }

    private fun assertBalance(
        actual: NpcRewardAllocationBalance,
        allocation: NpcRewardAllocation,
        keys: List<Pair<Int, Int>>,
        claimed: List<Int>,
        unclaimed: List<Int>,
    ) {
        assertEquals(allocation, actual.allocation)
        assertEquals(keys, actual.items.map { it.characterId to it.ordinal })
        assertEquals(claimed, actual.items.map { it.claimedCount })
        assertEquals(unclaimed, actual.items.map { it.unclaimedCount })
    }

    private fun read(source: DataSource, repository: CharacterAccountRepository, life: UUID) =
        source.connection.use { read(it, repository, life) }

    private fun read(
        jdbc: Connection,
        repository: CharacterAccountRepository,
        life: UUID,
    ): NpcRewardAllocationBalance? {
        var preparations = 0
        var executions = 0
        val observed =
            object : Connection by jdbc {
                override fun prepareStatement(sql: String): PreparedStatement {
                    preparations++
                    val statement = jdbc.prepareStatement(sql)
                    return object : PreparedStatement by statement {
                        override fun executeQuery(): ResultSet {
                            executions++
                            assertEquals(TIMEOUT, statement.queryTimeout)
                            return statement.executeQuery()
                        }
                    }
                }
            }
        val result =
            repository.findNpcRewardAllocationBalance(DatabaseConnection(observed), life, TIMEOUT)
        assertEquals(1, preparations, "Metadata and consumption must share one native statement")
        assertEquals(1, executions)
        return result
    }

    private fun recipient(
        account: Int,
        character: Int,
        items: List<NpcRewardAllocation.Item>,
        duration: Int,
    ) =
        NpcRewardAllocation.Recipient(
            account,
            character,
            CoordGrid(2913 + character % 4, 5215),
            duration,
            items,
        )

    private data class Person(val account: Int, val character: Int)

    private data class People(val first: Person, val second: Person, val other: Person)

    private fun seedCharacters(source: DataSource): People =
        source.tx { db ->
            fun account(name: String): Int =
                db.prepareStatement(
                        "INSERT INTO accounts(account_name, password_hash) VALUES (?, 'test-only') RETURNING id"
                    )
                    .use {
                        it.setString(1, name)
                        it.executeQuery().use { rows ->
                            check(rows.next())
                            rows.getInt(1)
                        }
                    }
            fun character(account: Int, name: String): Person =
                db.prepareStatement(
                        "INSERT INTO account_characters(account_id, display_name) VALUES (?, ?) RETURNING id"
                    )
                    .use {
                        it.setInt(1, account)
                        it.setString(2, name)
                        it.executeQuery().use { rows ->
                            check(rows.next())
                            Person(account, rows.getInt(1))
                        }
                    }
            val first = account("balance-one")
            val other = account("balance-two")
            People(
                character(first, "Balance One"),
                character(first, "Balance Two"),
                character(other, "Balance Three"),
            )
        }

    private fun Connection.begin() {
        autoCommit = false
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    }

    private fun <T> DataSource.tx(block: (DatabaseConnection) -> T): T =
        connection.use { jdbc ->
            jdbc.begin()
            try {
                val result = block(DatabaseConnection(jdbc))
                jdbc.commit()
                result
            } catch (failure: Throwable) {
                jdbc.rollback()
                throw failure
            }
        }

    private companion object {
        private const val TIMEOUT = 5
    }
}

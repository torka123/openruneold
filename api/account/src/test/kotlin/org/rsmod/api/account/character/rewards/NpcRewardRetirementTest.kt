package org.rsmod.api.account.character.rewards

import com.fasterxml.jackson.databind.ObjectMapper
import dev.or2.central.db.FlywayMigrator
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.sql.DataSource
import kotlin.test.*
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.rsmod.api.account.character.main.CharacterAccountApplier
import org.rsmod.api.account.character.main.CharacterAccountRepository
import org.rsmod.api.db.DatabaseConnection
import org.rsmod.map.CoordGrid

/**
 * S04 SQL retirement mapping in docs/bosses/nex/NEX_OSRS_AUDIT.md. Native Flyway precedes fixture
 * rows in an isolated logs PostgreSQL cluster. Actual V43 rows arbitrate V44 claims and V45
 * retirements; pg_blocking_pids proves waiting before the first transaction is resolved. Injected
 * acknowledgement failures occur after physical commits, without replacing repository SQL.
 * EXPIRED/DESTROYED are caller-supplied reasons, not proof of elapsed time or a removed world item.
 * No inventory snapshot, ground lifecycle, NPC, runtime activation or canonical database is used.
 */
class NpcRewardRetirementTest {
    @Test
    fun `retirement values detach and sort exact fragments without an aggregate Int cap`() {
        val low = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val high = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff")
        val first = NpcRewardRetirement.Fragment(low, 1, 0, Int.MAX_VALUE)
        val second = NpcRewardRetirement.Fragment(low, 1, 1, Int.MAX_VALUE)
        val third = NpcRewardRetirement.Fragment(low, 2, 0, 1)
        val fourth = NpcRewardRetirement.Fragment(high, 1, 0, 1)
        val fragments = mutableListOf(fourth, third, second, first)
        val attempt = UUID.randomUUID()
        val request = NpcRewardRetirement(attempt, NpcRewardRetirementReason.EXPIRED, fragments)
        val expected =
            NpcRewardRetirement(
                attempt,
                NpcRewardRetirementReason.EXPIRED,
                listOf(first, second, third, fourth),
            )
        fragments.clear()
        assertEquals(expected, request)
        assertEquals(expected.hashCode(), request.hashCode())
        assertEquals(listOf(first, second, third, fourth), request.fragments)
        assertTrue(request.fragments.sumOf { it.count.toLong() } > Int.MAX_VALUE)
        assertFailsWith<UnsupportedOperationException> {
            (request.fragments as MutableList<NpcRewardRetirement.Fragment>).clear()
        }
        assertNotEquals(
            request,
            NpcRewardRetirement(attempt, NpcRewardRetirementReason.DESTROYED, request.fragments),
        )
        assertFailsWith<IllegalArgumentException> {
            NpcRewardRetirement(attempt, NpcRewardRetirementReason.EXPIRED, emptyList())
        }
        assertFailsWith<IllegalArgumentException> {
            NpcRewardRetirement(
                attempt,
                NpcRewardRetirementReason.EXPIRED,
                listOf(first, first.copy(count = 1)),
            )
        }
        for (invalid in listOf(0, -1)) {
            assertFailsWith<IllegalArgumentException> { first.copy(allocatedCharacterId = invalid) }
            assertFailsWith<IllegalArgumentException> { first.copy(count = invalid) }
        }
        assertFailsWith<IllegalArgumentException> { first.copy(itemOrdinal = -1) }
    }

    @Test
    fun `balance keeps unclaimed distinct from remaining and validates combined Long counts`() {
        val item = NpcRewardAllocation.Item("obj.coins", Int.MAX_VALUE, 123)
        val allocation =
            NpcRewardAllocation(
                UUID.randomUUID(),
                "npc.nex",
                listOf(
                    recipient(Person(1, 2), listOf(item, item)),
                    recipient(Person(1, 3), emptyList()),
                ),
            )
        val claimed = mutableMapOf((2 to 0) to (Int.MAX_VALUE - 1).toLong(), (2 to 1) to 0L)
        val retired = mutableMapOf((2 to 0) to 1L, (2 to 1) to Int.MAX_VALUE.toLong())
        val balance = NpcRewardAllocationBalance(allocation, claimed, retired)
        val equal = NpcRewardAllocationBalance(allocation, claimed.toMap(), retired.toMap())
        val legacy = NpcRewardAllocationBalance(allocation, claimed)
        claimed.clear()
        retired.clear()
        assertEquals(equal, balance)
        assertEquals(equal.hashCode(), balance.hashCode())
        assertEquals(allocation, balance.allocation)
        assertEquals(listOf(1, Int.MAX_VALUE), balance.items.map { it.unclaimedCount })
        assertEquals(listOf(1, Int.MAX_VALUE), balance.items.map { it.retiredCount })
        assertEquals(listOf(0, 0), balance.items.map { it.remainingCount })
        assertEquals(listOf(0, 0), legacy.items.map { it.retiredCount })
        assertEquals(listOf(1, Int.MAX_VALUE), legacy.items.map { it.remainingCount })
        assertNotEquals(legacy, balance)
        assertTrue(balance.allocation.recipients.last().items.isEmpty())
        assertFailsWith<UnsupportedOperationException> {
            (balance.items as MutableList<NpcRewardAllocationBalance.Item>).clear()
        }
        val zero = mapOf((2 to 0) to 0L, (2 to 1) to 0L)
        for (invalid in
            listOf(
                emptyMap(),
                zero - (2 to 1),
                zero + ((3 to 0) to 0L),
                zero + ((2 to 0) to -1L),
                zero + ((2 to 0) to (Int.MAX_VALUE.toLong() + 1)),
                zero + ((2 to 0) to Long.MAX_VALUE),
            )) {
            assertFailsWith<IllegalArgumentException> {
                NpcRewardAllocationBalance(allocation, zero, invalid)
            }
        }
        assertFailsWith<IllegalArgumentException> {
            NpcRewardAllocationBalance(
                allocation,
                zero + ((2 to 0) to Int.MAX_VALUE.toLong()),
                zero + ((2 to 0) to 1L),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            NpcRewardAllocationBalance.Item(2, 0, item, Int.MAX_VALUE, 1)
        }
    }

    @Test
    fun `native retirement accounting settles exact attempts and survives two isolated restarts`() {
        val logs = Files.createDirectories(Path.of("logs")).toAbsolutePath().normalize()
        val directory = Files.createTempDirectory(logs, "npc-reward-retirement-test-").normalize()
        check(directory.startsWith(logs))
        val builder =
            EmbeddedPostgres.builder()
                .setDataDirectory(directory.resolve("data"))
                .setOverrideWorkingDirectory(directory.resolve("binaries").toFile())
                .setCleanDataDirectory(false)
                .setPort(0)
        val mapper = mock(ObjectMapper::class.java)
        val applier = mock(CharacterAccountApplier::class.java)
        val repository = CharacterAccountRepository(mapper, applier)
        val receipts = linkedMapOf<UUID, NpcRewardRetirementReceipt>()
        var history = emptyList<String>()
        var counts = emptyList<Int>()
        builder.start().use { postgres ->
            val source = postgres.postgresDatabase
            FlywayMigrator.migrate(source)
            history = migrationHistory(source)
            assertMigration(source)
            FlywayMigrator.migrate(source)
            assertEquals(history, migrationHistory(source))
            val f = Fixture(source, repository, seedCharacters(source), receipts)
            assertAtomicReplay(f)
            assertSettlement(f)
            assertAcknowledgementAndTimeout(f)
            assertClaimRetirementContention(f)
            assertConsumptionConstraints(f)
            assertTransactionGuards(f)
            assertBalanceSnapshots(f)
            counts = ledgerCounts(source)
        }
        repeat(2) {
            builder.start().use { postgres ->
                val source = postgres.postgresDatabase
                FlywayMigrator.migrate(source)
                assertEquals(history, migrationHistory(source))
                for ((attempt, expected) in receipts) {
                    assertEquals(
                        expected,
                        source.tx { repository.findNpcRewardRetirement(it, attempt, TIMEOUT) },
                    )
                    val stage =
                        source.tx {
                            repository.stageNpcRewardRetirement(it, expected.request, TIMEOUT)
                        }
                    val settle =
                        source.tx {
                            repository.settleNpcRewardRetirement(it, expected.request, TIMEOUT)
                        }
                    assertEquals(
                        expected,
                        assertIs<NpcRewardRetirementWrite.Existing>(stage).receipt,
                    )
                    assertEquals(
                        expected,
                        assertIs<NpcRewardRetirementWrite.Existing>(settle).receipt,
                    )
                }
                assertEquals(counts, ledgerCounts(source), "Restart/replay cannot retire twice")
            }
        }
        verifyNoInteractions(mapper, applier)
    }

    private fun assertAtomicReplay(f: Fixture) {
        val allocation = f.allocate(listOf(item(10), item(6)))
        val first = fragment(allocation, 0, 3)
        val second = fragment(allocation, 1, 2)
        f.claim(listOf(first.copy(count = 4)))
        val request = retirement(listOf(second, first))
        val expected = assertIs<NpcRewardRetirementWrite.Staged>(f.stage(request)).receipt
        assertEquals(NpcRewardRetirementOutcome.COMMITTED, expected.outcome)
        assertEquals(request, expected.request)
        assertEquals(listOf(1, 2, 2), attemptCounts(f.source, request.attemptId))
        repeat(2) {
            assertEquals(
                expected,
                assertIs<NpcRewardRetirementWrite.Existing>(f.stage(request)).receipt,
            )
            assertEquals(
                expected,
                assertIs<NpcRewardRetirementWrite.Existing>(f.settle(request)).receipt,
            )
        }
        assertEquals(listOf(4L, 5L), consumed(f.source, allocation.lifeId))
        val conflicts =
            listOf(
                NpcRewardRetirement(
                    request.attemptId,
                    NpcRewardRetirementReason.DESTROYED,
                    request.fragments,
                ),
                NpcRewardRetirement(
                    request.attemptId,
                    request.reason,
                    listOf(first.copy(count = 2), second),
                ),
                NpcRewardRetirement(request.attemptId, request.reason, listOf(first)),
                NpcRewardRetirement(
                    request.attemptId,
                    request.reason,
                    listOf(first, second.copy(itemOrdinal = 2)),
                ),
            )
        for (conflict in conflicts) {
            assertFailsWith<IllegalStateException> { f.stage(conflict) }
            assertFailsWith<IllegalStateException> { f.settle(conflict) }
            assertEquals(expected, f.find(request.attemptId))
        }
        // The first source row exists; failure on the later ordinal must roll back the reserved
        // attempt and its already-inserted request fragments, without returning a partial receipt.
        val missing = retirement(listOf(first.copy(count = 1), second.copy(itemOrdinal = 99)))
        assertFailsWith<IllegalStateException> { f.stage(missing) }
        assertNull(f.find(missing.attemptId))
        assertEquals(listOf(0, 0, 0), attemptCounts(f.source, missing.attemptId))
        assertFailsWith<IllegalStateException> {
            f.stage(retirement(listOf(first.copy(count = 4))))
        }
        assertEquals(listOf(4L, 5L), consumed(f.source, allocation.lifeId))

        for (settle in listOf(false, true)) {
            val rollback = retirement(listOf(first.copy(count = 1)))
            val failure = IllegalStateException("caller fails after provisional retirement result")
            val thrown = assertFails {
                f.source.tx { db ->
                    val result =
                        if (settle) f.repository.settleNpcRewardRetirement(db, rollback, TIMEOUT)
                        else f.repository.stageNpcRewardRetirement(db, rollback, TIMEOUT)
                    assertIs<NpcRewardRetirementWrite.Staged>(result)
                    throw failure
                }
            }
            assertSame(failure, thrown)
            assertNull(f.find(rollback.attemptId))
            assertEquals(listOf(0, 0, 0), attemptCounts(f.source, rollback.attemptId))
        }
        assertEquals(listOf(4L, 5L), consumed(f.source, allocation.lifeId))

        val large = f.allocate(listOf(item(Int.MAX_VALUE), item(Int.MAX_VALUE)))
        f.stage(
            retirement(listOf(fragment(large, 0, Int.MAX_VALUE), fragment(large, 1, Int.MAX_VALUE)))
        )
        assertEquals(listOf(0L, 2L * Int.MAX_VALUE), consumed(f.source, large.lifeId))
    }

    private fun assertSettlement(f: Fixture) {
        for (commitOriginal in listOf(false, true)) {
            val allocation = f.allocate(listOf(item(5)))
            val request = retirement(listOf(fragment(allocation, 0, 3)))
            f.source.connection.use { original ->
                original.begin()
                try {
                    f.repository.stageNpcRewardRetirement(
                        DatabaseConnection(original),
                        request,
                        TIMEOUT,
                    )
                    assertNull(
                        f.find(request.attemptId),
                        "A SELECT miss cannot settle an inflight attempt",
                    )
                    val winner =
                        awaitBlocked(
                                f,
                                original,
                                { db ->
                                    f.repository.settleNpcRewardRetirement(db, request, TIMEOUT)
                                },
                            ) {
                                assertNull(f.find(request.attemptId))
                                if (commitOriginal) original.commit() else original.rollback()
                            }
                            .getOrThrow()
                    val expected =
                        NpcRewardRetirementReceipt(
                            request,
                            if (commitOriginal) NpcRewardRetirementOutcome.COMMITTED
                            else NpcRewardRetirementOutcome.ABORTED,
                        )
                    if (commitOriginal) assertIs<NpcRewardRetirementWrite.Existing>(winner)
                    else assertIs<NpcRewardRetirementWrite.Staged>(winner)
                    assertEquals(expected, winner.receipt)
                    f.remember(expected)
                    assertEquals(expected, f.find(request.attemptId))
                    assertEquals(
                        expected,
                        assertIs<NpcRewardRetirementWrite.Existing>(f.stage(request)).receipt,
                    )
                    assertEquals(
                        listOf(0L, if (commitOriginal) 3L else 0L),
                        consumed(f.source, allocation.lifeId),
                    )
                } finally {
                    original.rollback()
                }
            }
        }
        val absent =
            retirement(listOf(NpcRewardRetirement.Fragment(UUID.randomUUID(), Int.MAX_VALUE, 0, 1)))
        val aborted = assertIs<NpcRewardRetirementWrite.Staged>(f.settle(absent)).receipt
        assertEquals(NpcRewardRetirementOutcome.ABORTED, aborted.outcome)
        assertEquals(aborted, assertIs<NpcRewardRetirementWrite.Existing>(f.stage(absent)).receipt)
        assertEquals(listOf(1, 1, 0), attemptCounts(f.source, absent.attemptId))
        assertFailsWith<IllegalStateException> {
            f.stage(
                NpcRewardRetirement(
                    absent.attemptId,
                    NpcRewardRetirementReason.DESTROYED,
                    absent.fragments,
                )
            )
        }
    }

    private fun assertAcknowledgementAndTimeout(f: Fixture) {
        for (settlement in listOf(false, true)) {
            val allocation = f.allocate(listOf(item(4)))
            val request = retirement(listOf(fragment(allocation, 0, 4)))
            val lostAck = SQLException("test acknowledgement failed after physical commit")
            var provisional: NpcRewardRetirementReceipt? = null
            var commitCalls = 0
            f.source.connection.use { real ->
                real.begin()
                // Native once-path classification remains covered by GameDatabaseCommitOutcomeTest.
                val transport =
                    object : Connection by real {
                        override fun commit() {
                            commitCalls++
                            real.commit()
                            throw lostAck
                        }
                    }
                val thrown = assertFails {
                    val db = DatabaseConnection(transport)
                    val result =
                        if (settlement) f.repository.settleNpcRewardRetirement(db, request, TIMEOUT)
                        else f.repository.stageNpcRewardRetirement(db, request, TIMEOUT)
                    provisional = assertIs<NpcRewardRetirementWrite.Staged>(result).receipt
                    transport.commit()
                }
                assertSame(lostAck, thrown)
                transport.rollback()
                assertEquals(1, commitCalls)
            }
            val expected = assertNotNull(provisional)
            assertEquals(
                if (settlement) NpcRewardRetirementOutcome.ABORTED
                else NpcRewardRetirementOutcome.COMMITTED,
                expected.outcome,
            )
            assertEquals(expected, f.find(request.attemptId))
            repeat(2) {
                assertEquals(
                    expected,
                    assertIs<NpcRewardRetirementWrite.Existing>(f.settle(request)).receipt,
                )
                assertEquals(
                    expected,
                    assertIs<NpcRewardRetirementWrite.Existing>(f.stage(request)).receipt,
                )
            }
            assertEquals(
                listOf(1, 1, if (settlement) 0 else 1),
                attemptCounts(f.source, request.attemptId),
            )
            assertEquals(
                listOf(0L, if (settlement) 0L else 4L),
                consumed(f.source, allocation.lifeId),
            )
        }
        val allocation = f.allocate(listOf(item(2)))
        val request = retirement(listOf(fragment(allocation, 0, 2)))
        f.source.connection.use { original ->
            original.begin()
            try {
                f.repository.stageNpcRewardRetirement(
                    DatabaseConnection(original),
                    request,
                    TIMEOUT,
                )
                val result =
                    awaitBlocked(
                        f,
                        original,
                        { db -> f.repository.settleNpcRewardRetirement(db, request, 1) },
                    ) {
                        assertNull(f.find(request.attemptId))
                    }
                assertEquals("57014", assertIs<SQLException>(result.exceptionOrNull()).sqlState)
                assertNull(f.find(request.attemptId))
                assertEquals(listOf(0, 0, 0), attemptCounts(f.source, request.attemptId))
            } finally {
                original.rollback()
            }
        }
        assertNull(f.find(request.attemptId), "A timeout must not invent an ABORTED tombstone")
        assertIs<NpcRewardRetirementWrite.Staged>(f.stage(request))
    }

    private fun assertClaimRetirementContention(f: Fixture) {
        for (retirementFirst in listOf(false, true)) for (commitFirst in listOf(false, true)) {
            val allocation = f.allocate(listOf(item(10)))
            val firstRetirement = retirement(listOf(fragment(allocation, 0, 7)))
            val firstClaim = f.claimRequest(listOf(fragment(allocation, 0, 7)))
            val secondRetirement = retirement(listOf(fragment(allocation, 0, 4)))
            val secondClaim = f.claimRequest(listOf(fragment(allocation, 0, 4)))
            f.source.connection.use { first ->
                first.begin()
                try {
                    val db = DatabaseConnection(first)
                    val staged =
                        if (retirementFirst)
                            f.repository.stageNpcRewardRetirement(db, firstRetirement, TIMEOUT)
                        else f.repository.stageNpcRewardClaim(db, firstClaim, TIMEOUT)
                    // Distinct attempt identities and retirement's lack of picker locks leave
                    // only the common V43 source row as a possible cross-journal blocker.
                    val second =
                        awaitBlocked(
                            f,
                            first,
                            { waiting ->
                                if (retirementFirst)
                                    f.repository.stageNpcRewardClaim(waiting, secondClaim, TIMEOUT)
                                else
                                    f.repository.stageNpcRewardRetirement(
                                        waiting,
                                        secondRetirement,
                                        TIMEOUT,
                                    )
                            },
                        ) {
                            if (commitFirst) first.commit() else first.rollback()
                        }
                    if (commitFirst) {
                        assertIs<IllegalStateException>(second.exceptionOrNull())
                        if (retirementFirst)
                            f.remember(assertIs<NpcRewardRetirementWrite.Staged>(staged).receipt)
                        assertEquals(
                            listOf(0, 0, 0),
                            attemptCounts(f.source, secondRetirement.attemptId),
                        )
                        assertNull(
                            f.source.tx {
                                f.repository.findNpcRewardClaim(it, secondClaim.attemptId, TIMEOUT)
                            }
                        )
                        val remaining = fragment(allocation, 0, 3)
                        if (retirementFirst) f.claim(listOf(remaining))
                        else f.stage(retirement(listOf(remaining)))
                    } else {
                        val winner = second.getOrThrow()
                        if (retirementFirst) assertIs<NpcRewardClaimWrite.Staged>(winner)
                        else f.remember(assertIs<NpcRewardRetirementWrite.Staged>(winner).receipt)
                        assertNull(f.find(firstRetirement.attemptId))
                        assertNull(
                            f.source.tx {
                                f.repository.findNpcRewardClaim(it, firstClaim.attemptId, TIMEOUT)
                            }
                        )
                        val remaining = fragment(allocation, 0, 6)
                        if (retirementFirst) f.stage(retirement(listOf(remaining)))
                        else f.claim(listOf(remaining))
                    }
                    assertEquals(10L, consumed(f.source, allocation.lifeId).sum())
                    assertFailsWith<IllegalStateException> {
                        f.stage(retirement(listOf(fragment(allocation, 0, 1))))
                    }
                    assertFailsWith<IllegalStateException> {
                        f.claim(listOf(fragment(allocation, 0, 1)))
                    }
                } finally {
                    first.rollback()
                }
            }
        }
    }

    private fun assertConsumptionConstraints(f: Fixture) {
        val allocation = f.allocate(listOf(item(3)))
        val aborted = retirement(listOf(fragment(allocation, 0, 1)))
        f.settle(aborted)
        val abortFailure =
            assertFailsWith<SQLException> {
                f.source.tx { db ->
                    db.prepareStatement(
                            "INSERT INTO npc_reward_retirement_consumption " +
                                "(attempt_id, life_id, allocated_character_id, item_ordinal, count, outcome) " +
                                "VALUES (?, ?, ?, 0, 1, 'COMMITTED')"
                        )
                        .use {
                            it.setObject(1, aborted.attemptId)
                            it.setObject(2, allocation.lifeId)
                            it.setInt(3, allocation.recipients.single().characterId)
                            it.executeUpdate()
                        }
                }
            }
        assertEquals(
            "23503",
            abortFailure.sqlState,
            "Valid source/request cannot consume an ABORTED header",
        )
        assertEquals(listOf(1, 1, 0), attemptCounts(f.source, aborted.attemptId))
        val committed = retirement(listOf(fragment(allocation, 0, 2)))
        f.stage(committed)
        val countFailure =
            assertFailsWith<SQLException> {
                f.source.tx { db ->
                    // UPDATE avoids a duplicate primary key masking the exact-request-count FK.
                    db.prepareStatement(
                            "UPDATE npc_reward_retirement_consumption SET count = 3 WHERE attempt_id = ?"
                        )
                        .use {
                            it.setObject(1, committed.attemptId)
                            assertEquals(1, it.executeUpdate())
                        }
                }
            }
        assertEquals("23503", countFailure.sqlState)
        assertEquals(listOf(0L, 2L), consumed(f.source, allocation.lifeId))

        val missing =
            retirement(
                listOf(
                    NpcRewardRetirement.Fragment(UUID.randomUUID(), f.people.first.character, 0, 1)
                )
            )
        val missingFailure =
            assertFailsWith<SQLException> {
                f.source.tx { db ->
                    f.repository.settleNpcRewardRetirement(db, missing, TIMEOUT)
                    // Test-only SQL satisfies the header/request FKs to isolate the missing V43
                    // source.
                    db.prepareStatement(
                            "UPDATE npc_reward_retirement_attempts SET outcome = 'COMMITTED' WHERE attempt_id = ?"
                        )
                        .use {
                            it.setObject(1, missing.attemptId)
                            assertEquals(1, it.executeUpdate())
                        }
                    db.prepareStatement(
                            "INSERT INTO npc_reward_retirement_consumption " +
                                "(attempt_id, life_id, allocated_character_id, item_ordinal, count, outcome) " +
                                "SELECT attempt_id, life_id, allocated_character_id, item_ordinal, count, 'COMMITTED' " +
                                "FROM npc_reward_retirement_fragments WHERE attempt_id = ?"
                        )
                        .use {
                            it.setObject(1, missing.attemptId)
                            it.executeUpdate()
                        }
                }
            }
        assertEquals("23503", missingFailure.sqlState)
        assertNull(f.find(missing.attemptId))
    }

    private fun assertTransactionGuards(f: Fixture) {
        val request = retirement(listOf(fragment(f.allocate(listOf(item(1))), 0, 1)))
        val operations: List<(DatabaseConnection, Int) -> Any?> =
            listOf(
                { db, timeout -> f.repository.stageNpcRewardRetirement(db, request, timeout) },
                { db, timeout -> f.repository.settleNpcRewardRetirement(db, request, timeout) },
                { db, timeout ->
                    f.repository.findNpcRewardRetirement(db, request.attemptId, timeout)
                },
            )
        f.source.connection.use { jdbc ->
            for (operation in operations) {
                assertTrue(jdbc.autoCommit)
                assertFailsWith<IllegalStateException> {
                    operation(DatabaseConnection(jdbc), TIMEOUT)
                }
            }
            jdbc.autoCommit = false
            for (isolation in
                listOf(
                    Connection.TRANSACTION_REPEATABLE_READ,
                    Connection.TRANSACTION_SERIALIZABLE,
                )) {
                jdbc.transactionIsolation = isolation
                for (operation in operations) {
                    assertFailsWith<IllegalStateException> {
                        operation(DatabaseConnection(jdbc), TIMEOUT)
                    }
                    jdbc.rollback()
                }
            }
            jdbc.transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
            for (timeout in listOf(0, -1)) for (operation in operations) {
                assertFailsWith<IllegalArgumentException> {
                    operation(DatabaseConnection(jdbc), timeout)
                }
                jdbc.rollback()
            }
        }
        assertNull(f.find(request.attemptId))
        assertEquals(listOf(0, 0, 0), attemptCounts(f.source, request.attemptId))
    }

    private fun assertBalanceSnapshots(f: Fixture) {
        val coins = item(10, vars = 12345)
        val rune = item(6, "obj.airrune", 6789)
        val allocation =
            NpcRewardAllocation(
                UUID.randomUUID(),
                "npc.nex",
                listOf(
                    recipient(f.people.other, listOf(rune), Int.MAX_VALUE),
                    recipient(f.people.second, emptyList(), 0),
                    recipient(f.people.first, listOf(coins, coins), 100),
                ),
            )
        f.source.tx { f.repository.recordNpcRewardAllocation(it, allocation) }
        val keys =
            listOf(
                f.people.first.character to 0,
                f.people.first.character to 1,
                f.people.other.character to 0,
            )
        fun part(person: Person, ordinal: Int, count: Int) =
            NpcRewardRetirement.Fragment(allocation.lifeId, person.character, ordinal, count)
        fun observed() =
            f.source.connection.use {
                assertNotNull(readBalance(it, f.repository, allocation.lifeId))
            }
        val original = observed()
        assertBalance(
            original,
            allocation,
            keys,
            listOf(0, 0, 0),
            listOf(0, 0, 0),
            listOf(10, 10, 6),
        )
        f.claim(listOf(part(f.people.first, 0, 3)))
        val prior = observed()
        assertBalance(prior, allocation, keys, listOf(3, 0, 0), listOf(0, 0, 0), listOf(7, 10, 6))
        val inflight = retirement(listOf(part(f.people.first, 0, 4), part(f.people.other, 0, 2)))
        f.source.connection.use { snapshot ->
            snapshot.autoCommit = false
            snapshot.transactionIsolation = Connection.TRANSACTION_REPEATABLE_READ
            assertEquals(prior, readBalance(snapshot, f.repository, allocation.lifeId))
            try {
                for (commit in listOf(false, true)) {
                    f.source.connection.use { writer ->
                        writer.begin()
                        try {
                            val staged =
                                f.repository.stageNpcRewardRetirement(
                                    DatabaseConnection(writer),
                                    inflight,
                                    TIMEOUT,
                                )
                            val own =
                                assertNotNull(readBalance(writer, f.repository, allocation.lifeId))
                            assertBalance(
                                own,
                                allocation,
                                keys,
                                listOf(3, 0, 0),
                                listOf(4, 0, 2),
                                listOf(3, 10, 4),
                            )
                            assertEquals(
                                prior,
                                observed(),
                                "Another backend cannot see provisional retirement",
                            )
                            if (commit) {
                                writer.commit()
                                f.remember(staged.receipt)
                                assertEquals(own, observed())
                            } else {
                                writer.rollback()
                                assertEquals(prior, observed())
                            }
                            assertEquals(
                                prior,
                                readBalance(snapshot, f.repository, allocation.lifeId),
                                "RR retains its pre-retirement snapshot",
                            )
                        } finally {
                            writer.rollback()
                        }
                    }
                }
            } finally {
                snapshot.rollback()
            }
            assertEquals(
                observed(),
                readBalance(snapshot, f.repository, allocation.lifeId),
                "A new transaction sees committed retirement",
            )
            snapshot.rollback()
        }
        val partial = observed()
        repeat(2) {
            assertIs<NpcRewardRetirementWrite.Existing>(f.stage(inflight))
            assertIs<NpcRewardRetirementWrite.Existing>(f.settle(inflight))
            assertEquals(partial, observed())
        }
        val abort = retirement(listOf(part(f.people.first, 1, 9)))
        assertEquals(NpcRewardRetirementOutcome.ABORTED, f.settle(abort).receipt.outcome)
        assertIs<NpcRewardRetirementWrite.Existing>(f.stage(abort))
        assertEquals(partial, observed(), "An ABORTED request does not retire its fragments")
        val finish =
            retirement(
                listOf(
                    part(f.people.first, 0, 3),
                    part(f.people.first, 1, 10),
                    part(f.people.other, 0, 4),
                ),
                NpcRewardRetirementReason.DESTROYED,
            )
        f.stage(finish)
        assertBalance(
            observed(),
            allocation,
            keys,
            listOf(3, 0, 0),
            listOf(7, 10, 6),
            listOf(0, 0, 0),
        )
        assertEquals(listOf(7, 10, 6), observed().items.map { it.unclaimedCount })
        assertEquals(listOf(coins, coins, rune), observed().items.map { it.allocated })
        assertTrue(observed().allocation.recipients[1].items.isEmpty())
        assertEquals(
            allocation,
            f.source.tx { f.repository.findNpcRewardAllocation(it, allocation.lifeId) },
        )
        assertBalance(
            original,
            allocation,
            keys,
            listOf(0, 0, 0),
            listOf(0, 0, 0),
            listOf(10, 10, 6),
        )
        assertBalance(partial, allocation, keys, listOf(3, 0, 0), listOf(4, 0, 2), listOf(3, 10, 4))
        val empty = NpcRewardAllocation(UUID.randomUUID(), "npc.nex", emptyList())
        f.source.tx { f.repository.recordNpcRewardAllocation(it, empty) }
        f.source.connection.use { jdbc ->
            val emptyBalance = assertNotNull(readBalance(jdbc, f.repository, empty.lifeId))
            assertEquals(empty, emptyBalance.allocation)
            assertTrue(emptyBalance.items.isEmpty())
            assertNull(readBalance(jdbc, f.repository, UUID.randomUUID()))
        }
    }

    private fun assertBalance(
        actual: NpcRewardAllocationBalance,
        allocation: NpcRewardAllocation,
        keys: List<Pair<Int, Int>>,
        claimed: List<Int>,
        retired: List<Int>,
        remaining: List<Int>,
    ) {
        assertEquals(allocation, actual.allocation)
        assertEquals(keys, actual.items.map { it.characterId to it.ordinal })
        assertEquals(claimed, actual.items.map { it.claimedCount })
        assertEquals(retired, actual.items.map { it.retiredCount })
        assertEquals(remaining, actual.items.map { it.remainingCount })
    }

    private fun readBalance(
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
        assertEquals(
            1,
            preparations,
            "Allocation metadata and both journals share one native statement",
        )
        assertEquals(1, executions)
        return result
    }

    private fun <T> awaitBlocked(
        f: Fixture,
        original: Connection,
        operation: (DatabaseConnection) -> T,
        whenBlocked: () -> Unit,
    ): Result<T> {
        val executor = Executors.newSingleThreadExecutor()
        val waitingPid = CompletableFuture<Int>()
        try {
            val waiting =
                executor.submit(
                    Callable {
                        runCatching {
                            f.source.tx { db ->
                                waitingPid.complete(backend(db))
                                operation(db)
                            }
                        }
                    }
                )
            assertBlockedBy(
                f.source,
                waitingPid.get(5, TimeUnit.SECONDS),
                backend(DatabaseConnection(original)),
            )
            assertFalse(waiting.isDone)
            whenBlocked()
            return waiting.get(10, TimeUnit.SECONDS)
        } finally {
            executor.shutdown()
            check(executor.awaitTermination(15, TimeUnit.SECONDS)) {
                "Retirement test worker did not finish"
            }
        }
    }

    private inner class Fixture(
        val source: DataSource,
        val repository: CharacterAccountRepository,
        val people: People,
        val receipts: MutableMap<UUID, NpcRewardRetirementReceipt>,
    ) {
        fun allocate(items: List<NpcRewardAllocation.Item>): NpcRewardAllocation {
            val allocation =
                NpcRewardAllocation(
                    UUID.randomUUID(),
                    "npc.nex",
                    listOf(recipient(people.first, items)),
                )
            return source.tx { repository.recordNpcRewardAllocation(it, allocation) }
        }

        fun claimRequest(fragments: List<NpcRewardRetirement.Fragment>) =
            NpcRewardClaim(
                UUID.randomUUID(),
                people.other.account,
                people.other.character,
                fragments.map {
                    NpcRewardClaim.Fragment(
                        it.lifeId,
                        it.allocatedCharacterId,
                        it.itemOrdinal,
                        it.count,
                    )
                },
            )

        fun claim(fragments: List<NpcRewardRetirement.Fragment>): NpcRewardClaimWrite {
            val request = claimRequest(fragments)
            return source.tx { repository.stageNpcRewardClaim(it, request, TIMEOUT) }
        }

        fun stage(request: NpcRewardRetirement): NpcRewardRetirementWrite =
            source
                .tx { repository.stageNpcRewardRetirement(it, request, TIMEOUT) }
                .also { remember(it.receipt) }

        fun settle(request: NpcRewardRetirement): NpcRewardRetirementWrite =
            source
                .tx { repository.settleNpcRewardRetirement(it, request, TIMEOUT) }
                .also { remember(it.receipt) }

        fun find(attempt: UUID) =
            source.tx { repository.findNpcRewardRetirement(it, attempt, TIMEOUT) }

        fun remember(receipt: NpcRewardRetirementReceipt) {
            receipts[receipt.request.attemptId] = receipt
        }
    }

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
            val first = account("retirement-one")
            val other = account("retirement-two")
            People(
                character(first, "Retire One"),
                character(first, "Retire Two"),
                character(other, "Retire Three"),
            )
        }

    private fun recipient(
        person: Person,
        items: List<NpcRewardAllocation.Item>,
        duration: Int = 100,
    ) =
        NpcRewardAllocation.Recipient(
            person.account,
            person.character,
            CoordGrid(2913, 5215),
            duration,
            items,
        )

    private fun retirement(
        fragments: List<NpcRewardRetirement.Fragment>,
        reason: NpcRewardRetirementReason = NpcRewardRetirementReason.EXPIRED,
    ) = NpcRewardRetirement(UUID.randomUUID(), reason, fragments)

    private fun fragment(allocation: NpcRewardAllocation, ordinal: Int, count: Int) =
        NpcRewardRetirement.Fragment(
            allocation.lifeId,
            allocation.recipients.single().characterId,
            ordinal,
            count,
        )

    private fun item(count: Int, key: String = "obj.coins", vars: Int = 0) =
        NpcRewardAllocation.Item(key, count, vars)

    private fun backend(db: DatabaseConnection): Int =
        db.prepareStatement("SELECT pg_backend_pid()").use { statement ->
            statement.executeQuery().use { rows ->
                check(rows.next())
                rows.getInt(1)
            }
        }

    private fun assertBlockedBy(source: DataSource, waiting: Int, blocking: Int) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        source.connection.use { jdbc ->
            jdbc.prepareStatement("SELECT ? = ANY(pg_blocking_pids(?))").use { statement ->
                statement.setInt(1, blocking)
                statement.setInt(2, waiting)
                while (System.nanoTime() < deadline) {
                    val blocked =
                        statement.executeQuery().use { rows ->
                            check(rows.next())
                            rows.getBoolean(1)
                        }
                    if (blocked) return
                    Thread.sleep(10)
                }
            }
        }
        fail("Retirement/claim backend $waiting never waited on original backend $blocking")
    }

    private fun assertMigration(source: DataSource) {
        source.connection.use { jdbc ->
            jdbc
                .prepareStatement(
                    "SELECT script, checksum, success FROM flyway_schema_history WHERE version::integer = 45"
                )
                .use {
                    it.executeQuery().use { rows ->
                        assertTrue(rows.next())
                        assertEquals("V45__npc_reward_retirements.sql", rows.getString(1))
                        assertNotNull(rows.getObject(2))
                        assertTrue(rows.getBoolean(3))
                        assertFalse(rows.next())
                    }
                }
        }
    }

    private fun migrationHistory(source: DataSource): List<String> =
        source.tx { db ->
            db.prepareStatement(
                    "SELECT row_to_json(history)::text FROM flyway_schema_history history ORDER BY installed_rank"
                )
                .use { statement ->
                    statement.executeQuery().use { rows ->
                        buildList { while (rows.next()) add(rows.getString(1)) }
                    }
                }
        }

    private fun consumed(source: DataSource, life: UUID): List<Long> =
        source.tx { db ->
            listOf("npc_reward_claim_consumption", "npc_reward_retirement_consumption").map { table
                ->
                db.prepareStatement("SELECT COALESCE(SUM(count), 0) FROM $table WHERE life_id = ?")
                    .use {
                        it.setObject(1, life)
                        it.executeQuery().use { rows ->
                            check(rows.next())
                            rows.getLong(1)
                        }
                    }
            }
        }

    private fun attemptCounts(source: DataSource, attempt: UUID): List<Int> =
        source.tx { db ->
            RETIREMENT_TABLES.map { table ->
                db.prepareStatement("SELECT count(*) FROM $table WHERE attempt_id = ?").use {
                    it.setObject(1, attempt)
                    it.executeQuery().use { rows ->
                        check(rows.next())
                        rows.getInt(1)
                    }
                }
            }
        }

    private fun ledgerCounts(source: DataSource): List<Int> =
        source.tx { db ->
            (RETIREMENT_TABLES +
                    listOf(
                        "npc_reward_claim_attempts",
                        "npc_reward_claim_fragments",
                        "npc_reward_claim_consumption",
                    ))
                .map { table ->
                    db.prepareStatement("SELECT count(*) FROM $table").use { statement ->
                        statement.executeQuery().use { rows ->
                            check(rows.next())
                            rows.getInt(1)
                        }
                    }
                }
        }

    private companion object {
        private const val TIMEOUT = 5
        private val RETIREMENT_TABLES =
            listOf(
                "npc_reward_retirement_attempts",
                "npc_reward_retirement_fragments",
                "npc_reward_retirement_consumption",
            )

        private fun Connection.begin() {
            autoCommit = false
            transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
            createStatement().use { it.execute("SET LOCAL statement_timeout = '10s'") }
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
    }
}

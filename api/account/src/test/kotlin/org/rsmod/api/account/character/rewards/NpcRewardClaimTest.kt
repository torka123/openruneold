package org.rsmod.api.account.character.rewards

import com.fasterxml.jackson.databind.ObjectMapper
import dev.or2.central.db.FlywayMigrator
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
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
 * S04 SQL-only claim mapping in docs/bosses/nex/NEX_OSRS_AUDIT.md. Native Flyway initializes a
 * fresh isolated logs cluster before fixture rows; two restarts reopen only that cluster. Real
 * PostgreSQL pg_blocking_pids establishes unique-key/item-lock waiting before either transaction
 * completes. A SELECT miss is unknown, and every repository return remains provisional until the
 * test's transaction owner commits. No account snapshot, inventory, NPC/RNG, ground eligibility,
 * gameplay hold or canonical database participates in these tests.
 */
class NpcRewardClaimTest {
    @Test
    fun `claim values detach and sort UUID text then character and ordinal with bounded quantities`() {
        val early = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val late = UUID.fromString("80000000-0000-0000-0000-000000000001")
        val sorted =
            listOf(
                NpcRewardClaim.Fragment(early, 1, 0, 2),
                NpcRewardClaim.Fragment(early, 1, 1, 3),
                NpcRewardClaim.Fragment(early, 2, 0, 4),
                NpcRewardClaim.Fragment(late, 1, 0, 5),
            )
        val mutable = sorted.reversed().toMutableList()
        val attempt = UUID(0L, 44L)
        val claim = NpcRewardClaim(attempt, 7, 8, mutable)
        val equivalent = NpcRewardClaim(attempt, 7, 8, sorted)
        mutable.clear()
        assertEquals(sorted, claim.fragments)
        assertEquals(equivalent, claim)
        assertEquals(equivalent.hashCode(), claim.hashCode())
        assertFailsWith<UnsupportedOperationException> {
            (claim.fragments as MutableList<NpcRewardClaim.Fragment>).clear()
        }
        assertEquals(sorted, claim.fragments)
        val receipt = NpcRewardClaimReceipt(claim, NpcRewardClaimOutcome.COMMITTED)
        assertEquals(receipt, NpcRewardClaimReceipt(equivalent, NpcRewardClaimOutcome.COMMITTED))
        assertNotEquals(receipt, NpcRewardClaimReceipt(claim, NpcRewardClaimOutcome.ABORTED))
        assertEquals(receipt, NpcRewardClaimWrite.Staged(receipt).receipt)
        assertEquals(receipt, NpcRewardClaimWrite.Existing(receipt).receipt)
        assertFailsWith<IllegalArgumentException> { NpcRewardClaim(attempt, 0, 8, sorted) }
        assertFailsWith<IllegalArgumentException> { NpcRewardClaim(attempt, 7, -1, sorted) }
        assertFailsWith<IllegalArgumentException> { NpcRewardClaim(attempt, 7, 8, emptyList()) }
        assertFailsWith<IllegalArgumentException> {
            NpcRewardClaim(attempt, 7, 8, listOf(sorted[0], sorted[0].copy(count = 1)))
        }
        assertFailsWith<IllegalArgumentException> { sorted[0].copy(allocatedCharacterId = 0) }
        assertFailsWith<IllegalArgumentException> { sorted[0].copy(itemOrdinal = -1) }
        for (count in listOf(0, -1)) assertFailsWith<IllegalArgumentException> {
            sorted[0].copy(count = count)
        }
        val maximum = sorted[0].copy(count = Int.MAX_VALUE)
        assertEquals(listOf(maximum), NpcRewardClaim(attempt, 7, 8, listOf(maximum)).fragments)
        assertFailsWith<IllegalArgumentException> {
            NpcRewardClaim(attempt, 7, 8, listOf(maximum, sorted[1].copy(count = 1)))
        }
        assertFailsWith<IllegalArgumentException> {
            NpcRewardClaim(attempt, 7, 8, listOf(maximum, sorted[1].copy(count = Int.MAX_VALUE)))
        }
    }

    @Test
    fun `native claim transactions settle uncertain attempts and survive two isolated restarts`() {
        val logs = Files.createDirectories(Path.of("logs")).toAbsolutePath().normalize()
        val directory = Files.createTempDirectory(logs, "npc-reward-claim-test-").normalize()
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
        val receipts = linkedMapOf<UUID, NpcRewardClaimReceipt>()
        var expectedMigrations = emptyList<String>()
        var counts = emptyList<Int>()
        builder.start().use { postgres ->
            val source = postgres.postgresDatabase
            FlywayMigrator.migrate(source)
            expectedMigrations = migrationHistory(source)
            assertTrue(expectedMigrations.isNotEmpty())
            assertMigration(source, 43, "V43__npc_reward_allocations.sql")
            assertMigration(source, 44, "V44__npc_reward_claims.sql")
            FlywayMigrator.migrate(source)
            assertEquals(expectedMigrations, migrationHistory(source))
            val f = Fixture(source, repository, seedCharacters(source), receipts)
            assertReplayQuantityAndOwnership(f)
            assertSettlementWaits(f)
            assertAbortFirst(f)
            assertCallerRollback(f)
            assertRemainingQuantityAfterWait(f)
            assertTimeoutRemainsUnknown(f)
            assertCommittedLostAcknowledgements(f)
            assertConsumptionConstraints(f)
            assertTransactionGuards(f)
            counts = ledgerCounts(source)
        }
        repeat(2) {
            builder.start().use { postgres ->
                val source = postgres.postgresDatabase
                FlywayMigrator.migrate(source)
                assertEquals(expectedMigrations, migrationHistory(source))
                for ((attempt, expected) in receipts) {
                    assertEquals(
                        expected,
                        source.tx { repository.findNpcRewardClaim(it, attempt, TIMEOUT) },
                    )
                    val stage =
                        source.tx { repository.stageNpcRewardClaim(it, expected.request, TIMEOUT) }
                    val settle =
                        source.tx { repository.settleNpcRewardClaim(it, expected.request, TIMEOUT) }
                    assertEquals(expected, assertIs<NpcRewardClaimWrite.Existing>(stage).receipt)
                    assertEquals(expected, assertIs<NpcRewardClaimWrite.Existing>(settle).receipt)
                }
                assertEquals(
                    counts,
                    ledgerCounts(source),
                    "Restart/replay writes no second consumption",
                )
                assertNull(
                    source.tx { repository.findNpcRewardClaim(it, UUID.randomUUID(), TIMEOUT) }
                )
            }
        }
        verifyNoInteractions(mapper, applier)
    }

    private fun assertReplayQuantityAndOwnership(f: Fixture) {
        val first = f.allocate(listOf(item(10), item(7, "obj.airrune", 123)))
        val second = f.allocate(listOf(item(9, "obj.firerune", 456)), f.people.second)
        // Public-ground provenance may belong to another account; this primitive is quantity-only.
        val request =
            f.claim(
                listOf(fragment(first, count = 3), fragment(second, count = 2)),
                picker = f.people.other,
            )
        assertNull(f.find(request.attemptId))
        val receipt = f.commitStage(request)
        assertEquals(listOf(1, 2, 2), attemptCounts(f.source, request.attemptId))
        val reordered =
            NpcRewardClaim(
                request.attemptId,
                request.pickerAccountId,
                request.pickerCharacterId,
                request.fragments.reversed(),
            )
        assertEquals(receipt, assertIs<NpcRewardClaimWrite.Existing>(f.stage(reordered)).receipt)
        assertEquals(receipt, assertIs<NpcRewardClaimWrite.Existing>(f.settle(request)).receipt)
        val baseline = ledgerCounts(f.source)
        val changes =
            listOf(
                NpcRewardClaim(
                    request.attemptId,
                    f.people.first.account,
                    request.pickerCharacterId,
                    request.fragments,
                ),
                NpcRewardClaim(
                    request.attemptId,
                    request.pickerAccountId,
                    f.people.first.character,
                    request.fragments,
                ),
                copyClaim(request, fragments = request.fragments.dropLast(1)),
                copyClaim(
                    request,
                    fragments =
                        listOf(request.fragments[0].copy(count = 4)) + request.fragments.drop(1),
                ),
                copyClaim(
                    request,
                    fragments =
                        listOf(request.fragments[0].copy(itemOrdinal = 1)) +
                            request.fragments.drop(1),
                ),
                copyClaim(
                    request,
                    fragments =
                        listOf(request.fragments[0].copy(lifeId = UUID.randomUUID())) +
                            request.fragments.drop(1),
                ),
                copyClaim(
                    request,
                    fragments =
                        listOf(
                            request.fragments[0].copy(
                                allocatedCharacterId = f.people.other.character
                            )
                        ) + request.fragments.drop(1),
                ),
            )
        for (changed in changes) {
            assertFailsWith<IllegalStateException> { f.stage(changed) }
            assertFailsWith<IllegalStateException> { f.settle(changed) }
            assertEquals(receipt, f.find(request.attemptId))
            assertEquals(baseline, ledgerCounts(f.source))
        }

        val invalid =
            listOf(
                f.claim(listOf(fragment(first, count = 8))),
                f.claim(listOf(fragment(first, count = 1).copy(itemOrdinal = 999))),
                f.claim(
                    listOf(fragment(first, count = 1).copy(allocatedCharacterId = Int.MAX_VALUE))
                ),
                f.claim(listOf(fragment(first, count = 1).copy(lifeId = UUID.randomUUID()))),
                f.claim(
                    listOf(fragment(first, count = 1)),
                    Person(f.people.other.account, f.people.first.character),
                ),
                f.claim(
                    listOf(fragment(first, count = 1)),
                    Person(Int.MAX_VALUE, f.people.first.character),
                ),
                f.claim(
                    listOf(fragment(first, count = 1)),
                    Person(f.people.first.account, Int.MAX_VALUE),
                ),
                f.claim(
                    listOf(
                        fragment(first, count = 1),
                        fragment(first, count = 1).copy(itemOrdinal = 999),
                    )
                ),
            )
        for (requestFailure in invalid) {
            assertFailsWith<IllegalStateException> { f.stage(requestFailure) }
            assertNull(f.find(requestFailure.attemptId))
            assertEquals(listOf(0, 0, 0), attemptCounts(f.source, requestFailure.attemptId))
            assertEquals(baseline, ledgerCounts(f.source))
        }
        f.commitStage(
            f.claim(
                listOf(fragment(first, count = 7), fragment(first, count = 7).copy(itemOrdinal = 1))
            )
        )
        assertEquals(first, f.source.tx { f.repository.findNpcRewardAllocation(it, first.lifeId) })
    }

    private fun assertSettlementWaits(f: Fixture) {
        for (commitOriginal in listOf(false, true)) {
            val allocation = f.allocate(listOf(item(5)))
            val request = f.claim(listOf(fragment(allocation, count = 3)))
            f.source.connection.use { original ->
                original.begin()
                try {
                    val staged =
                        f.repository.stageNpcRewardClaim(
                            DatabaseConnection(original),
                            request,
                            TIMEOUT,
                        )
                    assertEquals(
                        NpcRewardClaimOutcome.COMMITTED,
                        assertIs<NpcRewardClaimWrite.Staged>(staged).receipt.outcome,
                    )
                    assertNull(
                        f.find(request.attemptId),
                        "SELECT miss cannot settle an uncommitted original",
                    )
                    val winner =
                        awaitBlockedWrite(
                                f,
                                original,
                                { db -> f.repository.settleNpcRewardClaim(db, request, TIMEOUT) },
                            ) {
                                assertNull(f.find(request.attemptId))
                                if (commitOriginal) original.commit() else original.rollback()
                            }
                            .getOrThrow()
                    val expected =
                        NpcRewardClaimReceipt(
                            request,
                            if (commitOriginal) NpcRewardClaimOutcome.COMMITTED
                            else NpcRewardClaimOutcome.ABORTED,
                        )
                    if (commitOriginal) assertIs<NpcRewardClaimWrite.Existing>(winner)
                    else assertIs<NpcRewardClaimWrite.Staged>(winner)
                    assertEquals(expected, winner.receipt)
                    f.remember(expected)
                    assertEquals(expected, f.find(request.attemptId))
                    assertEquals(
                        listOf(1, 1, if (commitOriginal) 1 else 0),
                        attemptCounts(f.source, request.attemptId),
                    )
                    assertEquals(
                        expected,
                        assertIs<NpcRewardClaimWrite.Existing>(f.stage(request)).receipt,
                    )
                } finally {
                    original.rollback()
                }
            }
        }
    }

    private fun assertAbortFirst(f: Fixture) {
        val allocation = f.allocate(listOf(item(5)))
        val ordinary = f.claim(listOf(fragment(allocation, count = 5)))
        val missing =
            NpcRewardClaim(
                UUID.randomUUID(),
                Int.MAX_VALUE,
                Int.MAX_VALUE,
                listOf(NpcRewardClaim.Fragment(UUID.randomUUID(), Int.MAX_VALUE, 0, 5)),
            )
        for (request in listOf(ordinary, missing)) {
            val write = assertIs<NpcRewardClaimWrite.Staged>(f.settle(request))
            assertEquals(NpcRewardClaimOutcome.ABORTED, write.receipt.outcome)
            f.remember(write.receipt)
            assertEquals(
                write.receipt,
                assertIs<NpcRewardClaimWrite.Existing>(f.stage(request)).receipt,
            )
            assertEquals(
                write.receipt,
                assertIs<NpcRewardClaimWrite.Existing>(f.settle(request)).receipt,
            )
            val changed = copyClaim(request, listOf(request.fragments.single().copy(count = 4)))
            assertFailsWith<IllegalStateException> { f.stage(changed) }
            assertFailsWith<IllegalStateException> { f.settle(changed) }
            assertEquals(write.receipt, f.find(request.attemptId))
            assertEquals(listOf(1, 1, 0), attemptCounts(f.source, request.attemptId))
        }
        f.commitStage(f.claim(listOf(fragment(allocation, count = 5))))
    }

    private fun assertCallerRollback(f: Fixture) {
        for (settlement in listOf(false, true)) {
            val allocation = f.allocate(listOf(item(6)))
            val request = f.claim(listOf(fragment(allocation, count = 6)))
            val failure = IllegalStateException("caller failed after provisional repository result")
            assertSame(
                failure,
                assertFails {
                    f.source.tx { db ->
                        val provisional =
                            if (settlement) f.repository.settleNpcRewardClaim(db, request, TIMEOUT)
                            else f.repository.stageNpcRewardClaim(db, request, TIMEOUT)
                        assertIs<NpcRewardClaimWrite.Staged>(provisional)
                        assertNull(f.find(request.attemptId))
                        throw failure
                    }
                },
            )
            assertNull(f.find(request.attemptId))
            assertEquals(listOf(0, 0, 0), attemptCounts(f.source, request.attemptId))
            f.commitStage(request)
        }
    }

    private fun assertRemainingQuantityAfterWait(f: Fixture) {
        val allocation = f.allocate(listOf(item(10)))
        val first = f.claim(listOf(fragment(allocation, count = 7)))
        val second = f.claim(listOf(fragment(allocation, count = 4)), picker = f.people.other)
        f.source.connection.use { original ->
            original.begin()
            try {
                val staged =
                    f.repository.stageNpcRewardClaim(DatabaseConnection(original), first, TIMEOUT)
                // Different attempt and picker rows isolate this wait to their common V43 item.
                val loser =
                    awaitBlockedWrite(
                        f,
                        original,
                        { db -> f.repository.stageNpcRewardClaim(db, second, TIMEOUT) },
                    ) {
                        original.commit()
                    }
                assertIs<IllegalStateException>(loser.exceptionOrNull())
                f.remember(staged.receipt)
                assertNull(f.find(second.attemptId))
                assertEquals(listOf(0, 0, 0), attemptCounts(f.source, second.attemptId))
                f.commitStage(
                    f.claim(listOf(fragment(allocation, count = 3)), picker = f.people.other)
                )
                assertFailsWith<IllegalStateException> {
                    f.stage(f.claim(listOf(fragment(allocation, count = 1))))
                }
                assertEquals(10L, consumed(f.source, allocation.lifeId))
            } finally {
                original.rollback()
            }
        }
    }

    private fun assertTimeoutRemainsUnknown(f: Fixture) {
        val allocation = f.allocate(listOf(item(5)))
        val request = f.claim(listOf(fragment(allocation, count = 5)))
        f.source.connection.use { original ->
            original.begin()
            try {
                f.repository.stageNpcRewardClaim(DatabaseConnection(original), request, TIMEOUT)
                val timedOut =
                    awaitBlockedWrite(
                        f,
                        original,
                        { db -> f.repository.settleNpcRewardClaim(db, request, 1) },
                    ) {
                        assertNull(f.find(request.attemptId))
                        // Keep the original transaction unresolved until the waiter's native
                        // timeout.
                    }
                val failure = assertIs<SQLException>(timedOut.exceptionOrNull())
                assertEquals("57014", failure.sqlState)
                assertNull(f.find(request.attemptId))
                assertEquals(listOf(0, 0, 0), attemptCounts(f.source, request.attemptId))
            } finally {
                original.rollback()
            }
        }
        assertNull(f.find(request.attemptId), "Timeout must not invent an ABORTED tombstone")
        f.commitStage(request)
    }

    private fun assertCommittedLostAcknowledgements(f: Fixture) {
        for (settlement in listOf(false, true)) {
            val allocation = f.allocate(listOf(item(4)))
            val request = f.claim(listOf(fragment(allocation, count = 4)))
            val lostAck = SQLException("test receipt committed but acknowledgement was lost")
            var provisional: NpcRewardClaimReceipt? = null
            var commitCalls = 0
            f.source.connection.use { real ->
                real.begin()
                // Native once-path classification is covered by GameDatabaseCommitOutcomeTest.
                // This injects acknowledgement failure after a physical PostgreSQL commit.
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
                    val write =
                        if (settlement) f.repository.settleNpcRewardClaim(db, request, TIMEOUT)
                        else f.repository.stageNpcRewardClaim(db, request, TIMEOUT)
                    provisional = assertIs<NpcRewardClaimWrite.Staged>(write).receipt
                    transport.commit()
                }
                assertSame(lostAck, thrown)
                transport.rollback()
                assertEquals(1, commitCalls)
            }
            val expected = assertNotNull(provisional)
            assertEquals(
                if (settlement) NpcRewardClaimOutcome.ABORTED else NpcRewardClaimOutcome.COMMITTED,
                expected.outcome,
            )
            assertEquals(expected, f.find(request.attemptId))
            val counts = attemptCounts(f.source, request.attemptId)
            assertEquals(listOf(1, 1, if (settlement) 0 else 1), counts)
            repeat(2) {
                assertEquals(
                    expected,
                    assertIs<NpcRewardClaimWrite.Existing>(f.settle(request)).receipt,
                )
                assertEquals(
                    expected,
                    assertIs<NpcRewardClaimWrite.Existing>(f.stage(request)).receipt,
                )
                assertEquals(counts, attemptCounts(f.source, request.attemptId))
            }
            assertEquals(if (settlement) 0L else 4L, consumed(f.source, allocation.lifeId))
            f.remember(expected)
        }
    }

    private fun assertConsumptionConstraints(f: Fixture) {
        val allocation = f.allocate(listOf(item(3)))
        val aborted = f.claim(listOf(fragment(allocation, count = 1)))
        val abortReceipt = assertIs<NpcRewardClaimWrite.Staged>(f.settle(aborted)).receipt
        f.remember(abortReceipt)
        val abortFailure =
            assertFailsWith<SQLException> {
                f.source.tx { db ->
                    db.prepareStatement(
                            "INSERT INTO npc_reward_claim_consumption " +
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
        val committed = f.claim(listOf(fragment(allocation, count = 2)))
        val committedReceipt = f.commitStage(committed)
        val countFailure =
            assertFailsWith<SQLException> {
                f.source.tx { db ->
                    // UPDATE avoids a primary-key collision masking the exact-count foreign key.
                    db.prepareStatement(
                            "UPDATE npc_reward_claim_consumption SET count = 3 WHERE attempt_id = ?"
                        )
                        .use {
                            it.setObject(1, committed.attemptId)
                            assertEquals(1, it.executeUpdate())
                        }
                }
            }
        assertEquals("23503", countFailure.sqlState)
        assertEquals(2L, consumed(f.source, allocation.lifeId))
        assertEquals(abortReceipt, f.find(aborted.attemptId))
        assertEquals(committedReceipt, f.find(committed.attemptId))

        val absentSource =
            f.claim(
                listOf(NpcRewardClaim.Fragment(UUID.randomUUID(), f.people.first.character, 0, 1))
            )
        val sourceFailure =
            assertFailsWith<SQLException> {
                f.source.tx { db ->
                    f.repository.settleNpcRewardClaim(db, absentSource, TIMEOUT)
                    // Fixture-only direct SQL keeps the other two FKs valid, isolating the V43 FK.
                    db.prepareStatement(
                            "UPDATE npc_reward_claim_attempts SET outcome = 'COMMITTED' WHERE attempt_id = ?"
                        )
                        .use {
                            it.setObject(1, absentSource.attemptId)
                            assertEquals(1, it.executeUpdate())
                        }
                    db.prepareStatement(
                            "INSERT INTO npc_reward_claim_consumption " +
                                "(attempt_id, life_id, allocated_character_id, item_ordinal, count, outcome) " +
                                "SELECT attempt_id, life_id, allocated_character_id, item_ordinal, count, 'COMMITTED' " +
                                "FROM npc_reward_claim_fragments WHERE attempt_id = ?"
                        )
                        .use {
                            it.setObject(1, absentSource.attemptId)
                            it.executeUpdate()
                        }
                }
            }
        assertEquals("23503", sourceFailure.sqlState)
        assertNull(f.find(absentSource.attemptId))
        assertEquals(listOf(0, 0, 0), attemptCounts(f.source, absentSource.attemptId))
    }

    private fun assertTransactionGuards(f: Fixture) {
        val request = f.claim(listOf(fragment(f.allocate(listOf(item(1))), count = 1)))
        val operations: List<(DatabaseConnection, Int) -> Any?> =
            listOf(
                { db, timeout -> f.repository.stageNpcRewardClaim(db, request, timeout) },
                { db, timeout -> f.repository.settleNpcRewardClaim(db, request, timeout) },
                { db, timeout -> f.repository.findNpcRewardClaim(db, request.attemptId, timeout) },
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

    private fun awaitBlockedWrite(
        f: Fixture,
        original: Connection,
        operation: (DatabaseConnection) -> NpcRewardClaimWrite,
        whenBlocked: () -> Unit,
    ): Result<NpcRewardClaimWrite> {
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
                "Claim test worker did not finish"
            }
        }
    }

    private class Fixture(
        val source: DataSource,
        val repository: CharacterAccountRepository,
        val people: People,
        val receipts: MutableMap<UUID, NpcRewardClaimReceipt>,
    ) {
        fun allocate(
            items: List<NpcRewardAllocation.Item>,
            person: Person = people.first,
        ): NpcRewardAllocation {
            val allocation =
                NpcRewardAllocation(
                    UUID.randomUUID(),
                    "npc.nex",
                    listOf(
                        NpcRewardAllocation.Recipient(
                            person.account,
                            person.character,
                            CoordGrid(2913, 5215),
                            100,
                            items,
                        )
                    ),
                )
            return source.tx { repository.recordNpcRewardAllocation(it, allocation) }
        }

        fun claim(fragments: List<NpcRewardClaim.Fragment>, picker: Person = people.first) =
            NpcRewardClaim(UUID.randomUUID(), picker.account, picker.character, fragments)

        fun stage(request: NpcRewardClaim) =
            source.tx { repository.stageNpcRewardClaim(it, request, TIMEOUT) }

        fun settle(request: NpcRewardClaim) =
            source.tx { repository.settleNpcRewardClaim(it, request, TIMEOUT) }

        fun find(attempt: UUID) = source.tx { repository.findNpcRewardClaim(it, attempt, TIMEOUT) }

        fun commitStage(request: NpcRewardClaim): NpcRewardClaimReceipt {
            val receipt = assertIs<NpcRewardClaimWrite.Staged>(stage(request)).receipt
            assertEquals(NpcRewardClaimOutcome.COMMITTED, receipt.outcome)
            assertEquals(request, receipt.request)
            remember(receipt)
            return receipt
        }

        fun remember(receipt: NpcRewardClaimReceipt) {
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
            val first = account("claim-one")
            val other = account("claim-two")
            People(
                character(first, "Claim One"),
                character(first, "Claim Two"),
                character(other, "Claim Three"),
            )
        }

    private fun fragment(allocation: NpcRewardAllocation, count: Int) =
        NpcRewardClaim.Fragment(
            allocation.lifeId,
            allocation.recipients.single().characterId,
            0,
            count,
        )

    private fun item(count: Int, key: String = "obj.coins", vars: Int = 0) =
        NpcRewardAllocation.Item(key, count, vars)

    private fun copyClaim(request: NpcRewardClaim, fragments: List<NpcRewardClaim.Fragment>) =
        NpcRewardClaim(
            request.attemptId,
            request.pickerAccountId,
            request.pickerCharacterId,
            fragments,
        )

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
        fail("Claim backend $waiting never waited on original backend $blocking")
    }

    private fun assertMigration(source: DataSource, version: Int, script: String) {
        source.connection.use { jdbc ->
            jdbc
                .prepareStatement(
                    "SELECT script, checksum, success FROM flyway_schema_history WHERE version::integer = ?"
                )
                .use {
                    it.setInt(1, version)
                    it.executeQuery().use { rows ->
                        assertTrue(rows.next())
                        assertEquals(script, rows.getString(1))
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

    private fun consumed(source: DataSource, life: UUID): Long =
        source.tx { db ->
            db.prepareStatement(
                    "SELECT COALESCE(SUM(count), 0) FROM npc_reward_claim_consumption WHERE life_id = ?"
                )
                .use {
                    it.setObject(1, life)
                    it.executeQuery().use { rows ->
                        check(rows.next())
                        rows.getLong(1)
                    }
                }
        }

    private fun attemptCounts(source: DataSource, attempt: UUID): List<Int> =
        source.tx { db ->
            TABLES.map { table ->
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
            TABLES.map { table ->
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
        private val TABLES =
            listOf(
                "npc_reward_claim_attempts",
                "npc_reward_claim_fragments",
                "npc_reward_claim_consumption",
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

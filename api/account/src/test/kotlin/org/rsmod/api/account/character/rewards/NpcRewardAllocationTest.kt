package org.rsmod.api.account.character.rewards

import com.fasterxml.jackson.databind.ObjectMapper
import dev.openrune.ServerCacheManager
import dev.or2.central.db.FlywayMigrator
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.SQLException
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.sql.DataSource
import kotlin.test.*
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.rsmod.api.account.character.main.CharacterAccountApplier
import org.rsmod.api.account.character.main.CharacterAccountRepository
import org.rsmod.api.account.persistence.GamePersistenceRscmKeys
import org.rsmod.api.db.DatabaseConnection
import org.rsmod.game.inv.InvObj
import org.rsmod.map.CoordGrid

/**
 * S04 immutable allocation journal mapping in docs/bosses/nex/NEX_OSRS_AUDIT.md. Uses the public
 * native account repository and Central Flyway discovery, following RoyalTitansStatisticsTest's
 * separately owned logs cluster. No canonical connector, server cluster, NPC, RNG, ground delivery
 * or pickup path participates. A repository return inside a transaction is not a commit receipt.
 */
class NpcRewardAllocationTest {
    @Test
    fun `native item keys and immutable sorted recipient values detach caller lists`() {
        if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
        val type = assertNotNull(ServerCacheManager.getItem(13447))
        val origin = CoordGrid(2913, 5215)
        val item = NpcRewardAllocation.Item(InvObj(type, 2, origin.packed + 1))
        assertEquals(GamePersistenceRscmKeys.encodeObjKey(type.id), item.objKey)
        assertTrue(item.objKey.startsWith("obj."))
        assertEquals(2, item.count)
        assertEquals(origin.packed + 1, item.vars)
        val lines = mutableListOf(item, item)
        val first = NpcRewardAllocation.Recipient(1, 11, origin, Int.MAX_VALUE, lines)
        val second = NpcRewardAllocation.Recipient(1, 12, origin, 0, emptyList())
        val recipients = mutableListOf(second, first)
        val life = UUID.randomUUID()
        val allocation = NpcRewardAllocation(life, "npc.nex", recipients)
        val equivalent = NpcRewardAllocation(life, "npc.nex", listOf(first, second))
        lines.clear()
        recipients.clear()

        assertEquals(listOf(11, 12), allocation.recipients.map { it.characterId })
        assertEquals(listOf(item, item), allocation.recipients.first().items)
        assertTrue(allocation.recipients.last().items.isEmpty())
        assertEquals(allocation, equivalent)
        assertEquals(allocation.hashCode(), equivalent.hashCode())
        val copiedRecipient =
            NpcRewardAllocation.Recipient(1, 11, origin, Int.MAX_VALUE, listOf(item, item))
        assertEquals(first, copiedRecipient)
        assertEquals(first.hashCode(), copiedRecipient.hashCode())
        assertFailsWith<UnsupportedOperationException> {
            (allocation.recipients as MutableList<NpcRewardAllocation.Recipient>).clear()
        }
        assertFailsWith<UnsupportedOperationException> {
            (first.items as MutableList<NpcRewardAllocation.Item>).clear()
        }
        assertEquals(listOf(item, item), first.items)
        assertFailsWith<IllegalArgumentException> {
            NpcRewardAllocation(life, "npc.nex", listOf(first, first))
        }
        assertFailsWith<IllegalArgumentException> { NpcRewardAllocation.Item("obj.coins", 0) }
        assertFailsWith<IllegalArgumentException> {
            NpcRewardAllocation.Recipient(1, 11, origin, -1, emptyList())
        }
    }

    @Test
    fun `native allocation migration transactions conflicts and two isolated database restarts`() {
        val logs = Files.createDirectories(Path.of("logs")).toAbsolutePath().normalize()
        val directory = Files.createTempDirectory(logs, "npc-reward-allocation-test-").normalize()
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
        val committed = mutableListOf<NpcRewardAllocation>()
        var expectedMigrations = emptyMap<Int, Int>()

        builder.start().use { postgres ->
            val source = postgres.postgresDatabase
            // Discover native migrations before any fixture DDL can trigger Flyway baselining.
            FlywayMigrator.migrate(source)
            expectedMigrations = migrationChecksums(source)
            assertTrue(expectedMigrations.keys.containsAll(setOf(1, 4, 26, 43)))
            FlywayMigrator.migrate(source)
            assertEquals(expectedMigrations, migrationChecksums(source))
            val people = seedCharacters(source)
            val party = party(people)
            assertNull(source.tx { repository.findNpcRewardAllocation(it, party.lifeId) })
            assertEquals(party, source.tx { repository.recordNpcRewardAllocation(it, party) })
            assertEquals(party, source.tx { repository.findNpcRewardAllocation(it, party.lifeId) })
            assertEquals(listOf(1, 3, 4), rowCounts(source, party.lifeId))
            assertTrue(party.recipients.last().items.isEmpty(), "A zero-item recipient is recorded")
            committed += party

            repeat(3) {
                val reordered =
                    NpcRewardAllocation(
                        party.lifeId,
                        party.sourceNpcKey,
                        party.recipients.reversed(),
                    )
                assertEquals(
                    party,
                    source.tx { repository.recordNpcRewardAllocation(it, reordered) },
                )
            }
            assertEquals(listOf(1, 3, 4), rowCounts(source, party.lifeId))
            assertPayloadConflicts(source, repository, party)
            assertOwnershipFailures(source, repository, people)

            val empty = NpcRewardAllocation(UUID.randomUUID(), "npc.nex", emptyList())
            assertEquals(empty, source.tx { repository.recordNpcRewardAllocation(it, empty) })
            assertEquals(empty, source.tx { repository.findNpcRewardAllocation(it, empty.lifeId) })
            assertEquals(listOf(1, 0, 0), rowCounts(source, empty.lifeId))
            assertFailsWith<IllegalStateException> {
                source.tx {
                    repository.recordNpcRewardAllocation(
                        it,
                        NpcRewardAllocation(empty.lifeId, empty.sourceNpcKey, party.recipients),
                    )
                }
            }
            committed += empty

            assertPartialRollback(source, repository, party)
            val uncommitted =
                NpcRewardAllocation(UUID.randomUUID(), party.sourceNpcKey, party.recipients)
            source.connection.use { jdbc ->
                jdbc.beginTestTransaction()
                try {
                    assertEquals(
                        uncommitted,
                        repository.recordNpcRewardAllocation(DatabaseConnection(jdbc), uncommitted),
                    )
                    assertNull(
                        source.tx { repository.findNpcRewardAllocation(it, uncommitted.lifeId) }
                    )
                    assertEquals(listOf(0, 0, 0), rowCounts(source, uncommitted.lifeId))
                    jdbc.commit()
                } catch (failure: Throwable) {
                    jdbc.rollback()
                    throw failure
                }
            }
            assertEquals(
                uncommitted,
                source.tx { repository.findNpcRewardAllocation(it, uncommitted.lifeId) },
            )
            committed += uncommitted

            val identical =
                NpcRewardAllocation(UUID.randomUUID(), party.sourceNpcKey, party.recipients)
            val identicalResults = concurrentRecords(source, repository, List(4) { identical })
            assertTrue(identicalResults.all { it.getOrThrow() == identical })
            assertEquals(listOf(1, 3, 4), rowCounts(source, identical.lifeId))
            committed += identical

            val contender =
                NpcRewardAllocation(UUID.randomUUID(), party.sourceNpcKey, party.recipients)
            val alternative =
                NpcRewardAllocation(contender.lifeId, "npc.nex_smokemage", contender.recipients)
            val conflictResults =
                concurrentRecords(source, repository, listOf(contender, alternative))
            assertEquals(1, conflictResults.count { it.isSuccess })
            assertEquals(1, conflictResults.count { it.isFailure })
            assertIs<IllegalStateException>(
                conflictResults.single { it.isFailure }.exceptionOrNull()
            )
            val winner = conflictResults.single { it.isSuccess }.getOrThrow()
            assertEquals(
                winner,
                source.tx { repository.findNpcRewardAllocation(it, winner.lifeId) },
            )
            assertEquals(listOf(1, 3, 4), rowCounts(source, winner.lifeId))
            committed += winner
            committed += assertNativeSaveLockOrder(source, repository, party)
        }

        // Reopen only the newly created logs cluster; no entity/cache conversion occurs in lookup.
        repeat(2) {
            builder.start().use { postgres ->
                val source = postgres.postgresDatabase
                FlywayMigrator.migrate(source)
                assertEquals(expectedMigrations, migrationChecksums(source))
                for (allocation in committed) {
                    assertEquals(
                        allocation,
                        source.tx { repository.findNpcRewardAllocation(it, allocation.lifeId) },
                    )
                    assertEquals(
                        allocation,
                        source.tx { repository.recordNpcRewardAllocation(it, allocation) },
                    )
                    assertEquals(
                        allocation,
                        source.tx { repository.findNpcRewardAllocation(it, allocation.lifeId) },
                    )
                }
                assertNull(source.tx { repository.findNpcRewardAllocation(it, UUID.randomUUID()) })
            }
        }
        verifyNoInteractions(mapper, applier)
    }

    private fun assertPayloadConflicts(
        source: DataSource,
        repository: CharacterAccountRepository,
        original: NpcRewardAllocation,
    ) {
        val first = original.recipients.first()
        fun changed(recipient: NpcRewardAllocation.Recipient) =
            NpcRewardAllocation(
                original.lifeId,
                original.sourceNpcKey,
                listOf(recipient) + original.recipients.drop(1),
            )
        val changes =
            listOf(
                NpcRewardAllocation(original.lifeId, "npc.nex_smokemage", original.recipients),
                NpcRewardAllocation(
                    original.lifeId,
                    original.sourceNpcKey,
                    original.recipients.dropLast(1),
                ),
                changed(copyRecipient(first, accountId = original.recipients.last().accountId)),
                changed(copyRecipient(first, coords = first.coords.translate(1, 0))),
                changed(copyRecipient(first, duration = first.duration + 1)),
                changed(copyRecipient(first, items = first.items.dropLast(1))),
                changed(copyRecipient(first, items = first.items.reversed())),
                changed(
                    copyRecipient(
                        first,
                        items =
                            listOf(first.items[0].copy(objKey = "obj.airrune")) +
                                first.items.drop(1),
                    )
                ),
                changed(
                    copyRecipient(
                        first,
                        items = listOf(first.items[0].copy(count = 18)) + first.items.drop(1),
                    )
                ),
                changed(
                    copyRecipient(
                        first,
                        items = listOf(first.items[0].copy(vars = 77)) + first.items.drop(1),
                    )
                ),
                NpcRewardAllocation(original.lifeId, original.sourceNpcKey, emptyList()),
            )
        for (changed in changes) {
            assertFailsWith<IllegalStateException> {
                source.tx { repository.recordNpcRewardAllocation(it, changed) }
            }
            assertEquals(
                original,
                source.tx { repository.findNpcRewardAllocation(it, original.lifeId) },
            )
            assertEquals(listOf(1, 3, 4), rowCounts(source, original.lifeId))
        }
        assertFailsWith<SQLException> {
            source.tx { db ->
                db.prepareStatement(
                        "UPDATE npc_reward_recipients SET destination = 'inventory' WHERE life_id = ?"
                    )
                    .use {
                        it.setObject(1, original.lifeId)
                        it.executeUpdate()
                    }
            }
        }
        assertEquals(
            original,
            source.tx { repository.findNpcRewardAllocation(it, original.lifeId) },
        )
    }

    private fun assertOwnershipFailures(
        source: DataSource,
        repository: CharacterAccountRepository,
        people: People,
    ) {
        for ((account, character) in
            listOf(
                people.otherAccount to people.firstCharacter,
                Int.MAX_VALUE to people.firstCharacter,
                people.account to Int.MAX_VALUE,
            )) {
            val allocation =
                NpcRewardAllocation(
                    UUID.randomUUID(),
                    "npc.nex",
                    listOf(
                        NpcRewardAllocation.Recipient(
                            account,
                            character,
                            CoordGrid(2913, 5215),
                            10,
                            emptyList(),
                        )
                    ),
                )
            assertFailsWith<IllegalStateException> {
                source.tx { repository.recordNpcRewardAllocation(it, allocation) }
            }
            assertNull(source.tx { repository.findNpcRewardAllocation(it, allocation.lifeId) })
            assertEquals(listOf(0, 0, 0), rowCounts(source, allocation.lifeId))
        }
    }

    private fun assertPartialRollback(
        source: DataSource,
        repository: CharacterAccountRepository,
        original: NpcRewardAllocation,
    ) {
        val allocation =
            NpcRewardAllocation(UUID.randomUUID(), original.sourceNpcKey, original.recipients)
        source.connection.use { jdbc ->
            jdbc.createStatement().use {
                it.execute(
                    """
                    CREATE FUNCTION reject_second_reward_item() RETURNS trigger LANGUAGE plpgsql AS '
                    BEGIN
                        IF NEW.ordinal = 1 THEN RAISE EXCEPTION ''forced allocation item failure''; END IF;
                        RETURN NEW;
                    END;';
                    CREATE TRIGGER reject_second_reward_item BEFORE INSERT ON npc_reward_items
                    FOR EACH ROW EXECUTE FUNCTION reject_second_reward_item();
                    """
                        .trimIndent()
                )
            }
        }
        try {
            assertFailsWith<SQLException> {
                source.tx { repository.recordNpcRewardAllocation(it, allocation) }
            }
            assertEquals(listOf(0, 0, 0), rowCounts(source, allocation.lifeId))
            assertNull(source.tx { repository.findNpcRewardAllocation(it, allocation.lifeId) })
        } finally {
            source.connection.use { jdbc ->
                jdbc.createStatement().use {
                    it.execute(
                        "DROP TRIGGER reject_second_reward_item ON npc_reward_items; DROP FUNCTION reject_second_reward_item()"
                    )
                }
            }
        }
        // The transaction owner also rolls back a method that returned before a later failure.
        assertFailsWith<IllegalStateException> {
            source.tx {
                assertEquals(allocation, repository.recordNpcRewardAllocation(it, allocation))
                error("Failure after repository return but before commit")
            }
        }
        assertEquals(listOf(0, 0, 0), rowCounts(source, allocation.lifeId))
        assertNull(source.tx { repository.findNpcRewardAllocation(it, allocation.lifeId) })
    }

    private fun concurrentRecords(
        source: DataSource,
        repository: CharacterAccountRepository,
        requests: List<NpcRewardAllocation>,
    ): List<Result<NpcRewardAllocation>> {
        val executor = Executors.newFixedThreadPool(requests.size)
        val ready = CountDownLatch(requests.size)
        val start = CountDownLatch(1)
        try {
            val futures =
                requests.map { allocation ->
                    executor.submit(
                        Callable {
                            ready.countDown()
                            check(start.await(10, TimeUnit.SECONDS))
                            runCatching {
                                source.tx { repository.recordNpcRewardAllocation(it, allocation) }
                            }
                        }
                    )
                }
            assertTrue(ready.await(10, TimeUnit.SECONDS))
            start.countDown()
            return futures.map { it.get(30, TimeUnit.SECONDS) }
        } finally {
            start.countDown()
            executor.shutdown()
            check(executor.awaitTermination(20, TimeUnit.SECONDS)) {
                "Allocation test workers did not finish"
            }
        }
    }

    private fun assertNativeSaveLockOrder(
        source: DataSource,
        repository: CharacterAccountRepository,
        original: NpcRewardAllocation,
    ): NpcRewardAllocation {
        // V31 belongs to api/net, outside this standalone account test's migration classpath.
        // Apply its exact tracked native DDL after Flyway, without claiming Flyway discovered it.
        val accountAttributes =
            Files.readString(
                Path.of(
                    "api/net/src/main/resources/db/migration/V31__daily_adventure_account_attrs.sql"
                )
            )
        source.connection.use { jdbc ->
            jdbc.createStatement().use { it.execute(accountAttributes) }
        }
        val recipient = original.recipients.first()
        val allocation =
            NpcRewardAllocation(UUID.randomUUID(), original.sourceNpcKey, listOf(recipient))
        val executor = Executors.newSingleThreadExecutor()
        val journalBackend = CompletableFuture<Int>()
        try {
            source.connection.use { saving ->
                saving.beginTestTransaction()
                try {
                    val savingBackend =
                        saving.createStatement().use { statement ->
                            statement.executeQuery("SELECT pg_backend_pid()").use { rows ->
                                check(rows.next())
                                rows.getInt(1)
                            }
                        }
                    saving
                        .prepareStatement(
                            "UPDATE account_characters SET run_energy = 9999 WHERE id = ?"
                        )
                        .use {
                            it.setInt(1, recipient.characterId)
                            assertEquals(1, it.executeUpdate())
                        }
                    val recording =
                        executor.submit(
                            Callable {
                                source.tx { db ->
                                    db.prepareStatement("SELECT pg_backend_pid()").use { statement
                                        ->
                                        statement.executeQuery().use { rows ->
                                            check(rows.next())
                                            journalBackend.complete(rows.getInt(1))
                                        }
                                    }
                                    repository.recordNpcRewardAllocation(db, allocation)
                                }
                            }
                        )
                    val journalPid = journalBackend.get(10, TimeUnit.SECONDS)
                    assertBlockedBy(source, journalPid, savingBackend)
                    assertFalse(
                        recording.isDone,
                        "Journal must be waiting for the native character update",
                    )

                    // Native save order: character UPDATE, then account_attrs FK insertion.
                    // FOR UPDATE on the journal's account row deadlocks this ordering; KEY SHARE
                    // permits the FK check while the journal still waits on the character row.
                    saving
                        .prepareStatement(
                            """
                        INSERT INTO account_attrs (account_id, attr_key, value_json)
                        VALUES (?, ?, ?)
                        ON CONFLICT (account_id, attr_key) DO UPDATE
                        SET value_json = EXCLUDED.value_json, updated_at = CURRENT_TIMESTAMP
                        WHERE COALESCE((account_attrs.value_json::jsonb ->> 'updatedAtEpochMillis')::BIGINT, 0)
                            <= COALESCE((EXCLUDED.value_json::jsonb ->> 'updatedAtEpochMillis')::BIGINT, 0)
                        """
                                .trimIndent()
                        )
                        .use {
                            it.setInt(1, recipient.accountId)
                            it.setString(2, "account.reward-allocation-lock-test")
                            it.setString(3, "{\"updatedAtEpochMillis\":1}")
                            assertEquals(1, it.executeUpdate())
                        }
                    saving.commit()
                    assertEquals(allocation, recording.get(15, TimeUnit.SECONDS))
                } catch (failure: Throwable) {
                    saving.rollback()
                    throw failure
                }
            }
        } finally {
            executor.shutdown()
            check(executor.awaitTermination(20, TimeUnit.SECONDS)) {
                "Journal/save test worker did not finish"
            }
        }
        assertEquals(
            allocation,
            source.tx { repository.findNpcRewardAllocation(it, allocation.lifeId) },
        )
        return allocation
    }

    private fun assertBlockedBy(source: DataSource, waitingPid: Int, blockingPid: Int) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        source.connection.use { jdbc ->
            jdbc.prepareStatement("SELECT ? = ANY(pg_blocking_pids(?))").use {
                it.setInt(1, blockingPid)
                it.setInt(2, waitingPid)
                while (System.nanoTime() < deadline) {
                    val blocked =
                        it.executeQuery().use { rows ->
                            check(rows.next())
                            rows.getBoolean(1)
                        }
                    if (blocked) return
                    Thread.sleep(10)
                }
            }
        }
        fail(
            "PostgreSQL did not report journal backend $waitingPid waiting on native save $blockingPid"
        )
    }

    private data class People(
        val account: Int,
        val otherAccount: Int,
        val firstCharacter: Int,
        val secondCharacter: Int,
        val otherCharacter: Int,
    )

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
            fun character(accountId: Int, name: String): Int =
                db.prepareStatement(
                        "INSERT INTO account_characters(account_id, display_name) VALUES (?, ?) RETURNING id"
                    )
                    .use {
                        it.setInt(1, accountId)
                        it.setString(2, name)
                        it.executeQuery().use { rows ->
                            check(rows.next())
                            rows.getInt(1)
                        }
                    }
            val first = account("allocation-one")
            val second = account("allocation-two")
            People(
                first,
                second,
                character(first, "Reward One"),
                character(first, "Reward Two"),
                character(second, "Reward Three"),
            )
        }

    private fun party(people: People): NpcRewardAllocation {
        val tile = CoordGrid(2913, 5215)
        val duplicate = NpcRewardAllocation.Item("obj.coins", 17)
        return NpcRewardAllocation(
            UUID.randomUUID(),
            "npc.nex",
            listOf(
                NpcRewardAllocation.Recipient(
                    people.otherAccount,
                    people.otherCharacter,
                    tile.translate(2, 0),
                    0,
                    emptyList(),
                ),
                NpcRewardAllocation.Recipient(
                    people.account,
                    people.secondCharacter,
                    tile.translate(1, 0),
                    Int.MAX_VALUE,
                    listOf(NpcRewardAllocation.Item("obj.waterrune", 4, 12345)),
                ),
                NpcRewardAllocation.Recipient(
                    people.account,
                    people.firstCharacter,
                    tile,
                    100,
                    listOf(
                        duplicate,
                        duplicate,
                        NpcRewardAllocation.Item("obj.firerune", 2, tile.packed + 1),
                    ),
                ),
            ),
        )
    }

    private fun copyRecipient(
        value: NpcRewardAllocation.Recipient,
        accountId: Int = value.accountId,
        coords: CoordGrid = value.coords,
        duration: Int = value.duration,
        items: List<NpcRewardAllocation.Item> = value.items,
    ) = NpcRewardAllocation.Recipient(accountId, value.characterId, coords, duration, items)

    private fun migrationChecksums(source: DataSource): Map<Int, Int> =
        source.connection.use { jdbc ->
            jdbc.createStatement().use { statement ->
                statement
                    .executeQuery(
                        "SELECT version, checksum, success FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank"
                    )
                    .use { rows ->
                        buildMap {
                            while (rows.next()) {
                                assertTrue(rows.getBoolean("success"))
                                assertNotNull(rows.getObject("checksum"))
                                put(rows.getString("version").toInt(), rows.getInt("checksum"))
                            }
                        }
                    }
            }
        }

    private fun rowCounts(source: DataSource, life: UUID): List<Int> =
        source.tx { db ->
            listOf("npc_reward_lives", "npc_reward_recipients", "npc_reward_items").map { table ->
                db.prepareStatement("SELECT count(*) FROM $table WHERE life_id = ?").use {
                    it.setObject(1, life)
                    it.executeQuery().use { rows ->
                        check(rows.next())
                        rows.getInt(1)
                    }
                }
            }
        }

    private fun Connection.beginTestTransaction() {
        autoCommit = false
        createStatement().use {
            it.execute("SET LOCAL statement_timeout = '10s'; SET LOCAL lock_timeout = '5s'")
        }
    }

    private fun <T> DataSource.tx(block: (DatabaseConnection) -> T): T =
        connection.use { jdbc ->
            jdbc.beginTestTransaction()
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

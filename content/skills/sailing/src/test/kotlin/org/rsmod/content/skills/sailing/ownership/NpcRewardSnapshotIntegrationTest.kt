package org.rsmod.content.skills.sailing.ownership

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.inject.Guice
import com.google.inject.Key
import dev.openrune.ServerCacheManager
import dev.openrune.gamevals.GameValProvider
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.InvScope
import dev.openrune.types.util.UncheckedType
import dev.openrune.types.varp.VarpLifetime
import dev.or2.central.db.FlywayMigrator
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.SQLException
import java.time.LocalDateTime
import java.util.IdentityHashMap
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.*
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.account.character.CharacterDataStage
import org.rsmod.api.account.character.inv.CharacterInventoryApplier
import org.rsmod.api.account.character.inv.CharacterInventoryPipeline
import org.rsmod.api.account.character.main.CharacterAccountApplier
import org.rsmod.api.account.character.main.CharacterAccountRepository
import org.rsmod.api.account.character.rewards.NpcRewardAllocation
import org.rsmod.api.account.character.rewards.NpcRewardClaim
import org.rsmod.api.account.character.rewards.NpcRewardClaimOutcome
import org.rsmod.api.account.character.rewards.NpcRewardClaimReceipt
import org.rsmod.api.account.character.rewards.NpcRewardClaimWrite
import org.rsmod.api.account.character.stats.CharacterStatApplier
import org.rsmod.api.account.character.stats.CharacterStatPipeline
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.db.DatabaseConnection
import org.rsmod.api.parsers.jackson.JacksonModule
import org.rsmod.api.parsers.json.Json
import org.rsmod.api.parsers.json.JsonModule
import org.rsmod.content.skills.sailing.BoatTypes
import org.rsmod.content.skills.sailing.SailingBoatDefinitionRegistry
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.map.CoordGrid

/**
 * S04 verification mapping in docs/bosses/nex/NEX_OSRS_AUDIT.md: one supplied JDBC transaction
 * composes the existing claim, CharacterAccountRepository.save and the three current real saves
 * registered by CharacterModule and SailingModule. The explicit fixture list does not test DI
 * discovery, AccountSavingService invocation, live-player ownership or a runtime pause strategy.
 *
 * Flyway discovers the actual test classpath migrations before fixture rows. A real offline Player,
 * revision-240 SERVER definitions, native JSON mapper, inventory/stat appliers and cache-decoded
 * Sailing validator are used. Snapshot mutation is fixture setup, not a ground-pickup workflow.
 * Every reload uses a fresh Player; no production helper, mock pipeline or canonical database runs.
 */
@OptIn(InternalApi::class, UncheckedType::class)
@ResourceLock("sailing-server-cache")
class NpcRewardSnapshotIntegrationTest {
    @Test
    fun `claim and complete current native snapshot compose atomically and reconcile committed outcomes`() {
        val root = Path.of("").toAbsolutePath().normalize()
        GameValProvider.load("$root${File.separator}")
        val serverCache = ServerCacheManager.init(root.resolve(".data/cache/SERVER"), 240)
        try {
            val logs = Files.createDirectories(root.resolve("logs"))
            val directory = Files.createTempDirectory(logs, "npc-reward-snapshot-test-").normalize()
            check(directory.startsWith(logs))
            EmbeddedPostgres.builder()
                .setDataDirectory(directory.resolve("data"))
                .setOverrideWorkingDirectory(directory.resolve("binaries").toFile())
                .setCleanDataDirectory(false)
                .setPort(0)
                .start()
                .use { postgres ->
                    val source = postgres.postgresDatabase
                    FlywayMigrator.migrate(source)
                    assertMigrations(source)
                    val history = migrationHistory(source)
                    FlywayMigrator.migrate(source)
                    assertEquals(history, migrationHistory(source))
                    val mapper =
                        Guice.createInjector(JsonModule, JacksonModule)
                            .getInstance(Key.get(ObjectMapper::class.java, Json::class.java))
                    val repository = CharacterAccountRepository(mapper, CharacterAccountApplier())
                    val validator = SailingOwnershipValidator(SailingBoatDefinitionRegistry())
                    val pipelines: List<CharacterDataStage.Pipeline> =
                        listOf(
                            CharacterInventoryPipeline(CharacterInventoryApplier()),
                            CharacterStatPipeline(CharacterStatApplier()),
                            SailingOwnershipPipeline(SailingOwnershipApplier(), validator),
                        )
                    val f = Fixture(source, repository, validator, pipelines, seedCharacter(source))
                    val before = f.player(version = 0, coins = 100)
                    source.tx { f.saveSnapshot(it, before) }
                    f.assertReload(before, accountState = before)
                    val allocation = f.allocate()

                    val committedPlayer = f.player(version = 1, coins = 103)
                    val committedRequest = f.request(allocation, count = 3)
                    val committed =
                        assertIs<NpcRewardClaimWrite.Staged>(
                                source.tx { f.compose(it, committedRequest, committedPlayer) }
                            )
                            .receipt
                    assertEquals(NpcRewardClaimOutcome.COMMITTED, committed.outcome)
                    assertEquals(committedRequest, committed.request)
                    assertEquals(committed, f.find(committedRequest))
                    f.assertReload(committedPlayer, accountState = committedPlayer)
                    assertEquals(3L, consumed(source, allocation.lifeId))

                    assertLateRollback(f, allocation, committedPlayer)
                    val acknowledgedPlayer = f.player(version = 3, coins = 106)
                    val uncertainRequest = f.request(allocation, count = 3)
                    assertCommittedAcknowledgementFailure(f, uncertainRequest, acknowledgedPlayer)
                    f.assertReload(acknowledgedPlayer, accountState = acknowledgedPlayer)
                    assertEquals(6L, consumed(source, allocation.lifeId))

                    // An existing ABORTED attempt cannot write a freshly prepared snapshot either.
                    val abortedRequest = f.request(allocation, count = 2)
                    val aborted =
                        source
                            .tx { repository.settleNpcRewardClaim(it, abortedRequest, TIMEOUT) }
                            .receipt
                    assertEquals(NpcRewardClaimOutcome.ABORTED, aborted.outcome)
                    val beforeAbortReplay = persistedRows(source)
                    val writes = f.snapshotWrites
                    val replay = source.tx { f.compose(it, abortedRequest, f.player(4, 108)) }
                    assertEquals(aborted, assertIs<NpcRewardClaimWrite.Existing>(replay).receipt)
                    assertEquals(writes, f.snapshotWrites)
                    assertEquals(beforeAbortReplay, persistedRows(source))

                    // Exercise native clear-presence SQL. An older account-attribute value must not
                    // overwrite the newer value even though the other snapshot segments change.
                    val logoutPlayer = f.player(version = 4, coins = 109, accountVersion = 0)
                    logoutPlayer.pendingLogout = true
                    source.tx { f.compose(it, f.request(allocation, 3), logoutPlayer) }
                    f.assertReload(logoutPlayer, accountState = acknowledgedPlayer)
                    assertEquals(9L, consumed(source, allocation.lifeId))
                    assertEquals(history, migrationHistory(source))
                }
        } finally {
            serverCache.close()
        }
    }

    private fun assertLateRollback(f: Fixture, allocation: NpcRewardAllocation, committed: Player) {
        val request = f.request(allocation, count = 3)
        val before = persistedRows(f.source)
        f.source.connection.use { jdbc ->
            jdbc.createStatement().use {
                it.execute(
                    """
                    CREATE FUNCTION fail_snapshot_boat() RETURNS trigger LANGUAGE plpgsql AS '
                    BEGIN
                        RAISE EXCEPTION ''fixture late Sailing save failure'';
                        RETURN NEW;
                    END;';
                    CREATE TRIGGER fail_snapshot_boat BEFORE INSERT ON character_sailing_boats
                    FOR EACH ROW EXECUTE FUNCTION fail_snapshot_boat();
                    """
                        .trimIndent()
                )
            }
        }
        try {
            val completedBefore = f.completedPipelines.size
            val failure =
                assertFailsWith<SQLException> {
                    f.source.tx { f.compose(it, request, f.player(version = 2, coins = 106)) }
                }
            assertEquals("P0001", failure.sqlState)
            assertEquals(
                listOf(CharacterInventoryPipeline::class.java, CharacterStatPipeline::class.java),
                f.completedPipelines.drop(completedBefore),
                "The real inventory and stat saves completed before the real Sailing SQL failed",
            )
            assertNull(f.find(request))
            assertEquals(
                before,
                persistedRows(f.source),
                "Rollback restores every snapshot segment and claim row",
            )
            f.assertReload(committed, accountState = committed)
        } finally {
            f.source.connection.use { jdbc ->
                jdbc.createStatement().use {
                    it.execute(
                        "DROP TRIGGER fail_snapshot_boat ON character_sailing_boats; DROP FUNCTION fail_snapshot_boat()"
                    )
                }
            }
        }
    }

    private fun assertCommittedAcknowledgementFailure(
        f: Fixture,
        request: NpcRewardClaim,
        player: Player,
    ) {
        val failure = SQLException("fixture acknowledgement failure after physical snapshot commit")
        var provisional: NpcRewardClaimReceipt? = null
        var commits = 0
        f.source.connection.use { real ->
            real.begin()
            val transport =
                object : Connection by real {
                    override fun commit() {
                        commits++
                        real.commit()
                        throw failure
                    }
                }
            val thrown = assertFails {
                provisional =
                    assertIs<NpcRewardClaimWrite.Staged>(
                            f.compose(DatabaseConnection(transport), request, player)
                        )
                        .receipt
                transport.commit()
            }
            assertSame(failure, thrown)
            transport.rollback()
        }
        assertEquals(1, commits)
        val expected = assertNotNull(provisional)
        assertEquals(NpcRewardClaimOutcome.COMMITTED, expected.outcome)
        assertEquals(expected, f.find(request))
        val before = persistedRows(f.source)
        val writes = f.snapshotWrites
        repeat(2) {
            assertEquals(
                expected,
                assertIs<NpcRewardClaimWrite.Existing>(
                        f.source.tx { f.repository.settleNpcRewardClaim(it, request, TIMEOUT) }
                    )
                    .receipt,
            )
            // Deliberately different in-memory data makes an erroneous second save observable.
            val replay = f.source.tx { f.compose(it, request, f.player(version = 7, coins = 999)) }
            assertEquals(expected, assertIs<NpcRewardClaimWrite.Existing>(replay).receipt)
            assertEquals(writes, f.snapshotWrites)
            assertEquals(
                before,
                persistedRows(f.source),
                "Exact reconciliation must not rewrite any snapshot table",
            )
        }
    }

    private inner class Fixture(
        val source: DataSource,
        val repository: CharacterAccountRepository,
        val validator: SailingOwnershipValidator,
        val pipelines: List<CharacterDataStage.Pipeline>,
        val person: Person,
    ) {
        var snapshotWrites = 0
        val completedPipelines = mutableListOf<Class<*>>()
        private val writtenAt = IdentityHashMap<Player, LocalDateTime>()

        fun player(version: Int, coins: Int, accountVersion: Int = version): Player =
            Player().apply {
                accountId = person.account
                characterId = person.character
                username = ACCOUNT_NAME
                displayName = "Snapshot $version"
                coords = CoordGrid(3200 + version, 3210 + version, version % 4)
                lastLogin = LocalDateTime.of(2026, 9, 13, 12, version, 0)
                runEnergy = 7000 + version
                xpRate = 1.25 + version * 0.25
                members = version % 2 == 0

                // Complete intended fixture inventory map, including an empty permanent bag.
                // The real pipeline persists/removes slots across all four inventories.
                for (key in INVENTORIES) {
                    val inv = invMap.getOrPut(key)
                    assertEquals(InvScope.Perm, inv.type.scope, key)
                    inv.fillNulls()
                }
                invMap.getValue("inv.inv")[0] = InvObj("obj.coins", coins, COIN_VARS)
                invMap.getValue("inv.inv")[2] = InvObj("obj.airrune", 20 + version, 100 + version)
                if (version == 0) invMap.getValue("inv.inv")[3] = InvObj("obj.airrune", 1)
                invMap.getValue("inv.bank")[4] = InvObj("obj.coins", 200 + version, 200 + version)
                invMap.getValue("inv.worn")[0] = InvObj("obj.bronze_med_helm", 1)

                val permanent = "varp.playtime".asRSCM(RSCMType.VARP)
                val temporary = "varp.music_playlist".asRSCM(RSCMType.VARP)
                assertEquals(
                    VarpLifetime.Perm,
                    assertNotNull(ServerCacheManager.getVarp(permanent)).scope,
                )
                assertEquals(
                    VarpLifetime.Temp,
                    assertNotNull(ServerCacheManager.getVarp(temporary)).scope,
                )
                // Offline snapshot construction uses the same backing map populated by the native
                // account applier; no client synchronization or gameplay mutation is simulated.
                vars.backing[permanent] = 500 + version
                vars.backing[temporary] = 900 + version
                attr[CHARACTER_ATTR] = mapOf("version" to version, "label" to "snapshot-$version")
                attr[ACCOUNT_ATTR] =
                    mapOf(
                        "version" to accountVersion,
                        "updatedAtEpochMillis" to (1_700_000_000_000L + accountVersion * 1000L),
                    )
                if (version == 0) {
                    attr[OMITTED_CHARACTER_ATTR] = "remove-on-next-snapshot"
                    attr[RETAINED_ACCOUNT_ATTR] = "retain-when-omitted"
                }
                for (stat in ServerCacheManager.getStats().values) {
                    val key = RSCM.getReverseMapping(RSCMType.STAT, stat.id)
                    statMap.setBaseLevel(key, (30 + version + stat.id % 5).toByte())
                    statMap.setCurrentLevel(key, (25 + version + stat.id % 5).toByte())
                    statMap.setFineXP(key, 500_000 + version * 1000 + stat.id)
                }
                SailingOwnershipState.attach(
                    this,
                    validator.validate(
                        listOf(
                            SailingOwnedBoat(
                                0,
                                if (version % 2 == 0) BoatTypes.RAFT.cacheRow
                                else BoatTypes.SKIFF.cacheRow,
                            ),
                            SailingOwnedBoat(3, BoatTypes.SLOOP.cacheRow),
                        )
                    ),
                )
            }

        fun allocate(): NpcRewardAllocation {
            val allocation =
                NpcRewardAllocation(
                    UUID.randomUUID(),
                    "npc.nex",
                    listOf(
                        NpcRewardAllocation.Recipient(
                            person.account,
                            person.character,
                            CoordGrid(2924, 5202),
                            100,
                            listOf(NpcRewardAllocation.Item("obj.coins", 20, COIN_VARS)),
                        )
                    ),
                )
            return source.tx { repository.recordNpcRewardAllocation(it, allocation) }
        }

        fun request(allocation: NpcRewardAllocation, count: Int) =
            NpcRewardClaim(
                UUID.randomUUID(),
                person.account,
                person.character,
                listOf(NpcRewardClaim.Fragment(allocation.lifeId, person.character, 0, count)),
            )

        fun find(request: NpcRewardClaim) =
            source.tx { repository.findNpcRewardClaim(it, request.attemptId, TIMEOUT) }

        /** Test-only composition of existing methods, without invoking or modifying the saver. */
        fun compose(
            db: DatabaseConnection,
            request: NpcRewardClaim,
            player: Player,
        ): NpcRewardClaimWrite {
            val write = repository.stageNpcRewardClaim(db, request, TIMEOUT)
            if (write is NpcRewardClaimWrite.Staged) saveSnapshot(db, player)
            return write
        }

        fun saveSnapshot(db: DatabaseConnection, player: Player) {
            snapshotWrites++
            writtenAt[player] =
                db.prepareStatement("SELECT CURRENT_TIMESTAMP::timestamp").use { statement ->
                    statement.executeQuery().use { rows ->
                        check(rows.next())
                        rows.getTimestamp(1).toLocalDateTime()
                    }
                }
            repository.save(db, player, person.account, person.character, WORLD_ID)
            for (pipeline in pipelines) {
                pipeline.save(db, player, person.character)
                completedPipelines += pipeline.javaClass
            }
        }

        fun assertReload(expected: Player, accountState: Player) {
            val loaded =
                source.tx { db ->
                    val metadata =
                        assertNotNull(repository.selectAndCreateMetadataList(db, ACCOUNT_NAME))
                    for (pipeline in pipelines) pipeline.append(db, metadata)
                    Player().also { fresh -> metadata.forEach { it.apply(fresh) } }
                }
            assertNotSame(expected, loaded)
            assertEquals(person.account, loaded.accountId)
            assertEquals(person.character, loaded.characterId)
            assertEquals(expected.coords, loaded.coords)
            assertEquals(expected.displayName, loaded.displayName)
            assertEquals(expected.runEnergy, loaded.runEnergy)
            assertEquals(expected.xpRate, loaded.xpRate)
            assertEquals(expected.members, loaded.members)
            assertEquals(inventoryContents(expected), inventoryContents(loaded))
            assertEquals(
                expected.vars.backing.filterKeys {
                    ServerCacheManager.getVarp(it)?.scope == VarpLifetime.Perm
                },
                loaded.vars.backing.toMap(),
            )
            val attrs =
                expected.attr.toPersistentMap().toMutableMap().apply {
                    put(ACCOUNT_ATTR.persistenceKey!!, accountState.attr[ACCOUNT_ATTR]!!)
                    put(RETAINED_ACCOUNT_ATTR.persistenceKey!!, "retain-when-omitted")
                }
            assertEquals(attrs, loaded.attr.toPersistentMap())
            assertEquals(stats(expected), stats(loaded))
            assertEquals(
                SailingOwnershipState.snapshot(expected),
                SailingOwnershipState.snapshot(loaded),
            )
            source.connection.use { jdbc ->
                jdbc
                    .prepareStatement(
                        "SELECT last_login, last_logout, online_central_world_id, online_session_heartbeat " +
                            "FROM account_characters WHERE id = ?"
                    )
                    .use {
                        it.setInt(1, person.character)
                        it.executeQuery().use { rows ->
                            assertTrue(rows.next())
                            // CharacterAccountApplier intentionally resets in-memory lastLogin.
                            assertEquals(
                                expected.lastLogin,
                                rows.getTimestamp("last_login").toLocalDateTime(),
                            )
                            assertEquals(
                                writtenAt.getValue(expected),
                                rows.getTimestamp("last_logout").toLocalDateTime(),
                            )
                            if (expected.pendingLogout) {
                                assertNull(rows.getObject("online_central_world_id"))
                                assertNull(rows.getObject("online_session_heartbeat"))
                            } else {
                                assertEquals(WORLD_ID, rows.getInt("online_central_world_id"))
                                assertEquals(
                                    writtenAt.getValue(expected),
                                    rows.getTimestamp("online_session_heartbeat").toLocalDateTime(),
                                )
                            }
                            assertFalse(rows.next())
                        }
                    }
            }
        }
    }

    private fun inventoryContents(player: Player): Map<String, List<InvObj?>> =
        player.invMap.values.associate { it.internalName to it.objs.toList() }

    private fun stats(player: Player): Map<Int, Triple<Byte, Byte, Int>> =
        ServerCacheManager.getStats().values.associate { type ->
            val key = RSCM.getReverseMapping(RSCMType.STAT, type.id)
            type.id to
                Triple(
                    player.statMap.getBaseLevel(key),
                    player.statMap.getCurrentLevel(key),
                    player.statMap.getFineXP(key),
                )
        }

    private data class Person(val account: Int, val character: Int)

    private fun seedCharacter(source: DataSource): Person =
        source.tx { db ->
            val account =
                db.prepareStatement(
                        "INSERT INTO accounts(account_name, password_hash) VALUES (?, 'test-only') RETURNING id"
                    )
                    .use {
                        it.setString(1, ACCOUNT_NAME)
                        it.executeQuery().use { rows ->
                            check(rows.next())
                            rows.getInt(1)
                        }
                    }
            db.prepareStatement(
                    "INSERT INTO account_characters(account_id, display_name) VALUES (?, 'Snapshot Initial') RETURNING id"
                )
                .use {
                    it.setInt(1, account)
                    it.executeQuery().use { rows ->
                        check(rows.next())
                        Person(account, rows.getInt(1))
                    }
                }
        }

    private fun assertMigrations(source: DataSource) {
        val required =
            mapOf(
                30 to "V30__character_sailing_boats.sql",
                31 to "V31__daily_adventure_account_attrs.sql",
                43 to "V43__npc_reward_allocations.sql",
                44 to "V44__npc_reward_claims.sql",
                45 to "V45__npc_reward_retirements.sql",
            )
        source.connection.use { jdbc ->
            jdbc
                .prepareStatement(
                    "SELECT script, checksum, success FROM flyway_schema_history WHERE version::integer = ?"
                )
                .use { statement ->
                    for ((version, script) in required) {
                        statement.setInt(1, version)
                        statement.executeQuery().use { rows ->
                            assertTrue(rows.next(), "Native Flyway must discover V$version")
                            assertEquals(script, rows.getString(1))
                            assertNotNull(rows.getObject(2))
                            assertTrue(rows.getBoolean(3))
                            assertFalse(rows.next())
                        }
                    }
                }
            jdbc
                .prepareStatement(
                    "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank"
                )
                .use { statement ->
                    statement.executeQuery().use { rows ->
                        val versions = buildList { while (rows.next()) add(rows.getString(1)) }
                        println(
                            "NPC snapshot fixture native Flyway versions: ${versions.joinToString()}"
                        )
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

    /**
     * Full persisted row values, including timestamps, expose any partial save or replayed write.
     */
    private fun persistedRows(source: DataSource): Map<String, List<String>> =
        source.tx { db ->
            SNAPSHOT_TABLES.associateWith { table ->
                db.prepareStatement(
                        "SELECT to_jsonb(saved)::text FROM $table saved ORDER BY to_jsonb(saved)::text"
                    )
                    .use { statement ->
                        statement.executeQuery().use { rows ->
                            buildList { while (rows.next()) add(rows.getString(1)) }
                        }
                    }
            }
        }

    private fun consumed(source: DataSource, lifeId: UUID): Long =
        source.tx { db ->
            db.prepareStatement(
                    "SELECT COALESCE(SUM(count), 0) FROM npc_reward_claim_consumption WHERE life_id = ?"
                )
                .use {
                    it.setObject(1, lifeId)
                    it.executeQuery().use { rows ->
                        check(rows.next())
                        rows.getLong(1)
                    }
                }
        }

    private companion object {
        private const val ACCOUNT_NAME = "snapshot-fixture"
        private const val WORLD_ID = 255
        private const val TIMEOUT = 5
        private const val COIN_VARS = 4321
        private val CHARACTER_ATTR =
            AttributeKey<Map<String, Any>>(persistenceKey = "fixture.snapshot")
        private val OMITTED_CHARACTER_ATTR =
            AttributeKey<String>(persistenceKey = "fixture.omitted")
        private val ACCOUNT_ATTR =
            AttributeKey<Map<String, Any>>(persistenceKey = "account.fixture.snapshot")
        private val RETAINED_ACCOUNT_ATTR =
            AttributeKey<String>(persistenceKey = "account.fixture.retained")
        private val INVENTORIES = listOf("inv.inv", "inv.bank", "inv.worn", "inv.looting_bag")
        private val SNAPSHOT_TABLES =
            listOf(
                "accounts",
                "account_characters",
                "character_varps",
                "character_attrs",
                "account_attrs",
                "inventories",
                "inventory_objs",
                "stats",
                "character_sailing_boats",
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

package org.rsmod.content.bosses.royaltitans

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.sql.DataSource
import kotlin.test.*
import org.rsmod.api.db.DatabaseConnection

class RoyalTitansStatisticsTest {
    @Test
    fun `committed solo duo duplicate rollback concurrency reconnect and two database restarts`() {
        val directory =
            Files.createTempDirectory(
                    Files.createDirectories(Path.of("logs")),
                    "royal-titans-statistics-test-",
                )
                .toAbsolutePath()
        val builder =
            EmbeddedPostgres.builder()
                .setDataDirectory(directory.resolve("data"))
                .setOverrideWorkingDirectory(directory.resolve("binaries").toFile())
                .setCleanDataDirectory(false)
        val a = RoyalTitansFinisher(1, "Branda Last", true)
        val b = RoyalTitansFinisher(2, "Eldric Last", true)
        val first = RoyalTitansCompletion(UUID.randomUUID(), 534, listOf(a, b))
        val deathId = UUID.randomUUID()
        var expectedKills = 0
        builder.start().use { postgres ->
            val source = postgres.postgresDatabase
            source.connection.use { jdbc ->
                jdbc.createStatement().use {
                    it.execute("CREATE TABLE accounts (id INTEGER PRIMARY KEY)")
                    it.execute("INSERT INTO accounts(id) VALUES (1), (2), (3)")
                    val sql =
                        requireNotNull(
                                javaClass.getResourceAsStream(
                                    "/db/migration/V42__royal_titans_statistics.sql"
                                )
                            )
                            .bufferedReader()
                            .use { reader -> reader.readText() }
                    it.execute(sql)
                }
            }
            assertEquals(
                RoyalTitansStatisticsSnapshot(),
                source.tx { RoyalTitansStatisticsStore.snapshot(it, 1) },
            )
            val receipt = source.tx { RoyalTitansStatisticsStore.complete(it, first) }
            assertEquals(1, receipt.getValue(1).snapshot.personalKills)
            assertEquals(1, receipt.getValue(2).snapshot.globalKills)
            assertTrue(receipt.values.all { it.newPersonalBest })
            repeat(5) {
                assertEquals(
                    receipt,
                    source.tx {
                        RoyalTitansStatisticsStore.complete(
                            it,
                            first.copy(participants = first.participants.reversed()),
                        )
                    },
                )
            }
            assertFailsWith<IllegalStateException> {
                source.tx {
                    RoyalTitansStatisticsStore.complete(it, first.copy(elapsedTicks = 533))
                }
            }
            assertFailsWith<IllegalStateException> {
                source.tx {
                    RoyalTitansStatisticsStore.complete(it, first.copy(participants = listOf(a)))
                }
            }
            source.tx { RoyalTitansStatisticsStore.death(it, deathId, 1, first.encounterId) }
            source.tx { RoyalTitansStatisticsStore.death(it, deathId, 1, first.encounterId) }
            assertFailsWith<IllegalStateException> {
                source.tx { RoyalTitansStatisticsStore.death(it, deathId, 2, first.encounterId) }
            }

            // A complete rolled-back transaction leaves neither global nor participant rows.
            val rolledBack = RoyalTitansCompletion(UUID.randomUUID(), 10, listOf(a))
            assertFailsWith<IllegalStateException> {
                source.tx {
                    RoyalTitansStatisticsStore.complete(it, rolledBack)
                    error("Simulated failure before commit")
                }
            }
            assertEquals(1, source.tx { RoyalTitansStatisticsStore.snapshot(it, 1).globalKills })

            // Readers must see neither half of an uncommitted completion.
            val second = RoyalTitansCompletion(UUID.randomUUID(), 600, listOf(a, b))
            source.connection.use { jdbc ->
                jdbc.autoCommit = false
                val response = RoyalTitansStatisticsStore.complete(DatabaseConnection(jdbc), second)
                assertFalse(response.getValue(1).newPersonalBest)
                assertEquals(
                    1,
                    source.tx { RoyalTitansStatisticsStore.snapshot(it, 1).globalKills },
                )
                jdbc.commit()
            }

            val events =
                (0 until 8).map { RoyalTitansCompletion(UUID.randomUUID(), 300 + it, listOf(b, a)) }
            val executor = Executors.newFixedThreadPool(4)
            try {
                val calls =
                    (events + events).map { event ->
                        Callable { source.tx { RoyalTitansStatisticsStore.complete(it, event) } }
                    }
                executor.invokeAll(calls, 30, TimeUnit.SECONDS).forEach { it.get() }
            } finally {
                executor.shutdownNow()
            }
            expectedKills = 10
            val snapshot = source.tx { RoyalTitansStatisticsStore.snapshot(it, 1) }
            assertEquals(expectedKills, snapshot.personalKills)
            assertEquals(expectedKills, snapshot.globalKills)
            assertEquals(1, snapshot.personalDeaths)
            assertEquals(1, snapshot.globalDeaths)
            assertEquals(300, snapshot.personalBestTicks)
            assertEquals(300, snapshot.globalBestTicks)
            assertEquals("Branda Last", snapshot.globalRecordHolder)
            val other = source.tx { RoyalTitansStatisticsStore.snapshot(it, 3) }
            assertEquals(0, other.personalKills)
            assertNull(other.personalBestTicks)
            assertEquals(snapshot.globalKills, other.globalKills)
        }
        // Reuse only this newly allocated test cluster, never the live .data/postgres directory.
        repeat(2) { restart ->
            builder.start().use { postgres ->
                val source = postgres.postgresDatabase
                val snapshot = source.tx { RoyalTitansStatisticsStore.snapshot(it, 2) }
                assertEquals(expectedKills, snapshot.personalKills)
                assertEquals(0, snapshot.personalDeaths)
                assertEquals(1, snapshot.globalDeaths)
                source.tx { RoyalTitansStatisticsStore.complete(it, first) }
                source.tx { RoyalTitansStatisticsStore.death(it, deathId, 1, first.encounterId) }
                assertEquals(snapshot, source.tx { RoyalTitansStatisticsStore.snapshot(it, 2) })
                if (restart == 1) {
                    source.tx {
                        RoyalTitansStatisticsStore.complete(
                            it,
                            RoyalTitansCompletion(UUID.randomUUID(), 10, emptyList()),
                        )
                    }
                    val emptyRecord = source.tx { RoyalTitansStatisticsStore.snapshot(it, 3) }
                    assertEquals(10, emptyRecord.globalBestTicks)
                    assertNull(
                        emptyRecord.globalRecordHolder,
                        "Never name a slower completion's participant",
                    )
                    source.tx {
                        RoyalTitansStatisticsStore.complete(
                            it,
                            RoyalTitansCompletion(
                                UUID.randomUUID(),
                                8,
                                listOf(RoyalTitansFinisher(3, "No personal record", false)),
                            ),
                        )
                    }
                    val ineligible = source.tx { RoyalTitansStatisticsStore.snapshot(it, 3) }
                    assertEquals(1, ineligible.personalKills)
                    assertNull(ineligible.personalBestTicks)
                    assertEquals(8, ineligible.globalBestTicks)
                    assertEquals("No personal record", ineligible.globalRecordHolder)
                }
            }
        }
    }

    @Test
    fun `scoreboard text and time messages use exact labels and hundredths`() {
        assertEquals("-", RoyalTitansTime.format(null))
        assertEquals("0:00.60", RoyalTitansTime.format(1))
        assertEquals("1:30.00", RoyalTitansTime.format(150))
        assertEquals("5:20.40", RoyalTitansTime.format(534))
        assertEquals(534, RoyalTitansTime.elapsed(1271, 1806))
        assertEquals(31, RoyalTitansTime.elapsed(237, 269))
        assertEquals(34, RoyalTitansTime.elapsed(1995, 2030))
        assertEquals("59:59.40", RoyalTitansTime.format(5999))
        val snapshot = RoyalTitansStatisticsSnapshot(12, 3, 534, 20, 5, 150)
        assertEquals(
            listOf("Royal Titans Statistics", "12", "3", "5:20.40", "20", "5", "1:30.00"),
            RoyalTitansScoreboard.texts(snapshot).values.toList(),
        )
        assertEquals(
            listOf("Royal Titans Statistics", "22", "0", "0:10.80", "58,395,586", "967,404", "-"),
            RoyalTitansScoreboard.texts(
                    RoyalTitansStatisticsSnapshot(22, 0, 18, 58_395_586, 967_404)
                )
                .values
                .toList(),
        )
        assertEquals(
            "Fight duration: <col=ff0000>5:20.40</col> (new personal best)",
            RoyalTitansTime.durationMessage(534, RoyalTitansCompletionReceipt(snapshot, true)),
        )
        assertEquals(
            "Fight duration: <col=ff0000>6:00.00</col>. Personal best: 5:20.40",
            RoyalTitansTime.durationMessage(600, RoyalTitansCompletionReceipt(snapshot, false)),
        )
    }

    private fun <T> DataSource.tx(block: (DatabaseConnection) -> T): T =
        connection.use { jdbc ->
            jdbc.autoCommit = false
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

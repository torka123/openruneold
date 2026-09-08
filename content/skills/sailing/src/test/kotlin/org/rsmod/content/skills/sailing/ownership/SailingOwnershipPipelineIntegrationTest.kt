package org.rsmod.content.skills.sailing.ownership

import dev.or2.central.account.AccountData
import dev.or2.central.account.CharacterData
import dev.or2.central.account.Rights
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.rsmod.api.account.character.CharacterMetadataList
import org.rsmod.api.db.DatabaseConnection
import org.rsmod.game.entity.Player

class SailingOwnershipPipelineIntegrationTest {
    @Test
    fun `migration and pipeline round trip symbolic boat ownership`() {
        EmbeddedPostgres.start().use { postgres ->
            postgres.postgresDatabase.connection.use { jdbc ->
                jdbc.autoCommit = false
                jdbc.createStatement().use { statement ->
                    statement.execute("CREATE TABLE account_characters (id INTEGER PRIMARY KEY)")
                    statement.execute(MIGRATION_PATH.readText())
                    statement.execute("INSERT INTO account_characters (id) VALUES ($CHARACTER_ID)")
                }
                jdbc.commit()

                val validator =
                    SailingOwnershipValidator(
                        setOf(
                            "dbrow.sailing_boat_raft",
                            "dbrow.sailing_boat_skiff",
                            "dbrow.sailing_boat_sloop",
                        )
                    )
                val pipeline = SailingOwnershipPipeline(SailingOwnershipApplier(), validator)
                val expected =
                    validator.validate(
                        listOf(
                            SailingOwnedBoat(3, "dbrow.sailing_boat_sloop"),
                            SailingOwnedBoat(0, "dbrow.sailing_boat_raft"),
                            SailingOwnedBoat(1, "dbrow.sailing_boat_skiff"),
                        )
                    )
                val source = Player()
                SailingOwnershipState.attach(source, expected)

                pipeline.save(DatabaseConnection(jdbc), source, CHARACTER_ID)
                jdbc.commit()

                val stored = mutableListOf<SailingOwnedBoat>()
                jdbc
                    .prepareStatement(
                        """
                        SELECT slot, boat_type, format_version
                        FROM character_sailing_boats
                        WHERE character_id = ?
                        ORDER BY slot
                        """
                    )
                    .use { statement ->
                        statement.setInt(1, CHARACTER_ID)
                        statement.executeQuery().use { rows ->
                            while (rows.next()) {
                                stored +=
                                    SailingOwnedBoat(
                                        rows.getInt("slot"),
                                        rows.getString("boat_type"),
                                        rows.getInt("format_version"),
                                    )
                            }
                        }
                    }
                assertEquals(expected.boats, stored)

                val metadata = CharacterMetadataList(accountData(), mutableListOf())
                pipeline.append(DatabaseConnection(jdbc), metadata)
                val loaded = Player()
                metadata.forEach { transformer -> transformer.apply(loaded) }
                assertEquals(expected, SailingOwnershipState.snapshot(loaded))

                jdbc.createStatement().use { statement ->
                    statement.executeUpdate(
                        """
                        UPDATE character_sailing_boats
                        SET boat_type = 'dbrow.sailing_boat_unknown'
                        WHERE character_id = $CHARACTER_ID AND slot = 0
                        """
                    )
                }
                jdbc.commit()
                assertFailsWith<IllegalArgumentException> {
                    pipeline.append(
                        DatabaseConnection(jdbc),
                        CharacterMetadataList(accountData(), mutableListOf()),
                    )
                }
            }
        }
    }

    private fun accountData(): AccountData =
        AccountData(
            accountId = 1,
            accountName = "sailing-persistence-test",
            rights = Rights.NONE,
            email = null,
            characterData =
                CharacterData(
                    characterId = CHARACTER_ID,
                    displayName = "Sailor",
                    previousDisplayName = null,
                    displayNameChangedAtMillis = null,
                    members = true,
                    modLevel = null,
                    worldId = null,
                    coordX = 0,
                    coordZ = 0,
                    coordLevel = 0,
                    varps = emptyMap(),
                    createdAt = null,
                    lastLogin = null,
                    lastLogout = null,
                    mutedUntil = null,
                    bannedUntil = null,
                    runEnergy = 10_000,
                    xpRate = 1.0,
                    attrs = emptyMap(),
                ),
        )

    private companion object {
        private const val CHARACTER_ID = 42
        private val MIGRATION_PATH =
            Path.of("api/net/src/main/resources/db/migration/V30__character_sailing_boats.sql")
    }
}

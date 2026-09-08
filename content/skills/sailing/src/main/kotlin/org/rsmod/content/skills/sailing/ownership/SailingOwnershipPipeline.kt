package org.rsmod.content.skills.sailing.ownership

import jakarta.inject.Inject
import org.rsmod.api.account.character.CharacterDataStage
import org.rsmod.api.account.character.CharacterMetadataList
import org.rsmod.api.db.DatabaseConnection
import org.rsmod.game.entity.Player

class SailingOwnershipApplier @Inject constructor() :
    CharacterDataStage.Applier<SailingOwnershipData> {
    override fun apply(player: Player, data: SailingOwnershipData) {
        SailingOwnershipState.attach(player, data.snapshot)
    }
}

class SailingOwnershipPipeline
@Inject
constructor(
    private val applier: SailingOwnershipApplier,
    private val validator: SailingOwnershipValidator,
) : CharacterDataStage.Pipeline {
    override fun append(connection: DatabaseConnection, metadata: CharacterMetadataList) {
        val boats = ArrayList<SailingOwnedBoat>(SailingOwnershipValidator.MAX_OWNED_BOATS)
        connection.prepareStatement(SELECT_SQL).use { statement ->
            statement.setInt(1, metadata.characterId)
            statement.executeQuery().use { rows ->
                while (rows.next()) {
                    boats +=
                        SailingOwnedBoat(
                            slot = rows.getInt("slot"),
                            boatTypeKey = rows.getString("boat_type"),
                            formatVersion = rows.getInt("format_version"),
                        )
                }
            }
        }
        metadata.add(applier, SailingOwnershipData(validator.validate(boats)))
    }

    override fun save(connection: DatabaseConnection, player: Player, characterId: Int) {
        val snapshot = validator.validate(SailingOwnershipState.snapshot(player).boats)
        connection.prepareStatement(DELETE_SQL).use { statement ->
            statement.setInt(1, characterId)
            statement.executeUpdate()
        }
        if (snapshot.boats.isEmpty()) {
            return
        }
        connection.prepareStatement(INSERT_SQL).use { statement ->
            for (boat in snapshot.boats) {
                statement.setInt(1, characterId)
                statement.setInt(2, boat.slot)
                statement.setString(3, boat.boatTypeKey)
                statement.setInt(4, boat.formatVersion)
                statement.addBatch()
            }
            statement.executeBatch()
        }
    }

    private companion object {
        private const val SELECT_SQL =
            """
            SELECT slot, boat_type, format_version
            FROM character_sailing_boats
            WHERE character_id = ?
            ORDER BY slot
            """

        private const val DELETE_SQL = "DELETE FROM character_sailing_boats WHERE character_id = ?"

        private const val INSERT_SQL =
            """
            INSERT INTO character_sailing_boats
                (character_id, slot, boat_type, format_version)
            VALUES (?, ?, ?, ?)
            """
    }
}

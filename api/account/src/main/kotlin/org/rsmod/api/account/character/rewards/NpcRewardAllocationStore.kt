package org.rsmod.api.account.character.rewards

import dev.or2.sql.OpenRuneSql
import java.util.UUID
import org.rsmod.api.db.DatabaseConnection
import org.rsmod.map.CoordGrid

/** SQL only: the existing caller owns commit/rollback and any retry. */
internal object NpcRewardAllocationStore {
    fun record(db: DatabaseConnection, allocation: NpcRewardAllocation): NpcRewardAllocation {
        val inserted =
            db.prepareStatement(sql("insert_life")).use {
                it.setObject(1, allocation.lifeId)
                it.setString(2, allocation.sourceNpcKey)
                it.executeUpdate() == 1
            }
        // One life row serializes even competing payloads with different recipient sets.
        db.prepareStatement(sql("lock_life")).use {
            it.setObject(1, allocation.lifeId)
            it.executeQuery().use { rows ->
                check(rows.next()) { "NPC reward life disappeared during allocation" }
                check(rows.getString(1) == allocation.sourceNpcKey) {
                    "NPC reward source payload changed for ${allocation.lifeId}"
                }
            }
        }
        if (inserted) {
            lockRecipients(db, allocation)
            insertRecipients(db, allocation)
        }
        val stored = checkNotNull(find(db, allocation.lifeId))
        check(stored == allocation) {
            "NPC reward allocation payload changed for ${allocation.lifeId}"
        }
        return stored
    }

    private fun lockRecipients(db: DatabaseConnection, allocation: NpcRewardAllocation) {
        // Keep parent keys stable without blocking native saves' account-attribute FK checks.
        // Saves update characters before those checks, so an exclusive parent lock could deadlock.
        // Lock every parent account first, then characters, in stable order across parties.
        db.prepareStatement(sql("lock_account")).use { statement ->
            for (accountId in allocation.recipients.map { it.accountId }.distinct().sorted()) {
                statement.setInt(1, accountId)
                statement.executeQuery().use { rows ->
                    check(rows.next()) { "Missing NPC reward account $accountId" }
                }
            }
        }
        db.prepareStatement(sql("lock_character")).use { statement ->
            for (recipient in allocation.recipients) {
                statement.setInt(1, recipient.characterId)
                statement.executeQuery().use { rows ->
                    check(rows.next() && rows.getInt(1) == recipient.accountId) {
                        "NPC reward character ${recipient.characterId} does not belong to account ${recipient.accountId}"
                    }
                }
            }
        }
    }

    private fun insertRecipients(db: DatabaseConnection, allocation: NpcRewardAllocation) {
        db.prepareStatement(sql("insert_recipient")).use { recipientStatement ->
            db.prepareStatement(sql("insert_item")).use { itemStatement ->
                for (recipient in allocation.recipients) {
                    recipientStatement.setObject(1, allocation.lifeId)
                    recipientStatement.setInt(2, recipient.characterId)
                    recipientStatement.setInt(3, recipient.accountId)
                    recipientStatement.setInt(4, recipient.coords.packed)
                    recipientStatement.setInt(5, recipient.duration)
                    check(recipientStatement.executeUpdate() == 1)
                    for ((ordinal, item) in recipient.items.withIndex()) {
                        itemStatement.setObject(1, allocation.lifeId)
                        itemStatement.setInt(2, recipient.characterId)
                        itemStatement.setInt(3, ordinal)
                        itemStatement.setString(4, item.objKey)
                        itemStatement.setInt(5, item.count)
                        itemStatement.setInt(6, item.vars)
                        check(itemStatement.executeUpdate() == 1)
                    }
                }
            }
        }
    }

    fun find(db: DatabaseConnection, lifeId: UUID): NpcRewardAllocation? =
        db.prepareStatement(sql("select_allocation")).use { statement ->
            statement.setObject(1, lifeId)
            statement.executeQuery().use { rows ->
                if (!rows.next()) return null
                val source = rows.getString("source_npc_key")
                val recipients = mutableListOf<NpcRewardAllocation.Recipient>()
                var recipientId: Int? = null
                var accountId = 0
                var coords = CoordGrid(0)
                var duration = 0
                val items = mutableListOf<NpcRewardAllocation.Item>()
                fun finishRecipient() {
                    val id = recipientId ?: return
                    recipients +=
                        NpcRewardAllocation.Recipient(accountId, id, coords, duration, items)
                }
                do {
                    val characterId = rows.getObject("character_id", Integer::class.java)?.toInt()
                    if (characterId == null) continue // Recorded life with no recipients.
                    check(rows.getString("destination") == "personal_ground") {
                        "Unsupported stored NPC reward destination"
                    }
                    if (recipientId != characterId) {
                        finishRecipient()
                        items.clear()
                        recipientId = characterId
                        accountId = rows.getInt("account_id")
                        coords = CoordGrid(rows.getInt("drop_coords"))
                        duration = rows.getInt("duration")
                    }
                    val ordinal = rows.getObject("ordinal", Integer::class.java)?.toInt()
                    if (ordinal != null) {
                        check(ordinal == items.size) {
                            "Stored NPC reward ordinals are not consecutive"
                        }
                        items +=
                            NpcRewardAllocation.Item(
                                rows.getString("obj_key"),
                                rows.getInt("count"),
                                rows.getInt("vars"),
                            )
                    }
                } while (rows.next())
                finishRecipient()
                NpcRewardAllocation(lifeId, source, recipients)
            }
        }

    private fun sql(name: String): String = OpenRuneSql.text("game/npc_rewards/$name.sql")
}

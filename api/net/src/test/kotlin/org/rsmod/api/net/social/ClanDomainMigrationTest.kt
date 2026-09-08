package org.rsmod.api.net.social

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClanDomainMigrationTest {
    @Test
    fun `v33 persists every canonical clan subsystem`() {
        val sql = migrationSql()
        for (table in
            listOf(
                "clan_creation_drafts",
                "clan_creation_founder_invitations",
                "clan_interests",
                "clan_permission_settings",
                "clan_events",
                "clan_coffers",
                "clan_coffer_transactions",
                "clan_hall_settings",
                "clan_hall_party_chest_items",
                "clan_broadcast_settings",
                "clan_broadcast_permissions",
                "clan_member_notification_preferences",
                "clan_recruiter_notifications",
            )) {
            assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS $table"), "Missing table $table")
        }
        assertTrue(sql.contains("motif_primary_colour SMALLINT"))
        assertTrue(sql.contains("motif_secondary_colour SMALLINT"))
        assertTrue(sql.contains("motif_primary_symbol SMALLINT"))
        assertTrue(sql.contains("motif_secondary_symbol SMALLINT"))
        assertTrue(sql.contains("duration_ticks BIGINT"))
        assertFalse(sql.contains("duration_days"))
        assertTrue(sql.contains("rank_slot BETWEEN 0 AND 23"))
    }

    @Test
    fun `v33 uses only explicit revision 240 rank bytes`() {
        val sql = migrationSql()
        for (rank in ClanRanks.CUSTOM_RANKS) {
            assertTrue(Regex("(?<![0-9])$rank(?![0-9])").containsMatchIn(sql), "Missing rank $rank")
        }
        assertTrue(sql.contains("100, 101, 102, 103, 104, 105, 110, 115, 120, 124, 125, 126"))
        assertFalse(sql.contains("rank BETWEEN 0 AND 14"))
        assertFalse(sql.contains("rank BETWEEN 100 AND 109"))
    }

    @Test
    fun `v33 defers exactly one owner and exactly five founder validation`() {
        val sql = migrationSql()
        assertTrue(sql.contains("owner_count <> 1"))
        assertTrue(sql.contains("ranked_owner IS DISTINCT FROM header_owner"))
        assertTrue(sql.contains("CREATE CONSTRAINT TRIGGER clan_members_canonical_state"))
        assertTrue(sql.contains("CREATE CONSTRAINT TRIGGER clans_canonical_owner_state"))
        assertTrue(sql.contains("DEFERRABLE INITIALLY DEFERRED"))
        assertTrue(sql.contains("founder_count <> 5"))
        assertTrue(sql.contains("CREATE CONSTRAINT TRIGGER clan_founders_exact_count"))
    }

    @Test
    fun `v33 carries hard storage and workflow limits`() {
        val sql = migrationSql()
        assertTrue(sql.contains("balance BETWEEN 0 AND 50000000"))
        assertTrue(sql.contains("player_capacity BETWEEN 1 AND 2000"))
        assertTrue(sql.contains("item_count > 216"))
        assertTrue(sql.contains("ban_count > 100"))
        assertTrue(sql.contains("member_count > 500"))
        assertTrue(sql.contains("edit_session_expires_at > edit_session_started_at"))
        for (permission in ClanPermissionKey.entries) {
            assertTrue(sql.contains("'${permission.storageKey}'"), permission.storageKey)
        }
        for (broadcast in ClanBroadcastKey.entries) {
            assertTrue(sql.contains("'${broadcast.storageKey}'"), broadcast.storageKey)
        }
    }

    @Test
    fun `v36 persists every independent native notification control`() {
        val sql =
            checkNotNull(
                    javaClass.getResourceAsStream(
                        "/db/migration/V36__clan_native_broadcast_controls.sql"
                    )
                )
                .bufferedReader()
                .use { it.readText() }

        assertTrue(sql.contains("event_starting_notifications BOOLEAN"))
        assertTrue(sql.contains("level_every_ten_minimum SMALLINT"))
        assertTrue(sql.contains("level_every_five_minimum SMALLINT"))
        assertTrue(sql.contains("level_every_one_minimum SMALLINT"))
        assertTrue(sql.contains("CASE WHEN level_interval = 10 THEN 50"))
    }

    @Test
    fun `v37 permits native all combat achievement task threshold`() {
        val sql =
            checkNotNull(
                    javaClass.getResourceAsStream(
                        "/db/migration/V37__clan_ca_task_all_threshold.sql"
                    )
                )
                .bufferedReader()
                .use { it.readText() }
        assertTrue(sql.contains("BETWEEN -1 AND 5"))
    }

    @Test
    fun `v38 persists native applicant stat snapshots with legacy-safe nullable fields`() {
        val sql =
            checkNotNull(
                    javaClass.getResourceAsStream(
                        "/db/migration/V38__clan_application_skill_snapshots.sql"
                    )
                )
                .bufferedReader()
                .use { it.readText() }

        assertTrue(sql.contains("combat_level SMALLINT"))
        assertTrue(sql.contains("total_level SMALLINT"))
        assertTrue(sql.contains("quest_points SMALLINT"))
        assertTrue(sql.contains("is_iron BOOLEAN"))
        assertTrue(sql.contains("is_ultimate_iron BOOLEAN"))
        assertTrue(sql.contains("is_hardcore_iron BOOLEAN"))
        assertTrue(sql.contains("skill_levels VARCHAR(95)"))
    }

    @Test
    fun `v41 permits one to five persisted clan founders`() {
        val sql =
            checkNotNull(
                    javaClass.getResourceAsStream(
                        "/db/migration/V41__allow_one_to_five_clan_founders.sql"
                    )
                )
                .bufferedReader()
                .use { it.readText() }

        assertTrue(sql.contains("founder_count NOT BETWEEN 1 AND 5"))
        assertFalse(sql.contains("founder_count <> 5"))
    }

    private fun migrationSql(): String =
        checkNotNull(javaClass.getResourceAsStream("/db/migration/V33__canonical_clan_domain.sql"))
            .bufferedReader()
            .use { it.readText() }
}

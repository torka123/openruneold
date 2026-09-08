package org.rsmod.api.net.social

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClanHallPersistenceContractTest {
    @Test
    fun `v34 keeps party chest cleanup recoverable and filters inactive rows`() {
        val sql =
            checkNotNull(
                    javaClass.getResourceAsStream(
                        "/db/migration/V34__clan_hall_party_chest_cleanup_archive.sql"
                    )
                )
                .bufferedReader()
                .use { it.readText() }

        assertTrue(sql.contains("ADD COLUMN IF NOT EXISTS active BOOLEAN"))
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS clan_hall_party_chest_cleanup_archive"))
        assertTrue(sql.contains("hall_session_id VARCHAR(64)"))
        assertTrue(sql.contains("cleanup_reason VARCHAR(64)"))
        assertTrue(sql.contains("WHERE clan_id = NEW.clan_id AND active"))
        assertFalse(sql.contains("DELETE FROM clan_hall_party_chest_items"))
    }

    @Test
    fun `repository archives then deactivates live party chest contents`() {
        val source =
            Files.readString(
                Path.of("src/main/kotlin/org/rsmod/api/net/social/ClanDomainRepository.kt")
            )

        assertTrue(source.contains("public fun archivePartyChestCleanup("))
        assertTrue(source.contains("INSERT INTO clan_hall_party_chest_cleanup_archive"))
        assertTrue(source.contains("UPDATE clan_hall_party_chest_items SET active = FALSE"))
        assertTrue(source.contains("WHERE clan_id = ? AND active"))
        assertTrue(source.contains("deposited_at = CURRENT_TIMESTAMP, active = TRUE"))
    }

    @Test
    fun `hall edit lease is acquired and released under a row lock`() {
        val source =
            Files.readString(
                Path.of("src/main/kotlin/org/rsmod/api/net/social/ClanDomainRepository.kt")
            )

        assertTrue(source.contains("public fun beginHallEditSession("))
        assertTrue(source.contains("public fun releaseHallEditSession("))
        assertTrue(source.contains("ClanPermissionKey.HallEditSettings"))
        assertTrue(
            source.contains("SELECT clan_id FROM clan_hall_settings WHERE clan_id = ? FOR UPDATE")
        )
        assertTrue(source.contains("edit_session_character_id = ?"))
        assertTrue(source.contains("edit_session_character_id = NULL"))
        assertTrue(source.contains("ClanHallEditSessionRules.ownsLease"))
    }
}

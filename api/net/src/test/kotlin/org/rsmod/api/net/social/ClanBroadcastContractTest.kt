package org.rsmod.api.net.social

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.rsmod.game.stat.PlayerSkillXPTable

class ClanBroadcastContractTest {
    @Test
    fun `post 99 milestones use the configured absolute xp interval`() {
        val level99Xp = PlayerSkillXPTable.getXPFromLevel(99)

        assertFalse(ClanBroadcastRules.shouldBroadcastPost99Xp(level99Xp - 1, level99Xp, 1_000_000))
        assertFalse(ClanBroadcastRules.shouldBroadcastPost99Xp(level99Xp, 19_999_999, 10_000_000))
        assertTrue(ClanBroadcastRules.shouldBroadcastPost99Xp(19_999_999, 20_000_000, 10_000_000))
        assertFalse(ClanBroadcastRules.shouldBroadcastPost99Xp(20_000_000, 20_500_000, 10_000_000))
    }

    @Test
    fun `loot thresholds are disabled at zero and inclusive when configured`() {
        assertFalse(
            ClanBroadcastRules.shouldBroadcastLoot(totalValue = 1_000_000L, minimumValue = 0)
        )
        assertFalse(
            ClanBroadcastRules.shouldBroadcastLoot(totalValue = 999_999L, minimumValue = 1_000_000)
        )
        assertTrue(
            ClanBroadcastRules.shouldBroadcastLoot(
                totalValue = 1_000_000L,
                minimumValue = 1_000_000,
            )
        )
    }

    @Test
    fun `broadcast outbox insert gates local delivery and is request idempotent`() {
        val events =
            Files.readString(
                Path.of("src/main/kotlin/org/rsmod/api/net/social/SocialChatEventRepository.kt")
            )
        val service =
            Files.readString(Path.of("src/main/kotlin/org/rsmod/api/net/social/ClanChatService.kt"))

        assertTrue(events.contains("ON CONFLICT (request_id) DO NOTHING"))
        assertTrue(events.contains("statement.executeUpdate() == 1"))
        assertTrue(service.contains("if (inserted) ClanBroadcastDelivery"))
        assertTrue(service.contains("UUID.nameUUIDFromBytes"))
    }

    @Test
    fun `broadcast delivery selects opted in ranked members and never guest viewers`() {
        val repository =
            Files.readString(
                Path.of("src/main/kotlin/org/rsmod/api/net/social/ClanChatRepository.kt")
            )

        assertTrue(repository.contains("fun broadcastRecipientIds"))
        assertTrue(repository.contains("FROM clan_members m"))
        assertTrue(repository.contains("clan_member_notification_preferences p"))
        assertTrue(repository.contains("p.broadcasts_enabled"))
        assertFalse(
            repository
                .substringAfter("fun broadcastRecipientIds")
                .substringBefore("public fun")
                .contains("clan_guests")
        )
    }

    @Test
    fun `generic system and membership mutations publish through one delivery path`() {
        val methods = ClanChatService::class.java.methods.mapTo(HashSet()) { it.name }
        val chat =
            Files.readString(Path.of("src/main/kotlin/org/rsmod/api/net/social/ClanChatService.kt"))
        val management =
            Files.readString(
                Path.of("src/main/kotlin/org/rsmod/api/net/social/ClanManagementService.kt")
            )

        assertTrue("publishSystemBroadcast" in methods)
        assertTrue("publishMandatorySystemNotice" in methods)
        assertTrue(chat.contains("clan-system-"))
        assertTrue(chat.contains("if (mandatory) repository.rankedMemberIds"))
        assertTrue(chat.contains("else repository.broadcastRecipientIds"))
        assertTrue(chat.contains("message = \"was expelled from the clan.\""))
        assertTrue(management.contains("message = \"has joined the clan.\""))
        assertTrue(management.contains("message = \"has left the clan.\""))
        assertTrue(management.contains("membership:join:"))
        assertTrue(management.contains("membership:leave:"))
    }

    @Test
    fun `real personal best hardcore and post 99 sources feed clan broadcasts`() {
        val network =
            Files.readString(Path.of("src/main/kotlin/org/rsmod/api/net/rsprot/NetworkScript.kt"))
        val hooks =
            Files.readString(
                Path.of("src/main/kotlin/org/rsmod/api/net/social/ClanBroadcastHooks.kt")
            )

        assertTrue(network.contains("onEvent<InstancePersonalBestEvent>"))
        assertTrue(network.contains("ClanBroadcastKey.PersonalBest"))
        assertTrue(network.contains("onStatXpMilestone"))
        assertTrue(network.contains("ClanBroadcastKey.Post99Xp"))
        assertTrue(hooks.contains("context.isHardcoreIronman"))
        assertTrue(hooks.contains("context.player.hasSafeDeathMark()"))
        assertTrue(hooks.contains("ClanBroadcastKey.HardcoreStatus"))
    }

    @Test
    fun `event reminders are durable opted in rank filtered login and periodically refreshed`() {
        val repository =
            Files.readString(
                Path.of("src/main/kotlin/org/rsmod/api/net/social/ClanChatRepository.kt")
            )
        val service =
            Files.readString(Path.of("src/main/kotlin/org/rsmod/api/net/social/ClanChatService.kt"))
        val network =
            Files.readString(Path.of("src/main/kotlin/org/rsmod/api/net/rsprot/NetworkScript.kt"))
        val migration =
            Files.readString(
                Path.of("src/main/resources/db/migration/V39__clan_event_notification_claims.sql")
            )

        assertTrue(repository.contains("EVENT_STARTING_REMINDER_SECONDS: Long = 60L * 60L"))
        assertTrue(repository.contains("p.event_reminders_enabled"))
        assertTrue(repository.contains("b.event_starting_notifications"))
        assertTrue(repository.contains("e.minimum_join_rank <= m.rank"))
        assertTrue(repository.contains("ON CONFLICT (event_id, character_id) DO NOTHING"))
        assertTrue(service.contains("claimStartingEventReminders(connection, characterId)"))
        assertTrue(service.contains("fun pollEventReminders()"))
        assertTrue(service.contains("starting within the next hour"))
        assertTrue(service.contains("UTC on world"))
        assertTrue(network.contains("clanChat.pollEventReminders()"))
        assertTrue(migration.contains("PRIMARY KEY (event_id, character_id)"))
        assertFalse(service.contains("in the next 24 hours"))
    }
}

package org.rsmod.content.minigames.clanmayhem

import jakarta.inject.Singleton
import java.nio.charset.StandardCharsets
import java.util.UUID
import org.rsmod.api.attr.AttributeKey
import org.rsmod.game.entity.Player

internal val MAYHEM_CLAN_KEY: AttributeKey<MutableList<Int>> =
    AttributeKey(persistenceKey = "clan_mayhem_clan_identity_v1")
internal val MAYHEM_CLAN_NAME: AttributeKey<String> =
    AttributeKey(persistenceKey = "clan_mayhem_clan_name_v1")
internal val MAYHEM_CLAN_MANAGER: AttributeKey<Boolean> =
    AttributeKey(persistenceKey = "clan_mayhem_clan_manager_v1")

/**
 * OpenRune currently has no server-side clan membership service. This narrow persistent bridge is
 * intentionally isolated behind one service so a future central clan module can replace it without
 * changing roster or combat code.
 */
@Singleton
internal class ClanMayhemClanDirectory {
    fun identity(player: Player): ClanMayhemClanIdentity? {
        val values = player.attr[MAYHEM_CLAN_KEY] ?: return null
        if (values.size != 5) return null
        return ClanMayhemClanIdentity(
            clanId = UUID(join(values[0], values[1]), join(values[2], values[3])),
            displayName = player.attr[MAYHEM_CLAN_NAME]?.takeIf(String::isNotBlank) ?: return null,
            memberSinceEpochMillis = values[4].toLong() * 1_000L,
            canManageRoster = player.attr[MAYHEM_CLAN_MANAGER] == true,
        )
    }

    /** Staff-only bootstrap until OpenRune gains its central clan service. */
    fun assignForTesting(player: Player, displayName: String, manager: Boolean = true) {
        val normalized = displayName.trim().take(24)
        require(normalized.isNotBlank())
        val id =
            UUID.nameUUIDFromBytes(
                "openrune-clan:${normalized.lowercase()}".toByteArray(StandardCharsets.UTF_8)
            )
        val joined = (System.currentTimeMillis() / 1_000L - TEST_MEMBERSHIP_AGE_SECONDS).toInt()
        player.attr[MAYHEM_CLAN_KEY] =
            mutableListOf(
                high(id.mostSignificantBits),
                low(id.mostSignificantBits),
                high(id.leastSignificantBits),
                low(id.leastSignificantBits),
                joined,
            )
        player.attr[MAYHEM_CLAN_NAME] = normalized
        player.attr[MAYHEM_CLAN_MANAGER] = manager
    }

    private fun high(value: Long): Int = (value ushr 32).toInt()

    private fun low(value: Long): Int = value.toInt()

    private fun join(high: Int, low: Int): Long =
        (high.toLong() shl 32) or (low.toLong() and 0xffff_ffffL)

    private companion object {
        const val TEST_MEMBERSHIP_AGE_SECONDS = 31 * 60
    }
}

package dev.openrune

import org.junit.jupiter.api.Test

class ClanRankTitleCatalogAuditTest {
    @Test
    fun `dump revision 240 clan rank title catalog candidates`() {
        val cache = ServerCacheManager.init(240)
        try {
            ServerCacheManager.getEnums()
                .values
                .asSequence()
                .filter { enum ->
                    val values = enum.values.values.map(Any?::toString)
                    values.any { it.equals("Dogsbody", ignoreCase = true) } ||
                        (values.size >= 200 && values.count { it.isNotBlank() } >= 200)
                }
                .sortedBy { it.id }
                .forEach { enum ->
                    println("CLAN_TITLE_ENUM id=${enum.id} size=${enum.values.size} values=${enum.values}")
                }
        } finally {
            cache.close()
        }
    }
}

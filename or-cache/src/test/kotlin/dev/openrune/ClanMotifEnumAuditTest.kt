package dev.openrune

import org.junit.jupiter.api.Test

/** Cache evidence for the motif palette and symbol tables used by clientscripts 4431-4444. */
class ClanMotifEnumAuditTest {
    @Test
    fun `dump revision 240 motif enums`() {
        ServerCacheManager.init(240)
        listOf(3819, 3820, 3822, 3823).forEach { id ->
            val type = requireNotNull(ServerCacheManager.getEnum(id))
            println("CLAN_MOTIF_ENUM id=$id keyType=${type.keyType} valueType=${type.valueType}")
            type.values.forEach { (key, value) -> println("CLAN_MOTIF_ENUM_ENTRY id=$id key=$key value=$value") }
        }
    }
}

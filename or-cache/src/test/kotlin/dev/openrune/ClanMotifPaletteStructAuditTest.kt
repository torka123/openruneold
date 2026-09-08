package dev.openrune

import org.junit.jupiter.api.Test

/** Dumps the paired-colour structs selected by the revision-240 clan motif palette. */
class ClanMotifPaletteStructAuditTest {
    @Test
    fun `dump motif palette structs`() {
        ServerCacheManager.init(240)
        val palette = requireNotNull(ServerCacheManager.getEnum(3819))
        palette.values.forEach { (key, structId) ->
            val struct = requireNotNull(ServerCacheManager.getStruct(structId as Int))
            println("CLAN_MOTIF_PALETTE key=$key struct=$structId params=${struct.params}")
        }
    }
}

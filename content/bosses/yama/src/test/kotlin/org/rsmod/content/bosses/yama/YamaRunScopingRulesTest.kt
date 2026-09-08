package org.rsmod.content.bosses.yama

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.rsmod.map.CoordGrid

class YamaRunScopingRulesTest {
    @Test
    fun soloAndDuoGlyphSharingIncludesOnlyCurrentParticipants() {
        assertEquals(setOf(11L), YamaRules.glyphRecipientHashes(setOf(11L), listOf(11L, 99L)))
        assertEquals(
            setOf(11L, 12L),
            YamaRules.glyphRecipientHashes(setOf(11L, 12L), listOf(11L, 12L, 99L)),
        )
    }

    @Test
    fun simultaneousEncounterRecipientSetsStayIndependent() {
        val online = listOf(11L, 12L, 21L, 22L, 99L)
        val first = YamaRules.glyphRecipientHashes(setOf(11L, 12L), online)
        val second = YamaRules.glyphRecipientHashes(setOf(21L, 22L), online)
        assertEquals(setOf(11L, 12L), first)
        assertEquals(setOf(21L, 22L), second)
        assertEquals(emptySet(), first intersect second)
    }

    @Test
    fun personalCrashStateKeepsAccountAndSnapshotSeparate() {
        val first =
            YamaEncounterManager.PendingCrashImpact(
                accountHash = 11L,
                impactCycle = 503,
                centre = CoordGrid(1500, 10080, 0),
                orientation = YamaHazardRules.CrashOrientation.VERTICAL,
            )
        val second =
            YamaEncounterManager.PendingCrashImpact(
                accountHash = 12L,
                impactCycle = 503,
                centre = CoordGrid(1508, 10082, 0),
                orientation = YamaHazardRules.CrashOrientation.VERTICAL,
            )
        assertNotEquals(first.accountHash, second.accountHash)
        assertNotEquals(first.centre, second.centre)
    }
}

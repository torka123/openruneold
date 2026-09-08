package org.rsmod.content.drops.tables.monsters

import dtx.core.ArgMap
import dtx.core.RollResult
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.rsmod.api.attr.BossRewardAttributes
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.game.entity.Player

class PhantomMuspahDropTableTest {
    @Test
    fun chargedIceUsesExactSubThreeMinuteBoundaryAndOneTimeFlag() {
        val player = Player()
        player.attr[BossRewardAttributes.phantomMuspahLastKillTicks] = 299
        assertTrue(player.isMuspahChargedIceEligible())

        player.attr[BossRewardAttributes.phantomMuspahLastKillTicks] = 300
        assertFalse(player.isMuspahChargedIceEligible())

        player.attr[BossRewardAttributes.phantomMuspahLastKillTicks] = 299
        player.attr[BossRewardAttributes.phantomMuspahChargedIceUsed] = true
        assertFalse(player.isMuspahChargedIceEligible())
    }

    @Test
    fun highVolumeRollsTrackCurrentUniqueSupplyAndPetRates() {
        val player = Player()
        var shards = 0
        var caches = 0
        var icons = 0
        var supplies = 0
        var pets = 0
        val samples = 100_000

        repeat(samples) {
            val unique = muspahUniqueRoll.selectResult(player, ArgMap.Empty).items()
            val supply = muspahSupplyRoll.selectResult(player, ArgMap.Empty).items()
            if (unique.any { item -> item.obj == "obj.venator_shard" }) {
                shards++
                assertEquals(listOf("obj.venator_shard"), unique.map(DropRollItem::obj))
            }
            if (unique.any { item -> item.obj == "obj.frozen_cache" }) caches++
            if (unique.any { item -> item.obj == "obj.ancient_icon" }) icons++
            if (supply.any { item -> item.obj in supplyItems }) supplies++
            if (Random.nextInt(MUSPAH_PET_DENOMINATOR) == 0) pets++
        }

        assertTrue(shards in 700..1300, "1/100 shard rate drifted: $shards/$samples")
        assertTrue(caches in 3500..4500, "4/100 cache rate drifted: $caches/$samples")
        assertTrue(icons in 1650..2350, "2/100 icon rate drifted: $icons/$samples")
        assertTrue(supplies in 20_000..24_500, "2/9 supply rate drifted: $supplies/$samples")
        assertTrue(pets in 15..75, "1/2500 pet rate drifted: $pets/$samples")
    }

    private fun RollResult<DropRollItem>.items(): List<DropRollItem> =
        when (this) {
            is RollResult.Single -> listOf(result)
            is RollResult.ListOf -> results
            RollResult.Nothing -> emptyList()
        }

    private companion object {
        val supplyItems =
            setOf(
                "obj.shark",
                "obj.summer_pie",
                "obj.3doseancientbrew",
                "obj.3doserangerspotion",
                "obj.3dose2restore",
                "obj.3doseprayerrestore",
            )
    }
}

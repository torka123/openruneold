package org.rsmod.content.bosses.yama

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class YamaRulesTest {
    @Test
    fun `throne state values preserve seated challenge and empty summon variants`() {
        assertEquals(0, YamaAssets.THRONE_STATE_OCCUPIED)
        assertEquals(1, YamaAssets.THRONE_STATE_EMPTY)
    }

    @Test
    fun `every official contract has an explicit and unique item mapping`() {
        assertEquals(11, YamaContract.itemContracts.size)
        assertEquals(
            11,
            YamaContract.itemContracts.mapNotNull(YamaContract::itemId).distinct().size,
        )
        YamaContract.itemContracts.forEach { contract ->
            assertEquals(contract, YamaContract.forItem(requireNotNull(contract.itemId)))
        }
        assertEquals(null, YamaContract.forItem(-1))
    }

    @Test
    fun `duo and returned-on-victory restrictions are explicit`() {
        assertEquals(
            setOf(
                YamaContract.NONE,
                YamaContract.SHARD,
                YamaContract.CATALYST,
                YamaContract.WORM,
                YamaContract.FAMILIAR,
                YamaContract.CUSTOM,
            ),
            YamaContract.entries.filter(YamaContract::duoAllowed).toSet(),
        )
        assertEquals(
            setOf(
                YamaContract.GLYPHIC,
                YamaContract.SENSORY,
                YamaContract.BLOODIED,
                YamaContract.DIVINE,
                YamaContract.FORFEIT,
                YamaContract.HARMONY,
                YamaContract.FAMILIAR,
            ),
            YamaContract.entries.filter(YamaContract::returnedOnVictory).toSet(),
        )
    }

    @Test
    fun `normal and contracted health thresholds differ`() {
        assertEquals(1666, YamaRules.firstThreshold(2500))
        assertEquals(833, YamaRules.secondThreshold(2500))
        assertEquals(1875, YamaRules.firstThreshold(2500, YamaContract.CATALYST))
        assertEquals(1250, YamaRules.secondThreshold(2500, YamaContract.CATALYST))
        assertEquals(1, YamaRules.phaseFor(2000, 2500, YamaContract.CATALYST))
        assertEquals(2, YamaRules.phaseFor(1800, 2500, YamaContract.CATALYST))
        assertEquals(3, YamaRules.phaseFor(1200, 2500, YamaContract.CATALYST))
    }

    @Test
    fun `contract profiles expose their real mechanical differences`() {
        assertEquals(2500, YamaRules.profile(YamaContract.NONE).hitpoints)
        assertEquals(2875, YamaRules.profile(YamaContract.GLYPHIC).hitpoints)
        assertEquals(3000, YamaRules.profile(YamaContract.BLOODIED).hitpoints)
        assertEquals(8, YamaRules.attackRate(1, YamaContract.NONE))
        assertEquals(7, YamaRules.attackRate(1, YamaContract.CATALYST))
        assertEquals(7, YamaRules.attackRate(3, YamaContract.NONE))
        assertEquals(3, YamaRules.profile(YamaContract.NONE).regularsBeforeSpecial)
        assertEquals(2, YamaRules.profile(YamaContract.GLYPHIC).regularsBeforeSpecial)
        assertEquals(10, YamaRules.profile(YamaContract.NONE).glyphProtectionTicks)
        assertEquals(2, YamaRules.profile(YamaContract.GLYPHIC).glyphProtectionTicks)
        assertTrue(YamaRules.profile(YamaContract.FAMILIAR).continuousFlares)
        assertTrue(YamaRules.profile(YamaContract.DIVINE).continuousFlares)
        assertTrue(YamaRules.profile(YamaContract.FAMILIAR).healingBlocked)
        assertTrue(YamaRules.profile(YamaContract.SENSORY).prayerPierce)
        assertFalse(YamaRules.profile(YamaContract.NONE).prayerPierce)
        assertTrue(YamaContract.CATALYST.acquisitionShadowCrash)
        assertFalse(YamaContract.FAMILIAR.acquisitionShadowCrash)
        assertTrue(YamaContract.OATHPLATE.persistentShadowPools)
    }

    @Test
    fun `all acquisition contracts use the enhanced encounter profile`() {
        val acquisitions =
            setOf(
                YamaContract.OATHPLATE,
                YamaContract.SHARD,
                YamaContract.CATALYST,
                YamaContract.WORM,
                YamaContract.HARMONY,
                YamaContract.FAMILIAR,
                YamaContract.CUSTOM,
            )
        assertEquals(acquisitions, YamaContract.entries.filter { it.enhancedAcquisition }.toSet())
        acquisitions.forEach { contract ->
            val profile = YamaRules.profile(contract)
            assertEquals(50, profile.restorationPercent, contract.name)
            assertEquals(5, profile.shadowCrashSets, contract.name)
            assertEquals(2, profile.regularsBeforeSpecial, contract.name)
        }
        assertEquals(
            acquisitions - YamaContract.FAMILIAR,
            YamaContract.entries.filter { it.acquisitionShadowCrash }.toSet(),
        )
        assertEquals(
            setOf(YamaContract.OATHPLATE, YamaContract.HARMONY),
            YamaContract.entries.filter { it.persistentShadowPools }.toSet(),
        )
    }

    @Test
    fun `custom pizza offering is mechanically an acquisition contract`() {
        val custom = YamaRules.profile(YamaContract.CUSTOM)
        val catalyst = YamaRules.profile(YamaContract.CATALYST)

        assertTrue(YamaContract.CUSTOM.isContractFight)
        assertTrue(YamaContract.CUSTOM.enhancedAcquisition)
        assertTrue(YamaContract.CUSTOM.acquisitionShadowCrash)
        assertEquals(catalyst.hitpoints, custom.hitpoints)
        assertEquals(catalyst.standardMaxHit, custom.standardMaxHit)
        assertEquals(catalyst.attackRatePhaseOneTwo, custom.attackRatePhaseOneTwo)
        assertEquals(catalyst.regularsBeforeSpecial, custom.regularsBeforeSpecial)
        assertEquals(catalyst.glyphRespawnTicks, custom.glyphRespawnTicks)
        assertEquals(catalyst.shadowCrashSets, custom.shadowCrashSets)
        assertEquals(catalyst.restorationPercent, custom.restorationPercent)
    }

    @Test
    fun `prayer mitigation changes by phase and contract`() {
        assertEquals(
            0,
            YamaRules.protectedStandardMaximum(1, YamaAttackStyle.MAGIC, true, YamaContract.NONE),
        )
        assertEquals(
            6,
            YamaRules.protectedStandardMaximum(1, YamaAttackStyle.MELEE, true, YamaContract.NONE),
        )
        assertEquals(
            3,
            YamaRules.protectedStandardMaximum(3, YamaAttackStyle.RANGED, true, YamaContract.NONE),
        )
        assertEquals(
            22,
            YamaRules.protectedStandardMaximum(3, YamaAttackStyle.MELEE, true, YamaContract.NONE),
        )
        assertEquals(
            53,
            YamaRules.protectedStandardMaximum(1, YamaAttackStyle.MAGIC, true, YamaContract.SENSORY),
        )
        assertEquals(
            0,
            YamaRules.protectedStandardMaximum(1, YamaAttackStyle.MELEE, true, YamaContract.DIVINE),
        )
        assertEquals(
            46,
            YamaRules.protectedStandardMaximum(1, YamaAttackStyle.MAGIC, false, YamaContract.NONE),
        )
    }

    @Test
    fun `glyphs activate only from the centre of their three by three locations`() {
        assertEquals(3, YamaRules.GLYPH_SIZE)
        assertEquals(1, YamaRules.GLYPH_CENTRE_OFFSET)
        assertEquals(1, YamaRules.GLYPH_DEACTIVATE_TICKS)
        listOf(25 to 36, 23 to 29, 30 to 24, 37 to 29, 35 to 36).forEach { (x, z) ->
            assertFalse(YamaRules.isGlyphCentre(x, z, x, z))
            assertTrue(YamaRules.isGlyphCentre(x, z, x + 1, z + 1))
            assertFalse(YamaRules.isGlyphCentre(x, z, x + 2, z + 2))
        }
    }

    @Test
    fun `glyph costs and flare variants are contract aware`() {
        assertEquals(
            listOf(2, 6, 9, 12),
            listOf(4, 3, 2, 1).map { YamaRules.glyphCost(it, YamaContract.NONE) },
        )
        assertEquals(
            listOf(16, 18, 27, 36),
            listOf(4, 3, 2, 1).map { YamaRules.glyphCost(it, YamaContract.GLYPHIC, YamaGlyph.FIRE) },
        )
        assertEquals(
            listOf(6, 18, 27, 36),
            listOf(4, 3, 2, 1).map {
                YamaRules.glyphCost(it, YamaContract.GLYPHIC, YamaGlyph.SHADOW)
            },
        )
        assertEquals(2, YamaRules.flareCount(1, YamaContract.NONE))
        assertEquals(3, YamaRules.flareCount(2, YamaContract.DIVINE))
        assertEquals(4, YamaRules.flareCount(2, YamaContract.BLOODIED))
        YamaContract.entries.forEach { contract ->
            assertEquals(1, YamaRules.flareCount(3, contract), contract.displayName)
        }
        assertEquals(
            YamaRules.VOID_FLARE_PHASE_THREE_ORDER,
            List(YamaRules.VOID_FLARE_PHASE_THREE_ORDER.size) { cursor ->
                YamaRules.flareSpawnPositions(count = 1, singleCursor = cursor).single()
            },
        )
        assertEquals(71, YamaRules.flareHitpoints(3, YamaContract.NONE, 0))
        assertEquals(140, YamaRules.flareHitpoints(3, YamaContract.CATALYST, 2))
        assertEquals(140, YamaRules.flareHitpoints(2, YamaContract.CATALYST, 0))
        assertEquals(71, YamaRules.flareHitpoints(2, YamaContract.CATALYST, 2))
        assertEquals(71, YamaRules.flareHitpoints(2, YamaContract.DIVINE, 2))
        assertEquals(71, YamaRules.flareHitpoints(2, YamaContract.BLOODIED, 3))
        assertEquals(81, YamaRules.flareHitpoints(3, YamaContract.FAMILIAR, 0))
    }

    @Test
    fun `hazard success and failure branches are deterministic`() {
        assertEquals(44, YamaRules.flareExplosionDamage(140, 140))
        assertEquals(22, YamaRules.flareExplosionDamage(70, 140))
        assertEquals(0, YamaRules.flareExplosionDamage(0, 140))
        assertEquals(99, YamaRules.meteorDamage(0, 0, 99))
        assertEquals(80, YamaRules.meteorDamage(2, 2, 99))
        assertEquals(40, YamaRules.meteorDamage(3, 3, 99))
        assertEquals(0, YamaRules.meteorDamage(4, 4, 99))
        assertTrue(YamaRules.judgeCavernDamageTurn(5))
        assertTrue(YamaRules.judgeCavernDamageTurn(13))
        assertFalse(YamaRules.judgeCavernDamageTurn(12))
    }

    @Test
    fun `contribution and bloodied rules are bounded`() {
        assertFalse(YamaRules.rewardEligible(0, 2500))
        assertFalse(YamaRules.rewardEligible(374, 2500))
        assertTrue(YamaRules.rewardEligible(375, 2500))
        assertFalse(YamaRules.rewardEligible(375, 3000))
        assertFalse(YamaRules.rewardEligible(449, 3000))
        assertTrue(YamaRules.rewardEligible(450, 3000))
        assertFalse(YamaRules.rewardEligible(375, 0))
        assertFalse(YamaRules.petEligible(375, 2500))
        assertTrue(YamaRules.petEligible(376, 2500))
        assertEquals(0, YamaRules.bloodiedMinimumHit(100, 53))
        assertEquals(27, YamaRules.bloodiedMinimumHit(50, 53))
        assertEquals(53, YamaRules.bloodiedMinimumHit(0, 53))
    }

    @Test
    fun `attack rotation is deterministic and resets between phases`() {
        val normal = YamaRotation(YamaContract.NONE)
        assertEquals(listOf(null, YamaSpecial.GLYPH_ATTACK), List(2) { normal.next(1) })
        assertEquals(listOf(null, null, null, YamaSpecial.SHADOW_WAVES), List(4) { normal.next(1) })
        normal.resetForPhase()
        assertEquals(listOf(null, YamaSpecial.METEOR_STRIKE), List(2) { normal.next(2) })
        normal.resetForPhase(firstSpecialAfterOne = false)
        assertEquals(listOf(null, null, null, YamaSpecial.SHADOW_WAVES), List(4) { normal.next(3) })

        val contract = YamaRotation(YamaContract.CATALYST)
        assertEquals(listOf(null, YamaSpecial.GLYPH_ATTACK), List(2) { contract.next(1) })
        assertEquals(listOf(null, null, YamaSpecial.SHADOW_WAVES), List(3) { contract.next(1) })
    }

    @Test
    fun `entrance Voice uses the restored tile and command lands five tiles south`() {
        assertEquals(1439, YAMA_VOICE_SPAWN.x)
        assertEquals(10079, YAMA_VOICE_SPAWN.z)
        assertEquals(0, YAMA_VOICE_SPAWN.level)
        assertEquals(1439, YAMA_OUTSIDE.x)
        assertEquals(10074, YAMA_OUTSIDE.z)
        assertEquals(0, YAMA_OUTSIDE.level)
        assertEquals(YAMA_VOICE_SPAWN.x, YAMA_OUTSIDE.x)
        assertEquals(5, YAMA_VOICE_SPAWN.z - YAMA_OUTSIDE.z)
    }

    @Test
    fun `contract reward ids match revision 240`() {
        assertEquals(30750, YamaAssets.OATHPLATE_HELM)
        assertEquals(30753, YamaAssets.OATHPLATE_CHEST)
        assertEquals(30756, YamaAssets.OATHPLATE_LEGS)
        assertEquals(30759, YamaAssets.SOULFLAME_HORN)
        assertEquals(30765, YamaAssets.OATHPLATE_SHARDS)
        assertEquals(
            listOf(30783, 30785, 30787, 30789, 30791),
            listOf(
                YamaAssets.SIGIL_LEFT,
                YamaAssets.SIGIL_RIGHT,
                YamaAssets.SIGIL_MIDDLE,
                YamaAssets.SIGIL_TOP,
                YamaAssets.SIGIL_BOTTOM,
            ),
        )
        assertEquals(30888, YamaAssets.YAMI)
    }

    @Test
    fun `restoration restrictions halve or block only gains`() {
        assertEquals(60, YamaRules.restorationAfterRestriction(50, 70, 50))
        assertEquals(50, YamaRules.restorationAfterRestriction(50, 70, 0))
        assertEquals(70, YamaRules.restorationAfterRestriction(50, 70, 100))
        assertEquals(40, YamaRules.restorationAfterRestriction(50, 40, 50))
        assertEquals(50, YamaRules.restorationAfterRestriction(50, 51, 50))
    }

    @Test
    fun `grave fees use osrs tiers cap and discounts`() {
        assertEquals(0, YamaRules.graveItemFee(99_999))
        assertEquals(1_000, YamaRules.graveItemFee(100_000))
        assertEquals(1_000, YamaRules.graveItemFee(999_999))
        assertEquals(10_000, YamaRules.graveItemFee(1_000_000))
        assertEquals(10_000, YamaRules.graveItemFee(9_999_999))
        assertEquals(100_000, YamaRules.graveItemFee(10_000_000))
        assertEquals(500_000, YamaRules.discountedGraveFee(800_000, 10))
        assertEquals(125_000, YamaRules.discountedGraveFee(800_000, 9))
        assertEquals(0, YamaRules.discountedGraveFee(-1, 10))
    }

    @Test
    fun `custom offering and forfeit cadence are exact`() {
        assertEquals(100, YamaRules.customOfferingCount())
        assertFalse(YamaRules.forfeitRunDrain(100, 100))
        assertFalse(YamaRules.forfeitRunDrain(103, 100))
        assertTrue(YamaRules.forfeitRunDrain(104, 100))
        assertTrue(YamaRules.forfeitRunDrain(108, 100))
    }
}

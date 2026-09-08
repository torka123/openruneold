package org.rsmod.content.minigames.perilousmoons

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class PerilousMoonRulesTest {
    @Test
    fun `cache controllers are five hundred hp and separate from one hp display shells`() {
        assertEquals(500, MOON_MAX_HITPOINTS)
        assertEquals(listOf(13016, 13017, 13018), PerilousMoon.entries.map { it.bossNpcId })
        assertEquals(listOf(13011, 13013, 13012), PerilousMoon.entries.map { it.decorativeNpcId })
        assertTrue(PerilousMoon.entries.all { it.bossNpcId != it.decorativeNpcId })
    }

    @Test
    fun `moon transform visibility values fit the one bit cache varbit`() {
        assertEquals(0, MOON_NORMAL_VISIBLE_STATE)
        assertEquals(1, MOON_ENRAGED_VISIBLE_STATE)
        assertTrue(listOf(MOON_NORMAL_VISIBLE_STATE, MOON_ENRAGED_VISIBLE_STATE).all { it in 0..1 })
    }

    @Test
    fun `every moon restores its matching combat idle`() {
        assertEquals("seq.npc_djinn_01_idle", PerilousMoon.Blood.idleAnimation)
        assertEquals("seq.npc_djinn_01_idle", PerilousMoon.Blue.idleAnimation)
        assertEquals("seq.npc_djinn_02_idle", PerilousMoon.Eclipse.idleAnimation)
    }

    @Test
    fun `moon transitions use the revision 240 teleport animation names`() {
        assertEquals(
            "seq.npc_djinn_01_teleport_idle_disappear",
            PerilousMoon.Blood.disappearAnimation,
        )
        assertEquals("seq.npc_djinn_01_teleport_idle_appear", PerilousMoon.Blood.appearAnimation)
        assertEquals(PerilousMoon.Blood.disappearAnimation, PerilousMoon.Blue.disappearAnimation)
        assertEquals(PerilousMoon.Blood.appearAnimation, PerilousMoon.Blue.appearAnimation)
        assertEquals(
            "seq.npc_djinn_02_teleport_idle_disappear",
            PerilousMoon.Eclipse.disappearAnimation,
        )
        assertEquals("seq.npc_djinn_02_teleport_idle_appear", PerilousMoon.Eclipse.appearAnimation)
    }

    @Test
    fun `every encounter begins with its own five hundred hitpoints`() {
        val first = MoonProgressModel()
        val second = MoonProgressModel()
        assertNotSame(first, second)
        assertEquals(500, first.hitpoints)
        assertEquals(500, second.hitpoints)
        assertEquals(450, first.damaged(50).hitpoints)
        assertEquals(500, second.hitpoints)
    }

    @Test
    fun `healing is capped and isolated`() {
        val first = MoonProgressModel(hitpoints = 450).healed(100)
        val second = MoonProgressModel(hitpoints = 300)
        assertEquals(500, first.hitpoints)
        assertEquals(300, second.hitpoints)
    }

    @Test
    fun `flat armour changes every successful hitsplat without being consumed`() {
        assertEquals(12, PerilousMoonRules.flatArmour(10, -2))
        assertEquals(5, PerilousMoonRules.flatArmour(10, 5))
        assertEquals(1, PerilousMoonRules.flatArmour(2, 6))
        assertEquals(0, PerilousMoonRules.flatArmour(0, -5))
        val multi = listOf(4, 8, 20).map { PerilousMoonRules.flatArmour(it, -2) }
        assertEquals(listOf(6, 10, 22), multi)
    }

    @Test
    fun `combo misses cascade`() {
        assertTrue(PerilousMoonRules.comboCanContinue(emptyList(), 0))
        assertFalse(PerilousMoonRules.comboCanContinue(listOf(false), 1))
        assertFalse(PerilousMoonRules.comboCanContinue(listOf(true, false), 2))
        assertTrue(PerilousMoonRules.comboCanContinue(listOf(true, true), 2))
    }

    @Test
    fun `combo uses current maximum hits and curse additions`() {
        assertTrue(PerilousMoonRules.comboMaxHits.contentEquals(intArrayOf(4, 8, 20)))
        assertTrue(PerilousMoonRules.comboCurseAdds.contentEquals(intArrayOf(1, 2, 5)))
    }

    @Test
    fun `blood heal multipliers include the third-hit clamp`() {
        assertEquals(4, PerilousMoonRules.bloodHeal(0, 4))
        assertEquals(12, PerilousMoonRules.bloodHeal(1, 6))
        assertEquals(30, PerilousMoonRules.bloodHeal(2, 1))
        assertEquals(45, PerilousMoonRules.bloodHeal(2, 15))
        assertEquals(50, PerilousMoonRules.bloodHeal(2, 20))
        assertEquals(0, PerilousMoonRules.bloodHeal(2, 0))
    }

    @Test
    fun `glyph rotates clockwise after two attacks and special starts after six`() {
        var progress = MoonProgressModel(state = MoonEncounterState.STANDARD_COMBAT)
        progress = progress.completedStandardAttack()
        assertEquals(0, progress.glyphIndex)
        progress = progress.completedStandardAttack()
        assertEquals(1, progress.glyphIndex)
        repeat(4) { progress = progress.completedStandardAttack() }
        assertEquals(3, progress.glyphIndex)
        assertEquals(MoonEncounterState.SPECIAL_ATTACK_ONE, progress.state)
    }

    @Test
    fun `specials alternate`() {
        assertEquals(2, PerilousMoonRules.nextSpecial(1))
        assertEquals(1, PerilousMoonRules.nextSpecial(2))
    }

    @Test
    fun `aura damage ramps and caps`() {
        assertTrue(PerilousMoonRules.auraDamage.contentEquals(intArrayOf(1, 2, 4, 8, 16)))
        assertEquals(16, PerilousMoonRules.auraDamage.last())
    }

    @Test
    fun `curse caps and decays by two`() {
        assertEquals(127, PerilousMoonRules.addCurse(125, 5))
        assertEquals(7, PerilousMoonRules.decayCurse(9))
        assertEquals(0, PerilousMoonRules.decayCurse(1))
    }

    @Test
    fun `common rolls scale one three six`() {
        assertEquals(0, PerilousMoonRules.commonRolls(0))
        assertEquals(1, PerilousMoonRules.commonRolls(1))
        assertEquals(3, PerilousMoonRules.commonRolls(2))
        assertEquals(6, PerilousMoonRules.commonRolls(3))
    }

    @Test
    fun `glancing scales to one hundred percent and has phase reductions`() {
        assertEquals(0, PerilousMoonRules.glancingChance(0))
        assertEquals(100, PerilousMoonRules.glancingChance(127))
        assertEquals(28, PerilousMoonRules.glancingMaximum(40, clonePhase = false))
        assertEquals(32, PerilousMoonRules.glancingMaximum(40, clonePhase = true))
    }

    @Test
    fun `combat timing is expressed in authoritative six hundred millisecond ticks`() {
        assertEquals(600, MOON_GAME_TICK_MILLIS)
        assertEquals(6, MOON_ATTACK_RATE)
        assertEquals(6, MOON_STANDARD_ATTACKS_PER_SPECIAL)
    }

    @Test
    fun `mechanic impacts always use a valid deferred hit cycle`() {
        assertEquals(1, MOON_MECHANIC_HIT_DELAY)
        assertTrue(MOON_MECHANIC_HIT_DELAY > 0)
    }

    @Test
    fun `separate bosses reform after ten seconds on the game tick clock`() {
        assertEquals(17, MOON_RESPAWN_TICKS)
        assertEquals(10_200, MOON_RESPAWN_TICKS * MOON_GAME_TICK_MILLIS)
    }

    @Test
    fun `floor glyph safety is exactly the audited two by two footprint`() {
        assertEquals(2, MOON_GLYPH_SIZE)
        PerilousMoon.entries.forEach { moon ->
            val center = moon.glyphs.first()
            val southWest = moonGlyphSouthWest(center)
            assertEquals(center.translate(-1, -1), southWest)
            assertTrue(PerilousMoonRules.isOnMoonGlyph(center, southWest))
            assertTrue(PerilousMoonRules.isOnMoonGlyph(center, center))
            assertFalse(PerilousMoonRules.isOnMoonGlyph(center, center.translate(1, 0)))
            assertFalse(PerilousMoonRules.isOnMoonGlyph(center, center.translate(0, 1)))
        }
    }

    @Test
    fun `only blood third hit can be dodged by stepping under`() {
        assertFalse(PerilousMoonRules.thirdHitStepUnderDodges(PerilousMoon.Blood, 0, true))
        assertFalse(PerilousMoonRules.thirdHitStepUnderDodges(PerilousMoon.Blood, 1, true))
        assertTrue(PerilousMoonRules.thirdHitStepUnderDodges(PerilousMoon.Blood, 2, true))
        assertFalse(PerilousMoonRules.thirdHitStepUnderDodges(PerilousMoon.Blue, 2, true))
        assertFalse(PerilousMoonRules.thirdHitStepUnderDodges(PerilousMoon.Eclipse, 2, true))
    }

    @Test
    fun `blood hazards heal and jaguar timeline matches seven pools and six cleaves`() {
        assertEquals(30, PerilousMoonRules.bloodRainBossHeal(10))
        assertEquals(0, PerilousMoonRules.bloodRainAvoidanceChip(onPool = true))
        assertEquals(1, PerilousMoonRules.bloodRainAvoidanceChip(onPool = false))
        assertEquals(45, PerilousMoonRules.bloodRainBossHeal(15))
        assertEquals(25, PerilousMoonRules.jaguarPlayerHeal(40))
        assertEquals(15, PerilousMoonRules.jaguarBossSiphon(40))
        val poolSpawns = (1..32).filter(PerilousMoonRules::isJaguarPoolSpawnTick)
        val poolDamage = (1..32).filter(PerilousMoonRules::isJaguarPoolDamageTick)
        val cleaves = (1..32).filter(PerilousMoonRules::isJaguarCleaveTick)
        assertEquals(listOf(1, 5, 9, 13, 17, 21, 25), poolSpawns)
        assertEquals(listOf(3, 7, 11, 15, 19, 23, 27), poolDamage)
        assertEquals(listOf(4, 8, 12, 16, 20, 24), cleaves)
        assertFalse(PerilousMoonRules.isJaguarPhaseComplete(27))
        assertTrue(PerilousMoonRules.isJaguarPhaseComplete(28))

        val moon = PerilousMoon.Blood
        moon.glyphs.forEach { glyph ->
            val jaguar = PerilousMoonRules.jaguarCenter(moon.center, glyph)
            assertEquals(BLOOD_JAGUAR_OUTWARD_TILES, glyph.chebyshevDistance(jaguar))
            assertTrue(jaguar.chebyshevDistance(moon.center) > glyph.chebyshevDistance(moon.center))
        }
    }

    @Test
    fun `blue cold thresholds weapon lockout and storm healing are deterministic`() {
        assertEquals(ColdWarning.NONE, PerilousMoonRules.coldWarning(0, 2))
        assertEquals(ColdWarning.MILD, PerilousMoonRules.coldWarning(2, 3))
        assertEquals(ColdWarning.SEVERE, PerilousMoonRules.coldWarning(8, 9))
        assertTrue(PerilousMoonRules.shouldCancelColdAttack(18))
        assertEquals(5, PerilousMoonRules.curseAfterCancelledAttack(23))
        assertTrue(PerilousMoonRules.canAttackFrozenBlock(weaponEquipped = false))
        assertFalse(PerilousMoonRules.canAttackFrozenBlock(weaponEquipped = true))
        assertEquals(3, BLUE_FREEZE_NO_WEAPON_MIN_HIT)
        assertEquals(15, BLUE_FREEZE_NO_WEAPON_MAX_HIT)
        assertEquals(2, BLUE_FREEZE_SAFE_ATTACKS)
        assertEquals(28, BLUE_FREEZE_MAX_FAILURE)
        assertEquals(100, PerilousMoonRules.frostStormTotalHeal(unlitBraziers = 2))
    }

    @Test
    fun `eclipse clone phase is five groups of three at plus four armour`() {
        assertEquals(15, PerilousMoonRules.eclipseCloneCount)
        assertEquals(listOf(0, 0, 0, 1, 1, 1), (0..5).map(PerilousMoonRules::eclipseCloneGroup))
        assertTrue(PerilousMoonRules.pauseAfterClone(3))
        assertTrue(PerilousMoonRules.pauseAfterClone(12))
        assertFalse(PerilousMoonRules.pauseAfterClone(15))
        assertEquals(4, ECLIPSE_CLONE_FLAT_ARMOUR)
    }

    @Test
    fun `commands land beside revision two forty statues and statues enter chambers`() {
        assertEquals(
            listOf(1410 to 9631, 1439 to 9659, 1467 to 9631),
            PerilousMoon.entries.map { it.entryStatueCoords.x to it.entryStatueCoords.z },
        )
        for (moon in PerilousMoon.entries) {
            assertFalse(
                PerilousMoonRules.isInsideRoom(
                    moon,
                    moon.statueApproach.x,
                    moon.statueApproach.z,
                    moon.statueApproach.level,
                )
            )
            assertTrue(moon.statueApproach.chebyshevDistance(moon.entryStatueCoords) <= 2)
            assertTrue(
                PerilousMoonRules.isInsideRoom(
                    moon,
                    moon.arrival.x,
                    moon.arrival.z,
                    moon.arrival.level,
                )
            )
            assertTrue(moon.arrival.chebyshevDistance(moon.center) >= 8)
        }
    }
}

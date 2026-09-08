package org.rsmod.content.minigames.toa

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ToaPathBossInvocationRulesTest {
    @Test
    fun `Kephri level zero projection preserves the verified baseline`() {
        val rules = KephriInvocationRules.resolve(emptySet(), partySize = 1)

        assertEquals(2, rules.massIncubationBrownEggs)
        assertFalse(rules.addMissingOverlord)
        assertEquals(1, rules.dungStrikeTargets)
        assertFalse(rules.medicSwarmsOutsideShield)
        assertEquals(0, rules.bombRadius)
        assertEquals(KephriBombs.BASE_DAMAGE, rules.bombDamage)
    }

    @Test
    fun `every Kephri invocation changes its encounter projection`() {
        assertEquals(
            6,
            KephriInvocationRules.resolve(setOf(ToaInvocation.LIVELY_LARVAE), 2) { 0 }
                .massIncubationBrownEggs,
        )
        assertEquals(
            10,
            KephriInvocationRules.resolve(setOf(ToaInvocation.LIVELY_LARVAE), 2) { 2 }
                .massIncubationBrownEggs,
        )
        assertTrue(
            KephriInvocationRules.resolve(setOf(ToaInvocation.MORE_OVERLORDS), 1).addMissingOverlord
        )
        assertEquals(
            2,
            KephriInvocationRules.resolve(setOf(ToaInvocation.BLOWING_MUD), 2).dungStrikeTargets,
        )
        assertEquals(
            1,
            KephriInvocationRules.resolve(setOf(ToaInvocation.BLOWING_MUD), 1).dungStrikeTargets,
        )
        val medic = KephriInvocationRules.resolve(setOf(ToaInvocation.MEDIC), 1)
        assertTrue(medic.medicSwarmsOutsideShield)
        assertEquals(12, medic.medicSpawnInterval)
        assertEquals(3, medic.medicMoveInterval)
        val aerial = KephriInvocationRules.resolve(setOf(ToaInvocation.AERIAL_ASSAULT), 1)
        assertEquals(1, aerial.bombRadius)
        assertTrue(aerial.bombDamage > KephriBombs.BASE_DAMAGE)
    }

    @Test
    fun `Blowing Mud changes only group target cardinality and has no solo effect`() {
        val groupBaseline = KephriInvocationRules.resolve(emptySet(), partySize = 4)
        val groupBlowing =
            KephriInvocationRules.resolve(setOf(ToaInvocation.BLOWING_MUD), partySize = 4)
        val soloBaseline = KephriInvocationRules.resolve(emptySet(), partySize = 1)
        val soloBlowing =
            KephriInvocationRules.resolve(setOf(ToaInvocation.BLOWING_MUD), partySize = 1)

        assertEquals(groupBaseline.copy(dungStrikeTargets = 2), groupBlowing)
        assertEquals(soloBaseline, soloBlowing)
    }

    @Test
    fun `Zebak child invocations cannot bypass Not Just a Head`() {
        val malformed =
            ZebakInvocationRules.resolve(
                setOf(ToaInvocation.ARTERIAL_SPRAY, ToaInvocation.BLOOD_THINNERS)
            )

        assertFalse(malformed.bloodMagic)
        assertEquals(1, malformed.barrageRadius)
        assertEquals(1, malformed.bloodCloudCount)
        assertEquals(ZebakAssets.BLOOD_CLOUD, malformed.bloodCloudNpc)
    }

    @Test
    fun `every Zebak invocation changes its encounter projection`() {
        val head = ZebakInvocationRules.resolve(setOf(ToaInvocation.NOT_JUST_A_HEAD))
        assertTrue(head.bloodMagic)

        val arterial =
            ZebakInvocationRules.resolve(
                setOf(ToaInvocation.NOT_JUST_A_HEAD, ToaInvocation.ARTERIAL_SPRAY)
            )
        assertEquals(2, arterial.barrageRadius)
        assertTrue(
            ZebakInvocationRules.healAmount(30, arterial.arterialHealing) >
                ZebakInvocationRules.healAmount(30, false)
        )

        val thinners =
            ZebakInvocationRules.resolve(
                setOf(ToaInvocation.NOT_JUST_A_HEAD, ToaInvocation.BLOOD_THINNERS)
            )
        assertEquals(3, thinners.bloodCloudCount)
        assertEquals(ZebakAssets.SMALL_BLOOD_CLOUD, thinners.bloodCloudNpc)

        val upset = ZebakInvocationRules.resolve(setOf(ToaInvocation.UPSET_STOMACH))
        assertEquals(2, upset.acidRadius)
        assertEquals(1, upset.jugWaterRadius)
    }

    @Test
    fun `every Akkha invocation changes its encounter projection`() {
        val baseline = AkkhaInvocationRules.resolve(emptySet())
        assertFalse(baseline.doubleTrouble)
        assertFalse(baseline.keepBack)
        assertEquals(7, baseline.attacksBeforeSpecial)
        assertFalse(baseline.feelingSpecial)

        assertTrue(AkkhaInvocationRules.resolve(setOf(ToaInvocation.DOUBLE_TROUBLE)).doubleTrouble)
        assertTrue(AkkhaInvocationRules.resolve(setOf(ToaInvocation.KEEP_BACK)).keepBack)
        assertEquals(
            6,
            AkkhaInvocationRules.resolve(setOf(ToaInvocation.STAY_VIGILANT)).attacksBeforeSpecial,
        )
        val feeling = AkkhaInvocationRules.resolve(setOf(ToaInvocation.FEELING_SPECIAL))
        assertEquals(2, feeling.memoryStepWindow)
        assertTrue(feeling.diagonalDetonation)
        assertTrue(feeling.extraTrailingOrb)
    }

    @Test
    fun `every Ba-Ba invocation changes its encounter projection`() {
        val baseline = BabaInvocationRules.resolve(emptySet())
        assertFalse(baseline.lethalGap)
        assertEquals(0, baseline.bananaPilesPerBaboon)
        assertFalse(baseline.enhancedShockwave)

        assertTrue(BabaInvocationRules.resolve(setOf(ToaInvocation.MIND_THE_GAP)).lethalGap)
        val faith = BabaInvocationRules.resolve(setOf(ToaInvocation.GOTTA_HAVE_FAITH))
        assertTrue(faith.sarcophagusEnergyDamage > baseline.sarcophagusEnergyDamage)
        assertTrue(faith.sarcophagusEnergyInterval < baseline.sarcophagusEnergyInterval)
        assertEquals(
            2,
            BabaInvocationRules.resolve(setOf(ToaInvocation.JUNGLE_JAPES)).bananaPilesPerBaboon,
        )
        assertTrue(
            BabaInvocationRules.resolve(setOf(ToaInvocation.SHAKING_THINGS_UP)).enhancedShockwave
        )
        assertTrue(
            BabaInvocationRules.resolve(setOf(ToaInvocation.BOULDERDASH)).boulderReleaseInterval <
                baseline.boulderReleaseInterval
        )
    }
}

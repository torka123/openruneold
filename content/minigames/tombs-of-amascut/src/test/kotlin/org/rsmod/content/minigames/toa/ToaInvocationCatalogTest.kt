package org.rsmod.content.minigames.toa

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ToaInvocationCatalogTest {
    @Test
    fun catalogHasAllCurrentStandardInvocationsAndLegalMaximum() {
        assertEquals(44, ToaInvocationCatalog.standard.size)
        assertEquals(770, ToaInvocation.entries.sumOf(ToaInvocation::raidLevel))
        assertEquals(600, ToaInvocationCatalog.legalMaximumRaidLevel())
        assertTrue(ToaInvocation.ANCIENT_HASTE.selectable)
        assertTrue(ToaInvocation.OVERCLOCKED_2.selectable)
    }

    @Test
    fun revision240MappingsAreExplicitAndStable() {
        assertEquals(ToaInvocation.TRY_AGAIN, ToaInvocation.fromInterfaceSubcomponent(0))
        assertEquals(ToaInvocation.ANCIENT_HASTE, ToaInvocation.fromInterfaceSubcomponent(114))
        assertEquals(ToaInvocation.OVERCLOCKED_2, ToaInvocation.fromInterfaceSubcomponent(126))
        assertEquals(ToaInvocation.INSANITY, ToaInvocation.fromInterfaceSubcomponent(129))
        assertNull(ToaInvocation.fromInterfaceSubcomponent(1))
        assertEquals(1278, ToaInvocationCatalog.definition(ToaInvocation.ANCIENT_HASTE).ui.structId)
        assertEquals(2934, ToaInvocationCatalog.definition(ToaInvocation.OVERCLOCKED_2).ui.structId)
        assertEquals(14, ToaInvocationCategory.WARDENS.cacheParamValue)
    }

    @Test
    fun everyStandardEntryHasCompleteRulesMetadata() {
        val definitions = ToaInvocationCatalog.standard
        assertEquals(ToaInvocation.entries.toSet(), definitions.map { it.invocation }.toSet())
        assertEquals(44, definitions.map { it.ui.rootSubcomponent }.toSet().size)
        assertEquals(44, definitions.map { it.ui.structId }.toSet().size)
        assertEquals(44, definitions.map { it.ui.clientInvocationId }.toSet().size)
        definitions.forEach { definition ->
            assertEquals(definition.invocation.raidLevel, definition.raidLevel)
            assertEquals(definition.invocation.category, definition.category)
            assertTrue(definition.affectedMechanics.isNotEmpty(), definition.displayName)
            assertTrue(definition.ui.rootSubcomponent in ToaInvocation.interfaceSubcomponents)
        }
    }

    @Test
    fun exclusiveFamiliesReplaceOnlyTheirSibling() {
        val set = ToaInvocationSet()
        assertTrue(set.toggle(ToaInvocation.TRY_AGAIN))
        assertTrue(set.toggle(ToaInvocation.HARDCORE_RUN))
        assertEquals(setOf(ToaInvocation.HARDCORE_RUN), set.all())

        assertTrue(set.toggle(ToaInvocation.WALK_THE_PATH))
        assertTrue(set.toggle(ToaInvocation.PATHSEEKER))
        assertTrue(ToaInvocation.WALK_THE_PATH in set)
        assertTrue(ToaInvocation.PATHSEEKER in set)
        assertTrue(set.toggle(ToaInvocation.PATHMASTER))
        assertFalse(ToaInvocation.PATHSEEKER in set)
        assertTrue(ToaInvocation.PATHMASTER in set)
    }

    @Test
    fun prerequisitesAreRejectedAndDependentsCascadeOff() {
        val wardens = ToaInvocationSet()
        assertIs<ToaInvocationMutation.Rejected>(
            wardens.toggleValidated(ToaInvocation.OVERCLOCKED_2)
        )
        assertTrue(wardens.toggle(ToaInvocation.OVERCLOCKED))
        assertTrue(wardens.toggle(ToaInvocation.OVERCLOCKED_2))
        assertTrue(wardens.toggle(ToaInvocation.INSANITY))
        assertTrue(wardens.toggle(ToaInvocation.OVERCLOCKED))
        assertTrue(wardens.all().isEmpty())

        val zebak = ToaInvocationSet()
        assertIs<ToaInvocationMutation.Rejected>(
            zebak.toggleValidated(ToaInvocation.ARTERIAL_SPRAY)
        )
        assertTrue(zebak.toggle(ToaInvocation.NOT_JUST_A_HEAD))
        assertTrue(zebak.toggle(ToaInvocation.ARTERIAL_SPRAY))
        assertTrue(zebak.toggle(ToaInvocation.BLOOD_THINNERS))
        assertTrue(zebak.toggle(ToaInvocation.NOT_JUST_A_HEAD))
        assertTrue(zebak.all().isEmpty())
    }

    @Test
    fun quietAndDeadlyPrayersAndRestorationCanCoexist() {
        val set = ToaInvocationSet()
        listOf(
                ToaInvocation.QUIET_PRAYERS,
                ToaInvocation.DEADLY_PRAYERS,
                ToaInvocation.ON_A_DIET,
                ToaInvocation.DEHYDRATION,
                ToaInvocation.OVERLY_DRAINING,
            )
            .forEach { assertTrue(set.toggle(it)) }
        assertEquals(100, set.raidLevel)
        assertTrue(ToaInvocationRules.validate(set.all()).isEmpty())
    }

    @Test
    fun clientBitmapsUseCacheClientIdsNotEnumOrdinal() {
        val set = ToaInvocationSet()
        set.toggle(ToaInvocation.TRY_AGAIN)
        set.toggle(ToaInvocation.STAY_VIGILANT)
        set.toggle(ToaInvocation.INSANITY)
        assertFalse(ToaInvocation.INSANITY in set)
        set.toggle(ToaInvocation.OVERCLOCKED)
        set.toggle(ToaInvocation.OVERCLOCKED_2)
        set.toggle(ToaInvocation.INSANITY)

        val expected = intArrayOf(1 shl 1, (1 shl 1) or (1 shl 11) or (1 shl 12) or (1 shl 13), 0)
        assertContentEquals(expected, set.clientBitmaps())
    }

    @Test
    fun presetsRoundTripInExplicitClientOrderAndRejectIllegalData() {
        val selected =
            setOf(
                ToaInvocation.INSANITY,
                ToaInvocation.OVERCLOCKED_2,
                ToaInvocation.OVERCLOCKED,
                ToaInvocation.QUIET_PRAYERS,
            )
        val encoded = ToaInvocationPresetCodec.encode(selected)
        assertEquals("quiet_prayers,overclocked,overclocked_2,insanity", encoded)
        assertEquals(selected, ToaInvocationPresetCodec.decode(encoded))
        assertNull(ToaInvocationPresetCodec.decode("insanity"))
        assertNull(ToaInvocationPresetCodec.decode("not_a_real_invocation"))
        assertEquals(5, ToaInvocationPresetCodec.normalizeStorage(listOf("a")).size)
    }

    @Test
    fun emptySelectionPreservesLevelZeroBaseline() {
        val set = ToaInvocationSet()
        assertEquals(0, set.raidLevel)
        assertTrue(set.all().isEmpty())
        assertContentEquals(intArrayOf(0, 0, 0), set.clientBitmaps())
        assertTrue(ToaInvocationRules.validate(set.all()).isEmpty())
    }
}

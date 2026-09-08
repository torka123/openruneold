@file:OptIn(dev.openrune.types.util.UncheckedType::class)

package org.rsmod.content.other.pkkits

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.rsmod.api.combat.commons.magic.Spellbook
import org.rsmod.game.inv.InvObj

class KitSystemTest {
    @Test fun `tier zero gives five slots`() = assertEquals(5, KitSlotManager.capacity(0))

    @Test fun `tier one gives seven slots`() = assertEquals(7, KitSlotManager.capacity(1))

    @Test fun `tier two gives ten slots`() = assertEquals(10, KitSlotManager.capacity(2))

    @Test fun `tier three gives fifteen slots`() = assertEquals(15, KitSlotManager.capacity(3))

    @Test fun `tier four gives twenty slots`() = assertEquals(20, KitSlotManager.capacity(4))

    @Test fun `tier below range clamps safely`() = assertEquals(5, KitSlotManager.capacity(-100))

    @Test fun `tier above range clamps safely`() = assertEquals(20, KitSlotManager.capacity(100))

    @Test
    fun `slot unlock boundary is exact`() {
        assertTrue(KitSlotManager.isUnlocked(0, 4))
        assertFalse(KitSlotManager.isUnlocked(0, 5))
    }

    @Test
    fun `downgrade capacity locks rather than renumbers slots`() {
        assertTrue(KitSlotManager.isUnlocked(4, 19))
        assertFalse(KitSlotManager.isUnlocked(0, 19))
    }

    @Test
    fun `kit references round trip`() {
        val premade = KitReference(KitSource.PREMADE, "pure_melee")
        val custom = KitReference(KitSource.CUSTOM, "custom_5")
        assertEquals(premade, KitReference.decode(premade.encode()))
        assertEquals(custom, KitReference.decode(custom.encode()))
    }

    @Test fun `invalid kit reference returns null`() = assertNull(KitReference.decode("broken"))

    @Test fun `valid stats have no errors`() = assertTrue(stats().validate().isEmpty())

    @Test
    fun `zero stat is rejected`() = assertTrue(stats().copy(defence = 0).validate().isNotEmpty())

    @Test
    fun `stat above 99 is rejected`() =
        assertTrue(stats().copy(magic = 100).validate().isNotEmpty())

    @Test
    fun `default combat settings validate`() = assertTrue(KitCombatSettings().validate().isEmpty())

    @Test
    fun `invalid combat stance is rejected`() =
        assertTrue(KitCombatSettings(combatStance = 4).validate().isNotEmpty())

    @Test
    fun `invalid special energy is rejected`() =
        assertTrue(KitCombatSettings(specialEnergy = 1001).validate().isNotEmpty())

    @Test
    fun `invalid run energy is rejected`() =
        assertTrue(KitCombatSettings(runEnergy = -1).validate().isNotEmpty())

    @Test
    fun `negative quick prayer mask is rejected`() =
        assertTrue(KitCombatSettings(quickPrayers = Int.MIN_VALUE).validate().isNotEmpty())

    @Test
    fun `all requested kit categories are available`() {
        val categories = KitCategory.entries.mapTo(hashSetOf()) { it.displayName }
        assertTrue(
            categories.containsAll(
                setOf(
                    "Pure",
                    "Zerker",
                    "Med",
                    "Main",
                    "Max",
                    "Dharok",
                    "Vengeance",
                    "No-Honour",
                    "LMS",
                    "F2P",
                    "Risk Fight",
                    "Custom",
                )
            )
        )
    }

    @Test
    fun `kit policy exposes an explicit disallowed override`() =
        assertEquals(KitItemPolicyOverride.DISALLOWED, KitItemPolicyOverride.valueOf("DISALLOWED"))

    @Test
    fun `custom kit converts to custom definition`() {
        val custom = customKit()
        val definition = custom.asDefinition(1234)
        assertEquals(KitSource.CUSTOM, definition.source)
        assertEquals(1234, definition.riskValue)
        assertEquals(custom.name, definition.name)
    }

    @Test
    fun `free item loads into its exact inventory slot`() {
        val result =
            planner(KitItemAccess.FREE)
                .plan(definition(item(1, slot = 7)), empty(28), empty(14), empty(10), 10)
        val plan = assertIs<KitPlanResult.Success>(result).plan
        assertEquals(InvObj(1, 1, 0), plan.inventory[7])
    }

    @Test
    fun `free item does not consume an existing bank copy`() {
        val bank = empty(5).also { it[0] = InvObj(1, 40) }
        val result =
            planner(KitItemAccess.FREE)
                .plan(definition(item(1, count = 5)), empty(28), empty(14), bank, 5)
        val plan = assertIs<KitPlanResult.Success>(result).plan
        assertEquals(40, plan.bank[0]?.count)
        assertEquals(5, plan.inventory[0]?.count)
    }

    @Test
    fun `repeated free load is stable and does not duplicate`() {
        val definition = definition(item(1, count = 5))
        val first =
            assertIs<KitPlanResult.Success>(
                    planner(KitItemAccess.FREE).plan(definition, empty(28), empty(14), empty(5), 5)
                )
                .plan
        val second =
            assertIs<KitPlanResult.Success>(
                    planner(KitItemAccess.FREE)
                        .plan(definition, first.inventory, first.equipment, first.bank, 5)
                )
                .plan
        assertEquals(first.inventory.toList(), second.inventory.toList())
        assertEquals(first.bank.toList(), second.bank.toList())
    }

    @Test
    fun `owned item withdraws from bank`() {
        val bank = empty(5).also { it[0] = InvObj(2, 3) }
        val result =
            planner(KitItemAccess.OWNED)
                .plan(definition(item(2, count = 2)), empty(28), empty(14), bank, 5)
        val plan = assertIs<KitPlanResult.Success>(result).plan
        assertEquals(2, plan.inventory[0]?.count)
        assertEquals(1, plan.bank[0]?.count)
    }

    @Test
    fun `owned item can be reused from held setup`() {
        val held = empty(28).also { it[4] = InvObj(2, 1) }
        val result =
            planner(KitItemAccess.OWNED).plan(definition(item(2)), held, empty(14), empty(5), 5)
        val plan = assertIs<KitPlanResult.Success>(result).plan
        assertEquals(InvObj(2, 1), plan.inventory[0])
        assertTrue(plan.bank.all { it == null })
    }

    @Test
    fun `missing owned item fails without a plan`() {
        val result =
            planner(KitItemAccess.OWNED)
                .plan(definition(item(2)), empty(28), empty(14), empty(5), 5)
        assertIs<KitPlanResult.Failure>(result)
        assertTrue(result.reasons.single().contains("Missing"))
    }

    @Test
    fun `full bank rejects unrelated current item`() {
        val bank = arrayOf<InvObj?>(InvObj(10, 1), InvObj(11, 1))
        val held = empty(28).also { it[0] = InvObj(12, 1) }
        val result = planner(KitItemAccess.FREE).plan(definition(), held, empty(14), bank, 2)
        assertIs<KitPlanResult.Failure>(result)
    }

    @Test
    fun `full bank accepts current free target by direct reuse`() {
        val bank = arrayOf<InvObj?>(InvObj(10, 1), InvObj(11, 1))
        val held = empty(28).also { it[0] = InvObj(1, 1) }
        val result = planner(KitItemAccess.FREE).plan(definition(item(1)), held, empty(14), bank, 2)
        val plan = assertIs<KitPlanResult.Success>(result).plan
        assertEquals(listOf(InvObj(10, 1), InvObj(11, 1)), plan.bank.toList())
        assertEquals(InvObj(1, 1), plan.inventory[0])
    }

    @Test
    fun `exact item vars satisfy ownership`() {
        val bank = empty(5).also { it[0] = InvObj(2, 1, vars = 77) }
        val target = item(2, vars = 77)
        val result =
            planner(KitItemAccess.OWNED).plan(definition(target), empty(28), empty(14), bank, 5)
        assertEquals(77, assertIs<KitPlanResult.Success>(result).plan.inventory[0]?.vars)
    }

    @Test
    fun `different item vars do not satisfy ownership`() {
        val bank = empty(5).also { it[0] = InvObj(2, 1, vars = 76) }
        val result =
            planner(KitItemAccess.OWNED)
                .plan(definition(item(2, vars = 77)), empty(28), empty(14), bank, 5)
        assertIs<KitPlanResult.Failure>(result)
    }

    @Test
    fun `equipment slot is preserved`() {
        val kit = definition(equipment = listOf(item(3, slot = 12)))
        val result = planner(KitItemAccess.FREE).plan(kit, empty(28), empty(14), empty(5), 5)
        assertEquals(InvObj(3, 1), assertIs<KitPlanResult.Success>(result).plan.equipment[12])
    }

    @Test
    fun `extra current item is banked`() {
        val held = empty(28).also { it[0] = InvObj(9, 3, vars = 8) }
        val result = planner(KitItemAccess.FREE).plan(definition(), held, empty(14), empty(5), 5)
        assertEquals(InvObj(9, 3, 8), assertIs<KitPlanResult.Success>(result).plan.bank[0])
    }

    @Test
    fun `forbidden item rejects the kit`() {
        val result =
            planner(KitItemAccess.FORBIDDEN)
                .plan(definition(item(4)), empty(28), empty(14), empty(5), 5)
        assertIs<KitPlanResult.Failure>(result)
        assertTrue(result.reasons.single().contains("not permitted"))
    }

    @Test
    fun `multiple target items are planned together`() {
        val kit = definition(item(1, slot = 0), item(2, slot = 27))
        val result = planner(KitItemAccess.FREE).plan(kit, empty(28), empty(14), empty(5), 5)
        val inv = assertIs<KitPlanResult.Success>(result).plan.inventory
        assertNotNull(inv[0])
        assertNotNull(inv[27])
    }

    @Test
    fun `bank stack overflow is rejected`() {
        val bank = empty(2).also { it[0] = InvObj(9, Int.MAX_VALUE) }
        val held = empty(28).also { it[0] = InvObj(9, 1) }
        val result = planner(KitItemAccess.FREE).plan(definition(), held, empty(14), bank, 2)
        assertIs<KitPlanResult.Failure>(result)
    }

    @Test
    fun `empty persistence document decodes to defaults`() {
        val read = KitPersistence().decode(emptyMap())
        assertEquals(KitPlayerData(), read.data)
        assertTrue(read.warnings.isEmpty())
    }

    @Test
    fun `version one persistence defaults death interface off`() {
        val read = KitPersistence().decode(mapOf("version" to 1, "openAfterDeath" to false))
        assertFalse(read.data.openAfterDeath)
        assertEquals(KitPlayerData.CURRENT_VERSION, read.data.version)
    }

    @Test
    fun `newer persistence version is gracefully locked`() {
        val read = KitPersistence().decode(mapOf("version" to 999))
        assertTrue(read.unsupportedVersion)
        assertTrue(read.warnings.isNotEmpty())
    }

    @Test
    fun `persistence encoding includes schema tier default and death setting`() {
        val data =
            KitPlayerData(
                slotTier = 2,
                defaultKit = KitReference(KitSource.PREMADE, "pure_melee"),
                openAfterDeath = true,
            )
        val encoded = KitPersistence().encode(data)
        assertEquals(KitPlayerData.CURRENT_VERSION, encoded["version"])
        assertEquals(2, encoded["slotTier"])
        assertEquals("premade:pure_melee", encoded["defaultKit"])
        assertEquals(true, encoded["openAfterDeath"])
    }

    @Test
    fun `safe context allows kit use`() =
        assertTrue(evaluateKitRestrictions(KitRestrictionContext()).isEmpty())

    @Test
    fun `combat context is blocked`() =
        assertBlocked(KitRestrictionContext(inCombat = true), "combat")

    @Test
    fun `wilderness context is blocked`() =
        assertBlocked(KitRestrictionContext(inWilderness = true), "Wilderness")

    @Test
    fun `instance context is blocked`() =
        assertBlocked(KitRestrictionContext(inInstance = true), "instance")

    @Test
    fun `busy context is blocked`() =
        assertBlocked(KitRestrictionContext(busy = true), "current action")

    @Test
    fun `ultimate ironman context is blocked`() =
        assertBlocked(KitRestrictionContext(ultimateIronman = true), "Ultimate")

    @Test
    fun `offline or logout context is blocked`() =
        assertBlocked(KitRestrictionContext(mutableOnline = false), "logging out")

    @Test
    fun `dead context is blocked`() = assertBlocked(KitRestrictionContext(alive = false), "dead")

    @Test
    fun `unpermitted area context is blocked`() =
        assertBlocked(KitRestrictionContext(permittedArea = false), "safe area")

    @Test
    fun `all restriction reasons are reported together`() {
        val errors =
            evaluateKitRestrictions(
                KitRestrictionContext(
                    mutableOnline = false,
                    alive = false,
                    inCombat = true,
                    inWilderness = true,
                    inInstance = true,
                    busy = true,
                    ultimateIronman = true,
                    permittedArea = false,
                )
            )
        assertEquals(8, errors.size)
    }

    private fun assertBlocked(context: KitRestrictionContext, text: String) {
        assertTrue(evaluateKitRestrictions(context).any { it.contains(text, ignoreCase = true) })
    }

    private fun planner(access: KitItemAccess): KitBankPlanner =
        KitBankPlanner(
            object : KitItemPolicy() {
                override fun access(definition: KitDefinition, item: KitItem): KitItemAccess =
                    access
            }
        )

    private fun definition(
        vararg inventory: KitItem,
        equipment: List<KitItem> = emptyList(),
    ): KitDefinition =
        KitDefinition(
            id = "test",
            name = "Test",
            category = KitCategory.Custom,
            description = "Test kit",
            inventory = inventory.toList(),
            equipment = equipment,
            stats = stats(),
            settings = KitCombatSettings(),
            loadPolicy = KitLoadPolicy.MIXED,
            riskValue = 0,
        )

    private fun item(id: Int, slot: Int = 0, count: Int = 1, vars: Int = 0): KitItem =
        KitItem("obj.test_$id", id, count, vars, slot)

    private fun stats(): KitStats = KitStats(99, 99, 99, 99, 99, 99, 99)

    private fun customKit(): CustomKit =
        CustomKit(
            slot = 0,
            id = "custom_1",
            name = "My Kit",
            category = KitCategory.Custom,
            inventory = emptyList(),
            equipment = emptyList(),
            stats = stats(),
            settings = KitCombatSettings(spellbook = Spellbook.Ancients),
            createdAt = 1,
            updatedAt = 2,
        )

    private fun empty(size: Int): Array<InvObj?> = arrayOfNulls(size)
}

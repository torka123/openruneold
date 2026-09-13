package org.rsmod.content.other.special.attacks.ranged

import dev.openrune.types.ItemServerType
import dev.openrune.types.NpcServerType
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.specials.SpecialAttackManager
import org.rsmod.api.specials.SpecialAttackRepository
import org.rsmod.api.specials.combat.RangedSpecialAttack
import org.rsmod.game.entity.Npc
import org.rsmod.game.inv.InvObj
import org.rsmod.game.proj.ProjAnim
import org.rsmod.game.type.getInvObj
import org.rsmod.map.CoordGrid

class TonalzticsOfRalosSpecialDamageTest {
    @Test
    fun allTonalzticsHitsUseSeventyFivePercentOfTheNormalMaximum() {
        assertEquals(0, TonalzticsOfRalosSpecialDamage.maxHit(0))
        assertEquals(0, TonalzticsOfRalosSpecialDamage.maxHit(1))
        assertEquals(3, TonalzticsOfRalosSpecialDamage.maxHit(4))
        assertEquals(74, TonalzticsOfRalosSpecialDamage.maxHit(99))
        assertEquals(75, TonalzticsOfRalosSpecialDamage.maxHit(100))
    }

    @Test
    fun divisionDrainsTenPercentOfCurrentMagicWithIntegerFlooring() {
        assertEquals(0, TonalzticsOfRalosSpecialDamage.defenceDrain(0))
        assertEquals(0, TonalzticsOfRalosSpecialDamage.defenceDrain(9))
        assertEquals(1, TonalzticsOfRalosSpecialDamage.defenceDrain(10))
        assertEquals(30, TonalzticsOfRalosSpecialDamage.defenceDrain(300))
    }

    /** S08: retain Division's existing between-roll drain through the native NPC setter. */
    @Test
    fun chargedDivisionSecondAccuracyRollSeesNativeDefenceMinimum() {
        val target =
            Mockito.spy(
                Npc(NpcServerType(id = 1, defence = 260, magic = 300)).apply {
                    defenceDrainMinimum = 250
                }
            )
        // Only presentation is suppressed; both reads and mutations use the native NPC state.
        Mockito.doNothing()
            .`when`(target)
            .spotanim(Mockito.anyString(), Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt())
        val defenceAtAccuracy = mutableListOf<Int>()
        val queuedDefence = mutableListOf<Int>()
        val projectile =
            ProjAnim(
                spotanim = 0,
                startHeight = 0,
                endHeight = 0,
                startTime = 0,
                endTime = 30,
                angle = 0,
                progress = 0,
                sourceIndex = 0,
                targetIndex = 0,
                startCoord = CoordGrid.ZERO,
                endCoord = CoordGrid.ZERO,
            )
        val manager =
            Mockito.mock(SpecialAttackManager::class.java) { invocation ->
                when (invocation.method.name) {
                    "rollRangedAccuracy" -> {
                        assertTrue(invocation.arguments[1] === target)
                        defenceAtAccuracy += target.defenceLvl
                        true
                    }
                    "calculateRangedMaxHit" -> 40
                    "rollRangedMaxHit" -> 30
                    "spawnProjectile" -> projectile
                    "queueRangedHit",
                    "queueRangedDamage" -> {
                        queuedDefence += target.defenceLvl
                        null
                    }
                    else -> Mockito.RETURNS_DEFAULTS.answer(invocation)
                }
            }
        val registered = mutableMapOf<String, RangedSpecialAttack>()
        val repository =
            Mockito.mock(SpecialAttackRepository::class.java) { invocation ->
                if (invocation.method.name == "registerRanged") {
                    registered[invocation.arguments[0] as String] =
                        invocation.arguments[1] as RangedSpecialAttack
                }
                null
            }
        with(TonalzticsOfRalosSpecialAttack()) { repository.register(manager) }
        val charged = registered.getValue("obj.tonalztics_of_ralos_charged")
        val access = Mockito.mock(ProtectedAccess::class.java)
        val weaponType = ItemServerType(id = 1)
        val weapon = InvObj(weaponType)
        val attack = CombatAttack.Ranged(weapon, type = null, style = null)

        // Division checks the weapon's cache definition, independent of its inventory contents.
        // The scoped static mock is thread-local and leaves shared cache maps untouched.
        Mockito.mockStatic(Class.forName("org.rsmod.game.type.ItemTypeExtKt")).use { items ->
            items.`when`<ItemServerType> { getInvObj(weapon) }.thenReturn(weaponType)
            assertTrue(runImmediately { with(charged) { access.attack(target, attack) } })
        }

        assertEquals(listOf(260, 250), defenceAtAccuracy)
        assertEquals(listOf(250, 250), queuedDefence)
        assertEquals(250, target.defenceLvl)
    }

    private fun <T> runImmediately(block: suspend () -> T): T {
        var completed: Result<T>? = null
        block.startCoroutine(
            object : Continuation<T> {
                override val context = EmptyCoroutineContext

                override fun resumeWith(result: Result<T>) {
                    completed = result
                }
            }
        )
        assertNotNull(completed, "Registered Division must complete without suspending")
        return completed!!.getOrThrow()
    }
}

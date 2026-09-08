package org.rsmod.api.specials

import dev.openrune.ServerCacheManager
import dev.openrune.types.util.UncheckedType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.specials.combat.MagicSpecialAttack
import org.rsmod.api.specials.weapon.SpecialAttackWeapons
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj

@OptIn(UncheckedType::class)
class SpecialAttackRegistryMagicRoutingTest {
    @Test
    fun `only registered magic specials override normal attack routing`() {
        val registry = SpecialAttackRegistry(SpecialAttackWeapons())
        registry.add(VOLATILE_STAFF, NIGHTMARE_SPECIAL_COST, NoOpMagicSpecial)

        assertTrue(registry.isMagicSpecial(InvObj(VOLATILE_STAFF, 1)))
        assertFalse(registry.isMagicSpecial(InvObj(BASE_NIGHTMARE_STAFF, 1)))
        assertFalse(registry.isMagicSpecial(InvObj(HARMONISED_NIGHTMARE_STAFF, 1)))
        assertFalse(registry.isMagicSpecial(null))
    }

    @Test
    fun `revision 240 assigns 55 percent energy only to the special variants`() {
        val cache = ServerCacheManager.init(240)
        try {
            val weapons = SpecialAttackWeapons()
            weapons.startup()

            assertEquals(NIGHTMARE_SPECIAL_COST, weapons.getSpecialEnergy(VOLATILE_STAFF))
            assertEquals(NIGHTMARE_SPECIAL_COST, weapons.getSpecialEnergy(ELDRITCH_STAFF))
            assertNull(weapons.getSpecialEnergy(BASE_NIGHTMARE_STAFF))
            assertNull(weapons.getSpecialEnergy(HARMONISED_NIGHTMARE_STAFF))
        } finally {
            cache.close()
        }
    }

    private object NoOpMagicSpecial : MagicSpecialAttack {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Staff,
        ): Boolean = true

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Staff,
        ): Boolean = true
    }

    private companion object {
        const val BASE_NIGHTMARE_STAFF = 24422
        const val HARMONISED_NIGHTMARE_STAFF = 24423
        const val VOLATILE_STAFF = 24424
        const val ELDRITCH_STAFF = 24425
        const val NIGHTMARE_SPECIAL_COST = 550
    }
}

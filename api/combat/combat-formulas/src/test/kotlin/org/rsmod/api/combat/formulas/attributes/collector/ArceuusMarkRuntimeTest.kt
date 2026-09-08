package org.rsmod.api.combat.formulas.attributes.collector

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import java.util.EnumSet
import kotlin.test.*
import org.junit.jupiter.api.BeforeAll
import org.rsmod.annotations.InternalApi
import org.rsmod.api.combat.commons.magic.ArceuusSpellState
import org.rsmod.api.combat.commons.magic.Spellbook
import org.rsmod.api.combat.formulas.attributes.CombatSpellAttributes
import org.rsmod.api.combat.formulas.attributes.CombatNpcAttributes
import org.rsmod.api.combat.formulas.accuracy.magic.MagicAccuracyOperations
import org.rsmod.api.combat.formulas.maxhit.magic.MagicMaxHitOperations
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.game.entity.Player

class ArceuusMarkRuntimeTest {
    @OptIn(InternalApi::class)
    @Test fun `real spell attributes follow timed Mark and ignore display preference`() {
        val spell = assertNotNull(ServerCacheManager.getItem("obj.br_adamant_scimitar".asRSCM(RSCMType.OBJ)))
        val collector = CombatMagicAttributeCollector()
        val player = Player().apply {
            uuid = 333L; observerUUID = 333L; slotId = 1; assignUid()
            currentMapClock = 100
            inv = invMap.getOrPut("inv.inv")
            worn = invMap.getOrPut("inv.worn")
        }
        for (disabled in 0..1) {
            VarPlayerIntMapSetter.set(player, "varbit.buff_mark_of_darkness_disabled", disabled)
            ArceuusSpellState.clearMark(player)
            fun collect() = collector.spellCollect(player, spell, Spellbook.Arceuus, false, DefaultGameRandom(42L))
            assertFalse(CombatSpellAttributes.MarkOfDarkness in collect())
            val demon = EnumSet.of(CombatNpcAttributes.Demon)
            assertEquals(1200, MagicAccuracyOperations.modifySpellAttackRoll(1000, 0, collect(), demon))
            assertEquals(30, MagicMaxHitOperations.modifySpellDamageRange(30, 30, 5, 0, collect(), demon).last)
            ArceuusSpellState.mark(player, 102)
            assertTrue(CombatSpellAttributes.MarkOfDarkness in collect())
            assertTrue(CombatSpellAttributes.Demonbane in collect())
            assertEquals(1400, MagicAccuracyOperations.modifySpellAttackRoll(1000, 0, collect(), demon))
            assertEquals(37, MagicMaxHitOperations.modifySpellDamageRange(30, 30, 5, 0, collect(), demon).last)
            // Mark applies after a 20% equipment bonus: 30 -> 36 -> 45.
            val equippedBase = MagicMaxHitOperations.modifySpellBaseDamage(30, 99, 200, 0, collect(), demon)
            assertEquals(45, MagicMaxHitOperations.modifySpellDamageRange(equippedBase, 30, 5, 0, collect(), demon).last)
            player.currentMapClock = 102
            assertFalse(CombatSpellAttributes.MarkOfDarkness in collect())
            assertEquals(disabled, player.vars["varbit.buff_mark_of_darkness_disabled"])
            player.currentMapClock = 100
        }
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() {
            if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
        }
    }
}

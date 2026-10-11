package org.rsmod.content.bosses.yama

import kotlin.test.Test
import kotlin.test.assertEquals

class YamaFlareDamageRulesTest {
    @Test
    fun `ordinary weapons preserve their rolled flare damage`() {
        assertEquals(17, YamaRules.flareModifiedDamage(17, 42, YamaAttackStyle.MELEE, false, false))
    }

    @Test
    fun `magic and scorching bow force the current maximum hit`() {
        assertEquals(42, YamaRules.flareModifiedDamage(3, 42, YamaAttackStyle.MAGIC, false, false))
        assertEquals(42, YamaRules.flareModifiedDamage(3, 42, YamaAttackStyle.RANGED, true, false))
    }

    @Test
    fun `demonbane vulnerability doubles after maximum-hit forcing`() {
        assertEquals(34, YamaRules.flareModifiedDamage(17, 42, YamaAttackStyle.MELEE, false, true))
        assertEquals(84, YamaRules.flareModifiedDamage(3, 42, YamaAttackStyle.MAGIC, false, true))
        assertEquals(84, YamaRules.flareModifiedDamage(3, 42, YamaAttackStyle.RANGED, true, true))
    }
}

package org.rsmod.content.minigames.clanhall

import kotlin.test.Test
import kotlin.test.assertEquals

class ClanHallNativeUiTest {
    @Test
    fun `revision 240 coffer buttons have explicit non-ordinal mappings`() {
        assertEquals(
            mapOf(
                7 to CofferAmountPreset.One,
                8 to CofferAmountPreset.Thousand,
                9 to CofferAmountPreset.Million,
                10 to CofferAmountPreset.TenMillion,
                11 to CofferAmountPreset.Custom,
                12 to CofferAmountPreset.All,
            ),
            ClanHallNativeUi.CofferWithdrawButtons,
        )
        assertEquals(
            mapOf(
                5 to CofferAmountPreset.One,
                6 to CofferAmountPreset.Thousand,
                7 to CofferAmountPreset.Million,
                8 to CofferAmountPreset.TenMillion,
                9 to CofferAmountPreset.Custom,
                10 to CofferAmountPreset.All,
            ),
            ClanHallNativeUi.CofferDepositButtons,
        )
    }

    @Test
    fun `party quantity selection clamps to authoritative available count`() {
        assertEquals(1, PartyQuantityMode.One.resolve(99).coerceAtMost(3))
        assertEquals(3, PartyQuantityMode.Five.resolve(99).coerceAtMost(3))
        assertEquals(3, PartyQuantityMode.Custom.resolve(3).coerceAtMost(9))
        assertEquals(9, PartyQuantityMode.All.resolve(1).coerceAtMost(9))
    }

    @Test
    fun `native party interface contract is explicit`() {
        assertEquals(783, ClanHallNativeUi.PARTY_MAIN)
        assertEquals(266, ClanHallNativeUi.PARTY_SIDE)
        assertEquals(916, ClanHallNativeUi.PARTY_CHEST_INV)
        assertEquals(92, ClanHallNativeUi.PARTY_OFFER_INV)
        assertEquals(93, ClanHallNativeUi.PLAYER_INV)
        assertEquals((783 shl 16) or 17, ClanHallNativeUi.packed(783, 17))
    }
}

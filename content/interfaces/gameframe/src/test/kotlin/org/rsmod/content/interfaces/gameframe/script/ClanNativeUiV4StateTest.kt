package org.rsmod.content.interfaces.gameframe.script

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.rsmod.api.net.social.ClanApplication
import org.rsmod.api.net.social.ClanInterest
import org.rsmod.api.net.social.ClanMemberSnapshot

class ClanNativeUiV4StateTest {
    @Test
    fun `member context actions resolve the visible filtered row`() {
        val hidden = member(1, "Hidden")
        val visible = member(2, "Visible")
        val session =
            ClanUiV4Session(members = listOf(hidden, visible), filteredMembers = listOf(visible))

        assertEquals(visible, session.visibleMemberAt(0))
        assertNull(session.visibleMemberAt(1))
    }

    @Test
    fun `native applicant row receives only the player name`() {
        val application =
            ClanApplication(
                42,
                "Applicant",
                "message|that|must|not|be|a|name",
                combatLevel = 126,
                totalLevel = 2_376,
                questPoints = 321,
                isIron = true,
                isUltimateIron = false,
                isHardcoreIron = false,
                skillLevels = (1..24).map(Int::toInt),
            )

        assertEquals("Applicant", application.nativeApplicantName())
        val details = application.nativeApplicantDetails().split('|')
        assertEquals(27, details.size)
        assertEquals(listOf("Applicant", "126", "2376"), details.take(3))
        assertEquals((1..24).map(Int::toString), details.drop(3))
    }

    @Test
    fun `native interest mask uses storage ids from revision 240`() {
        val selected = setOf(ClanInterest.FreeToPlay, ClanInterest.BossHunting, ClanInterest.SoulWars)

        assertEquals((1 shl 0) or (1 shl 6) or (1 shl 15), selected.nativeInterestMask())
    }

    @Test
    fun `native interest row resolves only its clickable first dynamic child`() {
        assertEquals(ClanInterest.FreeToPlay, ClanUi.interestAt(0))
        assertEquals(ClanInterest.NewbieFriendly, ClanUi.interestAt(3))
        assertEquals(ClanInterest.SoulWars, ClanUi.interestAt(45))
        assertNull(ClanUi.interestAt(1))
        assertNull(ClanUi.interestAt(46))
        assertNull(ClanUi.interestAt(-1))
    }

    private fun member(id: Int, name: String): ClanMemberSnapshot =
        ClanMemberSnapshot(
            characterId = id,
            displayName = name,
            previousDisplayName = "",
            rank = 0,
            titleSlot = 0,
            muted = false,
            joinedRuneDay = 0,
            worldId = 0,
        )
}

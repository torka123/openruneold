package org.rsmod.content.bosses.royaltitans

import dev.openrune.rscm.RSCM.asRSCM
import kotlin.test.*
import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.*
import org.rsmod.api.player.input.ResumePNameDialogInput
import org.rsmod.api.player.input.ResumePStringDialogInput
import org.rsmod.api.player.protect.ProtectedAccessContext
import org.rsmod.api.player.protect.ProtectedAccessLauncher.Companion.withProtectedAccess
import org.rsmod.game.client.Client
import org.rsmod.game.client.NoopClient

class RoyalTitansNameDialogTest {
    @AfterEach fun clearMocks() = framework().clearInlineMocks()

    @Test
    fun `native name input sends script109 and accepts only the native name response`() {
        val f = RoyalTitansTestWorld()
        val player = f.newPlayer()
        val partner = f.newPlayer(2)
        val messages = mutableListOf<Any>()
        player.client =
            object : Client<Any, Any> by NoopClient {
                override fun write(message: Any) {
                    messages += message
                }
            }
        val expected = ResumePNameDialogInput.Result.SameWorld(partner.uid)
        var result: ResumePNameDialogInput.Result? = null
        val title = "Whose fight would you like to join? You must be on their friends' list."
        assertTrue(
            withProtectedAccess(player, mock(ProtectedAccessContext::class.java)) {
                result = nameDialog(title)
            }
        )
        assertEquals(
            listOf(RunClientScript(109, listOf(title))),
            messages.filterIsInstance<RunClientScript>(),
        )
        assertEquals(1, messages.size)
        assertNotNull(player.activeCoroutine)
        player.resumeActiveCoroutine(ResumePStringDialogInput(partner.displayName))
        assertNull(result, "A generic string resume cannot answer a name dialog")
        player.resumeActiveCoroutine(ResumePNameDialogInput(expected))
        assertEquals(expected, result)
        assertNull(player.activeCoroutine)
    }

    @Test
    fun `native name input rechecks delay and modal protection before continuing`() {
        for (changeModal in listOf(false, true)) {
            val player = RoyalTitansTestWorld().newPlayer()
            var resumed = false
            assertTrue(
                withProtectedAccess(player, mock(ProtectedAccessContext::class.java)) {
                    nameDialog("Player name")
                    resumed = true
                }
            )
            if (changeModal)
                player.ui.modals.backing.put(
                    "component.toplevel_osrs_stretch:mainmodal".asRSCM(),
                    909,
                )
            else player.delay(1)
            player.resumeActiveCoroutine(
                ResumePNameDialogInput(ResumePNameDialogInput.Result.NotFound)
            )
            assertFalse(resumed)
            assertNull(player.activeCoroutine)
        }
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() = RoyalTitansCombatIntegrationTest.cache()
    }
}

package org.rsmod.content.bosses.royaltitans

import dev.openrune.ServerCacheManager
import kotlin.test.*
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.*
import org.rsmod.api.game.process.player.PlayerInteractionProcessor
import org.rsmod.api.npc.isValidTarget
import org.rsmod.api.player.interact.NpcInteractions
import org.rsmod.api.player.protect.ProtectedAccessContext
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.route.BoundValidator
import org.rsmod.api.route.RayCastValidator
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext

class RoyalTitansCorpseInteractionTest {
    @Test
    fun `transformed corpses dispatch each native option from captured adjacent tiles`() {
        for ((active, corpseId, y) in listOf(Triple(12596, 14148, 34), Triple(14147, 14149, 28))) {
            val f = RoyalTitansTestWorld()
            val player = f.enter()
            f.tick(6)
            f.hit(player, f.npc(player, 12596), 406)
            f.tick(10)
            val corpse = f.npc(player, active)
            f.hit(player, corpse, 600)
            f.tick(2)
            assertEquals(corpseId, corpse.visType.id)
            assertTrue(
                corpse.isValidTarget(),
                "Native noncombat interaction must accept inactive stats",
            )
            player.coords = f.tile(player, 31, y)
            player.previousCoords = player.coords
            val received = mutableListOf<Int>()
            // This test probes native reach/option routing. The gameplay bus now already owns
            // the real corpse handlers through InstanceScript; do not register duplicate ops.
            val probeEvents = EventBus()
            val scripts = ScriptContext(probeEvents, CheatCommandMap(), EngineQueueCache())
            with(scripts) {
                onOpNpc1(assertNotNull(ServerCacheManager.getNpc(corpseId))) { received += 1 }
                onOpNpc3(corpse.visType.internalName) { received += 3 }
                onOpNpc4(corpse.visType.internalName) { received += 4 }
            }
            val interactions = NpcInteractions(probeEvents)
            val contextFactory = mock(ProtectedAccessContextFactory::class.java)
            `when`(contextFactory.create()).thenReturn(mock(ProtectedAccessContext::class.java))
            val launcher = ProtectedAccessLauncher(contextFactory)
            val constructor = PlayerInteractionProcessor::class.java.constructors.single()
            val processor =
                constructor.newInstance(
                    *constructor.parameterTypes
                        .map { type ->
                            when (type) {
                                EventBus::class.java -> probeEvents
                                BoundValidator::class.java -> BoundValidator(f.collision)
                                RayCastValidator::class.java -> RayCastValidator(f.collision)
                                NpcInteractions::class.java -> interactions
                                ProtectedAccessLauncher::class.java -> launcher
                                Set::class.java -> emptySet<Any>()
                                else -> mock(type)
                            }
                        }
                        .toTypedArray()
                ) as PlayerInteractionProcessor
            for (op in listOf(InteractionOp.Op1, InteractionOp.Op3, InteractionOp.Op4)) {
                interactions.interact(player, corpse, op)
                processor.process(player)
            }
            assertEquals(listOf(1, 3, 4), received)
            assertEquals(f.tile(player, 31, y), player.coords, "No need to walk inside the corpse")
            framework().clearInlineMocks()
        }
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() = RoyalTitansCombatIntegrationTest.cache()
    }
}

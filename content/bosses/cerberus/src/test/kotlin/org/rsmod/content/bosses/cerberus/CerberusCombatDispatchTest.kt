package org.rsmod.content.bosses.cerberus

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.rsmod.api.npc.events.NpcQueueEvents
import org.rsmod.api.npc.events.interact.AiPlayerDefaultEvents
import org.rsmod.api.npc.events.interact.AiPlayerEvents
import org.rsmod.api.npc.events.interact.ApDefaultEvent
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext

class CerberusCombatDispatchTest {
    @org.junit.jupiter.api.AfterEach
    fun releaseFixtureMocks() = org.mockito.Mockito.framework().clearInlineMocks()

    @Test
    fun `sitting spawn awakened to active uses encounter combat instead of generic combat`() {
        val bus = registeredBus()
        val boss = boss(CerberusAssets.SITTING_NPC_ID)
        boss.transmog(
            requireNotNull(ServerCacheManager.getNpc(CerberusAssets.ACTIVE_NPC_ID)),
            Int.MAX_VALUE,
        )

        assertEquals(CerberusAssets.SITTING_NPC_ID, boss.id)
        assertEquals(CerberusAssets.ACTIVE_NPC_ID, boss.visType.id)
        val event =
            AiPlayerInteractions(bus, PlayerList()).apTrigger(boss, Player(), InteractionOp.Op2)
        assertIs<AiPlayerEvents.Ap2>(event)
        assertEquals(boss.id.toLong(), event.id)
    }

    @Test
    fun `every base and visible boss form stays routed to the phase guarded encounter`() {
        val bus = registeredBus()
        val interactions = AiPlayerInteractions(bus, PlayerList())
        val target = Player()
        for (base in FORMS) {
            val boss = boss(base)
            for (visible in FORMS) {
                boss.transmog(requireNotNull(ServerCacheManager.getNpc(visible)), Int.MAX_VALUE)
                assertIs<AiPlayerEvents.Ap2>(
                    interactions.apTrigger(boss, target, InteractionOp.Op2),
                    "Base $base visible $visible must not fall through to generic combat",
                )
            }
        }
    }

    @Test
    fun `death cleanup and respawn are registered under the immutable spawn identity`() {
        val bus = registeredBus()
        val queue = "queue.death".asRSCM(RSCMType.QUEUE)
        for (base in FORMS) {
            val boss = boss(base)
            boss.transmog(
                requireNotNull(ServerCacheManager.getNpc(CerberusAssets.ACTIVE_NPC_ID)),
                Int.MAX_VALUE,
            )
            val event = NpcQueueEvents.Type(boss, Unit, queue)
            assertTrue(
                bus.contains(event::class.java, event.id),
                "Missing death callback for spawn $base",
            )
        }
    }

    @Test
    fun `unrelated npcs still select standard combat`() {
        val bus = registeredBus()
        val unrelated = Npc(dev.openrune.types.NpcServerType(id = 1, name = "unrelated"))
        assertIs<AiPlayerDefaultEvents.Ap2>(
            AiPlayerInteractions(bus, PlayerList())
                .apTrigger(unrelated, Player(), InteractionOp.Op2)
        )
    }

    private fun registeredBus(): EventBus {
        val bus = CerberusTestWorld().events
        val context = ScriptContext(bus, CheatCommandMap(), EngineQueueCache())
        with(context) {
            bus.subscribeSuspend(
                AiPlayerDefaultEvents.Ap2::class.java,
                ApDefaultEvent.ID.toLong(),
            ) {
                error("Generic combat must not run for a Cerberus encounter")
            }
        }
        return bus
    }

    private fun boss(id: Int) = Npc(requireNotNull(ServerCacheManager.getNpc(id)))

    companion object {
        private val FORMS =
            listOf(
                CerberusAssets.SITTING_NPC_ID,
                CerberusAssets.ACTIVE_NPC_ID,
                CerberusAssets.RESETTING_NPC_ID,
            )

        @JvmStatic
        @BeforeAll
        fun loadRealCache() {
            CerberusTestCache.ensureLoaded()
        }
    }
}

package org.rsmod.content.minigames.toa

import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.framework
import org.mockito.Mockito.mockingDetails
import org.rsmod.annotations.InternalApi
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.hit.HitType

@OptIn(InternalApi::class)
class ToaPendingHitNativeTest {
    @Test
    fun `owned hit dispatch shows source-less impact graphics before rejecting delivery`() {
        ToaNativeCombatFixture().use { f ->
            val player = f.player()
            val outside = f.player(2)
            val raid = f.start(player)
            val due = f.clock.cycle + 2
            for (target in listOf(player, outside)) {
                raid.encounter.queuePendingHit(
                    ToaPendingHit(target.uid, target.coords, due, 20, BabaAssets.EXPLOSION)
                )
            }
            clearInvocations(f.world)
            f.advance(2)
            f.queueProcess.process()
            assertEquals(2, raid.encounter.pendingHits.size)
            assertEquals(0, graphics(f))
            f.dispatchLateCycle()
            assertTrue(raid.encounter.pendingHits.isEmpty())
            assertEquals(2, graphics(f))
            assertTrue(f.hits(player).isEmpty())
            assertTrue(f.hits(outside).isEmpty())
        }
    }

    @Test
    fun `room reset reuses native hit owner and deletion invalidates already delivered native hits`() {
        ToaNativeCombatFixture().use { f ->
            val player = f.player()
            f.start(player)
            val raid = f.enterBaba(player)
            val queues = assertNotNull(raid.encounter.pendingHitQueues)
            raid.encounter.queuePendingHit(
                ToaPendingHit(
                    player.uid,
                    player.coords,
                    f.clock.cycle + 50,
                    20,
                    BabaAssets.EXPLOSION,
                )
            )
            assertTrue(f.manager.devEnterRoom(player, "akkha"))
            assertSame(queues, raid.encounter.pendingHitQueues)
            assertTrue(queues.pending.isEmpty())
            val source = f.boss(raid)
            raid.encounter.queuePendingHit(
                ToaPendingHit(
                    player.uid,
                    player.coords,
                    f.clock.cycle,
                    20,
                    BabaAssets.EXPLOSION,
                    requireTargetOnTile = false,
                    hitType = HitType.Melee,
                )
            )
            f.dispatchLateCycle()
            val queued = f.hits(player).single()
            assertEquals(1, queued.first)
            assertTrue(queues.pending.isEmpty())
            val hp = player.hitpoints
            f.npcs.del(source, Int.MAX_VALUE)
            f.processHit(player, queued.second)
            assertEquals(
                hp,
                player.hitpoints,
                "Native source retirement still guards damage impact",
            )
            f.instances.terminate(raid.instance)
            assertTrue(queues.pending.isEmpty())
            assertFailsWith<IllegalStateException> {
                raid.encounter.queuePendingHit(
                    ToaPendingHit(player.uid, player.coords, f.clock.cycle, 1, null)
                )
            }
        }
    }

    @Test
    fun `Zebak stale future hits retire immediately while valid pending hits survive offline pause`() {
        ToaNativeCombatFixture().use { f ->
            val player = f.player()
            f.start(player)
            val raid = f.enterZebak(player)
            val generation = raid.zebak.generation
            val due = f.clock.cycle + 6
            val valid =
                ToaPendingHit(
                    player.uid,
                    player.coords,
                    due,
                    17,
                    BabaAssets.EXPLOSION,
                    requireTargetOnTile = false,
                    generation = generation,
                )
            raid.encounter.queuePendingHit(
                valid.copy(landsAt = due + 100, generation = generation - 1)
            )
            raid.encounter.queuePendingHit(valid)
            clearInvocations(f.world)
            f.dispatchLateCycle()
            assertEquals(listOf(valid), raid.encounter.pendingHits)
            assertEquals(0, graphics(f))
            f.events.publish(SessionStateEvent.Logout(player))
            f.dispatchLateCycle(due + 10)
            assertEquals(listOf(valid), raid.encounter.pendingHits)
            assertTrue(f.manager.rejoin(player, raid))
            clearInvocations(f.world)
            f.dispatchLateCycle()
            assertTrue(raid.encounter.pendingHits.isEmpty())
            assertTrue(graphics(f) >= 1)
            assertTrue(f.hits(player).any { it.first == 1 && it.second.damage == 17 })
            f.instances.terminate(raid.instance)
        }
    }

    private fun graphics(f: ToaNativeCombatFixture): Int =
        mockingDetails(f.world).invocations.count { it.method.name.startsWith("spotanimMap") }

    @AfterEach fun releaseMocks() = framework().clearInlineMocks()

    companion object {
        @BeforeAll
        @JvmStatic
        fun cache() {
            ToaCacheTestSupport.initRevision240()
        }
    }
}

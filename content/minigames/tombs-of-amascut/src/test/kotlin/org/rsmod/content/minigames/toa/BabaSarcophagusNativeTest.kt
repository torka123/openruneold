package org.rsmod.content.minigames.toa

import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.framework
import org.mockito.Mockito.mockingDetails
import org.rsmod.annotations.InternalApi
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.proj.ProjAnim
import org.rsmod.map.CoordGrid

/** Actual manager integration of the retained sarcophagus behavior at db2b4f3f3. */
@OptIn(InternalApi::class)
class BabaSarcophagusNativeTest {
    @Test
    fun `reverse opening preserves room order at plus three and both successful energy cadences`() {
        for (faith in listOf(false, true)) {
            ToaNativeCombatFixture().use { f ->
                val player = f.player()
                val raid = start(f, player, faith)
                val first = sarc(f, raid, 26, 30)
                val second = sarc(f, raid, 44, 36)
                val queues = assertNotNull(raid.baba.sarcophagusQueues)
                open(f, raid, second)
                open(f, raid, first)
                val opened = f.clock.cycle
                val interval = if (faith) 4 else 6
                val actors =
                    listOf(first, second).map { runtime ->
                        raid.spawnedNpcs.single {
                            it.id == BabaAssets.SARCOPHAGUS && it.coords == runtime.tile
                        }
                    }
                assertEquals(
                    listOf(opened + 3, opened + 3),
                    listOf(first, second).map(queues::energyAt),
                )
                f.dispatchLateCycle(opened + 2)
                assertTrue(projectiles(f).isEmpty())
                f.advance(1)
                f.queueProcess.process()
                assertTrue(
                    projectiles(f).isEmpty(),
                    "Default processing cannot dispatch these owners",
                )
                f.dispatchLateCycle()
                assertEquals(actors.map { it.slotId + 1 }, projectiles(f).map { it.sourceIndex })
                assertEquals(
                    List(2) { -(player.slotId + 1) },
                    projectiles(f).map { it.targetIndex },
                )
                assertEquals(opened + 3 + interval, queues.energyAt(first))
                assertEquals(opened + 3 + interval, queues.energyAt(second))
                f.dispatchLateCycle(opened + 3 + interval - 1)
                assertEquals(2, projectiles(f).size)
                f.dispatchLateCycle(opened + 3 + interval)
                assertEquals(
                    actors.map { it.slotId + 1 },
                    projectiles(f).takeLast(2).map { it.sourceIndex },
                )
                val due = queues.energyAt(first)
                assertNotNull(raid.participant(player.uid)).spectating = true
                f.dispatchLateCycle(due + 20)
                assertEquals(4, projectiles(f).size)
                assertEquals(
                    due,
                    queues.energyAt(first),
                    "No eligible target retains the overdue deadline",
                )
                assertEquals(due, queues.energyAt(second))
                assertNotNull(raid.participant(player.uid)).spectating = false
                f.dispatchLateCycle()
                assertEquals(6, projectiles(f).size)
                assertEquals(f.clock.cycle + interval, queues.energyAt(first))
                assertEquals(f.clock.cycle + interval, queues.energyAt(second))
                assertEquals(
                    actors.map { it.slotId + 1 },
                    projectiles(f).takeLast(2).map { it.sourceIndex },
                )
            }
            framework().clearInlineMocks()
        }
    }

    @Test
    fun `missing energy actor uses current boss and native retirement cancels its next launch`() {
        for (ending in listOf("delete", "reset", "terminate")) {
            ToaNativeCombatFixture().use { f ->
                val player = f.player()
                val raid = start(f, player)
                val source = f.baba(raid)
                val sarc = sarc(f, raid, 26, 30)
                open(f, raid, sarc)
                val actor = raid.spawnedNpcs.single { it.id == BabaAssets.SARCOPHAGUS }
                remove(f, raid, actor)
                assertFalse(actor.isSlotAssigned)
                val queues = assertNotNull(raid.baba.sarcophagusQueues)
                f.dispatchLateCycle(queues.energyAt(sarc))
                assertEquals(source.slotId + 1, projectiles(f).single().sourceIndex, ending)
                assertEquals(f.clock.cycle + 6, queues.energyAt(sarc), ending)
                when (ending) {
                    "delete" -> f.npcs.del(source, Int.MAX_VALUE)
                    "reset" -> f.enterBaba(player)
                    "terminate" -> f.instances.terminate(raid.instance)
                }
                assertTrue(queues.isClosed, ending)
                assertEquals(-1, queues.energyAt(sarc), ending)
                assertEquals(-1, queues.runnerAt, ending)
                val packets = projectiles(f).size
                f.advance(20)
                f.queueProcess.process()
                queues.processRunner()
                queues.processEnergy(sarc)
                assertEquals(packets, projectiles(f).size, ending)
            }
            framework().clearInlineMocks()
        }
    }

    @Test
    fun `native runner waits eight ticks keeps global strike alignment and reassigns after loss`() {
        ToaNativeCombatFixture().use { f ->
            val player = f.player()
            val raid = start(f, player)
            val source = f.baba(raid)
            val sarc = sarc(f, raid, 26, 30)
            // A due tick congruent to one cannot become a strike merely because selection ran.
            f.advance((1 - f.clock.cycle.mod(4)).mod(4))
            spawnBaboons(f, raid, source)
            spawnBaboons(f, raid, source)
            val baboons = raid.spawnedNpcs.filter { it.id == BabaAssets.BABOON }
            assertTrue(baboons.size >= 2)
            val first = baboons[0]
            val second = baboons[1]
            val beside = CoordGrid(sarc.tile.x - 1, sarc.tile.z, sarc.tile.level)
            PathingEntityCommon.telejump(first, f.collision, beside)
            PathingEntityCommon.telejump(second, f.collision, beside)
            val due = f.clock.cycle + 8
            assertEquals(due, raid.baba.nextBaboonSarcophagusAt)
            f.dispatchLateCycle(due - 1)
            assertNull(raid.baba.baboonSarcophagusRunner)
            f.advance(1)
            f.queueProcess.process()
            assertNull(raid.baba.baboonSarcophagusRunner)
            f.dispatchLateCycle()
            assertEquals(first.hashCode(), raid.baba.baboonSarcophagusRunner)
            assertEquals(-1, raid.baba.nextBaboonSarcophagusAt)
            assertEquals(60, sarc.hitpoints)
            assertNull(first.mode, "Nearby runner route is cleared on each tick")
            f.dispatchLateCycle(due + 2)
            assertEquals(60, sarc.hitpoints)
            f.dispatchLateCycle(due + 3)
            assertEquals(40, sarc.hitpoints, "Damage retains the global cycle modulo four")
            f.npcs.del(first, Int.MAX_VALUE)
            f.dispatchLateCycle(due + 4)
            assertNull(raid.baba.baboonSarcophagusRunner)
            val reassignment = due + 4 + 8
            assertEquals(reassignment, raid.baba.nextBaboonSarcophagusAt)
            f.dispatchLateCycle(reassignment - 1)
            assertNull(raid.baba.baboonSarcophagusRunner)
            f.dispatchLateCycle(reassignment)
            assertEquals(second.hashCode(), raid.baba.baboonSarcophagusRunner)
            assertEquals(40, sarc.hitpoints)
            f.dispatchLateCycle(reassignment + 3)
            assertEquals(20, sarc.hitpoints)
        }
    }

    private fun start(
        f: ToaNativeCombatFixture,
        player: Player,
        faith: Boolean = false,
    ): ToaRaidSession {
        val party = f.manager.createParty(player)
        if (faith) assertTrue(party.invocations.toggle(ToaInvocation.GOTTA_HAVE_FAITH))
        f.start(player)
        val raid = f.enterBaba(player)
        f.baba(raid).actionDelay = Int.MAX_VALUE
        player.coords = tile(f, raid, 25, 24)
        return raid
    }

    private fun sarc(
        f: ToaNativeCombatFixture,
        raid: ToaRaidSession,
        x: Int,
        z: Int,
    ): BabaSarcophagusRuntime =
        BabaSarcophagusRuntime(tile(f, raid, x, z)).also { raid.baba.sarcophagi[it.tile] = it }

    private fun open(
        f: ToaNativeCombatFixture,
        raid: ToaRaidSession,
        sarc: BabaSarcophagusRuntime,
    ) {
        ToaRaidManager::class
            .java
            .getDeclaredMethod(
                "openBabaSarcophagus",
                ToaRaidSession::class.java,
                BabaSarcophagusRuntime::class.java,
            )
            .apply { isAccessible = true }
            .invoke(f.manager, raid, sarc)
        assertEquals(BabaSarcophagusState.OPEN, sarc.state)
    }

    private fun remove(f: ToaNativeCombatFixture, raid: ToaRaidSession, npc: Npc) {
        ToaRaidManager::class
            .java
            .getDeclaredMethod("removeNpc", ToaRaidSession::class.java, Npc::class.java)
            .apply { isAccessible = true }
            .invoke(f.manager, raid, npc)
    }

    private fun spawnBaboons(f: ToaNativeCombatFixture, raid: ToaRaidSession, source: Npc) {
        ToaRaidManager::class
            .java
            .getDeclaredMethod("spawnBabaBaboons", ToaRaidSession::class.java, Npc::class.java)
            .apply { isAccessible = true }
            .invoke(f.manager, raid, source)
    }

    private fun tile(f: ToaNativeCombatFixture, raid: ToaRaidSession, x: Int, z: Int): CoordGrid =
        assertNotNull(
            f.instances.localCoord(raid.instance, ToaInstanceLayout.roomLocal(ToaRoom.BABA, x, z))
        )

    private fun projectiles(f: ToaNativeCombatFixture): List<ProjAnim> =
        mockingDetails(f.world)
            .invocations
            .flatMap { it.arguments.filterIsInstance<ProjAnim>() }
            .filter { it.spotanim == BabaAssets.SARCOPHAGUS_ENERGY }

    @AfterEach fun releaseMocks() = framework().clearInlineMocks()

    companion object {
        @BeforeAll
        @JvmStatic
        fun cache() {
            ToaCacheTestSupport.initRevision240()
        }
    }
}

package org.rsmod.content.drops

import dev.openrune.ServerCacheManager
import dtx.core.ArgMap
import dtx.core.RollRandom
import dtx.rs.RSDropTable
import dtx.rs.RSWeightedTable
import java.util.IdentityHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
import org.mockito.Mockito.mock
import org.mockito.Mockito.withSettings
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.collectionlog.CollectionLogAwardKind
import org.rsmod.api.collectionlog.CollectionLogAwardSource
import org.rsmod.api.collectionlog.CollectionLogService
import org.rsmod.api.config.constants
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.DropTableRegistry
import org.rsmod.api.droptable.KillRollContext
import org.rsmod.api.market.MarketPrices
import org.rsmod.api.net.social.ClanChatService
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.content.drops.toml.ContentDropTableTomlResolver
import org.rsmod.content.quest.manager.QuestRequirementResolver
import org.rsmod.game.client.Client
import org.rsmod.game.client.NoopClient
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.obj.Obj
import org.rsmod.map.CoordGrid

/**
 * M11 mapping: docs/bosses/nex/NEX_OSRS_AUDIT.md, native bodyguard drop dispatch increment.
 * Retained W6 evidence (Fumus 15221135, Umbra 15221134, Cruor 15221130, Glacies 15221131) and the
 * four existing drops/tables/monsters TOMLs specify five 1/1000 ceremonial pieces.
 *
 * The registry/resolver load the real resources. A test-only spy inserts RollRandom.Context at
 * selectResult, then calls the real method: no table entries, hooks or selected results are
 * replaced. The native kill hook currently supplies source/area context without injectable table
 * entropy. Real reward delivery runs to recorded object, client, collection-log and broadcast
 * boundaries. Native Nex death/hero dispatch is covered by NexBodyguardDeathDropsIntegrationTest;
 * these tests start with its native kill context and make no ground-object persistence claim.
 */
class NexBodyguardDropPipelineTest {
    @Test
    fun `all four registered bodyguards deliver each ceremonial piece through native rewards`() {
        val f = Fixture()
        for (guard in guards) {
            val npc = f.npc(guard)
            for ((slot, piece) in pieces.withIndex()) {
                f.clearDeliveries()
                val eventId = npc.id * 1000 + slot
                f.kill(npc, slot, eventId)

                val expected = InvObj("obj.ancient_ceremonial_$piece", 1)
                val spawn = f.spawned.single()
                assertEquals(expected, spawn.item)
                assertSame(f.player, spawn.owner)
                assertEquals(f.dropCoords, spawn.coords)
                assertEquals(constants.lootdrop_duration, spawn.duration)
                assertEquals<List<Any>>(
                    listOf(RunClientScript(7192, listOf(npc.id, eventId, expected.id, 1))),
                    f.messages,
                )
                assertEquals<List<List<Any?>>>(
                    listOf(
                        listOf(
                            f.player,
                            expected.id,
                            1,
                            CollectionLogAwardSource(CollectionLogAwardKind.NpcDrop, guard.alias),
                            "$eventId:0",
                        )
                    ),
                    f.logEvents,
                )
                val broadcast = f.broadcasts.single()
                assertSame(f.player, broadcast[0])
                assertEquals(
                    assertNotNull(ServerCacheManager.getItem(expected.id)).name,
                    broadcast[1],
                )
                assertEquals(1, broadcast[2])
                assertEquals("npc:${guard.alias}:$eventId:0", broadcast[4])
            }
        }
        assertEquals(20, f.tableSelections)
    }

    @Test
    fun `registered nothing weight boundaries produce no object or reward notifications`() {
        val f = Fixture()
        for (guard in guards) {
            val npc = f.npc(guard)
            for (slot in listOf(5, 999)) {
                f.kill(npc, slot, npc.id * 1000 + slot)
            }
        }
        assertEquals(8, f.tableSelections)
        assertTrue(f.spawned.isEmpty())
        assertTrue(f.messages.isEmpty())
        assertTrue(f.logEvents.isEmpty())
        assertTrue(f.broadcasts.isEmpty())
    }

    private data class Guard(val id: Int, val alias: String, val table: String)

    private data class Spawn(
        val item: InvObj,
        val coords: CoordGrid,
        val duration: Int,
        val owner: Player,
    )

    private class Fixture {
        init {
            if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
        }

        val messages = mutableListOf<Any>()
        val player =
            Player(
                    client =
                        object : Client<Any, Any> by NoopClient {
                            override fun write(message: Any) {
                                messages += message
                            }
                        }
                )
                .apply {
                    accountHash = 11L
                    observerUUID = 11L
                }
        val dropCoords = CoordGrid(2911, 5197)
        val spawned = mutableListOf<Spawn>()
        val logEvents = mutableListOf<List<Any?>>()
        val broadcasts = mutableListOf<List<Any?>>()
        var tableSelections = 0
            private set

        private val areaChecker = sharedAreaChecker
        private val registered = sharedRegistry
        private val tables =
            IdentityHashMap<RSDropTable<Player, DropRollItem>, RSDropTable<Player, DropRollItem>>()
        private lateinit var activeNpc: Npc
        private lateinit var activeRandom: SlotRandom
        private val registry =
            mock(
                DropTableRegistry::class.java,
                withSettings().spiedInstance(registered).defaultAnswer { invocation ->
                    val result = invocation.callRealMethod()
                    if (invocation.method.name == "forNpc" && invocation.arguments.size == 2) {
                        assertSame(activeNpc, invocation.arguments[0])
                        assertSame(areaChecker, invocation.arguments[1])
                        @Suppress("UNCHECKED_CAST")
                        val table = assertNotNull(result) as RSDropTable<Player, DropRollItem>
                        tables.getOrPut(table) { withControlledEntropy(table) }
                    } else result
                },
            )
        private val objects =
            mock(ObjRepository::class.java) { invocation ->
                check(invocation.method.name.startsWith("add-"))
                val item = invocation.getArgument<InvObj>(0)
                val coords = CoordGrid(invocation.getArgument<Int>(1))
                val owner = invocation.getArgument<Player>(3)
                spawned += Spawn(item, coords, invocation.getArgument(2), owner)
                Obj.fromOwner(owner, coords, item)
            }
        private val log =
            mock(CollectionLogService::class.java) { invocation ->
                check(invocation.method.name == "record")
                logEvents += invocation.arguments.toList()
                null
            }
        private val clans =
            mock(ClanChatService::class.java) { invocation ->
                check(invocation.method.name == "publishLootBroadcast")
                broadcasts += invocation.arguments.toList()
                null
            }
        private val delivery =
            NpcRewardDeliveryService(
                areaChecker,
                objects,
                mock(GameRandom::class.java) { error("Fixed ceremonial counts need no entropy") },
                log,
                mock(MarketPrices::class.java),
                clans,
            )
        private val hook =
            NpcDropTableKillHook(
                registry,
                areaChecker,
                delivery,
                mock(Dt2AwakenedCompletionService::class.java),
            )

        fun npc(guard: Guard): Npc {
            val npc = Npc(assertNotNull(ServerCacheManager.getNpc(guard.id)), CoordGrid(2910, 5196))
            assertEquals(guard.alias, npc.type.internalName)
            val table = assertNotNull(registered.forNpc(npc, areaChecker))
            assertEquals(guard.table, table.tableIdentifier)
            assertEquals(listOf(guard.alias), table.npcs)
            val main =
                table.tableEntries
                    .filterIsInstance<RSWeightedTable<Player, DropRollItem>>()
                    .single()
            assertEquals(1000.0, main.maxRoll)
            assertEquals(
                listOf(1.0, 1.0, 1.0, 1.0, 1.0, 995.0),
                main.tableEntries.map { it.weight },
            )
            return npc
        }

        fun kill(npc: Npc, slot: Int, eventId: Int) {
            activeNpc = npc
            activeRandom = SlotRandom(slot)
            val previousSelections = tableSelections
            hook.onKill(NpcDeathKillContext(player, npc, eventId, dropCoords))
            assertEquals(previousSelections + 1, tableSelections)
            assertEquals(1, activeRandom.calls)
        }

        fun clearDeliveries() {
            spawned.clear()
            messages.clear()
            logEvents.clear()
            broadcasts.clear()
        }

        private fun withControlledEntropy(
            table: RSDropTable<Player, DropRollItem>
        ): RSDropTable<Player, DropRollItem> =
            mock(
                table.javaClass,
                withSettings().spiedInstance(table).defaultAnswer { invocation ->
                    if (invocation.method.name == "selectResult") {
                        assertSame(player, invocation.arguments[0])
                        val args = invocation.getArgument<ArgMap>(1)
                        assertSame(activeNpc, args[KillRollContext.npc])
                        assertSame(areaChecker, args[KillRollContext.areaChecker])
                        args[RollRandom.Context] = activeRandom
                        tableSelections++
                    }
                    invocation.callRealMethod()
                },
            )
    }

    private class SlotRandom(private val slot: Int) : RollRandom {
        var calls = 0
            private set

        override fun nextInt(bound: Int): Int {
            assertEquals(1000, bound)
            assertEquals(0, calls++, "Each bodyguard has one standard table roll")
            return slot.also { require(it in 0 until bound) }
        }

        override fun nextDouble(bound: Double): Double =
            error("Ceremonial bodyguard tables have no separate chance rolls")
    }

    private companion object {
        private val sharedAreaChecker = mock(AreaChecker::class.java)
        private val sharedRegistry by lazy {
            if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
            DropTableRegistry(
                ContentDropTableTomlResolver(
                    sharedAreaChecker,
                    mock(QuestRequirementResolver::class.java),
                )
            )
        }
        private val pieces = listOf("mask", "top", "legs", "gloves", "boots")
        private val guards =
            listOf(
                Guard(11283, "npc.nex_smokemage", "Fumus Drops"),
                Guard(11284, "npc.nex_shadowmage", "Umbra Drops"),
                Guard(11285, "npc.nex_bloodmage", "Cruor Drops"),
                Guard(11286, "npc.nex_icemage", "Glacies Drops"),
            )
    }
}

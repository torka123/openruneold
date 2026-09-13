package org.rsmod.content.drops

import dev.openrune.ServerCacheManager
import dtx.core.RollResult
import kotlin.test.*
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.collectionlog.CollectionLogService
import org.rsmod.api.config.constants
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.market.MarketPrices
import org.rsmod.api.net.social.ClanChatService
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.game.client.Client
import org.rsmod.game.client.NoopClient
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.obj.EnsouledHeadProvenance
import org.rsmod.game.obj.Obj
import org.rsmod.map.CoordGrid

/**
 * S04 preparation mapping in docs/bosses/nex/NEX_OSRS_AUDIT.md. Native delivery and
 * EnsouledHeadProvenance define the existing selection/order/provenance contract; ToA's concrete
 * reward plan is the comparable workflow. This opt-in snapshot does not interleave delivery with
 * arbitrary content callbacks. These tests claim no durable entitlement or exactly-once delivery.
 */
class NpcRewardPreparationTest {
    @Test
    fun `valid plans preserve native delivery order duplicate lines and quantity entropy`() {
        val result =
            RollResult.ListOf(
                listOf(
                    DropRollItem(
                        "obj.coins",
                        17,
                        transformObj = { "obj.firerune" },
                        bonusDrops =
                            listOf(
                                DropRollItem(
                                    "obj.waterrune",
                                    2..5,
                                    bonusDrops =
                                        listOf(
                                            DropRollItem(
                                                "obj.coins",
                                                1..1,
                                                countChoices = listOf(3, 9, 15),
                                            )
                                        ),
                                ),
                                DropRollItem("obj.firerune", 17),
                            ),
                    ),
                    DropRollItem("obj.earthrune", 1..1, countChoices = listOf(8, 13)),
                )
            )
        val extras = listOf(DropRollItem("obj.coins", 2))
        val prepared = Fixture(listOf(4, 1, 0))
        val legacy = Fixture(listOf(4, 1, 0))
        val plan = prepared.prepare(result, extras)
        legacy.deliver(result, extras)

        assertEquals(
            listOf(
                InvObj("obj.firerune", 17),
                InvObj("obj.waterrune", 4),
                InvObj("obj.coins", 9),
                InvObj("obj.firerune", 17),
                InvObj("obj.earthrune", 8),
                InvObj("obj.coins", 2),
            ),
            plan.items.map { it.obj },
        )
        assertEquals(legacy.spawned.map { it.item }, plan.items.map { it.obj })
        assertEquals((0..5).toList(), plan.items.map { it.ordinal })
        assertEquals((0..5).map { "71:$it" }, legacy.logEvents.map { it[4] })
        assertEquals(listOf("range:2:5", "choice:3", "choice:2"), prepared.random.calls)
        assertEquals(legacy.random.calls, prepared.random.calls)
        assertTrue(prepared.random.remaining.isEmpty())
        assertTrue(legacy.random.remaining.isEmpty())
        assertEquals(constants.lootdrop_duration, plan.duration)
        prepared.assertNoEmission()
    }

    @Test
    fun `gates suppress complete bonus subtrees while Nothing and extras retain native order`() {
        val f = Fixture()
        val callbacks = mutableListOf<String>()
        val forbiddenBonus =
            DropRollItem("obj.coins", 1, condition = { error("Skipped bonus ran") })
        val result =
            RollResult.ListOf(
                listOf(
                    DropRollItem(
                        "obj.coins",
                        1,
                        isNothing = true,
                        condition = { error("Nothing condition ran") },
                        bonusDrops = listOf(forbiddenBonus),
                    ),
                    DropRollItem(
                        "obj.coins",
                        1,
                        condition = {
                            assertSame(f.player, it)
                            callbacks += "condition"
                            false
                        },
                        killCondition = { _, _, _ ->
                            error("Rejected condition reached kill gate")
                        },
                        transformObj = { error("Rejected condition reached transform") },
                        bonusDrops = listOf(forbiddenBonus),
                    ),
                    DropRollItem(
                        "obj.coins",
                        1,
                        condition = {
                            callbacks += "before-kill"
                            true
                        },
                        killCondition = { player, npc, area ->
                            assertSame(f.player, player)
                            assertSame(f.npc, npc)
                            assertSame(f.area, area)
                            callbacks += "kill"
                            false
                        },
                        transformObj = { error("Rejected kill gate reached transform") },
                        bonusDrops = listOf(forbiddenBonus),
                    ),
                )
            )
        val extras =
            listOf(
                DropRollItem("obj.coins", 2, bonusDrops = listOf(DropRollItem("obj.firerune", 1)))
            )
        val plan = f.prepare(result, extras)
        assertEquals(listOf("condition", "before-kill", "kill"), callbacks)
        assertEquals(
            listOf(InvObj("obj.coins", 2), InvObj("obj.firerune", 1)),
            plan.items.map { it.obj },
        )
        assertEquals(listOf(0, 1), plan.items.map { it.ordinal })
        assertTrue(f.prepare(RollResult.Nothing()).items.isEmpty())
        assertTrue(f.prepare(RollResult.ListOf(emptyList())).items.isEmpty())
        assertEquals(plan.items, f.prepare(RollResult.Nothing(), extras).items)
        assertTrue(f.random.calls.isEmpty())
        f.assertNoEmission()
    }

    @Test
    fun `stored ensouled heads become fresh native items with the supplied packed origin`() {
        val f = Fixture()
        val stored = assertNotNull(ServerCacheManager.getItem(13448))
        val fresh = assertNotNull(ServerCacheManager.getItem(13447))
        val result =
            RollResult.ListOf(
                listOf(DropRollItem(stored.internalName, 2), DropRollItem(fresh.internalName, 1))
            )
        val plan = f.prepare(result)
        assertEquals(listOf(13447, 13447), plan.items.map { it.obj.id })
        assertEquals(listOf(2, 1), plan.items.map { it.obj.count })
        for (line in plan.items) {
            assertEquals(f.coords.packed + 1, line.obj.vars)
            assertEquals(f.coords, EnsouledHeadProvenance.origin(line.obj.id, line.obj.vars))
        }
        val legacy = Fixture()
        legacy.deliver(result)
        assertEquals(legacy.spawned.map { it.item }, plan.items.map { it.obj })
        f.assertNoEmission()
    }

    @Test
    fun `prepared output is detached immutable and freezes original source coordinates and duration`() {
        val f = Fixture(listOf(1))
        f.player.lootDropDuration = 321
        f.npc.transmog(assertNotNull(ServerCacheManager.getNpc(14148)), Int.MAX_VALUE)
        var selected = "obj.firerune"
        val choices = mutableListOf(3, 9)
        val bonuses = mutableListOf(DropRollItem("obj.waterrune", 2))
        val drops =
            mutableListOf(
                DropRollItem(
                    "obj.coins",
                    1..1,
                    countChoices = choices,
                    transformObj = { selected },
                    bonusDrops = bonuses,
                )
            )
        val extras = mutableListOf(DropRollItem("obj.coins", 1))
        val originalKey = f.npc.type.internalName
        val plan = f.prepare(RollResult.ListOf(drops), extras)
        val expected = plan.items.toList()

        choices.clear()
        bonuses.clear()
        drops.clear()
        extras.clear()
        selected = "obj.airrune"
        f.player.lootDropDuration = 999
        f.player.coords = f.coords.translate(20, 20)
        f.npc.coords = f.coords.translate(30, 30)
        f.npc.transmog(assertNotNull(ServerCacheManager.getNpc(14149)), Int.MAX_VALUE)

        assertEquals(12596, plan.sourceNpcId)
        assertEquals(originalKey, plan.sourceNpcKey)
        assertEquals(f.coords, plan.coords)
        assertEquals(321, plan.duration)
        assertEquals(
            listOf(InvObj("obj.firerune", 9), InvObj("obj.waterrune", 2), InvObj("obj.coins", 1)),
            plan.items.map { it.obj },
        )
        assertEquals(expected, plan.items)
        assertFailsWith<UnsupportedOperationException> {
            (plan.items as MutableList<PreparedNpcReward.Item>).clear()
        }
        assertEquals(expected, plan.items)
        f.assertNoEmission()
    }

    @Test
    fun `preparation callbacks see no intervening delivery or client output`() {
        val f = Fixture()
        val observedObjects = mutableListOf<Int>()
        var callbackState = 0
        val result =
            RollResult.ListOf(
                listOf(
                    DropRollItem(
                        "obj.coins",
                        1,
                        condition = {
                            callbackState++
                            true
                        },
                    ),
                    DropRollItem(
                        "obj.firerune",
                        2,
                        condition = {
                            assertEquals(
                                1,
                                callbackState,
                                "Earlier callback effects remain visible",
                            )
                            observedObjects += f.spawned.size
                            true
                        },
                    ),
                )
            )
        val plan = f.prepare(result)
        assertEquals(2, plan.items.size)
        assertEquals(listOf(0), observedObjects)
        f.assertNoEmission()

        // Legacy delivery keeps its existing per-item output interleaving; preparation is opt-in.
        callbackState = 0
        f.deliver(result)
        assertEquals(listOf(0, 1), observedObjects)
        assertEquals(2, f.spawned.size)
        assertEquals(2, f.messages.size)
    }

    @Test
    fun `late callback quantity and unresolved item failures return no partial plan or output`() {
        val failures =
            listOf<Pair<String, (RuntimeException) -> DropRollItem>>(
                "condition" to
                    { error ->
                        DropRollItem("obj.coins", 1, condition = { throw error })
                    },
                "kill condition" to
                    { error ->
                        DropRollItem("obj.coins", 1, killCondition = { _, _, _ -> throw error })
                    },
                "transform" to
                    { error ->
                        DropRollItem("obj.coins", 1, transformObj = { throw error })
                    },
                "zero count" to { DropRollItem("obj.coins", 0) },
                "negative count" to { DropRollItem("obj.coins", -1) },
                // No cache mutation: an unresolved alias exercises rejection, not a valid-key
                // mapping whose final native definition is missing.
                "unresolved item alias" to
                    {
                        DropRollItem("obj.__npc_reward_preparation_missing__", 1)
                    },
            )
        for ((label, invalid) in failures) {
            val f = Fixture()
            val failure = IllegalStateException(label)
            var returned: PreparedNpcReward? = null
            val thrown =
                assertFails(label) {
                    returned =
                        f.prepare(
                            RollResult.Single(
                                DropRollItem(
                                    "obj.firerune",
                                    1,
                                    bonusDrops = listOf(invalid(failure)),
                                )
                            )
                        )
                }
            if (label in setOf("condition", "kill condition", "transform"))
                assertSame(failure, thrown)
            assertNull(returned, label)
            f.assertNoEmission()
        }
    }

    private data class Spawn(
        val item: InvObj,
        val coords: CoordGrid,
        val duration: Int,
        val owner: Player,
    )

    private class Fixture(rolls: List<Int> = emptyList()) {
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
        val npc = Npc(assertNotNull(ServerCacheManager.getNpc(12596)), CoordGrid(2910, 9571))
        val coords = CoordGrid(2911, 9567)
        val area = mock(AreaChecker::class.java)
        val random = ScriptedRandom(rolls)
        val spawned = mutableListOf<Spawn>()
        val logEvents = mutableListOf<List<Any?>>()
        val objects =
            mock(ObjRepository::class.java) { invocation ->
                check(invocation.method.name.startsWith("add-"))
                val item = invocation.getArgument<InvObj>(0)
                val tile = CoordGrid(invocation.getArgument<Int>(1))
                val owner = invocation.getArgument<Player>(3)
                spawned += Spawn(item, tile, invocation.getArgument(2), owner)
                Obj.fromOwner(owner, tile, item)
            }
        val log =
            mock(CollectionLogService::class.java) { invocation ->
                check(invocation.method.name == "record")
                logEvents += invocation.arguments.toList()
                null
            }
        val prices = mock(MarketPrices::class.java)
        val clans = mock(ClanChatService::class.java)
        val service = NpcRewardDeliveryService(area, objects, random, log, prices, clans)

        fun prepare(
            result: RollResult<DropRollItem>,
            extras: List<DropRollItem> = emptyList(),
        ): PreparedNpcReward = service.prepare(player, npc, result, coords, extras)

        fun deliver(result: RollResult<DropRollItem>, extras: List<DropRollItem> = emptyList()) =
            service.deliver(player, npc, result, coords, 71, extraDrops = extras)

        fun assertNoEmission() {
            verifyNoInteractions(objects, log, prices, clans)
            assertTrue(spawned.isEmpty())
            assertTrue(logEvents.isEmpty())
            assertTrue(
                messages.isEmpty(),
                "Preparation must not emit a loot-tracker or client packet",
            )
        }
    }

    private class ScriptedRandom(rolls: List<Int>) : GameRandom {
        val remaining = ArrayDeque(rolls)
        val calls = mutableListOf<String>()

        override fun of(maxExclusive: Int): Int {
            calls += "choice:$maxExclusive"
            return remaining.removeFirst().also { require(it in 0 until maxExclusive) }
        }

        override fun of(minInclusive: Int, maxInclusive: Int): Int {
            calls += "range:$minInclusive:$maxInclusive"
            return remaining.removeFirst().also { require(it in minInclusive..maxInclusive) }
        }

        override fun randomDouble(): Double =
            error("Item quantity preparation needs no floating roll")
    }
}

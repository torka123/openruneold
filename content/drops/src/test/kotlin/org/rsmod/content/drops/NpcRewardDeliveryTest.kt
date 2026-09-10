package org.rsmod.content.drops

import dev.openrune.ServerCacheManager
import dtx.rs.RSDropTable
import dtx.rs.rsGuaranteedTable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.mockito.Mockito.mock
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.collectionlog.CollectionLogService
import org.rsmod.api.config.constants
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.DropTableRegistry
import org.rsmod.api.market.MarketPrices
import org.rsmod.api.net.social.ClanChatService
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.obj.Obj
import org.rsmod.map.CoordGrid

/** Characterizes the existing kill-hook delivery before extracting it for deferred rewards. */
class NpcRewardDeliveryTest {
    @Test
    fun `native delivery uses shared duration and personal owner`() {
        val f = Fixture(listOf(DropRollItem("obj.coins", 17)))
        f.kill()
        assertEquals(1, f.spawned.size)
        assertEquals(constants.lootdrop_duration, f.spawned.single().duration)
        assertSame(f.player, f.spawned.single().owner)
        assertEquals(f.dropCoords, f.spawned.single().coords)
        assertEquals(17, f.spawned.single().item.count)
    }

    @Test
    fun `duration override transformations counts and recursive bonuses retain award order`() {
        val f =
            Fixture(
                listOf(
                    DropRollItem(
                        "obj.coins",
                        3..9,
                        transformObj = { "obj.firerune" },
                        bonusDrops = listOf(DropRollItem("obj.waterrune", 2)),
                    )
                )
            )
        f.player.lootDropDuration = 321
        f.kill()
        assertEquals(listOf(554, 555), f.spawned.map { it.item.id })
        assertEquals(listOf(9, 2), f.spawned.map { it.item.count })
        assertEquals(listOf(321, 321), f.spawned.map { it.duration })
        assertEquals(listOf("71:0", "71:1"), f.logEvents.map { it[4] })
        assertEquals(
            listOf("npc:${f.npc.type.internalName}:71:0", "npc:${f.npc.type.internalName}:71:1"),
            f.broadcasts.map { it[4] },
        )
    }

    @Test
    fun `nothing player and kill conditions prevent delivery and bonus rewards`() {
        val f =
            Fixture(
                listOf(
                    DropRollItem("obj.coins", 1, isNothing = true),
                    DropRollItem(
                        "obj.coins",
                        1,
                        condition = { false },
                        bonusDrops = listOf(DropRollItem("obj.firerune", 1)),
                    ),
                    DropRollItem("obj.coins", 1, killCondition = { _, _, _ -> false }),
                )
            )
        f.kill()
        assertTrue(f.spawned.isEmpty())
        assertTrue(f.logEvents.isEmpty())
        assertTrue(f.broadcasts.isEmpty())
    }

    @Test
    fun `suppressed death does not deliver a deferred reward early`() {
        val f = Fixture(listOf(DropRollItem("obj.coins", 1)))
        f.npc.suppressDrops = true
        f.kill()
        assertTrue(f.spawned.isEmpty())
    }

    @Test
    fun `deferred delivery preserves source and stable ordinal across bonus and completion extras`() {
        val f = Fixture(emptyList())
        f.npc.suppressDrops = true
        f.npc.transmog(assertNotNull(ServerCacheManager.getNpc(14148)), Int.MAX_VALUE)
        f.delivery.deliver(
            f.player,
            f.npc,
            dtx.core.RollResult.Single(
                DropRollItem(
                    "obj.firerune",
                    1,
                    bonusDrops = listOf(DropRollItem("obj.waterrune", 2)),
                )
            ),
            f.dropCoords,
            99,
            awardIdentity = "completion:account",
            extraDrops = listOf(DropRollItem("obj.coins", 3)),
        )
        assertEquals(listOf(554, 555, 995), f.spawned.map { it.item.id })
        assertEquals(
            listOf("completion:account:0", "completion:account:1", "completion:account:2"),
            f.logEvents.map { it[4] },
        )
        assertTrue(
            f.broadcasts.all {
                (it[4] as String).startsWith("npc:${f.npc.type.internalName}:completion:account:")
            }
        )
        assertEquals(12596, f.npc.id)
        assertEquals(14148, f.npc.visType.id)
    }

    private data class Spawn(
        val item: InvObj,
        val coords: CoordGrid,
        val duration: Int,
        val owner: Player,
    )

    private class Fixture(rewards: List<DropRollItem>) {
        init {
            if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
        }

        val player =
            Player().apply {
                accountHash = 11L
                observerUUID = 11L
            }
        val npc = Npc(assertNotNull(ServerCacheManager.getNpc(12596)), CoordGrid(2910, 9571))
        val dropCoords = CoordGrid(2911, 9567)
        val spawned = mutableListOf<Spawn>()
        val logEvents = mutableListOf<List<Any?>>()
        val broadcasts = mutableListOf<List<Any?>>()
        private val table =
            RSDropTable<Player, DropRollItem>(
                tableIdentifier = "delivery characterization",
                guaranteed = rsGuaranteedTable { rewards.forEach { add(it) } },
            )
        private val registry =
            mock(DropTableRegistry::class.java) { invocation ->
                if (invocation.method.name == "forNpc") table else null
            }
        private val objects =
            mock(ObjRepository::class.java) { invocation ->
                if (invocation.method.name.startsWith("add-")) {
                    val item = invocation.getArgument<InvObj>(0)
                    val coords = CoordGrid(invocation.getArgument<Int>(1))
                    val owner = invocation.getArgument<Player>(3)
                    spawned += Spawn(item, coords, invocation.getArgument(2), owner)
                    Obj.fromOwner(owner, coords, item)
                } else null
            }
        private val random =
            mock(GameRandom::class.java) { invocation ->
                if (invocation.method.name == "of") invocation.arguments.last() else 0.5
            }
        private val log =
            mock(CollectionLogService::class.java) { invocation ->
                if (invocation.method.name == "record") logEvents += invocation.arguments.toList()
                null
            }
        private val clans =
            mock(ClanChatService::class.java) { invocation ->
                if (invocation.method.name == "publishLootBroadcast")
                    broadcasts += invocation.arguments.toList()
                null
            }
        val delivery =
            NpcRewardDeliveryService(
                mock(AreaChecker::class.java),
                objects,
                random,
                log,
                mock(MarketPrices::class.java),
                clans,
            )
        private val hook =
            NpcDropTableKillHook(
                registry,
                mock(AreaChecker::class.java),
                delivery,
                mock(Dt2AwakenedCompletionService::class.java),
            )

        fun kill() = hook.onKill(NpcDeathKillContext(player, npc, 71, dropCoords))
    }
}

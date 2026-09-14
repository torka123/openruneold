package org.rsmod.content.bosses.nex

import dev.openrune.ParamMap
import dev.openrune.ServerCacheManager
import dev.openrune.toml.rsconfig.decodeRuneScape
import dev.openrune.toml.rsconfig.decodeRuneScapeBlocks
import dev.openrune.toml.rsconfig.rsconfig
import dev.openrune.toml.tomlMapper
import dev.openrune.types.NpcServerType
import java.nio.file.Path
import kotlin.reflect.typeOf
import kotlin.test.*
import net.rsprot.protocol.game.outgoing.misc.player.MessageGame
import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.*
import org.rsmod.annotations.InternalApi
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.collectionlog.CollectionLogAwardKind
import org.rsmod.api.collectionlog.CollectionLogAwardSource
import org.rsmod.api.collectionlog.CollectionLogService
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.BaseParams
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.droptable.DropTableRegistry
import org.rsmod.api.market.MarketPrices
import org.rsmod.api.net.social.ClanChatService
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.random.GameRandom
import org.rsmod.api.registry.obj.ObjRegistry
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.EntityLifecycleProcess
import org.rsmod.api.repo.controller.ControllerRepository
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.content.drops.Dt2AwakenedCompletionService
import org.rsmod.content.drops.NpcDropTableKillHook
import org.rsmod.content.drops.NpcRewardDeliveryService
import org.rsmod.content.generic.killcount.KillcountNpcKillHook
import org.rsmod.game.MapClock
import org.rsmod.game.client.Client
import org.rsmod.game.client.NoopClient
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.PlayerUid

/**
 * M31 routing, NEX_OSRS_AUDIT.md: real shared delivery, ground registry, lifecycle timers and
 * native tracker event allocation. Rolls/lifetime characterize current Nex behavior, not OSRS
 * parity or durable S04 recovery. Collection Log/clan output is observed at its shared boundary.
 */
@OptIn(InternalApi::class)
class NexSharedRewardDeliveryTest {
    @Test
    fun `two recipients retain concrete rolls and native source with one log event per item`() {
        val f = Fixture()
        val spectator = f.player(3, 33).also { f.players[3] = it }
        f.npc.transmog(assertNotNull(ServerCacheManager.getNpc(11282)), Int.MAX_VALUE)
        val contributions =
            linkedMapOf(
                f.first.uid to NexContribution(600, 10),
                f.second.uid to NexContribution(400, 11),
            )
        val rewarded = linkedSetOf<PlayerUid>()
        assertEquals(2, f.service.reward(contributions, rewarded, f.tile, f.npc))
        assertEquals(11278, f.npc.id)
        assertEquals(11282, f.npc.visType.id)
        assertEquals(expectedRolls + expectedRolls, f.random.calls)
        assertEquals(listOf(f.first, f.second), f.kills.map { it.hero })
        assertTrue(f.kills.all { it.npc === f.npc && it.dropCoords == f.tile })
        assertTrue(f.kills.all { it.lootTrackerEventId == 1 })
        assertTrue(f.kills.all { it.dropTableHandled && !it.killcountHandled })
        verifyNoInteractions(f.tables)
        assertEquals<List<Any>>(
            listOf(f.first, f.second).map {
                MessageGame(
                    ChatType.GameMessage.id,
                    "<col=ff0000>${it.displayName} received a unique drop from Nex!</col>",
                )
            },
            f.messages.getValue(spectator),
        )
        assertTrue(f.logs.none { it.player === spectator })
        assertTrue(f.objects.findAll(f.tile).none { it.ownerId == spectator.observerUUID })

        for ((player, common, mvp) in
            listOf(Triple(f.first, 55, true), Triple(f.second, 33, false))) {
            val expected = buildList {
                add(565 to common)
                add(565 to common)
                if (mvp) add(532 to 1)
                add(26370 to 1)
                add(26390 to 1)
                add(26231 to 80)
                add(26231 to 85)
                add(1289 to 1)
                add(12073 to 1)
            }
            val itemLogs =
                f.logs.filter {
                    it.player === player && it.source.kind == CollectionLogAwardKind.NpcDrop
                }
            assertEquals(expected, itemLogs.map { it.id to it.count })
            assertTrue(
                itemLogs.all {
                    it.source ==
                        CollectionLogAwardSource(
                            CollectionLogAwardKind.NpcDrop,
                            f.npc.type.internalName,
                        )
                }
            )
            assertEquals(expected.indices.map { "${player.uid}:1:$it" }, itemLogs.map { it.event })
            assertEquals(expected.size, itemLogs.map { it.event }.distinct().size)
            val packets = f.messages.getValue(player).filterIsInstance<RunClientScript>()
            assertEquals(
                expected.map { (id, amount) ->
                    RunClientScript(7192, listOf(11278, 1, id, amount))
                },
                packets,
            )
            val ground =
                f.objects.findAll(f.tile).filter { it.ownerId == player.observerUUID }.toList()
            assertTrue(ground.all { it.receiverId == player.observerUUID && it.coords == f.tile })
            assertEquals(
                expected.groupBy { it.first }.mapValues { (_, items) -> items.sumOf { it.second } },
                ground.groupBy { it.type }.mapValues { (_, items) -> items.sumOf { it.count } },
            )
            assertEquals(1, player.vars["varp.total_nex_kills"])
            assertEquals(
                1,
                f.logs.count {
                    it.player === player && it.source.kind == CollectionLogAwardKind.PetGrant
                },
            )
            assertEquals(expected.size, f.broadcasts.count { it[0] === player })
        }
        val beforeLogs = f.logs.toList()
        val beforeGround = f.objects.findAll(f.tile).map { it to it.count }.toList()
        val beforeMessages = f.messages.mapValues { it.value.toList() }
        assertEquals(0, f.service.reward(contributions, rewarded, f.tile, f.npc))
        assertEquals(beforeLogs, f.logs)
        assertEquals(beforeGround, f.objects.findAll(f.tile).map { it to it.count }.toList())
        assertEquals(beforeMessages, f.messages)
        assertEquals(2, f.kills.size)
        assertEquals(expectedRolls + expectedRolls, f.random.calls)

        // A fresh completion gets a new native event, while the already unlocked pet is not logged
        // twice.
        f.messages.values.forEach { it.clear() }
        assertEquals(
            1,
            f.service.reward(
                mapOf(f.first.uid to NexContribution(1, 20)),
                linkedSetOf(),
                f.tile,
                f.npc,
            ),
        )
        val soloItems =
            listOf(
                565 to 92,
                565 to 92,
                532 to 1,
                26370 to 1,
                26390 to 1,
                26231 to 80,
                26231 to 85,
                1289 to 1,
                12073 to 1,
            )
        assertEquals(
            soloItems.map { (id, amount) -> RunClientScript(7192, listOf(11278, 2, id, amount)) },
            f.messages.getValue(f.first).filterIsInstance<RunClientScript>(),
        )
        assertEquals(2, f.first.vars["varp.total_nex_kills"])
        assertEquals(
            1,
            f.logs.count {
                it.player === f.first && it.source.kind == CollectionLogAwardKind.PetGrant
            },
        )
        assertEquals(3 * expectedRolls.size, f.random.calls.size)
        assertEquals(listOf(1, 1, 2), f.kills.map { it.lootTrackerEventId })
    }

    @Test
    fun `native reveal and expiry honor admitted Nex override and ordinary null fallback`() {
        for (duration in listOf(NEX_DROP_DURATION, null)) {
            val f = Fixture()
            f.first.lootDropDuration = duration
            assertEquals(
                1,
                f.service.reward(
                    mapOf(f.first.uid to NexContribution(1)),
                    linkedSetOf(),
                    f.tile,
                    f.npc,
                ),
            )
            val ground = f.objects.findAll(f.tile).toList()
            assertTrue(ground.isNotEmpty())
            assertTrue(ground.none { it.isVisibleTo(f.second) })
            f.processAt(199)
            assertTrue(ground.none { it.isVisibleTo(f.second) })
            f.processAt(200)
            assertTrue(
                ground.all { it.isVisibleTo(f.second) && it.ownerId == f.first.observerUUID }
            )
            val expiry = 100 + (duration ?: constants.lootdrop_duration)
            f.processAt(expiry - 1)
            assertEquals(ground, f.objects.findAll(f.tile).toList())
            f.processAt(expiry)
            assertTrue(f.objects.findAll(f.tile).none())
            framework().clearInlineMocks()
        }
    }

    @Test
    fun `zero contribution ineligible and replaced identities cannot roll or receive loot`() {
        val f = Fixture()
        assertEquals(0, f.service.reward(emptyMap(), linkedSetOf(), f.tile, f.npc))
        assertEquals(
            0,
            f.service.reward(mapOf(f.first.uid to NexContribution(0)), linkedSetOf(), f.tile, f.npc),
        )
        val stale = f.second.uid
        val replacement = f.player(2, 99)
        f.players[2] = replacement
        assertEquals(
            0,
            f.service.reward(
                linkedMapOf(f.first.uid to NexContribution(1), stale to NexContribution(1000)),
                linkedSetOf(),
                f.tile,
                f.npc,
            ),
        )
        assertTrue(f.random.calls.isEmpty())
        assertTrue(f.logs.isEmpty())
        assertTrue(f.broadcasts.isEmpty())
        assertTrue(f.messages.values.all { it.isEmpty() })
        assertTrue(f.objects.findAll(f.tile).none())
        assertEquals(0, replacement.vars["varp.total_nex_kills"])
        assertTrue(f.kills.isEmpty())
    }

    @Test
    fun `suppressed source cannot roll deliver or publish a personal completion`() {
        val f = Fixture()
        f.npc.suppressRewards = true
        assertEquals(
            0,
            f.service.reward(mapOf(f.first.uid to NexContribution(1)), linkedSetOf(), f.tile, f.npc),
        )
        assertTrue(f.random.calls.isEmpty())
        assertTrue(f.kills.isEmpty())
        assertTrue(f.objects.findAll(f.tile).none())
        assertEquals(0, f.first.vars["varp.total_nex_kills"])
    }

    @Test
    fun `native configured KC skips handled completion and still counts ordinary kills`() {
        val f = Fixture()
        val npc = Npc(assertNotNull(ServerCacheManager.getNpc(239)), f.tile)
        val varp = assertNotNull(npc.paramOrNull(BaseParams.killcount_varp))
        val hook = KillcountNpcKillHook()
        val context = NpcDeathKillContext(f.first, npc, 1, f.tile, killcountHandled = true)
        assertEquals(0, f.first.vars[varp])
        hook.onKill(context)
        assertEquals(0, f.first.vars[varp])
        hook.onKill(context.copy(killcountHandled = false))
        assertEquals(1, f.first.vars[varp])
    }

    private data class Log(
        val player: Player,
        val id: Int,
        val count: Int,
        val source: CollectionLogAwardSource,
        val event: String,
    )

    private class MinimumRandom : GameRandom {
        val calls = mutableListOf<IntRange>()

        override fun of(maxExclusive: Int): Int = 0.also { calls += 0 until maxExclusive }

        override fun of(minInclusive: Int, maxInclusive: Int): Int =
            minInclusive.also { calls += minInclusive..maxInclusive }

        override fun randomDouble(): Double = error("Existing Nex rewards do not request doubles")
    }

    private class Fixture {
        val clock = MapClock().apply { cycle = 100 }
        val objects = ObjRepository(clock, ObjRegistry(ZoneUpdateMap()))
        val players = PlayerList()
        val messages = linkedMapOf<Player, MutableList<Any>>()
        val first = player(1, 11).also { players[1] = it }
        val second = player(2, 22).also { players[2] = it }
        val npc = Npc(sourceNex, NEX_SPAWN)
        val tile = NEX_SPAWN
        val random = MinimumRandom()
        val logs = mutableListOf<Log>()
        val broadcasts = mutableListOf<List<Any?>>()
        val kills = mutableListOf<NpcDeathKillContext>()
        val tables = mock(DropTableRegistry::class.java)
        private val log =
            mock(CollectionLogService::class.java) { call ->
                check(call.method.name == "record") {
                    "Local item batch must not duplicate native delivery"
                }
                logs +=
                    Log(
                        call.getArgument(0),
                        call.getArgument(1),
                        call.getArgument(2),
                        call.getArgument(3),
                        call.getArgument(4),
                    )
                null
            }
        private val clans =
            mock(ClanChatService::class.java) { call ->
                check(call.method.name == "publishLootBroadcast")
                broadcasts += call.arguments.toList()
                null
            }
        private val npcs = mock(NpcRepository::class.java)
        private val lifecycle =
            EntityLifecycleProcess(
                npcs,
                mock(LocRepository::class.java),
                objects,
                mock(ControllerRepository::class.java),
            )
        private val delivery =
            NpcRewardDeliveryService(
                mock(AreaChecker::class.java),
                objects,
                mock(GameRandom::class.java) {
                    error("Concrete fixed-count delivery must not roll again")
                },
                log,
                mock(MarketPrices::class.java),
                clans,
            )
        val service =
            NexRewardService(
                random,
                delivery,
                players,
                log,
                NpcDeath(
                    npcs,
                    players,
                    objects,
                    emptySet(),
                    linkedSetOf(
                        NpcDeathKillHook { kills += it },
                        NpcDropTableKillHook(
                            tables,
                            mock(AreaChecker::class.java),
                            delivery,
                            mock(Dt2AwakenedCompletionService::class.java),
                        ),
                        KillcountNpcKillHook(),
                    ),
                ),
            )

        fun player(slot: Int, identity: Long): Player {
            val output = mutableListOf<Any>()
            val player =
                Player(
                        client =
                            object : Client<Any, Any> by NoopClient {
                                override fun write(message: Any) {
                                    output += message
                                }
                            }
                    )
                    .apply {
                        slotId = slot
                        uuid = identity
                        observerUUID = identity
                        accountHash = identity
                        displayName = "Reward$identity"
                        assignUid()
                        lootDropDuration = NEX_DROP_DURATION
                    }
            messages[player] = output
            return player
        }

        fun processAt(cycle: Int) {
            clock.cycle = cycle
            lifecycle.process()
        }
    }

    @AfterEach fun releaseMocks() = framework().clearInlineMocks()

    companion object {
        // Decode the canonical overlay using the same route as the selected-NPC installer.
        // A copied definition exercises source before installation without changing cache globals.
        @OptIn(dev.openrune.toml.util.InternalAPI::class)
        private val sourceNex: NpcServerType by lazy {
            val mapper = tomlMapper { rsconfig { enableConstantProvider() } }
            val overlay =
                mapper
                    .decodeRuneScapeBlocks(Path.of(".data/raw-cache/server/npcs.toml"))
                    .filter { it.name == "npc" }
                    .map {
                        mapper.decodeRuneScape(typeOf<List<NpcServerType>>(), it.map.properties)
                            as NpcServerType
                    }
                    .single { it.id == 11278 }
            val base = assertNotNull(ServerCacheManager.getNpc(11278))
            val raw = (base.paramsRaw.orEmpty() + overlay.paramsRaw.orEmpty()).toMutableMap()
            base.copy(paramsRaw = raw).apply { paramMap = ParamMap(raw) }
        }

        private val expectedRolls =
            listOf(
                0..18,
                84..876,
                0..18,
                84..876,
                0..42_999_999,
                0..11,
                0..81,
                1..2,
                0..81,
                80..85,
                0..208,
                85..95,
                0..208,
                0..47,
                0..499,
            )

        @BeforeAll
        @JvmStatic
        fun cache() {
            if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
        }
    }
}

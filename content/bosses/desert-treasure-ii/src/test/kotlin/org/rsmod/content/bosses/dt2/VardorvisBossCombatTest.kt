package org.rsmod.content.bosses.dt2

import dev.openrune.ServerCacheManager
import dev.openrune.types.NpcMode
import kotlin.test.*
import org.junit.jupiter.api.BeforeAll
import org.mockito.Answers
import org.mockito.Mockito.*
import org.rsmod.annotations.InternalApi
import org.rsmod.api.bossbar.plugin.BossHpBarScript
import org.rsmod.api.bosses.runtime.*
import org.rsmod.api.combat.NvPCombat
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.combat.formulas.MaxHitFormulae
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.instances.RegionLocal
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.access.StandardNpcAccessContext
import org.rsmod.api.npc.events.interact.AiPlayerEvents
import org.rsmod.api.npc.hit.modifier.StandardNpcHitModifier
import org.rsmod.api.npc.hit.processor.StandardNpcHitProcessor
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.owner.assignSpawnOwner
import org.rsmod.api.player.music.MusicPlayer
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.route.RouteFactory
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.*
import org.rsmod.game.entity.npc.NoopNpcInfo
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.hit.Hit
import org.rsmod.game.hit.HitScope
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * Real manager, production AI registration, native NvPCombat and queued hits; scene IO is mocked.
 */
@OptIn(InternalApi::class)
class VardorvisBossCombatTest {
    @Test
    fun `native melee keeps five ticks dynamic max prayer and accepted-launch callbacks`() {
        val f = Fixture()
        f.run.vardorvisEnragePending = true
        VarPlayerIntMapSetter.set(f.player, "varbit.prayer_protectfrommelee", 1)
        f.attack()
        assertEquals(1, f.run.attackCounter)
        assertTrue(f.run.enraged)
        assertFalse(f.run.vardorvisEnragePending)
        assertEquals(105, f.boss.actionDelay)
        assertEquals(1, f.hits().single().first)
        assertEquals(8, f.hits().single().second.damage)
        assertEquals(listOf(33), f.rollBounds)
        repeat(4) {
            f.advance(1)
            f.attack()
        }
        assertEquals(1, f.run.attackCounter)
        f.advance(1)
        f.boss.hitpoints = 1
        f.attack()
        assertEquals(2, f.run.attackCounter)
        assertEquals(listOf(33, 44), f.rollBounds)
        assertEquals(10, f.hits().last().second.damage)
        f.advance(5)
        f.attack()
        assertEquals(3, f.run.attackCounter)
        assertEquals(1, f.run.vardorvisAxeSets.size)
        assertEquals(Dt2Rules.vardorvisAxeCount(1, false), f.run.helpers.size)
    }

    @Test
    fun `native action-delay extensions retain precedence over DSL cadence`() {
        val f = Fixture()
        f.boss.actionDelay = 110
        repeat(10) {
            f.attack()
            f.advance(1)
        }
        assertEquals(0, f.run.attackCounter)
        assertTrue(f.rollBounds.isEmpty())
        f.attack()
        assertEquals(1, f.run.attackCounter)
        assertEquals(115, f.boss.actionDelay)
        f.boss.actionDelay = 117
        f.advance(5)
        f.attack()
        assertEquals(1, f.run.attackCounter)
        f.advance(2)
        f.attack()
        assertEquals(2, f.run.attackCounter)
        assertEquals(122, f.boss.actionDelay)
    }

    @Test
    fun `miss still counts accepted melee and actual impact damage alone heals`() {
        val miss = Fixture(accurate = false)
        miss.attack()
        assertEquals(1, miss.run.attackCounter)
        assertEquals(0, miss.hits().single().second.damage)
        assertTrue(miss.rollBounds.isEmpty())
        framework().clearInlineMocks()

        val f = Fixture()
        f.boss.hitpoints = 650
        f.attack()
        val hit = f.hits().single().second
        assertTrue(hit.damage > 3)
        hit.dispatchImpact(f.player, 3)
        assertEquals(651, f.boss.hitpoints, "Only actual damage 3 heals by floor(3/2)")
        f.run.state = Dt2State.DYING
        hit.dispatchImpact(f.player, 20)
        assertEquals(651, f.boss.hitpoints)
    }

    @Test
    fun `already fired melee retains death lifetime and retires on deletion or departure`() {
        for (ending in listOf("death", "delete", "logout", "scope")) {
            try {
                val f = Fixture()
                f.attack()
                val hit = f.hits().single().second
                when (ending) {
                    "death" -> {
                        f.boss.hitpoints = 0
                        f.run.state = Dt2State.DYING
                    }
                    "delete" -> {
                        f.events.publish(NpcStateEvents.Delete(f.boss))
                        f.run.state = Dt2State.INVALIDATED
                    }
                    "logout" -> f.player.pendingLogout = true
                    else -> {
                        f.player.combatHitScope!!.close()
                        f.run.state = Dt2State.INVALIDATED
                    }
                }
                assertEquals(ending == "death", hit.isValidNow(), ending)
                f.advance(5)
                f.attack()
                assertEquals(1, f.run.attackCounter, ending)
            } finally {
                framework().clearInlineMocks()
            }
        }
    }

    private class Fixture(accurate: Boolean = true) {
        val clock = MapClock().apply { cycle = 100 }
        val events = EventBus()
        val player =
            Player().apply {
                slotId = 1
                uuid = 1L
                assignUid()
                coords = CoordGrid(1126, 3419)
                for (stat in listOf("hitpoints", "prayer", "defence")) {
                    statMap.setBaseLevel("stat.$stat", 99)
                    statMap.setCurrentLevel("stat.$stat", 99)
                }
                inv = invMap.getOrPut("inv.inv")
                worn = invMap.getOrPut("inv.worn")
                combatHitScope = HitScope()
            }
        val boss =
            Npc(requireNotNull(ServerCacheManager.getNpc(12223)), CoordGrid(1127, 3419)).apply {
                slotId = 1
                assignUid()
                infoProtocol = NoopNpcInfo
                currentMapClock = 100
                processedMapClock = 100
                baseHitpointsLvl = 700
                hitpoints = 700
                mode = NpcMode.ApPlayer2
                assignSpawnOwner(player, 100)
            }
        val players = PlayerList().apply { this[1] = player }
        val npcs = NpcList().apply { this[1] = boss }
        val rollBounds = mutableListOf<Int>()
        private val random =
            mock(GameRandom::class.java) { call ->
                if (
                    call.method.name == "of" &&
                        call.arguments.size == 1 &&
                        call.arguments[0] is IntRange
                ) {
                    // NvPCombat.rollDamage uses the inclusive-range overload. Mockito does not
                    // execute its default delegation to the bounded overload automatically.
                    val range = call.arguments[0] as IntRange
                    rollBounds += range.last - range.first + 1
                    range.last
                } else if (
                    call.method.name == "of" && call.arguments.size == 1 && call.arguments[0] is Int
                ) {
                    val bound = call.arguments[0] as Int
                    rollBounds += bound
                    bound - 1
                } else Answers.RETURNS_DEFAULTS.answer(call)
            }
        private val accuracy =
            mock(AccuracyFormulae::class.java) { call ->
                if (call.method.name.startsWith("roll")) accurate
                else Answers.RETURNS_DEFAULTS.answer(call)
            }
        private val world = mock(WorldRepository::class.java)
        private val native = NvPCombat(accuracy, mock(MaxHitFormulae::class.java), world)
        private val collision = CollisionFlagMap()
        private var nextNpcSlot = 2
        private val repository =
            mock(NpcRepository::class.java) { call ->
                if (call.method.name == "add") {
                    val npc = call.arguments[0] as Npc
                    npc.slotId = nextNpcSlot++
                    npc.assignUid()
                    npc.infoProtocol = NoopNpcInfo
                    npcs[npc.slotId] = npc
                    null
                } else Answers.RETURNS_DEFAULTS.answer(call)
            }
        private val instances =
            mock(InstanceManager::class.java) { call ->
                // CoordGrid is a value class, so the JVM name of this nullable-return API is
                // mangled.
                // This fixture uses original map coordinates; preserve each requested axe lane
                // tile.
                if (call.method.name.startsWith("localCoord")) {
                    val local = call.arguments[1] as RegionLocal
                    CoordGrid(
                        local.level,
                        local.regionZoneX,
                        local.regionZoneZ,
                        local.localX,
                        local.localZ,
                    )
                } else Answers.RETURNS_DEFAULTS.answer(call)
            }
        val run =
            Dt2EncounterManager.Run(
                player,
                mock(InstanceSession::class.java),
                1,
                Dt2Boss.VARDORVIS,
                Dt2Difficulty.NORMAL,
                boss,
                Dt2State.ACTIVE,
                Dt2SeededRng(42),
                42,
                100,
                100,
            )
        private val manager =
            Dt2EncounterManager(
                clock,
                instances,
                mock(BossHpBarScript::class.java),
                repository,
                mock(LocRepository::class.java),
                mock(AiPlayerInteractions::class.java),
                collision,
                native,
                world,
                events,
                mock(LeviathanRecordService::class.java),
                mock(MusicPlayer::class.java),
                mock(RouteFactory::class.java),
                mock(DukeEncounter::class.java),
            )
        private val registry = EncounterRegistry(random)
        private val deps =
            BossDeps(
                random,
                accuracy,
                world,
                repository,
                players,
                clock,
                WorldQueueList(),
                collision,
                registry,
                BossExtensionRegistry(),
                native,
            )

        init {
            // Bootstrap content state without invoking entry UI or adding a production test hook.
            val field = Dt2EncounterManager::class.java.getDeclaredField("runs")
            field.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val runs = field.get(manager) as MutableMap<PlayerUid, Dt2EncounterManager.Run>
            runs[player.uid] = run
            val context = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            with(Dt2Combat(manager, mock(NpcDeath::class.java), players, deps)) {
                context.startup()
            }
        }

        fun advance(ticks: Int) {
            clock.cycle += ticks
            boss.currentMapClock = clock.cycle
            boss.processedMapClock = clock.cycle
        }

        fun attack() {
            boss.launch {
                val access =
                    StandardNpcAccess(
                        boss,
                        this,
                        StandardNpcAccessContext(
                            { random },
                            { StandardNpcHitModifier(events) },
                            { StandardNpcHitProcessor(players, events, emptySet()) },
                        ),
                    )
                events.publish(access, AiPlayerEvents.Ap2(player, boss))
            }
        }

        fun hits(): List<Pair<Int, Hit>> {
            val iterator = player.queueList.iterator() ?: return emptyList()
            return try {
                buildList {
                    while (iterator.hasNext()) {
                        val queue = iterator.next()
                        (queue.args as? Hit)?.let { add(queue.remainingCycles to it) }
                    }
                }
            } finally {
                iterator.cleanUp()
            }
        }
    }

    @AfterTest
    fun releaseMocks() {
        framework().clearInlineMocks()
    }

    companion object {
        @BeforeAll
        @JvmStatic
        fun cache() {
            if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
        }
    }
}

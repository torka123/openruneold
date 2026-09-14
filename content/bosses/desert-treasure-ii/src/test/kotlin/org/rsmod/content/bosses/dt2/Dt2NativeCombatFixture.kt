package org.rsmod.content.bosses.dt2

import dev.openrune.ServerCacheManager
import dev.openrune.types.NpcMode
import org.mockito.Answers
import org.mockito.Mockito.*
import org.rsmod.annotations.InternalApi
import org.rsmod.api.bossbar.plugin.BossHpBarScript
import org.rsmod.api.bosses.runtime.*
import org.rsmod.api.combat.NvPCombat
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.combat.formulas.MaxHitFormulae
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.game.process.world.WorldQueueListProcess
import org.rsmod.api.instances.InstanceAccess
import org.rsmod.api.instances.InstanceId
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.instances.InstanceStateKey
import org.rsmod.api.instances.RegionLocal
import org.rsmod.api.instances.region.InstanceAreaResolver
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.access.StandardNpcAccessContext
import org.rsmod.api.npc.events.interact.AiPlayerEvents
import org.rsmod.api.npc.hit.modifier.StandardNpcHitModifier
import org.rsmod.api.npc.hit.processor.StandardNpcHitProcessor
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.owner.assignSpawnOwner
import org.rsmod.api.player.hit.DeferredPlayerHit
import org.rsmod.api.player.music.MusicPlayer
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
import org.rsmod.game.hit.Hit
import org.rsmod.game.hit.HitScope
import org.rsmod.game.inv.InvObj
import org.rsmod.game.proj.ProjAnim
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/** Native combat and real content manager, with scene IO isolated for focused behavior tests. */
@OptIn(InternalApi::class)
internal class Dt2NativeCombatFixture(
    accurate: Boolean = true,
    bossKind: Dt2Boss = Dt2Boss.VARDORVIS,
    difficulty: Dt2Difficulty = Dt2Difficulty.NORMAL,
) {
    val clock = MapClock().apply { cycle = 100 }
    val events = EventBus()
    val player =
        Player().apply {
            slotId = 1
            uuid = 1L
            assignUid()
            coords =
                when (bossKind) {
                    Dt2Boss.WHISPERER -> CoordGrid(2654, 6368)
                    Dt2Boss.LEVIATHAN -> CoordGrid(2075, 6377)
                    else -> CoordGrid(1126, 3419)
                }
            for (stat in listOf("hitpoints", "prayer", "defence")) {
                statMap.setBaseLevel("stat.$stat", 99)
                statMap.setCurrentLevel("stat.$stat", 99)
            }
            inv = invMap.getOrPut("inv.inv")
            worn = invMap.getOrPut("inv.worn")
            combatHitScope = HitScope()
        }
    val boss =
        Npc(
                requireNotNull(
                    ServerCacheManager.getNpc(
                        if (bossKind == Dt2Boss.WHISPERER) Dt2Assets.WHISPERER_SPAWN_ID
                        else bossKind.normalNpcId
                    )
                ),
                when (bossKind) {
                    Dt2Boss.WHISPERER -> CoordGrid(2655, 6368)
                    Dt2Boss.LEVIATHAN -> CoordGrid(2078, 6369)
                    else -> CoordGrid(1127, 3419)
                },
            )
            .apply {
                slotId = 1
                assignUid()
                infoProtocol = NoopNpcInfo
                currentMapClock = 100
                processedMapClock = 100
                baseHitpointsLvl = bossKind.hitpoints(difficulty)
                hitpoints = baseHitpointsLvl
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
    val projectiles = mutableListOf<ProjAnim>()
    private val world =
        mock(WorldRepository::class.java) { call ->
            if (call.method.name == "projAnim") {
                projectiles += call.arguments[0] as ProjAnim
                null
            } else Answers.RETURNS_DEFAULTS.answer(call)
        }
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
    val session =
        InstanceSession(
            InstanceId(1),
            mutableSetOf(),
            1L,
            bossKind.key,
            bossKind.spec(boss.type),
            (InstanceAreaResolver().resolve(bossKind.area()) as InstanceAreaResolver.Result.Ready)
                .placement,
            InstanceAccess.Private,
        )
    private val instances =
        mock(InstanceManager::class.java) { call ->
            // CoordGrid is a value class, so the JVM name of this nullable-return API is
            // mangled.
            // This fixture uses original map coordinates; preserve each requested axe lane
            // tile.
            if (call.method.name == "sessionForPlayer") {
                session.takeIf { call.arguments[0] === player }
            } else if (call.method.name.startsWith("sessionForId")) {
                session
            } else if (call.method.name.startsWith("instanceForNpc")) {
                val npc = call.arguments[0] as Npc
                session.id.takeIf { npc.isSlotAssigned && npcs[npc.slotId] === npc }
            } else if (call.method.name == "sessionsForKey") {
                if (call.arguments[0] == session.key) listOf(session)
                else emptyList<InstanceSession>()
            } else if (call.method.name.startsWith("localCoord")) {
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
                session,
                1,
                bossKind,
                difficulty,
                boss,
                if (bossKind in setOf(Dt2Boss.WHISPERER, Dt2Boss.LEVIATHAN)) Dt2State.PREPARING
                else Dt2State.ACTIVE,
                Dt2SeededRng(42),
                42,
                100,
                100,
            )
            .apply {
                if (bossKind == Dt2Boss.LEVIATHAN) {
                    attackCounter =
                        Dt2Rules.leviathanInitialAttackCounter(difficulty == Dt2Difficulty.AWAKENED)
                }
            }
    val registry = EncounterRegistry(random)
    val worldQueues = WorldQueueList()
    val queueProcess = WorldQueueListProcess(worldQueues)
    val manager =
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
            registry,
            worldQueues,
            queueProcess,
        )
    private val deps =
        BossDeps(
            random,
            accuracy,
            world,
            repository,
            players,
            clock,
            worldQueues,
            collision,
            registry,
            BossExtensionRegistry(),
            native,
        )

    init {
        // Bootstrap the same native typed state as entry without scene/UI setup.
        val field = Dt2EncounterManager::class.java.getDeclaredField("RUN_STATE")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val key = field.get(manager) as InstanceStateKey<Dt2EncounterManager.Run>
        session.state(key) { run }
        // Real entry allocates Run.generation with the manager's post-incrementing counter.
        Dt2EncounterManager::class.java.getDeclaredField("generation").apply {
            isAccessible = true
            setLong(manager, run.generation + 1)
        }
        val context = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
        with(Dt2Combat(manager, mock(NpcDeath::class.java), players, deps)) { context.startup() }
    }

    fun activateWhisperer() {
        check(run.bossKind == Dt2Boss.WHISPERER)
        // Exercise the real native activation while retaining this fixture's first combat tick.
        player.inv[0] =
            InvObj(requireNotNull(ServerCacheManager.getItem(Dt2Assets.BLACKSTONE_FRAGMENT)), 1)
        clock.cycle -= 4
        check(manager.activateWhisperer(player, boss))
        advance(4)
        manager.tick(player)
    }

    fun activateLeviathan() {
        check(run.bossKind == Dt2Boss.LEVIATHAN)
        val attackTile = player.coords
        clock.cycle -= 5
        val handholds =
            requireNotNull(instances.localCoord(session, RegionLocal(0, 32, 99, 22, 32)))
        val inside = requireNotNull(instances.localCoord(session, Dt2Assets.LEVIATHAN_INSIDE_ENTRY))
        collision.allocateIfAbsent(inside.x, inside.z, inside.level)
        check(manager.activateLeviathan(player, Dt2Assets.LEVIATHAN_START_HANDHOLDS, handholds))
        advance(1)
        manager.tick(player)
        advance(4)
        manager.tick(player)
        player.coords = attackTile
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

    fun deferredHits(): List<Pair<Int, DeferredPlayerHit>> {
        val iterator = player.queueList.iterator() ?: return emptyList()
        return try {
            buildList {
                while (iterator.hasNext()) {
                    val queue = iterator.next()
                    (queue.args as? DeferredPlayerHit)?.let { add(queue.remainingCycles to it) }
                }
            }
        } finally {
            iterator.cleanUp()
        }
    }
}

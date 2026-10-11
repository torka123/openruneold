package org.rsmod.content.bosses.yama

import com.google.inject.AbstractModule
import com.google.inject.Guice
import dev.openrune.ServerCacheManager
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.mockito.Answers
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.rsmod.annotations.InternalApi
import org.rsmod.api.bossbar.plugin.BossHpBarScript
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossExtensionRegistry
import org.rsmod.api.bosses.runtime.EncounterRegistry
import org.rsmod.api.combat.NvPCombat
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.combat.formulas.MaxHitFormulae
import org.rsmod.api.combat.weapon.types.AttackTypes
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.instances.BossInstanceRegistry
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.instances.InstanceStateKey
import org.rsmod.api.instances.region.InstanceAreaResolver
import org.rsmod.api.mechanics.toxins.BurnEffectService
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.bonus.WornBonuses
import org.rsmod.api.player.hit.processor.InstantPlayerHitProcessor
import org.rsmod.api.player.hook.PlayerPostTickHook
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.game.region.Region
import org.rsmod.game.region.zone.RegionZoneCopy
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * Native session, NPC identity, lifecycle events and Yama scripts; allocation and I/O are mocked.
 */
@OptIn(InternalApi::class)
internal class YamaNativeLifecycleFixture {
    val clock = MapClock(100)
    val collision = CollisionFlagMap()
    val events = EventBus()
    val players = PlayerList()
    val npcList = NpcList()
    val npcRegistry = NpcRegistry(npcList, collision, events)
    val npcs = NpcRepository(clock, npcRegistry, npcList)
    private var allocations = 0
    private val regions =
        mock(RegionRepository::class.java) { call ->
            when {
                call.method.name == "add" -> allocateRegion()
                call.method.name.startsWith("isValid") -> true
                else -> Answers.RETURNS_DEFAULTS.answer(call)
            }
        }
    val instances =
        InstanceManager(
            regions,
            npcs,
            players,
            events,
            InstanceAreaResolver(),
            clock,
            collision,
            npcList,
        )
    val instanceRegistry = BossInstanceRegistry()
    val burns = mock(BurnEffectService::class.java)
    private val objects = mock(ObjRepository::class.java)
    private val world = mock(WorldRepository::class.java)
    private val random = DefaultGameRandom(804L)
    private val bossEncounters = EncounterRegistry(random)
    private val combat =
        NvPCombat(mock(AccuracyFormulae::class.java), mock(MaxHitFormulae::class.java), world)
    var rewardRolls = 0
        private set

    private val rewards =
        mock(YamaRewardService::class.java) { call ->
            if (call.method.name == "roll") {
                rewardRolls++
                val input = call.getArgument<YamaRewardInput>(0)
                YamaRewardRoll(
                    YamaPrimaryReward.NONE,
                    0,
                    emptySet(),
                    false,
                    YamaRewardContribution(input.damage, input.maximumHitpoints),
                )
            } else Answers.RETURNS_DEFAULTS.answer(call)
        }
    val encounters =
        YamaEncounterManager(
            clock,
            instances,
            npcs,
            mock(LocRepository::class.java),
            objects,
            world,
            collision,
            random,
            AiPlayerInteractions(events, players),
            combat,
            mock(AttackTypes::class.java),
            players,
            rewards,
            mock(BossHpBarScript::class.java),
            burns,
            mock(InstantPlayerHitProcessor::class.java),
            mock(WornBonuses::class.java),
            bossEncounters,
        )
    val nativeLifecycle: PlayerPostTickHook

    init {
        val context = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
        val worldScript =
            YamaWorld(encounters, mock(YamaDeathStorage::class.java), instanceRegistry)
        Guice.createInjector(
                object : AbstractModule() {
                    override fun configure() {
                        bind(InstanceManager::class.java).toInstance(instances)
                        bind(MapClock::class.java).toInstance(clock)
                    }
                }
            )
            .injectMembers(worldScript)
        with(worldScript) { context.startup() }
        val lifecycle =
            Class.forName("org.rsmod.api.instances.hook.InstanceLifecycleScript")
                .getConstructor(InstanceManager::class.java, MapClock::class.java)
                .newInstance(instances, clock) as PluginScript
        with(lifecycle) { context.startup() }
        nativeLifecycle = lifecycle as PlayerPostTickHook
        val deps =
            BossDeps(
                random,
                mock(AccuracyFormulae::class.java),
                world,
                npcs,
                players,
                clock,
                WorldQueueList(),
                collision,
                bossEncounters,
                BossExtensionRegistry(),
                combat,
            )
        val death = NpcDeath(npcs, players, objects, emptySet(), emptySet())
        with(YamaCombat(encounters, death, players, npcList, deps)) { context.startup() }
    }

    private fun allocateRegion(): Region {
        val base = CoordGrid(6400 + allocations * 64, 6400)
        val region = Region(base, base.translate(64, 64), ++allocations, allocations)
        for (level in 0..3) for (x in 0..7) for (z in 0..7) {
            val destination = ZoneKey.fromAbsolute(base.x + x * 8, base.z + z * 8, level)
            region.registerZone(
                RegionZoneCopy(ZoneKey(23 * 8 + x, 157 * 8 + z, level), 0, null),
                destination,
            )
            collision.allocateIfAbsent(base.x + x * 8, base.z + z * 8, level)
        }
        return region
    }

    fun player(id: Int = 1): Player =
        Player().apply {
            uuid = id.toLong()
            accountHash = id.toLong()
            accountId = id
            characterId = id
            observerUUID = id.toLong()
            username = "yamamember$id"
            displayName = "Yama Member $id"
            slotId = id
            assignUid()
            coords = YAMA_OUTSIDE
            currentMapClock = clock.cycle
            processedMapClock = clock.cycle
            for (stat in
                listOf("hitpoints", "attack", "strength", "defence", "ranged", "magic", "prayer")) {
                statMap.setBaseLevel("stat.$stat", 99)
                statMap.setCurrentLevel("stat.$stat", 99)
            }
            inv = invMap.getOrPut("inv.inv")
            worn = invMap.getOrPut("inv.worn")
            players[id] = this
        }

    fun enter(player: Player = player()): YamaEncounterManager.Run {
        assertTrue(encounters.travel(player))
        return run(assertNotNull(instances.sessionForPlayer(player)))
    }

    fun duo(): Triple<Player, Player, YamaEncounterManager.Run> {
        val owner = player()
        val partner = player(2)
        assertTrue(encounters.invite(owner, partner.displayName))
        val run = enter(owner)
        assertTrue(encounters.joinInvitation(partner))
        assertEquals(run.session.id, instances.sessionForPlayer(partner)?.id)
        return Triple(owner, partner, run)
    }

    @Suppress("UNCHECKED_CAST")
    fun run(session: InstanceSession): YamaEncounterManager.Run {
        val field = YamaEncounterManager::class.java.getDeclaredField("RUN_STATE")
        field.isAccessible = true
        val key = field.get(encounters) as InstanceStateKey<YamaEncounterManager.Run>
        return assertNotNull(session.stateOrNull(key))
    }

    fun startFight(owner: Player, run: YamaEncounterManager.Run): Npc {
        assertTrue(encounters.challenge(owner))
        clock.cycle = run.stateCycle
        encounters.tickAll()
        val boss = assertNotNull(run.boss)
        clock.cycle = run.stateCycle
        encounters.tickAll()
        assertEquals(YamaState.PHASE_ONE, run.state)
        return boss
    }

    fun attachHelper(run: YamaEncounterManager.Run): Npc =
        Npc(assertNotNull(ServerCacheManager.getNpc(YamaAssets.METEOR)), run.throne.coords).also {
            npcs.add(it, 1000)
            instances.attachNpc(run.session.id, it)
        }

    fun burnCleanupCount(player: Player): Int =
        mockingDetails(burns).invocations.count {
            it.method.name == "consumeRemainingDamage" && it.arguments.firstOrNull() === player
        }
}

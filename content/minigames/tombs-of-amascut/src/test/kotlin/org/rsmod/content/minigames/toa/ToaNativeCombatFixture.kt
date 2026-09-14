package org.rsmod.content.minigames.toa

import com.google.inject.AbstractModule
import com.google.inject.Guice
import dev.openrune.types.ObjectServerType
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.mockito.Answers
import org.mockito.Mockito.mock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.bossbar.plugin.BossHpBarScript
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossExtensionRegistry
import org.rsmod.api.bosses.runtime.EncounterRegistry
import org.rsmod.api.combat.NvPCombat
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.combat.formulas.MaxHitFormulae
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.game.process.world.WorldQueueListProcess
import org.rsmod.api.instances.BossInstanceRegistry
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.region.InstanceAreaResolver
import org.rsmod.api.mechanics.toxins.NpcPoisonEffectService
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.access.StandardNpcAccessContext
import org.rsmod.api.npc.access.StandardNpcAccessContextFactory
import org.rsmod.api.npc.access.StandardNpcAccessLauncher
import org.rsmod.api.npc.events.interact.AiPlayerEvents
import org.rsmod.api.npc.hit.modifier.StandardNpcHitModifier
import org.rsmod.api.npc.hit.processor.StandardNpcHitProcessor
import org.rsmod.api.npc.hit.queueHit
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.bonus.WornBonuses
import org.rsmod.api.player.hit.DeferredPlayerHit
import org.rsmod.api.player.hit.processor.StandardPlayerHitProcessor
import org.rsmod.api.player.hook.PlayerPostTickHook
import org.rsmod.api.player.hook.PlayerTeleportHooks
import org.rsmod.api.player.hook.PlayerTeleportValidateHook
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.protect.ProtectedAccessContext
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.random.GameRandom
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.route.RouteFactory
import org.rsmod.api.specials.energy.SpecialAttackEnergy
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.hit.Hit
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.game.region.Region
import org.rsmod.game.region.zone.RegionZoneCopy
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.loc.LocLayerConstants

/** Real raid admission, native actor ownership, registered combat and protected hit processing. */
@OptIn(InternalApi::class)
internal class ToaNativeCombatFixture(nativeLifecycleFirst: Boolean = true) : AutoCloseable {
    val clock = MapClock(100)
    val collision = CollisionFlagMap()
    val events = EventBus()
    val players = PlayerList()
    val npcList = NpcList()
    val npcRegistry = NpcRegistry(npcList, collision, events)
    val npcs = NpcRepository(clock, npcRegistry, npcList)
    var allocations = 0
        private set

    var allocationRefused = false
    var teleportRefused = false
    var refuseRegionValidationAt: Int? = null
    var regionValidationCount = 0
        private set

    private val regions =
        mock(RegionRepository::class.java) { call ->
            when {
                call.method.name == "add" -> if (allocationRefused) null else allocateRegion()
                call.method.name.startsWith("isValid") ->
                    ++regionValidationCount != refuseRegionValidationAt
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
    val locs =
        mock(LocRepository::class.java) { call ->
            when {
                call.method.name.startsWith("findAll") -> emptySequence<LocInfo>()
                call.method.name.startsWith("add") &&
                    call.arguments.getOrNull(1) is ObjectServerType -> {
                    val coords = CoordGrid(call.arguments[0] as Int)
                    val type = call.arguments[1] as ObjectServerType
                    val angle = call.arguments[3] as LocAngle
                    val shape = call.arguments[4] as LocShape
                    LocInfo(
                        LocLayerConstants.of(shape.id),
                        coords,
                        LocEntity(type.id, shape.id, angle.id),
                    )
                }
                else -> Answers.RETURNS_DEFAULTS.answer(call)
            }
        }
    val world = mock(WorldRepository::class.java)
    val supplies = mock(ToaSupplies::class.java)
    val ui = mock(ToaUi::class.java)
    val hpBar = mock(BossHpBarScript::class.java)
    val rewards =
        mock(ToaRewardService::class.java) { call ->
            if (call.method.name == "snapshotTertiaryProfile") ToaTertiaryProfile()
            else Answers.RETURNS_DEFAULTS.answer(call)
        }
    val accessRandom = RecordingRandom()
    val selectionRandom = RecordingRandom()
    val impactRandom = RecordingRandom()
    var accurate = true
    val accuracyTargets = mutableListOf<Player>()
    private val accuracy =
        mock(AccuracyFormulae::class.java) { call ->
            if (call.method.name.startsWith("roll")) {
                (call.arguments.getOrNull(1) as? Player)?.let(accuracyTargets::add)
                accurate
            } else Answers.RETURNS_DEFAULTS.answer(call)
        }
    private val nativeCombat = NvPCombat(accuracy, mock(MaxHitFormulae::class.java), world)
    val bossEncounters = EncounterRegistry(selectionRandom)
    val worldQueues = WorldQueueList()
    val queueProcess = WorldQueueListProcess(worldQueues)
    val deps =
        BossDeps(
            selectionRandom,
            accuracy,
            world,
            npcs,
            players,
            clock,
            worldQueues,
            collision,
            bossEncounters,
            BossExtensionRegistry(),
            nativeCombat,
        )
    val nativeAccess =
        StandardNpcAccessLauncher(
            StandardNpcAccessContextFactory(
                accessRandom,
                StandardNpcHitModifier(events),
                StandardNpcHitProcessor(players, events, emptySet()),
            )
        )
    val objects = mock(ObjRepository::class.java)
    val crondisZebak =
        ToaCrondisZebakRuntime(
            clock,
            players,
            instances,
            locs,
            objects,
            npcs,
            world,
            mock(RouteFactory::class.java),
            collision,
            accuracy,
            accessRandom,
            supplies,
            deps,
            nativeAccess,
            events,
            queueProcess,
        )
    private val protectedContext =
        mock(ProtectedAccessContext::class.java) { call ->
            when (call.method.name) {
                "getRandom" -> impactRandom
                "getEventBus" -> events
                "getNpcList" -> npcList
                "getPlayerList" -> players
                "getCollision" -> collision
                "getAreaChecker" -> mock(call.method.returnType)
                "getTeleportHooks" -> PlayerTeleportHooks.EMPTY
                "getTeleportValidator" ->
                    PlayerTeleportValidator(
                        setOf(
                            PlayerTeleportValidateHook { _, _, _ ->
                                if (teleportRefused) "Test teleport refusal." else null
                            }
                        )
                    )
                else -> Answers.RETURNS_DEFAULTS.answer(call)
            }
        }
    val protectedAccess =
        ProtectedAccessLauncher(
            mock(ProtectedAccessContextFactory::class.java) { call ->
                if (call.method.name == "create") protectedContext
                else Answers.RETURNS_DEFAULTS.answer(call)
            }
        )
    val manager =
        ToaRaidManager(
            clock,
            players,
            mock(WornBonuses::class.java),
            instances,
            locs,
            npcs,
            AiPlayerInteractions(events, players),
            world,
            mock(RouteFactory::class.java),
            collision,
            rewards,
            supplies,
            crondisZebak,
            mock(ToaScabarasIntegration::class.java),
            mock(ToaHetOpenRuneBridge::class.java),
            mock(ToaHetPickaxeStorage::class.java),
            ui,
            mock(ToaPartyBoard::class.java),
            mock(SpecialAttackEnergy::class.java),
            hpBar,
            mock(NpcPoisonEffectService::class.java),
            protectedAccess,
            deps,
            nativeAccess,
            events,
            queueProcess,
        )
    val death =
        NpcDeath(
            npcs,
            players,
            mock(ObjRepository::class.java),
            emptySet(),
            setOf(ToaNpcKillHook(manager)),
        )
    val instanceRegistry = BossInstanceRegistry()
    val nativeLifecycle: PlayerPostTickHook

    init {
        val context = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
        val lifecycle =
            Class.forName("org.rsmod.api.instances.hook.InstanceLifecycleScript")
                .getConstructor(InstanceManager::class.java, MapClock::class.java)
                .newInstance(instances, clock) as PluginScript
        if (nativeLifecycleFirst) with(lifecycle) { context.startup() }
        nativeLifecycle = lifecycle as PlayerPostTickHook
        val instanceScript = ToaInstanceScript(instanceRegistry, manager)
        Guice.createInjector(
                object : AbstractModule() {
                    override fun configure() {
                        bind(InstanceManager::class.java).toInstance(instances)
                        bind(MapClock::class.java).toInstance(clock)
                    }
                }
            )
            .injectMembers(instanceScript)
        with(instanceScript) { context.startup() }
        with(ToaLifecycle(manager)) { context.startup() }
        if (!nativeLifecycleFirst) with(lifecycle) { context.startup() }
        with(ToaCombat(nativeCombat, manager, death, players, deps, crondisZebak)) {
            context.startup()
        }
    }

    private fun allocateRegion(): Region {
        val base = CoordGrid(6400 + allocations * 512, 6400)
        val region = Region(base, base.translate(512, 512), ++allocations, allocations)
        for (room in TOA_ACTIVE_ROOMS) {
            val segment = room.segment
            val sourceX = (segment.sourceRegion ushr 8) * 8
            val sourceZ = (segment.sourceRegion and 255) * 8
            for (level in 0..segment.sourceMaxLevel) for (x in 0..7) for (z in 0..7) {
                val dx = base.x + (segment.destinationZoneX + x) * 8
                val dz = base.z + (segment.destinationZoneZ + z) * 8
                val destination = ZoneKey.fromAbsolute(dx, dz, level)
                region.registerZone(
                    RegionZoneCopy(ZoneKey(sourceX + x, sourceZ + z, level), 0, null),
                    destination,
                )
                collision.allocateIfAbsent(dx, dz, level)
            }
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
            username = "toamember$id"
            displayName = "ToA Member $id"
            slotId = id
            assignUid()
            coords = TOA_ENTRANCE
            collision.allocateIfAbsent(coords.x, coords.z, coords.level)
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

    fun start(player: Player = player(), partySize: Int = 1, seed: Long = 42): ToaRaidSession {
        assertTrue(manager.devStart(player, partySize, seed))
        return assertNotNull(manager.raidFor(player))
    }

    fun joinParty(leader: Player, member: Player) {
        assertTrue(manager.openPartyView(member, 0))
        assertTrue(manager.applyToViewedParty(member))
        assertTrue(manager.acceptApplicant(leader, 0))
    }

    fun enterBaba(player: Player, activate: Boolean = true): ToaRaidSession {
        assertTrue(manager.devEnterRoom(player, "baba"))
        val raid = assertNotNull(manager.raidFor(player))
        if (activate) dispatchLateCycle(babaQueues(raid).completionAt)
        return raid
    }

    fun babaQueues(raid: ToaRaidSession): BabaCombatQueues =
        assertNotNull(bossEncounters.of(boss(raid)).stateOrNull(BABA_COMBAT_QUEUES))

    fun baba(raid: ToaRaidSession): Npc = boss(raid)

    fun boss(raid: ToaRaidSession): Npc =
        raid.npcRoles.entries.single { it.value == ToaNpcRole.BOSS }.key

    fun enterAkkha(player: Player): ToaRaidSession = enterRoom(player, "akkha")

    fun enterKephri(player: Player): ToaRaidSession = enterRoom(player, "kephri")

    fun enterZebak(player: Player): ToaRaidSession = enterRoom(player, "zebak")

    private fun enterRoom(player: Player, room: String): ToaRaidSession {
        assertTrue(manager.devEnterRoom(player, room))
        return assertNotNull(manager.raidFor(player))
    }

    fun advance(cycles: Int) {
        require(cycles >= 0)
        clock.cycle += cycles
        for (npc in npcList) {
            npc.currentMapClock = clock.cycle
            npc.processedMapClock = clock.cycle
        }
        for (player in players) {
            player.currentMapClock = clock.cycle
            player.processedMapClock = clock.cycle
        }
    }

    fun dispatchLateCycle(cycle: Int = clock.cycle) {
        advance(cycle - clock.cycle)
        events.publish(GameLifecycle.LateCycle)
    }

    fun attack(npc: Npc, target: Player, op: Boolean = false) {
        npc.currentMapClock = clock.cycle
        npc.processedMapClock = clock.cycle
        npc.launch {
            val access =
                StandardNpcAccess(
                    npc,
                    this,
                    StandardNpcAccessContext(
                        { accessRandom },
                        { StandardNpcHitModifier(events) },
                        { StandardNpcHitProcessor(players, events, emptySet()) },
                    ),
                )
            if (op) events.publish(access, AiPlayerEvents.Op2(target, npc))
            else events.publish(access, AiPlayerEvents.Ap2(target, npc))
        }
    }

    fun hits(player: Player): List<Pair<Int, Hit>> {
        val iterator = player.queueList.iterator() ?: return emptyList()
        return try {
            buildList {
                while (iterator.hasNext()) {
                    val entry = iterator.next()
                    (entry.args as? Hit)?.let { add(entry.remainingCycles to it) }
                }
            }
        } finally {
            iterator.cleanUp()
        }
    }

    fun processHit(player: Player, hit: Hit) {
        protectedAccess.launchLenient(player) { with(StandardPlayerHitProcessor) { process(hit) } }
    }

    fun deferredHits(player: Player): List<Pair<Int, DeferredPlayerHit>> {
        val iterator = player.queueList.iterator() ?: return emptyList()
        return try {
            buildList {
                while (iterator.hasNext()) {
                    val entry = iterator.next()
                    (entry.args as? DeferredPlayerHit)?.let { add(entry.remainingCycles to it) }
                }
            }
        } finally {
            iterator.cleanUp()
        }
    }

    /** Native launch modification now, then attribution and impact processing one tick later. */
    fun hitNpc(player: Player, npc: Npc, damage: Int, type: HitType = HitType.Melee): Hit {
        val hit = npc.queueHit(player, 1, type, damage, StandardNpcHitModifier(events))
        advance(1)
        nativeAccess.launch(npc) {
            with(StandardNpcHitProcessor(players, events, emptySet())) { process(hit) }
        }
        return hit
    }

    /**
     * Native deletion followed by dispatch through the registered kill hook, without world drops.
     */
    fun nativeDeath(npc: Npc, hero: Player): NpcDeathKillContext {
        val context = NpcDeathKillContext(hero, npc, death.nextLootTrackerEventId(), npc.coords)
        npc.hitpoints = 0
        npcs.del(npc, Int.MAX_VALUE)
        assertTrue(death.dispatchDeferredKill(context))
        return context
    }

    /** The actual suspended death sequence, retaining kill hooks while suppressing remains. */
    fun beginDeathWithDrops(npc: Npc, hero: Player) {
        npc.heroPoints(hero, 10)
        npc.hitpoints = 0
        npc.suppressDrops = true
        npc.launch {
            death.deathWithDrops(
                StandardNpcAccess(
                    npc,
                    this,
                    StandardNpcAccessContext(
                        { accessRandom },
                        { StandardNpcHitModifier(events) },
                        { StandardNpcHitProcessor(players, events, emptySet()) },
                    ),
                )
            )
        }
    }

    fun advanceDeath(npc: Npc) {
        advance(1)
        if (npc.isSlotAssigned && npc.activeCoroutine != null) npc.advanceActiveCoroutine()
    }

    override fun close() {
        instances.sessionsForKey(TOA_INSTANCE_KEY).toList().forEach { instances.terminate(it) }
    }

    class RecordingRandom : GameRandom {
        val bounds = mutableListOf<Int>()

        override fun of(maxExclusive: Int): Int = (maxExclusive - 1).also { bounds += maxExclusive }

        override fun of(minInclusive: Int, maxInclusive: Int): Int =
            minInclusive + of(maxInclusive - minInclusive + 1)

        override fun randomDouble(): Double = 0.99
    }
}

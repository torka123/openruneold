package org.rsmod.content.bosses.cerberus

import com.google.inject.Guice
import dev.openrune.types.NpcMode
import kotlin.test.*
import net.rsprot.protocol.game.outgoing.zone.payload.MapProjAnimV2
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.*
import org.rsmod.annotations.InternalApi
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.combat.formulas.attributes.collector.DamageReductionAttributeCollector
import org.rsmod.api.combat.weapon.scripts.WeaponAttackStylesScript
import org.rsmod.api.combat.weapon.styles.AttackStyles
import org.rsmod.api.death.PlayerDeathContext
import org.rsmod.api.game.process.npc.NpcFaceSquareProcessor
import org.rsmod.api.game.process.npc.NpcMovementProcessor
import org.rsmod.api.game.process.world.WorldQueueListProcess
import org.rsmod.api.instances.BossInstanceRegistry
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.instances.RegionLocal
import org.rsmod.api.instances.region.InstanceAreaResolver
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.access.StandardNpcAccessContext
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.bonus.WornBonuses
import org.rsmod.api.player.events.PlayerMovementEvent
import org.rsmod.api.player.hook.PlayerPostTickHook
import org.rsmod.api.player.hook.PlayerTeleportHooks
import org.rsmod.api.player.hook.PlayerTeleportValidateHook
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.protect.ProtectedAccessContext
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.random.GameRandom
import org.rsmod.api.registry.controller.ControllerRegistry
import org.rsmod.api.registry.loc.LocRegistryNormal
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.registry.zone.ZonePlayerActivityBitSet
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.NpcRevealProcessor
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.route.RouteFactory
import org.rsmod.api.route.StepFactory
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.ControllerList
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.entity.util.EntityFaceTarget
import org.rsmod.game.hit.Hit
import org.rsmod.game.hit.HitType
import org.rsmod.game.map.LocZoneStorage
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.game.region.RegionListLarge
import org.rsmod.game.region.RegionListSmall
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * Real allocated regions, cache definitions, NPC repository, combat formulas and zone packets. The
 * flat source collision fixture isolates instance allocation from cache terrain evidence.
 */
class CerberusEncounterIntegrationTest {
    @org.junit.jupiter.api.AfterEach fun releaseFixtureMocks() = framework().clearInlineMocks()

    @Test
    fun `chamber entry wakes sitting boss in four independently allocated clones`() {
        val world = CerberusTestWorld()
        repeat(4) { index ->
            val player = world.enter(index + 1)
            val boss = world.boss(player)
            assertEquals(CerberusAssets.SITTING_NPC_ID, boss.visType.id)
            player.coords = world.tile(player, 24, 30)
            world.tick(CerberusRules.STAND_TRANSITION_TICKS + 1)
            assertEquals(
                CerberusAssets.ACTIVE_NPC_ID,
                boss.visType.id,
                "Clone $index: ${world.status(player)} at ${player.coords}",
            )
            for (x in 0..63) for (z in 0..63) {
                assertEquals(
                    CoordGrid(CERBERUS_SOURCE_MAP_X * 64 + x, CERBERUS_SOURCE_MAP_Z * 64 + z, 0),
                    world.arena.sourceCoords(world.session(player), world.tile(player, x, z)),
                )
            }
        }
    }

    @Test
    fun `opening triple emits magic then ranged packets and queues melee from a real instance`() {
        val world = CerberusTestWorld()
        val player = world.enter()
        world.wake(player)
        world.updates.clear()
        VarPlayerIntMapSetter.set(player, "varbit.prayer_protectfrommagic", 1)
        world.attack(player)
        assertTrue(world.status(player).contains("counter=2 "), world.status(player))
        assertEquals(CerberusAssets.MAGIC_PROJECTILE_SPOTANIM, world.projectiles().single().id)
        val magic = world.hits(player).single()
        assertEquals(HitType.Magic, magic.type)
        assertEquals(0, magic.damage)
        VarPlayerIntMapSetter.set(player, "varbit.prayer_protectfrommagic", 0)
        VarPlayerIntMapSetter.set(player, "varbit.prayer_protectfrommissiles", 1)
        world.tick(1)
        assertEquals(1, world.projectileCount())
        world.tick(1)
        assertEquals(
            setOf(
                CerberusAssets.MAGIC_PROJECTILE_SPOTANIM,
                CerberusAssets.RANGED_PROJECTILE_SPOTANIM,
            ),
            world.projectiles().map { it.id }.toSet(),
        )
        VarPlayerIntMapSetter.set(player, "varbit.prayer_protectfrommissiles", 0)
        VarPlayerIntMapSetter.set(player, "varbit.prayer_protectfrommelee", 1)
        world.tick(CerberusRules.TRIPLE_COMPONENT_GAP_TICKS)
        assertEquals(2, world.projectileCount())
        VarPlayerIntMapSetter.set(player, "varbit.prayer_protectfrommelee", 0)
        assertEquals(
            listOf(HitType.Magic, HitType.Ranged, HitType.Melee),
            world.hits(player).map { it.type },
        )
        assertTrue(world.hits(player).all { it.damage == 0 && it.isValidNow() })
        assertEquals(0, magic.damage, "Switching after launch must not change the queued hit")
        assertTrue(world.projectiles().all { it.targetIndex == -player.slotId - 1 })
    }

    @Test
    fun `native instance owns persistent boss respawn after repository registration`() {
        val world = CerberusTestWorld()
        val player = world.enter()
        assertTrue(
            world.boss(player).respawns,
            "Native NpcDeath must retain the configured actor for its next reveal",
        )
    }

    @Test
    fun `death item hook resolves an allocated chamber to the recoverable public exit`() {
        val world = CerberusTestWorld()
        val player = world.enter()
        player.coords = world.tile(player, 24, 30)
        val context =
            PlayerDeathContext(
                player,
                player.coords,
                false,
                0,
                false,
                true,
                false,
                false,
                false,
                0,
                null,
            )
        assertEquals(
            CERBERUS_PUBLIC_EXIT,
            CerberusDeathDropLocation(world.instances, world.arena).dropLocation(context),
        )
    }

    @Test
    fun `real encounter advances through attack 35 and emits all three special mechanics`() {
        val world = CerberusTestWorld()
        val player = world.enter()
        world.wake(player)
        world.boss(player).hitpoints = 150
        var sawSouls = false
        var sawLava = false
        for (number in 1..35) {
            world.attack(player)
            assertTrue(
                world.status(player).contains("counter=${number + 1} "),
                world.status(player),
            )
            sawSouls = sawSouls || world.souls(player).isNotEmpty()
            world.tick((world.boss(player).actionDelay - world.clock.cycle).coerceAtLeast(1))
            sawLava = sawLava || !world.status(player).contains("lava=0 ")
        }
        assertTrue(sawSouls, "A seeded low-health fight must actually spawn Souls")
        assertTrue(sawLava, "A seeded low-health fight must actually activate lava")
        assertTrue(world.projectiles().any { it.id == CerberusAssets.MAGIC_PROJECTILE_SPOTANIM })
        assertTrue(world.projectiles().any { it.id == CerberusAssets.RANGED_PROJECTILE_SPOTANIM })
    }

    @Test
    fun `Soul wave emits one attack per ghost in west to east order then despawns`() {
        val world = CerberusTestWorld()
        val player = world.enter()
        world.wake(player)
        world.force(player, "souls")
        val ghosts = world.souls(player).sortedBy { it.coords.x }
        assertEquals(3, ghosts.size)
        assertEquals(3, ghosts.map { it.id }.toSet().size)
        assertTrue(ghosts.none { it.respawns })
        assertTrue(
            ghosts.all {
                it.mode == NpcMode.None && it.ignoreCombatInteractions && it.huntRange == 0
            }
        )
        assertTrue(ghosts.all { world.manager.attackDenial(player, it) != null })
        world.updates.clear()
        world.tick(
            CerberusRules.SOUL_APPROACH_DELAY_TICKS +
                CerberusRules.SOUL_WALK_DISTANCE +
                CerberusRules.SOUL_FIRST_ATTACK_DELAY_TICKS - 1
        )
        assertEquals(0, world.projectileCount())
        ghosts.forEachIndexed { index, ghost ->
            world.tick(if (index == 0) 1 else CerberusRules.SOUL_COMPONENT_GAP_TICKS)
            val expected =
                when (ghost.id) {
                    CerberusAssets.MAGIC_SOUL_NPC_ID ->
                        CerberusAssets.MAGIC_SOUL_PROJECTILE_SPOTANIM
                    CerberusAssets.RANGED_SOUL_NPC_ID ->
                        CerberusAssets.RANGED_SOUL_PROJECTILE_SPOTANIM
                    else -> CerberusAssets.MELEE_SOUL_PROJECTILE_SPOTANIM
                }
            assertEquals(expected, world.projectiles().single().id)
            assertEquals(index + 1, world.hits(player).size)
            assertEquals(30, world.hits(player).last().damage)
            world.updates.clear()
        }
        world.tick(CerberusRules.SOUL_RETREAT_DELAY_TICKS + CerberusRules.SOUL_WALK_DISTANCE + 1)
        assertTrue(ghosts.none { it.isSlotAssigned })
        assertEquals(0, world.projectileCount())
    }

    @Test
    fun `attacking Souls keep their facing independent of the moving player`() {
        val world = CerberusTestWorld()
        val player = world.enter()
        world.wake(player)
        world.force(player, "souls")
        val ghosts = world.souls(player).sortedBy { it.coords.x }
        world.tick(
            CerberusRules.SOUL_APPROACH_DELAY_TICKS +
                CerberusRules.SOUL_WALK_DISTANCE +
                CerberusRules.SOUL_FIRST_ATTACK_DELAY_TICKS
        )
        ghosts.forEachIndexed { index, ghost ->
            if (index > 0) world.tick(CerberusRules.SOUL_COMPONENT_GAP_TICKS)
            assertEquals(
                EntityFaceTarget.NULL,
                ghost.faceEntity,
                "Soul ${ghost.id} must not retain a client-facing player target after launch",
            )
            world.processSoulMovement(ghost)
            val position = ghost.coords
            val facing = ghost.pendingFaceAngle
            player.coords = world.tile(player, if (index % 2 == 0) 20 else 28, 30)
            // Exercise player-driven position/facing updates before the scheduled retreat.
            world.processSoulMovement(ghost)
            assertEquals(position, ghost.coords)
            assertEquals(facing, ghost.pendingFaceAngle)
            assertEquals(EntityFaceTarget.NULL, ghost.faceEntity)
        }
        assertEquals(3, world.hits(player).size)
        assertTrue(
            world.projectiles().all { it.targetIndex == -player.slotId - 1 },
            "Soul projectiles must still target the player even though the NPC does not track them",
        )
    }

    @Test
    fun `attacking Souls discard an unscheduled movement route while holding their tile`() {
        val world = CerberusTestWorld()
        val player = world.enter()
        world.wake(player)
        world.force(player, "souls")
        val soul = world.souls(player).minBy { it.coords.x }
        world.tick(
            CerberusRules.SOUL_APPROACH_DELAY_TICKS +
                CerberusRules.SOUL_WALK_DISTANCE +
                CerberusRules.SOUL_FIRST_ATTACK_DELAY_TICKS
        )
        val position = soul.coords
        soul.walk(position.translateX(1))
        world.processSoulMovement(soul)
        assertTrue(
            soul.routeDestination.isEmpty(),
            "The ordinary movement processor must discard stray routes",
        )
        assertEquals(position, soul.coords)
    }

    @Test
    fun `lava emits three unique owned targets and never hits another instance`() {
        val world = CerberusTestWorld()
        val player = world.enter(1)
        val other = world.enter(2)
        world.wake(player)
        world.wake(other)
        world.force(player, "lava")
        val tiles = world.projectiles().map { CoordGrid(it.end.x, it.end.z, it.end.level) }
        assertEquals(3, tiles.size)
        assertEquals(3, tiles.toSet().size)
        assertTrue(player.coords in tiles)
        assertTrue(tiles.all { world.arena.contains(world.session(player), it) })
        assertTrue(tiles.none { world.arena.contains(world.session(other), it) })
        world.tick(10)
        assertTrue(world.status(player).contains("lava=3 "), world.status(player))
        assertTrue(world.hits(player).any { it.type == HitType.Typeless && it.damage == 10 })
        assertTrue(world.hits(other).isEmpty())
        val pending = world.hits(player)
        world.leave(player)
        world.tick(30)
        assertTrue(pending.none { it.isValidNow() })
        assertTrue(world.hits(other).isEmpty())
    }

    @Test
    fun `leaving between triple components cancels old timers before a new encounter`() {
        val world = CerberusTestWorld()
        val player = world.enter()
        world.wake(player)
        world.attack(player)
        val oldSession = world.session(player)
        val oldBoss = world.boss(player)
        val launched = world.hits(player).single()
        assertTrue(world.leave(player))
        assertNull(world.instances.sessionForId(oldSession.id))
        assertFalse(oldBoss.isSlotAssigned)
        assertTrue(world.start(player))
        world.updates.clear()
        world.tick(5)
        assertEquals(0, world.projectileCount())
        assertFalse(launched.isValidNow())
        world.wake(player)
        world.attack(player)
        assertEquals(CerberusAssets.MAGIC_PROJECTILE_SPOTANIM, world.projectiles().single().id)
        assertFalse(launched.isValidNow())
    }

    @Test
    fun `stale membership after external movement cannot launch or keep an encounter alive`() {
        val world = CerberusTestWorld()
        val player = world.enter(1)
        val other = world.enter(2)
        world.wake(player)
        val boss = world.boss(player)
        player.coords = world.tile(other, 24, 30)
        world.updates.clear()
        world.attack(player)
        assertEquals(0, world.projectileCount())
        world.tick()
        assertFalse(boss.isSlotAssigned)
        assertFalse(world.manager.isEncounterNpc(boss))
    }

    @Test
    fun `boss death cancels each Soul stage and respawns exactly one sitting boss`() {
        for (deathTick in listOf(0, 1, 11, 12, 14, 16)) {
            val world = CerberusTestWorld()
            val player = world.enter()
            world.wake(player)
            world.force(player, "souls")
            val ghosts = world.souls(player)
            world.tick(deathTick)
            val oldBoss = world.boss(player)
            val launched = world.hits(player)
            assertTrue(world.manager.beginBossDeath(oldBoss))
            assertFalse(world.manager.beginBossDeath(oldBoss))
            assertTrue(ghosts.none { it.isSlotAssigned })
            assertTrue(launched.none { it.isValidNow() })
            world.npcs.despawn(oldBoss, CerberusRules.RESPAWN_TICKS)
            world.manager.finishBossDeath(oldBoss)
            world.manager.finishBossDeath(oldBoss)
            world.updates.clear()
            world.tick(CerberusRules.RESPAWN_TICKS)
            assertEquals(0, world.projectileCount())
            val alive =
                world.instances.npcsForInstance(world.session(player).id).filter {
                    it.isSlotAssigned
                }
            assertEquals(1, alive.size)
            assertEquals(CerberusAssets.SITTING_NPC_ID, alive.single().visType.id)
            assertTrue(alive.single().respawns)
            assertSame(oldBoss, alive.single())
        }
    }

    companion object {
        @JvmStatic @BeforeAll fun loadCache() = CerberusTestCache.ensureLoaded()
    }
}

@OptIn(InternalApi::class)
internal class CerberusTestWorld {
    val clock = MapClock(100)
    val collision = CollisionFlagMap()
    val updates = ZoneUpdateMap()
    val events = EventBus()
    private val playerList = PlayerList()
    private val npcList = NpcList()
    private val npcRegistry = NpcRegistry(npcList, collision, events)
    val npcs = NpcRepository(clock, npcRegistry, npcList)
    private val locs = LocZoneStorage()
    private val activity = ZonePlayerActivityBitSet()
    val regions =
        RegionRegistry(
            RegionListSmall(),
            RegionListLarge(),
            LocRegistryNormal(updates, collision, locs),
            collision,
            locs,
            npcRegistry,
            ControllerRegistry(clock, ControllerList()),
            activity,
        )
    val instances =
        InstanceManager(
            RegionRepository(regions),
            npcs,
            playerList,
            events,
            InstanceAreaResolver(),
            clock,
            collision,
        )
    val arena = CerberusArena(regions)
    private val random = DefaultGameRandom(42L)
    private val styles =
        AttackStyles().also { styles ->
            with(WeaponAttackStylesScript(styles)) {
                ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup()
            }
        }
    private val injector =
        Guice.createInjector({
            it.bind(GameRandom::class.java).toInstance(random)
            it.bind(AttackStyles::class.java).toInstance(styles)
            it.bind(InstanceManager::class.java).toInstance(instances)
            it.bind(MapClock::class.java).toInstance(clock)
        })
    private val queues = WorldQueueList()
    private val queueProcess = WorldQueueListProcess(queues)
    val manager =
        CerberusEncounterManager(
            clock,
            instances,
            npcs,
            AiPlayerInteractions(events, playerList),
            collision,
            random,
            injector.getInstance(AccuracyFormulae::class.java),
            injector.getInstance(DamageReductionAttributeCollector::class.java),
            WornBonuses(),
            queues,
            WorldRepository(updates),
            RouteFactory(collision),
            arena,
        )

    var denyTeleport = false
    private val protectedContext =
        mock(ProtectedAccessContext::class.java) { call ->
            when (call.method.name) {
                "getCollision" -> collision
                "getEventBus" -> events
                "getTeleportHooks" -> PlayerTeleportHooks.EMPTY
                "getTeleportValidator" ->
                    PlayerTeleportValidator(
                        setOf(
                            PlayerTeleportValidateHook { _, _, _ ->
                                if (denyTeleport) "Test teleport denied" else null
                            }
                        )
                    )
                "getAreaChecker" -> mock(call.method.returnType)
                else -> RETURNS_DEFAULTS.answer(call)
            }
        }
    private val protectedLauncher =
        ProtectedAccessLauncher(
            mock(ProtectedAccessContextFactory::class.java) { call ->
                if (call.method.name == "create") protectedContext
                else RETURNS_DEFAULTS.answer(call)
            }
        )
    val instanceScript = CerberusWorld(BossInstanceRegistry(), manager, protectedLauncher)
    val nativeLifecycle: PlayerPostTickHook
    val kills = mutableListOf<org.rsmod.api.death.NpcDeathKillContext>()
    private val death =
        org.rsmod.api.death.NpcDeath(
            npcs,
            playerList,
            mock(org.rsmod.api.repo.obj.ObjRepository::class.java),
            setOf(org.rsmod.api.death.NpcDeathDropHook { true }),
            setOf(org.rsmod.api.death.NpcDeathKillHook { kills += it }),
        )

    init {
        injector.injectMembers(instanceScript)
        val context = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
        with(instanceScript) { context.startup() }
        with(CerberusLifecycle(manager)) { context.startup() }
        with(CerberusCombat(manager, death)) { context.startup() }
        val lifecycle =
            injector.getInstance(
                Class.forName("org.rsmod.api.instances.hook.InstanceLifecycleScript")
                    .asSubclass(PluginScript::class.java)
            )
        with(lifecycle) { context.startup() }
        nativeLifecycle = lifecycle as PlayerPostTickHook
        for (x in 0..7) for (z in 0..7) {
            collision.allocateIfAbsent(
                CERBERUS_SOURCE_MAP_X * 64 + x * 8,
                CERBERUS_SOURCE_MAP_Z * 64 + z * 8,
                0,
            )
        }
    }

    fun enter(slot: Int = 1): Player =
        player(slot).also {
            assertTrue(start(it))
            // Model the occupancy flag normally maintained by PlayerRegistry each world tick.
            activity.flag(ZoneKey.from(it.coords))
        }

    fun player(slot: Int = 1): Player {
        val player =
            Player().apply {
                uuid = slot.toLong()
                observerUUID = slot.toLong()
                slotId = slot
                assignUid()
                coords = CERBERUS_PUBLIC_EXIT
                for (stat in
                    listOf(
                        "hitpoints",
                        "attack",
                        "strength",
                        "defence",
                        "ranged",
                        "magic",
                        "prayer",
                    )) {
                    statMap.setBaseLevel("stat.$stat", 99)
                    statMap.setCurrentLevel("stat.$stat", 99)
                }
                worn = invMap.getOrPut("inv.worn")
                inv = invMap.getOrPut("inv.inv")
            }
        playerList[slot] = player
        return player
    }

    fun end(player: Player, reason: String) {
        when (reason) {
            "logout" -> events.publish(SessionStateEvent.Logout(player))
            "disconnect" -> {
                player.clientDisconnected.set(true)
                nativeLifecycle.onPostTick(player)
            }
            "death" -> instances.handleDeath(player, clock.cycle)
            "teleport" -> {
                val previous = player.coords
                player.coords = CERBERUS_PUBLIC_EXIT
                events.publish(PlayerMovementEvent.CoordsMovedEvent(player, previous))
                player.previousCoords = player.coords
            }
            else -> error("Unknown test exit: $reason")
        }
    }

    fun kill(player: Player) {
        val boss = boss(player)
        boss.hitpoints = 0
        boss.launch {
            val access =
                StandardNpcAccess(
                    boss,
                    this,
                    StandardNpcAccessContext(
                        { random },
                        { error("Not processing hits") },
                        { error("Not processing hits") },
                    ),
                )
            events.publish(
                access,
                org.rsmod.api.npc.events.NpcQueueEvents.Type(
                    boss,
                    null,
                    dev.openrune.rscm.RSCM.getRSCM("queue.death"),
                ),
            )
        }
    }

    fun start(player: Player): Boolean {
        player.currentMapClock = clock.cycle
        player.processedMapClock = clock.cycle
        protectedLauncher.launch(player) { with(instanceScript) { startLair() } }
        return instances.sessionForPlayer(player) != null
    }

    fun leave(player: Player): Boolean {
        protectedLauncher.launch(player) { with(instanceScript) { leaveLair() } }
        return instances.sessionForPlayer(player) == null
    }

    fun session(player: Player): InstanceSession =
        requireNotNull(instances.sessionForPlayer(player))

    fun tile(player: Player, x: Int, z: Int): CoordGrid =
        requireNotNull(
            instances.localCoord(
                session(player),
                RegionLocal(0, CERBERUS_SOURCE_MAP_X, CERBERUS_SOURCE_MAP_Z, x, z),
            )
        )

    fun boss(player: Player): Npc =
        instances.npcsForInstance(session(player).id).first {
            it.isSlotAssigned && it.id == CerberusAssets.SITTING_NPC_ID
        }

    fun status(player: Player): String = manager.debug(player, listOf("status"))

    fun souls(player: Player): List<Npc> =
        instances.npcsForInstance(session(player).id).filter {
            it.isSlotAssigned &&
                it.id in CerberusAssets.RANGED_SOUL_NPC_ID..CerberusAssets.MELEE_SOUL_NPC_ID
        }

    fun force(player: Player, attack: String) {
        manager.debug(player, listOf("next", attack))
        updates.clear()
        attack(player)
    }

    fun wake(player: Player) {
        player.coords = tile(player, 24, 30)
        tick(CerberusRules.STAND_TRANSITION_TICKS + 1)
        assertTrue(status(player).contains("phase=ACTIVE "), status(player))
    }

    fun attack(player: Player) {
        val access =
            StandardNpcAccess(
                boss(player),
                GameCoroutine(),
                StandardNpcAccessContext(
                    { random },
                    { error("Encounter uses player hit processor") },
                    { error("Not processing NPC hits") },
                ),
            )
        manager.attack(access, player)
    }

    fun tick(count: Int = 1) =
        repeat(count) {
            clock.tick()
            playerList.forEach { player ->
                player.currentMapClock = clock.cycle
                player.processedMapClock = clock.cycle
                if (player.coords != player.previousCoords) {
                    events.publish(
                        PlayerMovementEvent.CoordsMovedEvent(player, player.previousCoords)
                    )
                    player.previousCoords = player.coords
                }
            }
            npcList.toList().forEach {
                it.currentMapClock = clock.cycle
                it.processedMapClock = clock.cycle
                if (it.canProcess && it.activeCoroutine != null) it.advanceActiveCoroutine()
                NpcRevealProcessor(npcs).process(it)
            }
            queueProcess.process()
            instances.tickReclaim(clock.cycle)
            manager.tickAll()
        }

    fun projectiles(): List<MapProjAnimV2> =
        updates.updatedZones.values.flatMap { zone ->
            zone.protList.filterIsInstance<MapProjAnimV2>()
        }

    fun projectileCount(): Int = projectiles().size

    fun processSoulMovement(soul: Npc) {
        soul.currentMapClock = clock.cycle
        NpcMovementProcessor(collision, StepFactory(collision), events).process(soul)
        NpcFaceSquareProcessor().process(soul)
    }

    fun hits(player: Player): List<Hit> = buildList {
        val iterator = player.queueList.iterator() ?: return@buildList
        while (iterator.hasNext()) (iterator.next().args as? Hit)?.let { add(it) }
        iterator.cleanUp()
    }
}

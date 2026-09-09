package org.rsmod.content.skills.agility

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.BasType
import kotlin.test.*
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.*
import org.rsmod.annotations.InternalApi
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.game.process.player.PlayerInteractionProcessor
import org.rsmod.api.game.process.player.PlayerMovementProcessor
import org.rsmod.api.player.events.interact.LocEvents
import org.rsmod.api.player.hook.PlayerTeleportHooks
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.interact.*
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContext
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.protect.ProtectedAccessLauncher.Companion.withProtectedAccess
import org.rsmod.api.random.GameRandom
import org.rsmod.api.registry.loc.LocRegistry
import org.rsmod.api.registry.obj.ObjRegistry
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.route.BoundValidator
import org.rsmod.api.route.RayCastValidator
import org.rsmod.api.route.RouteFactory
import org.rsmod.api.route.StepFactory
import org.rsmod.content.skills.agility.rooftop.*
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.WorldEntityList
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.movement.RouteRequestLoc
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

class RooftopRuntimeTest {
    @Test
    fun `all nine courses execute two complete laps through registered native loc events`() {
        val expectedXp = listOf(120.0, 216.0, 269.7, 240.0, 586.0, 570.0, 890.0, 780.0, 889.0)
        for ((courseIndex, layout) in RooftopCourses.layouts.withIndex()) {
            val f = RooftopFixture()
            repeat(2) { lap ->
                for ((index, obstacle) in layout.obstacles.withIndex()) {
                    f.player.coords = obstacle.start // Walk between obstacles is tested separately.
                    f.click(obstacle)
                    assertFalse(f.launch { error("A second click acquired protected access") })
                    f.finish()
                    assertEquals(
                        obstacle.move.destination,
                        f.player.coords,
                        "${layout.course}:$index",
                    )
                    assertNull(f.player.bas)
                }
                assertEquals(expectedXp[courseIndex] * (lap + 1), f.xp, 0.1, layout.course.name)
            }
        }
    }

    @Test
    fun `legacy courses and rooftops register together without duplicate handlers`() {
        val f = RooftopFixture()
        with(AgilityScript(f.random, f.objects)) { f.scriptContext.startup() }
        assertEquals(3, AgilityDefinitions.courses.size)
        val newIds =
            RooftopCourses.layouts
                .flatMap { it.obstacles }
                .flatMap { it.locs }
                .map { it.asRSCM(RSCMType.LOC) }
        assertEquals(newIds.size, newIds.toSet().size)
        assertTrue(AgilityDefinitions.obstacleIds.none { it in newIds })
        newIds.forEach { assertTrue(f.events.contains(LocEvents.Op1::class.java, it)) }
        val swing =
            RooftopCourses.layout(RooftopCourse.AlKharid).obstacles.single {
                it.name == "Tropical tree"
            }
        swing.locs.forEach {
            assertTrue(f.events.contains(LocEvents.Ap1::class.java, it.asRSCM(RSCMType.LOC)))
        }
    }

    @Test
    fun `skipping reordering and repeating final obstacles cannot farm lap bonuses`() {
        val layout = RooftopCourses.layout(RooftopCourse.Draynor)
        for (indices in listOf(listOf(6, 6), listOf(0, 2, 1, 3, 4, 5, 6))) {
            val f = RooftopFixture()
            for (index in indices) f.complete(layout.obstacles[index])
            assertEquals(indices.sumOf { layout.obstacles[it].xp }, f.xp, 0.1)
        }
    }

    @Test
    fun `logout or an interrupted obstacle invalidates the partial lap`() {
        val layout = RooftopCourses.layout(RooftopCourse.Draynor)
        for (logout in listOf(false, true)) {
            val f = RooftopFixture()
            f.complete(layout.obstacles[0])
            if (logout) {
                f.events.publish(SessionStateEvent.Logout(f.player))
            } else {
                val obstacle = layout.obstacles[1]
                f.player.coords = obstacle.start
                f.click(obstacle)
                f.player.activeCoroutine?.cancel()
                f.tick(20) // Native protected action delay must expire before the next click.
            }
            for (obstacle in layout.obstacles.drop(1)) f.complete(obstacle)
            assertEquals(layout.obstacles.sumOf { it.xp }, f.xp, 0.1)
        }
    }

    @Test
    fun `a death during a cross level leap cannot be undone by the landing cleanup`() {
        val f = RooftopFixture()
        f.player.coords = CoordGrid(3201, 3416, 3)
        val dest = CoordGrid(3193, 3416, 1)
        f.launch { leapTo(dest, AgilityAnims.JUMP, 3) }
        f.player.statMap.setCurrentLevel("stat.hitpoints", 0)
        f.player.activeCoroutine?.cancel()
        assertEquals(CoordGrid(dest.x, dest.z, 3), f.player.coords)
    }

    @Test
    fun `marks keep cooldown owner lifetime and the Canifis overlevel exception`() {
        val f = RooftopFixture()
        val marks = MarksOfGrace(f.objects)
        val layout = RooftopCourses.layout(RooftopCourse.Canifis)
        assertTrue(marks.roll(f.player, f.random, layout))
        verify(f.objects)
            .add("obj.grace", layout.markTiles.first(), 1000, f.player, 1, Int.MAX_VALUE)
        assertFalse(marks.roll(f.player, f.random, layout))
        f.tick(299)
        assertFalse(marks.roll(f.player, f.random, layout))
        f.tick()
        assertTrue(marks.roll(f.player, f.random, layout))
        verify(f.objects, times(2))
            .add("obj.grace", layout.markTiles.first(), 1000, f.player, 1, Int.MAX_VALUE)
        val penaltyRoll =
            object : GameRandom {
                override fun of(maxExclusive: Int): Int = if (maxExclusive == 5) 1 else 0

                override fun of(minInclusive: Int, maxInclusive: Int): Int = minInclusive

                override fun randomDouble() = 0.0
            }
        f.tick(300)
        assertFalse(marks.roll(f.player, penaltyRoll, RooftopCourses.layout(RooftopCourse.Draynor)))
        assertTrue(marks.roll(f.player, penaltyRoll, layout))
    }

    @Test
    fun `level checks reject every course above the player's current level`() {
        for (layout in RooftopCourses.layouts) {
            val f = RooftopFixture()
            f.player.statMap.setCurrentLevel("stat.agility", (layout.course.level - 1).toByte())
            val obstacle = layout.obstacles.first()
            f.player.coords = obstacle.start
            f.click(obstacle)
            f.finish()
            assertEquals(obstacle.start, f.player.coords)
            assertEquals(0.0, f.xp)
        }
    }

    @Test
    fun `each failable obstacle drops at its failure tile and awards no XP`() {
        for (layout in RooftopCourses.layouts) for (obstacle in layout.obstacles) {
            val failure = obstacle.failure ?: continue
            val f = RooftopFixture()
            f.random.high = true
            f.player.statMap.setCurrentLevel("stat.agility", layout.course.level.toByte())
            f.complete(obstacle)
            assertEquals(failure.landing, f.player.coords, "${layout.course}: ${obstacle.name}")
            assertEquals(0.0, f.xp)
            assertNull(f.player.bas)
            assertTrue(f.player.queueList.isNotEmpty, "Fall must queue a native environmental hit")
        }
    }

    @Test
    fun `interrupted balance restores the previous render and cannot undo an external teleport`() {
        val f = RooftopFixture()
        val previous = BasType(id = 90, readyAnim = 808, walkForward = 819)
        f.player.bas = previous
        val path = line(CoordGrid(3099, 3277, 3), CoordGrid(3090, 3277, 3))
        f.player.coords = CoordGrid(3099, 3277, 3)
        f.launch { balanceAlong(path, BalanceStyle.Tightrope) }
        f.tick(2)
        val outside = CoordGrid(3087, 3495)
        f.player.coords = outside
        f.player.activeCoroutine?.cancel()
        f.tick()
        assertEquals(outside, f.player.coords)
        assertSame(previous, f.player.bas)
        assertEquals(0.0, f.xp)
    }

    @Test
    fun `cross level leap uses one glide plane and cancellation lands only owned movement`() {
        for (external in listOf(false, true)) {
            val f = RooftopFixture()
            val start = CoordGrid(3201, 3416, 3)
            val dest = CoordGrid(3193, 3416, 1)
            f.player.coords = start
            f.launch { leapTo(dest, AgilityAnims.JUMP, 3) }
            assertEquals(CoordGrid(dest.x, dest.z, start.level), f.player.coords)
            val outside = CoordGrid(3087, 3495)
            if (external) f.player.coords = outside
            f.player.activeCoroutine?.cancel()
            assertEquals(if (external) outside else dest, f.player.coords)
        }
    }

    @Test
    fun `wrong floor cannot pull player onto a rooftop`() {
        val f = RooftopFixture()
        val obstacle = RooftopCourses.layout(RooftopCourse.Draynor).obstacles[1]
        f.player.coords = CoordGrid(obstacle.start.x, obstacle.start.z, 0)
        val origin = f.player.coords
        f.click(obstacle)
        f.finish()
        assertEquals(origin, f.player.coords)
        assertEquals(0.0, f.xp)
    }

    @Test
    fun `distant approach walks rather than jumping to the start`() {
        val f = RooftopFixture()
        val start = CoordGrid(3103, 3279)
        val origin = start.translate(0, -5)
        f.player.coords = origin
        var reached = false
        f.launch { reached = stepOnto(start, RouteFactory(f.collision)) }
        assertEquals(origin, f.player.coords)
        f.finish()
        assertTrue(reached)
        assertEquals(start, f.player.coords)
    }

    @Test
    fun `long alignment follows native pathfinding around scenery`() {
        val f = RooftopFixture()
        val start = CoordGrid(3103, 3279)
        val blocked = start.translate(0, -3)
        f.player.coords = start.translate(0, -5)
        f.collision.add(
            blocked.x,
            blocked.z,
            blocked.level,
            org.rsmod.routefinder.flag.CollisionFlag.LOC,
        )
        var reached = false
        f.launch { reached = stepOnto(start, RouteFactory(f.collision)) }
        repeat(20) {
            val previous = f.player.coords
            f.tick()
            assertNotEquals(blocked, f.player.coords)
            assertTrue(previous.chebyshevDistance(f.player.coords) <= 1)
        }
        assertTrue(reached)
        assertEquals(start, f.player.coords)
    }

    @Test
    fun `normal balance follows every tile at one tile per tick and restores prior render`() {
        for (style in BalanceStyle.entries) {
            val f = RooftopFixture()
            val previous = BasType(id = 90, readyAnim = 808, walkForward = 819)
            f.player.bas = previous
            f.player.coords = CoordGrid(3099, 3277, 3)
            val path = line(f.player.coords, CoordGrid(3090, 3277, 3))
            f.launch { balanceAlong(path, style) }
            if (style.mount != null) f.tick()
            for (tile in path) {
                assertEquals(tile, f.player.coords)
                assertEquals(style.walk.asRSCM(RSCMType.SEQ), f.player.bas?.walkForward)
                f.tick()
            }
            assertSame(previous, f.player.bas)
        }
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun cache() {
            if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
        }
    }
}

internal class RooftopRandom : GameRandom {
    var high = false

    override fun of(maxExclusive: Int): Int = if (high) maxExclusive - 1 else 0

    override fun of(minInclusive: Int, maxInclusive: Int): Int =
        if (high) maxInclusive else minInclusive

    override fun randomDouble(): Double = if (high) 0.999 else 0.0
}

@OptIn(InternalApi::class)
internal class RooftopFixture(
    val collision: CollisionFlagMap = CollisionFlagMap(),
    allocate: Boolean = true,
) {
    val context = mock(ProtectedAccessContext::class.java)
    val random = RooftopRandom()
    val objects = mock(ObjRepository::class.java)
    val events = EventBus()
    val scriptContext = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
    val movement =
        PlayerMovementProcessor(collision, RouteFactory(collision), StepFactory(collision), events)
    private var interactions: PlayerInteractionProcessor? = null
    private val locRegistry = mock(LocRegistry::class.java)
    private val locInteractions = LocInteractions(BoundValidator(collision), events)
    val player =
        Player().apply {
            uuid = 1L
            observerUUID = 1L
            slotId = 1
            assignUid()
            currentMapClock = 100
            processedMapClock = 100
            statMap.setBaseLevel("stat.agility", 99)
            statMap.setCurrentLevel("stat.agility", 99)
            statMap.setBaseLevel("stat.hitpoints", 99)
            statMap.setCurrentLevel("stat.hitpoints", 99)
            inv = invMap.getOrPut("inv.inv")
            worn = invMap.getOrPut("inv.worn")
        }
    val xp
        get() = player.statMap.getFineXP("stat.agility") / 10.0

    init {
        player.client =
            object : Client<Any, Any> {
                override fun write(message: Any) = Unit

                override fun close() = Unit

                override fun read(player: Player) = Unit

                override fun flush() = Unit

                override fun flushHighPriority() = Unit

                override fun unregister(service: Any, player: Player) = Unit
            }
        // Empty native collision map for movement tests; actual cache locs are audited separately.
        if (allocate)
            for (x in 2600..3550 step 8) for (z in 2950..3700 step 8) for (level in 0..3) {
                collision.allocateIfAbsent(x, z, level)
            }
        `when`(context.random).thenReturn(random)
        `when`(context.collision).thenReturn(collision)
        `when`(context.areaChecker).thenReturn(mock(AreaChecker::class.java))
        `when`(context.teleportValidator).thenReturn(PlayerTeleportValidator(emptySet()))
        `when`(context.teleportHooks).thenReturn(PlayerTeleportHooks.EMPTY)
        `when`(context.eventBus).thenReturn(events)
        with(RooftopScript(MarksOfGrace(objects), RouteFactory(collision))) {
            scriptContext.startup()
        }
    }

    fun launch(block: suspend ProtectedAccess.() -> Unit): Boolean =
        withProtectedAccess(player, context, block = block)

    fun click(obstacle: RooftopObstacle) {
        val id = obstacle.locs.first().asRSCM(RSCMType.LOC)
        val type = assertNotNull(ServerCacheManager.getObject(id))
        val loc = BoundLocInfo(LocInfo(1, obstacle.start, LocEntity(id, 10, 0)), type)
        assertTrue(launch { assertTrue(events.publish(this, LocEvents.Op1(loc, loc, type))) })
    }

    fun complete(obstacle: RooftopObstacle) {
        player.coords = obstacle.start
        click(obstacle)
        finish()
    }

    fun interact(loc: BoundLocInfo) {
        if (interactions == null) {
            val factory = mock(ProtectedAccessContextFactory::class.java)
            `when`(factory.create()).thenReturn(context)
            interactions =
                PlayerInteractionProcessor(
                    events,
                    locRegistry,
                    mock(ObjRegistry::class.java),
                    BoundValidator(collision),
                    RayCastValidator(collision),
                    locInteractions,
                    mock(LocTInteractions::class.java),
                    mock(NpcInteractions::class.java),
                    mock(NpcTInteractions::class.java),
                    mock(ObjInteractions::class.java),
                    mock(PlayerInteractions::class.java),
                    mock(PlayerTInteractions::class.java),
                    ProtectedAccessLauncher(factory),
                    movement,
                    WorldEntityList(),
                    emptySet(),
                )
        }
        `when`(locRegistry.isValid(loc.coords, loc.id)).thenReturn(true)
        locInteractions.interact(player, loc, InteractionOp.Op1)
        // A client click includes the native OpLocHandler's route request; interact() alone
        // only sets a straight script walk and is not a faithful network-click fixture.
        player.routeRequest =
            RouteRequestLoc(
                loc.coords,
                loc.width,
                loc.length,
                loc.shapeId,
                loc.angleId,
                loc.forceApproachFlags,
                clientRequest = true,
            )
        val trace = mutableListOf<String>()
        repeat(100) {
            tick()
            if (it < 12)
                trace +=
                    "${player.coords.x},${player.coords.z},${player.coords.level} route=${player.routeDestination.peekFirst()} flags=${collision[player.coords.x, player.coords.z - 1, player.coords.level]}"
            if (player.interaction == null && player.activeCoroutine == null) return
        }
        error("Native click did not finish: $loc at ${player.coords}, trace=$trace")
    }

    fun tick(count: Int = 1) =
        repeat(count) {
            player.pendingTeleport = false
            player.pendingTelejump = false
            player.pendingExactMove = null
            player.pendingStepCount = 0
            player.currentMapClock++
            player.processedMapClock = player.currentMapClock
            player.advanceActiveCoroutine()
            if (interactions == null) movement.process(player) else interactions!!.process(player)
        }

    fun finish() {
        repeat(100) {
            if (player.activeCoroutine == null) return
            tick()
        }
        error("Rooftop interaction did not finish")
    }
}

package org.rsmod.content.minigames.toa

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.rsmod.api.instances.InstanceId
import org.rsmod.game.entity.Npc
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid

class ToaScabarasLiveRuntimeTest {
    @Test
    fun initializationSpawnsVerifiedRevision240AssetsAtDonorGeneratedPositions() {
        val world = FakeScabarasLiveWorld()
        val runtime = ToaScabarasLiveRuntime(world)
        val instance = InstanceId(41)

        val result = runtime.initialize(instance, seed = 0x5CA8A5L, partySize = 1)
        val snapshot = assertNotNull(runtime.snapshot(instance))

        assertTrue(result.handled)
        assertEquals(ScabarasLiveEventKind.INITIALIZED, result.events.first().kind)
        assertEquals(6, world.activeNpcs.size)
        assertEquals(5, world.activeNpcs.count { it.spawnId == 11698 })
        assertEquals(1, world.activeNpcs.count { it.spawnId == 11699 })

        val fireTiles =
            (ScabarasLiveGeometry.BRANCH_FIRES + ScabarasLiveGeometry.EXIT_FIRES).mapTo(
                linkedSetOf(),
                world::coord,
            )
        assertEquals(21, fireTiles.size)
        assertTrue(
            fireTiles.all { tile ->
                world.activeLocs.values.any {
                    it.coords == tile && it.id == 45335 && it.shape == LocShape.CentrepieceStraight
                }
            }
        )

        val rooms = snapshot.solutions.sideRooms.zip(ScabarasLiveGeometry.PUZZLE_BASES).toMap()
        val sequenceBase = rooms.getValue(ScabarasPuzzle.SEQUENCE)
        assertLoc(
            world,
            ScabarasRevision240RuntimeLayout.sequenceCache.buttonLoc,
            sequenceBase + ScabarasLiveGeometry.BUTTON_OFFSET,
            LocShape.CentrepieceStraight,
        )
        ScabarasLiveGeometry.MEMORY_OFFSETS.forEach { offset ->
            assertLoc(
                world,
                ScabarasRevision240RuntimeLayout.sequenceCache.tileUpLoc,
                sequenceBase + offset,
                LocShape.GroundDecor,
            )
        }

        val additionBase = rooms.getValue(ScabarasPuzzle.ADDITION)
        ScabarasLiveGeometry.SUM_OFFSETS.forEach { (value, offsets) ->
            offsets.forEach { offset ->
                assertLoc(
                    world,
                    ScabarasRevision240RuntimeLayout.additionTiles[value - 1].groundLoc,
                    additionBase + offset,
                    LocShape.GroundDecor,
                )
            }
        }

        val obeliskBase = rooms.getValue(ScabarasPuzzle.OBELISKS)
        assertEquals(
            ScabarasLiveGeometry.OBELISK_OFFSETS.map { world.coord(obeliskBase + it) },
            world.activeNpcs
                .filter { it.kind != ScabarasLiveNpcKind.RANGED_SCARAB }
                .map { it.coords },
        )
        ScabarasLiveGeometry.MATCHING_BASES.values.forEach { base ->
            ScabarasLiveGeometry.MATCHING_OFFSETS.forEach { offset ->
                assertTrue(
                    world.activeLocs.values.any {
                        it.coords == world.coord(base + offset) && it.shape == LocShape.GroundDecor
                    }
                )
            }
        }
    }

    @Test
    fun soloFirstRoomKeepsItsDoorClosedAndOpensTheOppositeLane() {
        val world = FakeScabarasLiveWorld()
        val runtime = ToaScabarasLiveRuntime(world)
        val instance = InstanceId(41)
        runtime.initialize(instance, seed = 0x1234L, partySize = 1)

        val solutions = assertNotNull(runtime.snapshot(instance)).solutions
        val completedRoomIndex = 2
        val result =
            solveSideRoom(
                runtime,
                instance,
                solutions.sideRooms[completedRoomIndex],
                ScabarasLiveGeometry.PUZZLE_BASES[completedRoomIndex],
            )

        val opened =
            result
                .filter { it.kind == ScabarasLiveEventKind.BARRIER_OPENED }
                .mapNotNullTo(linkedSetOf()) { it.tile }
        assertEquals(setOf(world.coord(ScabarasLiveGeometry.BRANCH_FIRES[0])), opened)
        assertTrue(
            world.activeLocs.values.any {
                it.coords == world.coord(ScabarasLiveGeometry.BRANCH_FIRES[completedRoomIndex]) &&
                    it.id == 45335
            }
        )
        assertFalse(
            world.activeLocs.values.any {
                it.coords == world.coord(ScabarasLiveGeometry.BRANCH_FIRES[0]) && it.id == 45335
            }
        )
    }

    @Test
    fun soloDiagonalRouteUnlocksMatchingAndOpensEveryBarrier() {
        val world = FakeScabarasLiveWorld()
        val runtime = ToaScabarasLiveRuntime(world)
        val instance = InstanceId(42)
        runtime.initialize(instance, seed = 0x1234L, partySize = 1)

        val solutions = assertNotNull(runtime.snapshot(instance)).solutions
        val completionEvents = mutableListOf<ScabarasLiveEvent>()
        listOf(0, 3).forEach { roomIndex ->
            val puzzle = solutions.sideRooms[roomIndex]
            val base = ScabarasLiveGeometry.PUZZLE_BASES[roomIndex]
            completionEvents += solveSideRoom(runtime, instance, puzzle, base)
        }

        val beforeMatching = assertNotNull(runtime.snapshot(instance))
        assertEquals(2, beforeMatching.completedPuzzles.size)
        assertFalse(beforeMatching.challengeComplete)

        val matchingResult = solveMatching(runtime, instance, beforeMatching.solutions)
        completionEvents += matchingResult.events

        assertTrue(matchingResult.handled)
        assertTrue(matchingResult.challengeComplete)
        assertTrue(assertNotNull(runtime.snapshot(instance)).challengeComplete)
        assertEquals(
            1,
            completionEvents.count { it.kind == ScabarasLiveEventKind.CHALLENGE_COMPLETED },
        )
        assertEquals(
            setOf(solutions.sideRooms[0], solutions.sideRooms[3], ScabarasPuzzle.MATCHING),
            completionEvents
                .filter { it.kind == ScabarasLiveEventKind.PUZZLE_COMPLETED }
                .mapNotNullTo(linkedSetOf()) { it.puzzle },
        )
        assertTrue(
            (ScabarasLiveGeometry.BRANCH_FIRES + ScabarasLiveGeometry.EXIT_FIRES).none { local ->
                world.activeLocs.values.any { it.coords == world.coord(local) && it.id == 45335 }
            }
        )
    }

    @Test
    fun completedSideRoomsRetireTheirPresentationAndIgnoreStaleActions() {
        ScabarasPuzzle.entries
            .filterNot { it == ScabarasPuzzle.MATCHING }
            .forEachIndexed { testIndex, puzzle ->
                val seed =
                    (0L..1000L).first { candidate ->
                        ScabarasEncounter(candidate, 1).solutions.sideRooms.first() == puzzle
                    }
                val world = FakeScabarasLiveWorld()
                val runtime = ToaScabarasLiveRuntime(world)
                val instance = InstanceId(60L + testIndex)
                runtime.initialize(instance, seed, partySize = 1)
                val base = ScabarasLiveGeometry.PUZZLE_BASES.first()
                val roomTiles = roomPresentationTiles(world, puzzle, base)

                solveSideRoom(runtime, instance, puzzle, base)

                assertTrue(
                    world.activeLocs.values.none { it.coords in roomTiles },
                    "$puzzle left a live object behind after completion.",
                )
                assertTrue(
                    world.activeNpcs.none {
                        it.coords in roomTiles &&
                            it.kind in
                                setOf(
                                    ScabarasLiveNpcKind.OBELISK,
                                    ScabarasLiveNpcKind.OBELISK_DECOY,
                                )
                    },
                    "$puzzle left an obelisk actor behind after completion.",
                )

                val staleResult =
                    when (puzzle) {
                        ScabarasPuzzle.SEQUENCE ->
                            runtime.onTileStep(
                                instance,
                                world.coord(base + ScabarasLiveGeometry.MEMORY_OFFSETS.first()),
                            )
                        ScabarasPuzzle.LIGHTS ->
                            runtime.onObjectInteraction(
                                instance,
                                ScabarasLiveObjectAction.FLIP,
                                ScabarasRevision240RuntimeLayout.lightsCache.tileOffLoc,
                                world.coord(base + ScabarasLiveGeometry.LIGHT_OFFSETS.first()),
                            )
                        ScabarasPuzzle.OBELISKS ->
                            runtime.onNpcHit(
                                instance,
                                ScabarasRevision240RuntimeLayout.obelisks.attackableNpc,
                                world.coord(base + ScabarasLiveGeometry.OBELISK_OFFSETS.first()),
                            )
                        ScabarasPuzzle.ADDITION ->
                            runtime.onObjectInteraction(
                                instance,
                                ScabarasLiveObjectAction.INSPECT,
                                ScabarasRevision240RuntimeLayout.ADDITION_TABLET_LOC,
                                world.coord(base + ScabarasLiveGeometry.BUTTON_OFFSET),
                            )
                        ScabarasPuzzle.MATCHING -> error("Matching is not a side-room puzzle.")
                    }
                assertFalse(staleResult.handled)
                assertTrue(world.activeLocs.values.none { it.coords in roomTiles })

                runtime.cleanup(instance)
                runtime.initialize(instance, seed, partySize = 1)
                assertTrue(
                    world.activeLocs.values.any { it.coords in roomTiles } ||
                        world.activeNpcs.any { it.coords in roomTiles },
                    "$puzzle presentation did not return after a clean reset.",
                )
                runtime.cleanup(instance)
            }
    }

    @Test
    fun correctObeliskRemainsLitAndDuplicateCombatCallbacksAreIgnored() {
        val seed =
            (0L..1000L).first { candidate ->
                val rooms = ScabarasEncounter(candidate, 1).solutions.sideRooms
                rooms.indexOf(ScabarasPuzzle.OBELISKS) % 2 == 0
            }
        val world = FakeScabarasLiveWorld()
        val runtime = ToaScabarasLiveRuntime(world)
        val instance = InstanceId(43)
        runtime.initialize(instance, seed, partySize = 1)
        val snapshot = assertNotNull(runtime.snapshot(instance))
        val roomIndex = snapshot.solutions.sideRooms.indexOf(ScabarasPuzzle.OBELISKS)
        val base = ScabarasLiveGeometry.PUZZLE_BASES[roomIndex]
        val first = snapshot.solutions.obeliskOrder.first()

        val hit =
            runtime.onNpcHit(
                instance,
                ScabarasRevision240RuntimeLayout.obelisks.attackableNpc,
                world.coord(base + ScabarasLiveGeometry.OBELISK_OFFSETS[first]),
            )
        assertTrue(hit.handled)
        assertTrue(hit.cancelNpcDamage)
        assertEquals(1, assertNotNull(runtime.snapshot(instance)).obeliskProgress)

        repeat(20) { runtime.tick(instance) }
        assertEquals(1, assertNotNull(runtime.snapshot(instance)).obeliskProgress)
        assertEquals(
            ScabarasRevision240RuntimeLayout.obelisks.hitNpc,
            world.activeNpcs.single { it.obeliskIndex == first }.visibleId,
        )

        val duplicate =
            runtime.onNpcHit(
                instance,
                ScabarasRevision240RuntimeLayout.obelisks.attackableNpc,
                world.coord(base + ScabarasLiveGeometry.OBELISK_OFFSETS[first]),
            )
        assertFalse(duplicate.handled)
        assertEquals(1, assertNotNull(runtime.snapshot(instance)).obeliskProgress)
        assertTrue(
            runtime
                .onNpcKill(
                    instance,
                    ScabarasRevision240RuntimeLayout.obelisks.hitNpc,
                    world.coord(base + ScabarasLiveGeometry.OBELISK_OFFSETS[first]),
                )
                .handled
                .not()
        )
    }

    @Test
    fun scarabsScaleToEightPlayersAndCleanupIsPerInstanceAndIdempotent() {
        val world = FakeScabarasLiveWorld()
        val runtime = ToaScabarasLiveRuntime(world)
        val first = InstanceId(44)
        val second = InstanceId(45)
        runtime.initialize(first, seed = 1L, partySize = 8)
        runtime.initialize(second, seed = 2L, partySize = 1)

        repeat(60) { runtime.tick(first) }
        val firstSnapshot = assertNotNull(runtime.snapshot(first))
        assertEquals(14, firstSnapshot.activeNpcCount)
        assertEquals(
            8,
            world.activeNpcs.count {
                it.instanceId == first && it.kind == ScabarasLiveNpcKind.RANGED_SCARAB
            },
        )
        ScabarasLane.entries.forEach { lane ->
            assertEquals(
                4,
                world.activeNpcs.count {
                    it.instanceId == first &&
                        it.kind == ScabarasLiveNpcKind.RANGED_SCARAB &&
                        it.lane == lane
                },
            )
        }

        val scarab =
            world.activeNpcs.first {
                it.instanceId == first && it.kind == ScabarasLiveNpcKind.RANGED_SCARAB
            }
        val kill = runtime.onNpcKill(first, scarab.visibleId, scarab.coords)
        assertTrue(kill.handled)
        assertEquals(ScabarasLiveEventKind.NPC_KILLED, kill.events.single().kind)
        assertEquals(13, assertNotNull(runtime.snapshot(first)).activeNpcCount)

        val cleanup = runtime.cleanup(first)
        assertTrue(cleanup.handled)
        assertEquals(ScabarasLiveEventKind.CLEANED_UP, cleanup.events.single().kind)
        assertNull(runtime.snapshot(first))
        assertNotNull(runtime.snapshot(second))
        assertFalse(runtime.cleanup(first).handled)
        assertTrue(world.activeLocs.values.none { it.instanceId == first })
        assertTrue(world.activeNpcs.none { it.instanceId == first && it !== scarab })
    }

    private fun solveSideRoom(
        runtime: ToaScabarasLiveRuntime,
        instance: InstanceId,
        puzzle: ScabarasPuzzle,
        base: ScabarasTile,
    ): List<ScabarasLiveEvent> =
        when (puzzle) {
            ScabarasPuzzle.SEQUENCE -> {
                val requested =
                    runtime.onObjectInteraction(
                        instance,
                        ScabarasLiveObjectAction.PUSH,
                        ScabarasRevision240RuntimeLayout.sequenceCache.buttonLoc,
                        worldCoord(base + ScabarasLiveGeometry.BUTTON_OFFSET),
                    )
                val sequence =
                    requested.events
                        .single { it.kind == ScabarasLiveEventKind.SEQUENCE_REQUESTED }
                        .tiles
                repeat(sequence.size) { runtime.tick(instance) }
                sequence.flatMapIndexed { index, tile ->
                    val result =
                        runtime.onTileStep(
                            instance,
                            worldCoord(base + ScabarasLiveGeometry.MEMORY_OFFSETS[tile]),
                        )
                    if (index == sequence.lastIndex) result.events else emptyList()
                }
            }
            ScabarasPuzzle.LIGHTS -> {
                val solution = assertNotNull(runtime.snapshot(instance)).solutions.lightPresses
                solution.flatMapIndexed { index, tile ->
                    val result =
                        runtime.onTileStep(
                            instance,
                            worldCoord(base + ScabarasLiveGeometry.LIGHT_OFFSETS[tile]),
                        )
                    if (index == solution.lastIndex) result.events else emptyList()
                }
            }
            ScabarasPuzzle.OBELISKS -> {
                val solution = assertNotNull(runtime.snapshot(instance)).solutions.obeliskOrder
                solution.flatMapIndexed { index, obelisk ->
                    val result =
                        runtime.onNpcHit(
                            instance,
                            ScabarasRevision240RuntimeLayout.obelisks.attackableNpc,
                            worldCoord(base + ScabarasLiveGeometry.OBELISK_OFFSETS[obelisk]),
                        )
                    if (index == solution.lastIndex) result.events else emptyList()
                }
            }
            ScabarasPuzzle.ADDITION -> {
                val solution = assertNotNull(runtime.snapshot(instance)).solutions.additionTiles
                val inspected =
                    runtime.onObjectInteraction(
                        instance,
                        ScabarasLiveObjectAction.INSPECT,
                        ScabarasRevision240RuntimeLayout.ADDITION_TABLET_LOC,
                        worldCoord(base + ScabarasLiveGeometry.BUTTON_OFFSET),
                    )
                assertEquals(
                    assertNotNull(runtime.snapshot(instance)).solutions.additionTarget,
                    inspected.events.single().value,
                )
                solution.flatMapIndexed { index, value ->
                    val result =
                        runtime.onTileStep(
                            instance,
                            worldCoord(
                                base + ScabarasLiveGeometry.SUM_OFFSETS.getValue(value).first()
                            ),
                        )
                    if (index == solution.lastIndex) result.events else emptyList()
                }
            }
            ScabarasPuzzle.MATCHING -> error("Matching is not a side-room puzzle.")
        }

    private fun solveMatching(
        runtime: ToaScabarasLiveRuntime,
        instance: InstanceId,
        solutions: ScabarasSolutions,
    ): ScabarasLiveResult {
        var last = ScabarasLiveResult(false, false)
        val remaining = (0..8).filterNot(solutions.initiallySolvedMatchingSymbols::contains)
        remaining.forEach { symbol ->
            ScabarasMatchingBoard.entries.forEach { board ->
                val symbols =
                    when (board) {
                        ScabarasMatchingBoard.NORTH -> solutions.northMatchingSymbols
                        ScabarasMatchingBoard.SOUTH -> solutions.southMatchingSymbols
                    }
                val tile = symbols.indexOf(symbol)
                last =
                    runtime.onObjectInteraction(
                        instance,
                        ScabarasLiveObjectAction.ACTIVATE,
                        ScabarasRevision240RuntimeLayout.matchingTiles[symbol].hiddenButtonLoc,
                        worldCoord(
                            ScabarasLiveGeometry.MATCHING_BASES.getValue(board) +
                                ScabarasLiveGeometry.MATCHING_OFFSETS[tile]
                        ),
                    )
            }
        }
        return last
    }

    private fun assertLoc(
        world: FakeScabarasLiveWorld,
        id: Int,
        local: ScabarasTile,
        shape: LocShape,
    ) {
        assertTrue(
            world.activeLocs.values.any {
                it.id == id && it.coords == world.coord(local) && it.shape == shape
            },
            "Missing loc $id at $local with shape $shape.",
        )
    }

    private fun roomPresentationTiles(
        world: FakeScabarasLiveWorld,
        puzzle: ScabarasPuzzle,
        base: ScabarasTile,
    ): Set<CoordGrid> =
        when (puzzle) {
            ScabarasPuzzle.SEQUENCE ->
                buildSet {
                    add(world.coord(base + ScabarasLiveGeometry.BUTTON_OFFSET))
                    ScabarasLiveGeometry.MEMORY_OFFSETS.forEach { add(world.coord(base + it)) }
                }
            ScabarasPuzzle.LIGHTS ->
                ScabarasLiveGeometry.LIGHT_OFFSETS.mapTo(linkedSetOf()) { world.coord(base + it) }
            ScabarasPuzzle.OBELISKS ->
                ScabarasLiveGeometry.OBELISK_OFFSETS.mapTo(linkedSetOf()) { world.coord(base + it) }
            ScabarasPuzzle.ADDITION ->
                buildSet {
                    add(world.coord(base + ScabarasLiveGeometry.BUTTON_OFFSET))
                    ScabarasLiveGeometry.SUM_OFFSETS.values.flatten().forEach {
                        add(world.coord(base + it))
                    }
                }
            ScabarasPuzzle.MATCHING -> emptySet()
        }

    private fun worldCoord(local: ScabarasTile): CoordGrid =
        CoordGrid(WORLD_BASE_X + local.x, WORLD_BASE_Z + local.z, WORLD_LEVEL)

    private companion object {
        const val WORLD_BASE_X = 6400
        const val WORLD_BASE_Z = 6400
        const val WORLD_LEVEL = 0
    }

    private class FakeScabarasLiveWorld : ScabarasLiveWorld {
        val activeLocs = linkedMapOf<Pair<CoordGrid, LocShape>, ScabarasLiveLoc>()
        val activeNpcs = mutableListOf<ScabarasLiveNpc>()

        fun coord(local: ScabarasTile): CoordGrid =
            CoordGrid(WORLD_BASE_X + local.x, WORLD_BASE_Z + local.z, WORLD_LEVEL)

        override fun resolve(instanceId: InstanceId, local: ScabarasTile): CoordGrid = coord(local)

        override fun spawnLoc(loc: ScabarasLiveLoc): Boolean {
            activeLocs[loc.coords to loc.shape] = loc
            return true
        }

        override fun removeLoc(loc: ScabarasLiveLoc): Boolean =
            activeLocs.remove(loc.coords to loc.shape) != null

        override fun spawnNpc(npc: ScabarasLiveNpc): Npc? {
            activeNpcs += npc
            return null
        }

        override fun transformNpc(npc: ScabarasLiveNpc, id: Int) = Unit

        override fun removeNpc(npc: ScabarasLiveNpc) {
            activeNpcs.remove(npc)
        }
    }
}

private operator fun ScabarasTile.plus(offset: ScabarasTileOffset): ScabarasTile =
    ScabarasTile(x + offset.x, z + offset.z)

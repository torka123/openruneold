package org.rsmod.content.minigames.toa

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ToaHetLiveRuntimeTest {
    @Test
    fun donorRoomRotationIsTranslatedOntoRevision240Families() {
        assertEquals(listOf(47, 51, 54, 47, 51), HetLiveRoomSets.all.map { it.locs.size })
        assertEquals(42, HetLiveOrbPaths.all.size)
        assertTrue(
            HetLiveRoomSets.all
                .flatMap { it.locs }
                .all { HetRevision240RuntimeLayout.isPlacementSafe(it.tile) }
        )

        val runtimeIds =
            HetLiveRoomSets.all
                .flatMap { room ->
                    room.locs.flatMap { listOf(it.kind.settledLocId, it.kind.transitionLocId) }
                }
                .toSet()
        assertEquals(
            setOf(45455, 45456, 45458, 45459, 45460, 45461, 45462, 45463, 45464, 45465),
            runtimeIds,
        )
        assertEquals(
            HetTile(3676, 5279),
            HetRevision240RuntimeLayout.sourceAbsolute(HetLiveAssets.CASTER_TILE),
        )
        assertEquals(
            HetTile(3682, 5279),
            HetRevision240RuntimeLayout.sourceAbsolute(HetLiveAssets.SHIELD_TILE),
        )
        assertEquals(
            HetTile(3679, 5279),
            HetRevision240RuntimeLayout.sourceAbsolute(HetLiveAssets.SEAL_TILE),
        )

        HetLiveRoomSets.all.indices.forEach { index ->
            val runtime = ToaHetLiveRuntime(seed = index.toLong())
            runtime.initialize()
            runtime.tick(1)
            runtime.tick(2)
            assertEquals(index, runtime.snapshot().roomSetIndex)
            assertEquals(HetLivePhase.ACTIVE, runtime.snapshot().phase)
        }
    }

    @Test
    fun initializeSpawnsCoreAndSeedSelectedRisingRoom() {
        val runtime = ToaHetLiveRuntime(seed = 7)
        val events = runtime.initialize(0)
        val spawns = events.filterIsInstance<HetLiveEvent.SpawnLoc>().map { it.loc }

        assertEquals(2, runtime.snapshot().roomSetIndex)
        assertEquals(HetLivePhase.RISING, runtime.snapshot().phase)
        assertTrue(spawns.any { it.locId == 45486 && it.tile == HetTile(28, 31) && it.angle == 1 })
        assertTrue(spawns.any { it.locId == 45485 && it.tile == HetTile(34, 31) && it.angle == 3 })
        assertTrue(
            events.filterIsInstance<HetLiveEvent.SpawnNpc>().any {
                it.npcId == HetLiveAssets.SEAL_NPC && it.tile == HetTile(31, 31)
            }
        )
        assertTrue(spawns.any { it.locId in setOf(45459, 45461, 45463, 45465) })

        runtime.tick(1)
        val settled = runtime.tick(2)
        assertEquals(HetLivePhase.ACTIVE, runtime.snapshot().phase)
        assertTrue(settled.any { it is HetLiveEvent.ReplaceLoc })
        assertTrue(
            runtime.snapshot().activeLocs.values.none {
                it.locId in setOf(45459, 45461, 45463, 45465)
            }
        )
    }

    @Test
    fun movableMirrorsAndTieredBarriersOwnTheirLiveState() {
        val runtime = ToaHetLiveRuntime(seed = 0, partyMemberIds = setOf(42L))
        runtime.initialize()
        runtime.tick(1)
        runtime.tick(2)

        val pickUp =
            runtime.objectInteraction(
                HetLiveObjectRequest(
                    actorId = 42,
                    locId = 45455,
                    tile = HetTile(27, 23),
                    action = HetLiveObjectAction.PICK_UP,
                )
            )
        assertTrue(
            pickUp.any { it == HetLiveEvent.MirrorItemDelta(42, HetLiveAssets.MIRROR_ITEM, 1) }
        )
        assertEquals(1, runtime.snapshot().heldMirrorCounts[42])

        val placed =
            runtime.placeMirror(
                HetLivePlaceMirrorRequest(42, HetTile(24, 32), HetMirrorDirection.NORTH_EAST)
            )
        assertEquals(
            listOf(HetLiveEvent.PlayerAnimation(42, HetLiveAssets.PICK_OR_PLACE_ANIMATION)),
            placed,
        )
        assertEquals(1, runtime.snapshot().heldMirrorCounts[42])
        assertFalse(HetTile(24, 32) in runtime.snapshot().activeLocs)

        val placementCommit = runtime.tick(3)
        assertTrue(
            placementCommit.any { it is HetLiveEvent.SpawnLoc && it.loc.tile == HetTile(24, 32) }
        )
        assertEquals(null, runtime.snapshot().heldMirrorCounts[42])
        runtime.objectInteraction(
            HetLiveObjectRequest(42, 45455, HetTile(24, 32), HetLiveObjectAction.ROTATE_CLOCKWISE)
        )
        val push =
            runtime.objectInteraction(
                HetLiveObjectRequest(
                    42,
                    45455,
                    HetTile(24, 32),
                    HetLiveObjectAction.PUSH,
                    pushDirection = HetDirection.EAST,
                )
            )
        assertEquals(listOf(HetLiveEvent.PlayerAnimation(42, HetLiveAssets.PUSH_ANIMATION)), push)
        assertTrue(HetTile(24, 32) in runtime.snapshot().activeLocs)
        assertFalse(HetTile(25, 32) in runtime.snapshot().activeLocs)

        val pushCommit = runtime.tick(4)
        assertTrue(pushCommit.any { it is HetLiveEvent.ClearLoc && it.loc.tile == HetTile(24, 32) })
        assertEquals(
            HetMirrorDirection.SOUTH_EAST.ordinal,
            runtime.snapshot().activeLocs[HetTile(25, 32)]?.angle,
        )
        assertFalse(HetTile(24, 32) in runtime.snapshot().activeLocs)

        val bronze =
            runtime.itemOnObject(
                HetLiveItemOnObjectRequest(42, 45464, HetTile(23, 29), HetPickaxeTier.BRONZE)
            )
        assertEquals(6, bronze.filterIsInstance<HetLiveEvent.BarrierWorked>().single().remaining)
        val dragon =
            runtime.itemOnObject(
                HetLiveItemOnObjectRequest(42, 45464, HetTile(23, 29), HetPickaxeTier.DRAGON)
            )
        assertEquals(0, dragon.filterIsInstance<HetLiveEvent.BarrierWorked>().single().remaining)
        assertEquals(45464, runtime.snapshot().activeLocs[HetTile(23, 29)]?.locId)
        runtime.tick(5)
        assertEquals(45464, runtime.snapshot().activeLocs[HetTile(23, 29)]?.locId)
        val wallCommit = runtime.tick(6)
        assertTrue(wallCommit.any { it is HetLiveEvent.ReplaceLoc && it.loc.locId == 45466 })
        assertEquals(45466, runtime.snapshot().activeLocs[HetTile(23, 29)]?.locId)
    }

    @Test
    fun successfulPushMovesOnlyTheMirrorOneTickLater() {
        val runtime = ToaHetLiveRuntime(seed = 0, partyMemberIds = setOf(42L))
        runtime.initialize()
        runtime.tick(1)
        runtime.tick(2)
        runtime.objectInteraction(
            HetLiveObjectRequest(
                actorId = 42,
                locId = 45455,
                tile = HetTile(27, 23),
                action = HetLiveObjectAction.PICK_UP,
            )
        )
        runtime.placeMirror(
            HetLivePlaceMirrorRequest(42, HetTile(24, 32), HetMirrorDirection.NORTH_EAST)
        )
        runtime.tick(3)

        val successful =
            runtime.objectInteraction(
                HetLiveObjectRequest(
                    actorId = 42,
                    locId = 45455,
                    tile = HetTile(24, 32),
                    action = HetLiveObjectAction.PUSH,
                    actorTile = HetTile(23, 32),
                    pushDirection = HetDirection.EAST,
                )
            )
        assertEquals(
            listOf(HetLiveEvent.PlayerAnimation(42, HetLiveAssets.PUSH_ANIMATION)),
            successful,
        )
        assertTrue(HetTile(24, 32) in runtime.snapshot().activeLocs)
        assertFalse(HetTile(25, 32) in runtime.snapshot().activeLocs)

        val commit = runtime.tick(4)
        assertTrue(commit.any { it is HetLiveEvent.ClearLoc && it.loc.tile == HetTile(24, 32) })
        assertTrue(commit.any { it is HetLiveEvent.SpawnLoc && it.loc.tile == HetTile(25, 32) })
        assertFalse(HetTile(24, 32) in runtime.snapshot().activeLocs)
        assertTrue(HetTile(25, 32) in runtime.snapshot().activeLocs)

        val rejected =
            runtime.objectInteraction(
                HetLiveObjectRequest(
                    actorId = 42,
                    locId = 45455,
                    tile = HetTile(25, 32),
                    action = HetLiveObjectAction.PUSH,
                    actorTile = HetTile(24, 32),
                    pushDirection = null,
                )
            )
        assertEquals(
            HetLiveEvent.Rejected(42, HetLiveRejectReason.DESTINATION_BLOCKED),
            rejected.single(),
        )
    }

    @Test
    fun dirtyFixedMirrorCanOnlyBeCleanedThroughItsCacheAction() {
        val scaling = HetGroupSealScaling { HetSealHitpoints.SOLO + 96 * (it - 1) }
        val runtime =
            (0L..64L)
                .map { seed ->
                    ToaHetLiveRuntime(
                            seed = seed,
                            partySize = 2,
                            groupSealScaling = scaling,
                            roomSets = listOf(solvableRoom),
                        )
                        .also {
                            it.initialize()
                            it.tick(1)
                            it.tick(2)
                        }
                }
                .first { candidate ->
                    candidate.snapshot().activeLocs.values.any { it.locId == 45457 }
                }
        val dirty = runtime.snapshot().activeLocs.values.first { it.locId == 45457 }
        val events =
            runtime.objectInteraction(
                HetLiveObjectRequest(1, 45457, dirty.tile, HetLiveObjectAction.CLEAN)
            )

        assertTrue(events.any { it is HetLiveEvent.PlayerAnimation && it.sequenceId == 827 })
        assertEquals(45456, runtime.snapshot().activeLocs[dirty.tile]?.locId)
    }

    @Test
    fun beamUsesFullShieldFootprintAndFollowsTheProvenShieldTimeline() {
        val runtime =
            ToaHetLiveRuntime(
                seed = 0,
                roomSets = listOf(solvableRoom, alternateSolvableRoom),
                orbPaths = listOf(HetOrbPath(HetTile(23, 37), HetDirection.EAST)),
            )
        runtime.initialize()
        advance(runtime, 1, 9)
        val beamTick = runtime.tick(10, mapOf(55L to HetTile(27, 32)))
        val beam = beamTick.filterIsInstance<HetLiveEvent.Beam>().single()
        val lastGraphic = beam.graphics.last()

        assertEquals(HetBeamEnd.HIT_SHIELD, beam.trace.end)
        assertEquals(HetLivePhase.EXPOSING, runtime.snapshot().phase)
        assertFalse(HetLiveEvent.ShieldHit in beamTick)
        assertEquals(
            if (beam.graphics.lastIndex > 0) beam.graphics.lastIndex else 1,
            lastGraphic.delay,
        )
        if (beam.graphics.size > 1) {
            assertEquals(beam.graphics[beam.graphics.lastIndex - 1].delay, lastGraphic.delay)
        }
        assertTrue(
            beamTick.filterIsInstance<HetLiveEvent.DamagePlayer>().any {
                it.actorId == 55L && it.source == HetLiveDamageSource.BEAM && it.delay == 1
            }
        )

        val impact = runtime.tick(11)
        assertTrue(HetLiveEvent.ShieldHit in impact)
        assertTrue(
            impact.filterIsInstance<HetLiveEvent.Graphic>().any {
                it.spotanimId == HetLiveAssets.SHIELD_SUCCESS_GRAPHIC &&
                    it.tile == HetLiveAssets.SHIELD_HIT_TILE &&
                    it.height == 254
            }
        )
        assertTrue(
            impact.filterIsInstance<HetLiveEvent.Sound>().any {
                it.soundId == HetLiveAssets.SHIELD_HIT_SOUND
            }
        )
        assertTrue(
            HetLiveEvent.CameraShake(axis = 2, random = 2, amplitude = 10, rate = 10) in impact
        )
        assertEquals(HetLivePhase.EXPOSING, runtime.snapshot().phase)

        val reveal = runtime.tick(12)
        assertEquals(HetLivePhase.VULNERABLE, runtime.snapshot().phase)
        assertEquals(
            2,
            runtime.snapshot().activeLocs.size,
            "Only caster and shield locs remain during exposure",
        )
        assertTrue(
            reveal.any {
                it is HetLiveEvent.TransformNpc && it.npcId == HetLiveAssets.VULNERABLE_SEAL_NPC
            }
        )
        assertTrue(
            reveal.filterIsInstance<HetLiveEvent.ClearLoc>().isNotEmpty(),
            "The old layout must be replaced with explicit empty locs for RuneLite to clear it.",
        )
        assertEquals(
            HetLiveAssets.VULNERABLE_SEAL_NPC,
            HetLivePhase.VULNERABLE.sealStateVarpValue(),
        )
        assertEquals(HetLiveAssets.SEAL_NPC, HetLivePhase.RISING.sealStateVarpValue())
        assertEquals(-1, HetLivePhase.COMPLETE.sealStateVarpValue())

        runtime.tick(13)
        assertTrue(HetLiveEvent.CameraReset in runtime.tick(14))
        advance(runtime, 15, 35)
        val rotation = runtime.tick(36)
        assertEquals(1, runtime.snapshot().roomSetIndex)
        assertEquals(HetLivePhase.RISING, runtime.snapshot().phase)
        assertTrue(
            rotation.any { it is HetLiveEvent.TransformNpc && it.npcId == HetLiveAssets.SEAL_NPC }
        )
        assertTrue(rotation.any { it is HetLiveEvent.SpawnLoc && it.loc.locId == 45455 })
    }

    @Test
    fun vulnerableSealMiningEmitsChallengeCompleteExactlyOnce() {
        val runtime =
            ToaHetLiveRuntime(
                seed = 0,
                partyMemberIds = setOf(99L),
                roomSets = listOf(solvableRoom),
                orbPaths = listOf(HetOrbPath(HetTile(23, 37), HetDirection.EAST)),
            )
        runtime.initialize()
        advance(runtime, 1, 12)

        val allEvents = mutableListOf<HetLiveEvent>()
        var ticket = 0
        while (runtime.snapshot().sealHitpoints > 0) {
            allEvents +=
                runtime.itemOnObject(
                    HetLiveItemOnObjectRequest(
                        actorId = 99,
                        targetLocId = 45489,
                        targetTile = HetLiveAssets.SEAL_TILE,
                        pickaxeTier = HetPickaxeTier.DRAGON,
                        miningLevel = 99,
                        rngTicket = ticket++,
                    )
                )
        }
        assertEquals(1, allEvents.count { it == HetLiveEvent.ChallengeComplete })
        assertEquals(HetLivePhase.COMPLETE, runtime.snapshot().phase)
        assertEquals(0, runtime.snapshot().sealHitpoints)
        assertTrue(allEvents.any { it is HetLiveEvent.RemoveAllMirrorItems && it.actorId == 99L })

        val repeated = runtime.mineSeal(99, HetPickaxeTier.DRAGON, 99, 0)
        assertEquals(
            HetLiveEvent.Rejected(99, HetLiveRejectReason.ALREADY_COMPLETE),
            repeated.single(),
        )
    }

    @Test
    fun fallingOrbsDamageOnOccupiedTilesAndCleanupIsIdempotent() {
        val runtime =
            ToaHetLiveRuntime(
                seed = 0,
                partyMemberIds = setOf(7L),
                orbPaths = listOf(HetOrbPath(HetTile(23, 37), HetDirection.EAST)),
            )
        runtime.initialize()
        advance(runtime, 1, 6)
        val telegraph = runtime.tick(7)
        assertEquals(2, telegraph.filterIsInstance<HetLiveEvent.FallingOrbTelegraph>().size)
        runtime.tick(8)
        runtime.tick(9)
        runtime.tick(10)
        val activation = runtime.tick(11)
        assertEquals(2, activation.filterIsInstance<HetLiveEvent.FallingOrbActivated>().size)

        val impact = runtime.tick(12, mapOf(7L to HetTile(23, 37)))
        assertEquals(
            2,
            impact.filterIsInstance<HetLiveEvent.DamagePlayer>().count {
                it.source == HetLiveDamageSource.FALLING_ORB
            },
        )
        assertEquals(2, impact.filterIsInstance<HetLiveEvent.FallingOrbExploded>().size)
        assertEquals(0, runtime.snapshot().fallingOrbCount)

        val cleanup = runtime.cleanup()
        assertTrue(cleanup.any { it is HetLiveEvent.RemoveNpc && it.npcId in 11706..11707 })
        assertTrue(cleanup.any { it is HetLiveEvent.RemoveAllMirrorItems && it.actorId == 7L })
        assertEquals(HetLivePhase.CLEANED, runtime.snapshot().phase)
        assertTrue(runtime.cleanup().isEmpty())
    }

    private fun advance(runtime: ToaHetLiveRuntime, from: Int, through: Int) {
        for (cycle in from..through) runtime.tick(cycle)
    }

    private companion object {
        val solvableRoom =
            HetLiveRoomSetSpec(
                listOf(
                    HetRoomLocSpec(HetRoomLocKind.FIXED_MIRROR, HetTile(25, 32), 0),
                    HetRoomLocSpec(HetRoomLocKind.FIXED_MIRROR, HetTile(25, 36), 1),
                    HetRoomLocSpec(HetRoomLocKind.FIXED_MIRROR, HetTile(35, 36), 2),
                    HetRoomLocSpec(HetRoomLocKind.MOVABLE_MIRROR, HetTile(27, 23), 1),
                )
            )

        val alternateSolvableRoom =
            HetLiveRoomSetSpec(
                listOf(
                    HetRoomLocSpec(HetRoomLocKind.FIXED_MIRROR, HetTile(25, 32), 0),
                    HetRoomLocSpec(HetRoomLocKind.FIXED_MIRROR, HetTile(25, 36), 1),
                    HetRoomLocSpec(HetRoomLocKind.FIXED_MIRROR, HetTile(35, 36), 2),
                    HetRoomLocSpec(HetRoomLocKind.MOVABLE_MIRROR, HetTile(27, 41), 3),
                )
            )
    }
}

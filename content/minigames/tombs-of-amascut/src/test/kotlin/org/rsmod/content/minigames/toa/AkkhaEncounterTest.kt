package org.rsmod.content.minigames.toa

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.hit.HitType
import org.rsmod.map.CoordGrid

class AkkhaEncounterTest {
    private fun encounter(seed: Long = 42) = AkkhaEncounter(UUID(0, seed), seed)

    @Test
    fun `revision 240 asset ranges are complete`() {
        assertEquals((11789..11805).toSet(), AkkhaAssets.npcIds)
        assertEquals((9760..9794).toSet(), AkkhaAssets.sequenceIds)
        assertEquals((2253..2260).toSet(), AkkhaAssets.spotanimIds)
        assertEquals(11805, AkkhaAssets.HEADBAR)
        assertEquals(36, AkkhaAssets.SHADOW_TIMER_HEADBAR)
        assertEquals(100, AkkhaAssets.SHADOW_TIMER_SEGMENTS)
        assertEquals(9776, AkkhaSpecial.DETONATE.spearAnimation)
        assertEquals(9777, AkkhaSpecial.MEMORY_BLAST.spearAnimation)
        assertEquals(9778, AkkhaSpecial.TRAILING_ORBS.spearAnimation)
        assertEquals(9779, AkkhaAssets.FINAL_STAND_SPEAR)
    }

    @Test
    fun `shadow timer fills charge headbar upward over fifty active ticks`() {
        assertEquals(0, akkhaShadowTimerFill(0, 50))
        assertEquals(50, akkhaShadowTimerFill(25, 50))
        assertEquals(100, akkhaShadowTimerFill(50, 50))
    }

    @Test
    fun `shadow tint identifies attackable and immune quadrants at every threshold`() {
        val state =
            encounter().apply {
                startIntro()
                startNormalCombat()
            }
        assertTrue(
            AkkhaQuadrant.entries.all {
                AkkhaShadowVisuals.tint(state, it) == AkkhaShadowVisuals.IMMUNE
            }
        )

        state.queueCrossedThresholds(400, 320, 400)
        assertEquals(80, state.beginNextShadow(10))
        assertTrue(
            AkkhaQuadrant.entries.all {
                AkkhaShadowVisuals.tint(state, it) == AkkhaShadowVisuals.ATTACKABLE
            }
        )

        assertTrue(state.completeShadow(AkkhaQuadrant.BURN, 20))
        assertTrue(
            AkkhaQuadrant.entries.all {
                AkkhaShadowVisuals.tint(state, it) == AkkhaShadowVisuals.IMMUNE
            }
        )
        assertTrue(state.confirmLure(AkkhaQuadrant.BURN))
        state.queueCrossedThresholds(320, 240, 400)
        assertEquals(60, state.beginNextShadow(30))
        assertEquals(AkkhaShadowVisuals.IMMUNE, AkkhaShadowVisuals.tint(state, AkkhaQuadrant.BURN))
        AkkhaQuadrant.entries
            .filterNot { it == AkkhaQuadrant.BURN }
            .forEach {
                assertEquals(AkkhaShadowVisuals.ATTACKABLE, AkkhaShadowVisuals.tint(state, it))
            }
    }

    @Test
    fun `shadow tint packet values match the revision 240 capture`() {
        assertEquals(AkkhaShadowTint(0, 0, 255, 255, 255, 0), AkkhaShadowVisuals.ATTACKABLE)
        assertEquals(AkkhaShadowTint(0, 600, 0, 0, 106, 40), AkkhaShadowVisuals.IMMUNE)
    }

    @Test
    fun `memory hide duration reveals on the captured reappearance tick`() {
        assertEquals(21, akkhaMemoryRepositoryHideDuration(104, 126))
    }

    @Test
    fun `removed Akkha actor is never packed as a hit source`() {
        assertTrue(akkhaHitCanUseNpcSource(isSlotAssigned = true))
        assertFalse(akkhaHitCanUseNpcSource(isSlotAssigned = false))
    }

    @Test
    fun `normal forms and style order match current combat loop`() {
        assertEquals(listOf(11790, 11791, 11792), AkkhaStyle.entries.map { it.npcId })
        assertEquals(0, AkkhaTuning.INTRO_TARGETABLE_DELAY.value)
        assertEquals(AkkhaStyle.RANGED, AkkhaStyle.MELEE.next())
        assertEquals(AkkhaStyle.MAGIC, AkkhaStyle.RANGED.next())
        assertEquals(AkkhaStyle.MELEE, AkkhaStyle.MAGIC.next())
        assertEquals(1, akkhaAttackRange(AkkhaStyle.MELEE))
        assertEquals(32, akkhaAttackRange(AkkhaStyle.RANGED))
        assertEquals(32, akkhaAttackRange(AkkhaStyle.MAGIC))
        assertTrue(AkkhaTuning.ROOM_CHASE_RANGE.value > AkkhaArenaLayout.ARENA_RADIUS)
    }

    @Test
    fun `pending shadow lure forces every combat style into pursuit range`() {
        AkkhaStyle.entries.forEach { style ->
            assertEquals(1, AkkhaAggressionPolicy.attackRange(style, lureRequired = true))
            assertEquals(
                akkhaAttackRange(style),
                AkkhaAggressionPolicy.attackRange(style, lureRequired = false),
            )
        }
    }

    @Test
    fun `phase transitions and changed attack ranges rebuild retained combat interactions`() {
        assertFalse(
            AkkhaAggressionPolicy.shouldRearm(
                force = false,
                hasPlayerInteractionMode = true,
                targetMatches = true,
                interactionRangeMatches = true,
            )
        )
        assertTrue(AkkhaAggressionPolicy.shouldRearm(true, true, true, true))
        assertTrue(
            AkkhaAggressionPolicy.shouldRearm(
                force = false,
                hasPlayerInteractionMode = true,
                targetMatches = true,
                interactionRangeMatches = false,
            )
        )
    }

    @Test
    fun `one matching overhead blocks while both other styles remain legal`() {
        val state =
            encounter().apply {
                startIntro()
                startNormalCombat()
            }
        assertFalse(state.canReceiveDamage(HitType.Melee))
        assertTrue(state.canReceiveDamage(HitType.Ranged))
        assertTrue(state.canReceiveDamage(HitType.Magic))
    }

    @Test
    fun `special advances style exactly once`() {
        val state =
            encounter().apply {
                startIntro()
                startNormalCombat()
                recordEmittedAuto(1)
            }
        state.startSpecial(listOf(AkkhaSpecial.MEMORY_BLAST))
        state.finishSpecial()
        assertEquals(AkkhaStyle.RANGED, state.style)
        state.finishSpecial()
        assertEquals(AkkhaStyle.RANGED, state.style)
    }

    @Test
    fun `special does not rotate style without a regular auto since the prior special`() {
        val state =
            encounter().apply {
                startIntro()
                startNormalCombat()
            }
        state.startSpecial(listOf(AkkhaSpecial.MEMORY_BLAST))
        state.finishSpecial()
        assertEquals(AkkhaStyle.MELEE, state.style)
    }

    @Test
    fun `trailing orbs are a nonblocking overlay that preserves combat`() {
        val state =
            encounter().apply {
                startIntro()
                startNormalCombat()
                recordEmittedAuto(1)
            }
        state.startTrailingOrbs(2)
        state.startSpecial(listOf(AkkhaSpecial.TRAILING_ORBS))
        assertEquals(AkkhaState.NORMAL_COMBAT, state.state)
        assertTrue(state.activeSpecials.isEmpty())
        assertEquals(AkkhaStyle.RANGED, state.style)
        assertTrue(state.trailEndsAt > 2)
    }

    @Test
    fun `oversized damage stops at one shadow threshold and cannot queue another`() {
        val state =
            encounter().apply {
                startIntro()
                startNormalCombat()
            }
        val damage = state.capDamageAtNextShadowThreshold(400, 360, 400)
        assertEquals(80, damage)
        assertEquals(listOf(80), state.queueCrossedThresholds(400, 400 - damage, 400))
        assertEquals(listOf(80), state.pendingThresholds.toList())
        assertEquals(0, state.capDamageAtNextShadowThreshold(320, 100, 400))
        assertFalse(state.completeShadow(AkkhaQuadrant.BURN, 9))
        assertEquals(80, state.beginNextShadow(10))
        assertTrue(state.completeShadow(AkkhaQuadrant.BURN, 10))
        assertEquals(AkkhaState.SHADOW_TRANSITION, state.state)
        assertFalse(state.canReceiveDamage(HitType.Ranged))
        assertFalse(state.confirmLure(AkkhaQuadrant.FREEZE))
        assertTrue(state.confirmLure(AkkhaQuadrant.BURN))
        assertEquals(AkkhaState.NORMAL_COMBAT, state.state)
        assertTrue(state.pendingThresholds.isEmpty())
        assertEquals(null, state.beginNextShadow(11))
    }

    @Test
    fun `shadow threshold can only begin and complete once`() {
        val state =
            encounter().apply {
                startIntro()
                startNormalCombat()
                queueCrossedThresholds(400, 300, 400)
            }
        assertEquals(80, state.beginNextShadow(10))
        assertEquals(null, state.beginNextShadow(10))
        assertTrue(state.completeShadow(AkkhaQuadrant.BURN, 10))
        assertFalse(state.completeShadow(AkkhaQuadrant.FREEZE, 11))
        assertEquals(setOf(AkkhaQuadrant.BURN), state.defeatedShadows)
    }

    @Test
    fun `shadow timer blast recharges without defeating a shadow or resuming Akkha`() {
        val state =
            encounter().apply {
                startIntro()
                startNormalCombat()
                queueCrossedThresholds(400, 300, 400)
            }
        assertEquals(80, state.beginNextShadow(10))
        val living = AkkhaQuadrant.entries.toSet()
        val blast = state.startShadowBlast(20, living)
        assertEquals(living, blast?.quadrants)
        assertEquals(AkkhaState.SHADOW_TRANSITION, state.state)
        assertTrue(state.defeatedShadows.isEmpty())

        state.finishShadowBlast(24)

        assertEquals(AkkhaState.SHADOW_TRANSITION, state.state)
        assertEquals(80, state.activeShadowThreshold)
        assertTrue(state.defeatedShadows.isEmpty())
        assertEquals(24 + AkkhaTuning.SHADOW_CHARGE.value, state.shadowChargeEndsAt)
    }

    @Test
    fun `one disrupted shadow leaves the other three charging until the next threshold`() {
        val state =
            encounter().apply {
                startIntro()
                startNormalCombat()
                queueCrossedThresholds(400, 300, 400)
            }
        assertEquals(80, state.beginNextShadow(10))
        AkkhaQuadrant.entries.forEach { state.shadowHeadbarActors[it] = it.ordinal + 1 }

        assertTrue(state.completeShadow(AkkhaQuadrant.BURN, 20))

        assertEquals(AkkhaState.SHADOW_TRANSITION, state.state)
        assertEquals(AkkhaQuadrant.BURN, state.pendingLureQuadrant)
        assertEquals(3, state.shadowHeadbarActors.size)
        assertFalse(AkkhaQuadrant.BURN in state.shadowHeadbarActors)
        assertEquals(10 + AkkhaTuning.SHADOW_CHARGE.value, state.shadowChargeEndsAt)
        assertTrue(state.hasChargingShadows())
        assertEquals(setOf(AkkhaQuadrant.BURN), state.defeatedShadows)
        assertFalse(state.confirmLure(AkkhaQuadrant.DARKNESS))
        assertTrue(state.confirmLure(AkkhaQuadrant.BURN))
        assertEquals(AkkhaState.NORMAL_COMBAT, state.state)
        assertTrue(state.hasChargingShadows())
    }

    @Test
    fun `a shadow killed during a blocking special still completes and cleans up`() {
        val state =
            encounter().apply {
                startIntro()
                startNormalCombat()
                queueCrossedThresholds(400, 300, 400)
            }
        assertEquals(80, state.beginNextShadow(10))

        state.startSpecial(listOf(AkkhaSpecial.DETONATE))
        assertEquals(AkkhaState.SPECIAL_ATTACK, state.state)
        assertTrue(state.completeShadow(AkkhaQuadrant.BURN, 20))
        assertEquals(AkkhaQuadrant.BURN, state.pendingLureQuadrant)

        state.finishSpecial()
        assertEquals(AkkhaState.SHADOW_TRANSITION, state.state)
        assertEquals(AkkhaQuadrant.BURN, state.pendingLureQuadrant)
    }

    @Test
    fun `shadow disruption preserves the emitted auto timestamp`() {
        val state =
            encounter().apply {
                startIntro()
                startNormalCombat()
            }

        repeat(AkkhaTuning.BASE_AUTOS_PER_SPECIAL.value) { tick -> state.recordEmittedAuto(tick) }
        assertEquals(listOf(80), state.queueCrossedThresholds(400, 320, 400))
        assertEquals(80, state.beginNextShadow(10))
        assertTrue(state.completeShadow(AkkhaQuadrant.BURN, 20))
        assertTrue(state.confirmLure(AkkhaQuadrant.BURN))

        state.recordEmittedAuto(21)
        assertEquals(listOf(60), state.queueCrossedThresholds(320, 240, 400))
        assertEquals(60, state.beginNextShadow(40))
        assertTrue(state.completeShadow(AkkhaQuadrant.FREEZE, 50))
        assertTrue(state.confirmLure(AkkhaQuadrant.FREEZE))

        assertEquals(AkkhaState.NORMAL_COMBAT, state.state)
        assertEquals(21, state.lastEmittedAutoAt)
    }

    @Test
    fun `all four thresholds run once with one combat phase between each`() {
        val state =
            encounter().apply {
                startIntro()
                startNormalCombat()
            }
        val completed = mutableListOf<Int>()
        var hitpoints = 400
        AkkhaQuadrant.entries.forEachIndexed { index, quadrant ->
            val damage = state.capDamageAtNextShadowThreshold(hitpoints, 400, 400)
            val nextHitpoints = hitpoints - damage
            assertEquals(
                listOf(AkkhaTuning.SHADOW_THRESHOLDS.value[index]),
                state.queueCrossedThresholds(hitpoints, nextHitpoints, 400),
            )
            val threshold = state.beginNextShadow(100 + index)
            completed += requireNotNull(threshold)
            val living = AkkhaQuadrant.entries.toSet()
            assertTrue(state.startShadowBlast(200 + index, living) != null)
            state.finishShadowBlast(210 + index)
            assertTrue(state.completeShadow(quadrant, 220 + index))
            assertTrue(state.confirmLure(quadrant))
            assertEquals(AkkhaState.NORMAL_COMBAT, state.state)
            hitpoints = nextHitpoints
        }
        assertEquals(listOf(80, 60, 40, 20), completed)
        assertEquals(AkkhaQuadrant.entries.toSet(), state.defeatedShadows)
        assertEquals(AkkhaState.NORMAL_COMBAT, state.state)
        assertFalse(state.hasUnresolvedShadowThresholds())
    }

    @Test
    fun `one simultaneous death reserves the threshold and used quadrants stay immune`() {
        val state =
            encounter().apply {
                startIntro()
                startNormalCombat()
                queueCrossedThresholds(400, 0, 400)
            }
        assertEquals(80, state.beginNextShadow(10))
        assertTrue(state.recordShadowDeath(AkkhaQuadrant.BURN))
        AkkhaQuadrant.entries
            .filterNot { it == AkkhaQuadrant.BURN }
            .forEach { assertFalse(state.recordShadowDeath(it)) }
        assertTrue(state.completeShadow(AkkhaQuadrant.BURN, 10))
        assertTrue(state.confirmLure(AkkhaQuadrant.BURN))
        assertFalse(state.allShadowsDefeated())
        assertTrue(state.pendingThresholds.isEmpty())
        assertTrue(state.hasUnresolvedShadowThresholds())
        assertEquals(listOf(60), state.queueCrossedThresholds(320, 240, 400))
        assertEquals(60, state.beginNextShadow(20))
        assertFalse(state.canDisruptShadow(AkkhaQuadrant.BURN))
        assertTrue(state.canDisruptShadow(AkkhaQuadrant.FREEZE))
    }

    @Test
    fun `separate raids never share shadow state`() {
        val first =
            encounter(1).apply {
                startIntro()
                startNormalCombat()
            }
        val second =
            encounter(2).apply {
                startIntro()
                startNormalCombat()
            }
        first.queueCrossedThresholds(400, 300, 400)
        first.beginNextShadow(10)
        first.completeShadow(AkkhaQuadrant.BURN, 10)
        assertEquals(setOf(AkkhaQuadrant.BURN), first.defeatedShadows)
        assertTrue(second.defeatedShadows.isEmpty())
        assertTrue(second.pendingThresholds.isEmpty())
    }

    @Test
    fun `memory iterations scale at documented path brackets`() {
        assertEquals(listOf(4, 4, 5, 5, 6, 6), (0..5).map(::memoryIterations))
    }

    @Test
    fun `memory independently reverses direction and may revisit a prior quadrant`() {
        val choices = ArrayDeque(listOf(true, false, false))
        val sequence =
            MemoryBlastController.sequence(AkkhaQuadrant.DARKNESS.ordinal, 4) {
                choices.removeFirst()
            }
        assertEquals(
            listOf(
                AkkhaQuadrant.DARKNESS,
                AkkhaQuadrant.LIGHTNING,
                AkkhaQuadrant.DARKNESS,
                AkkhaQuadrant.BURN,
            ),
            sequence,
        )
    }

    @Test
    fun `base memory follows the captured reveal resolution and reappear timeline`() {
        val normal = MemoryBlastController.schedule(100, 0, 4, 4) { true }
        val feelingSpecial = MemoryBlastController.schedule(100, 0, 4, 2) { true }
        assertTrue(MemoryBlastController.revealsFinishBeforeResolution(normal))
        assertTrue(MemoryBlastController.revealsFinishBeforeResolution(feelingSpecial))
        assertEquals(listOf(104, 106, 108, 110), normal.map { it.revealAt })
        assertEquals(listOf(112, 116, 120, 124), normal.map { it.resolvesAt })
        assertEquals(listOf(112, 114, 116, 118), feelingSpecial.map { it.resolvesAt })
        assertEquals(normal.map { it.resolvesAt }, normal.map { it.blastStartsAt })
        assertEquals(27, MemoryBlastController.duration(100, normal))
    }

    @Test
    fun `memory state is removed when the special finishes or encounter resets`() {
        val state =
            encounter().apply {
                startIntro()
                startNormalCombat()
            }
        state.memorySteps += MemoryBlastController.schedule(10, 0, 4, 3)
        state.startSpecial(listOf(AkkhaSpecial.MEMORY_BLAST))
        state.finishSpecial()
        assertTrue(state.memorySteps.isEmpty())
        state.memorySteps += MemoryBlastController.schedule(40, 1, 4, 3)
        state.reset()
        assertTrue(state.memorySteps.isEmpty())
    }

    @Test
    fun `captured floor footprint contains exactly seventy five tiles per quadrant`() {
        AkkhaQuadrant.entries.forEach { quadrant ->
            assertEquals(75, AkkhaArenaLayout.floorTiles(setOf(quadrant)).size)
        }
        assertEquals(300, AkkhaArenaLayout.floorTiles().size)
    }

    @Test
    fun `quadrant collision uses the players true tile and rejects another plane`() {
        val center = CoordGrid(100, 100, 1)
        val tile = center.translate(4, -3)
        assertEquals(
            AkkhaArenaLayout.quadrant(AkkhaArenaLayout.CENTER_X + 4, AkkhaArenaLayout.CENTER_Z - 3),
            AkkhaFloorBlastController.quadrantAt(center, tile),
        )
        assertEquals(null, AkkhaFloorBlastController.quadrantAt(center, tile.translateLevel(1)))
    }

    @Test
    fun `memory markers use one native composite graphic anchor per quadrant`() {
        val center = CoordGrid(100, 100, 1)
        val markers =
            AkkhaQuadrant.entries.associateWith { AkkhaFloorBlastController.markerTile(center, it) }
        assertEquals(4, markers.values.toSet().size)
        markers.forEach { (quadrant, marker) ->
            assertEquals(quadrant, AkkhaFloorBlastController.quadrantAt(center, marker))
            assertEquals(1, marker.level)
        }
    }

    @Test
    fun `memory blast emits every tile in the three unsafe clipped quadrants`() {
        val center = CoordGrid(100, 100, 1)
        AkkhaQuadrant.entries.forEach { safe ->
            val effects = AkkhaFloorBlastController.memoryBlastTiles(center, safe)
            assertEquals(AkkhaQuadrant.entries.filterNot { it == safe }.toSet(), effects.keys)
            assertEquals(225, effects.values.sumOf { it.size })
            effects.forEach { (quadrant, tiles) ->
                assertEquals(75, tiles.size)
                assertTrue(
                    tiles.all { AkkhaFloorBlastController.quadrantAt(center, it) == quadrant }
                )
                val delays =
                    tiles.map { AkkhaFloorBlastController.memoryBlastDelay(center, it, quadrant) }
                assertEquals(30, delays.min())
                assertEquals(66, delays.max())
            }
        }
    }

    @Test
    fun `final orbs have valid clipped floor paths in all eight directions`() {
        val center = CoordGrid(100, 100, 1)
        val expected =
            listOf(
                AkkhaOrbDirection.NORTH,
                AkkhaOrbDirection.WEST,
                AkkhaOrbDirection.SOUTH,
                AkkhaOrbDirection.EAST,
                AkkhaOrbDirection.NORTH_EAST,
                AkkhaOrbDirection.NORTH_WEST,
                AkkhaOrbDirection.SOUTH_WEST,
                AkkhaOrbDirection.SOUTH_EAST,
            )
        val spawns = (0..7).map { AkkhaFinalOrbController.spawn(center, it, 0) }
        assertEquals(expected, spawns.map { it.second })
        (0..7).forEach { lane ->
            val path = AkkhaFinalOrbController.path(center, lane, 0)
            assertTrue(path.size > 1)
            assertTrue(path.all { AkkhaFloorBlastController.quadrantAt(center, it) != null })
        }
    }

    @Test
    fun `final orb collision includes stationary and intermediate running tiles`() {
        val orb = CoordGrid(101, 100, 1)
        val next = CoordGrid(102, 100, 1)
        assertTrue(AkkhaFinalOrbController.collides(orb, next, orb, orb))
        assertTrue(
            AkkhaFinalOrbController.collides(
                orb,
                next,
                CoordGrid(100, 100, 1),
                CoordGrid(102, 100, 1),
            )
        )
        assertFalse(
            AkkhaFinalOrbController.collides(
                orb,
                next,
                CoordGrid(100, 102, 1),
                CoordGrid(102, 102, 1),
            )
        )
    }

    @Test
    fun `detonate uses a local three by three plus or feeling special X`() {
        val origin = CoordGrid(100, 100, 1)
        assertTrue(isAkkhaDetonationTile(CoordGrid(100, 101, 1), origin, false))
        assertTrue(isAkkhaDetonationTile(CoordGrid(99, 100, 1), origin, false))
        assertFalse(isAkkhaDetonationTile(CoordGrid(100, 102, 1), origin, false))
        assertFalse(isAkkhaDetonationTile(CoordGrid(101, 101, 1), origin, false))
        assertTrue(isAkkhaDetonationTile(CoordGrid(101, 101, 1), origin, true))
        assertFalse(isAkkhaDetonationTile(CoordGrid(100, 101, 1), origin, true))
        assertFalse(isAkkhaDetonationTile(origin, origin, true))
    }

    @Test
    fun `same tile detonate origin is excluded`() {
        val origin = CoordGrid(100, 100, 1)
        assertFalse(DetonateController.isRayTile(origin, origin, false))
    }

    @Test
    fun `running trail records vacated and intermediate true tiles`() {
        val old = CoordGrid(100, 100, 1)
        val now = CoordGrid(102, 100, 1)
        assertEquals(
            listOf(old, CoordGrid(101, 100, 1)),
            TrailingOrbController.traversedTiles(old, now),
        )
        assertEquals(CoordGrid(103, 100, 1), TrailingOrbController.oneTileAhead(old, now))
    }

    @Test
    fun `diagonal run trail records both traversed tiles`() {
        val old = CoordGrid(100, 100, 1)
        val now = CoordGrid(102, 102, 1)
        assertEquals(
            listOf(old, CoordGrid(101, 101, 1)),
            TrailingOrbController.traversedTiles(old, now),
        )
    }

    @Test
    fun `trailing orb overlay runs from cast plus two through cast plus twenty two`() {
        val state = encounter()
        state.startTrailingOrbs(100)
        assertEquals(102, state.trailStartsAt)
        assertEquals(122, state.trailEndsAt)
        assertEquals(2, AkkhaTuning.TRAIL_INITIAL_GRACE.value)
        assertEquals(20, AkkhaTuning.TRAIL_LIFETIME.value)
    }

    @Test
    fun `distinct overlapping trailing orbs each resolve their own collision`() {
        val state = encounter()
        val player = PlayerUid(42)
        assertTrue(state.claimTrailCollision(player, 100))
        assertTrue(state.claimTrailCollision(player, 100))
    }

    @Test
    fun `final stand restores exactly twenty percent and only accepts melee`() {
        val state =
            encounter().apply {
                startIntro()
                startNormalCombat()
                beginFinalStand()
                activateFinalStand(1)
            }
        assertEquals(200, akkhaFinalHitpoints(1_000))
        assertTrue(state.canReceiveDamage(HitType.Melee))
        assertFalse(state.canReceiveDamage(HitType.Magic))
        assertFalse(state.canReceiveDamage(HitType.Ranged))
    }

    @Test
    fun `solo final stand teleports after three positive validated hits`() {
        val state =
            encounter().apply {
                startIntro()
                startNormalCombat()
                beginFinalStand()
                activateFinalStand(1)
            }
        assertFalse(state.recordFinalStandHit(1))
        assertFalse(state.recordFinalStandHit(1))
        assertTrue(state.recordFinalStandHit(1))
        assertTrue(state.pendingFinalStandSwap != null)
    }

    @Test
    fun `final stand swaps the real actor with a seeded different decoy`() {
        val state =
            encounter(7).apply {
                startIntro()
                startNormalCombat()
                beginFinalStand()
                activateFinalStand(1)
                registerFinalStandFormation(100, 0, mapOf(1 to 101, 2 to 102, 3 to 103))
            }
        repeat(2) { assertFalse(state.recordFinalStandHit(1)) }
        assertTrue(state.recordFinalStandHit(1))
        val pending = requireNotNull(state.pendingFinalStandSwap)
        assertEquals(0, pending.fromIndex)
        assertTrue(pending.toIndex in 1..3)
        assertEquals(100, pending.outgoingBossActorKey)
        assertEquals(100 + pending.toIndex, pending.incomingBossActorKey)
        assertFalse(state.recordFinalStandHit(1))
        assertEquals(pending, state.completeFinalStandSwap())
        assertEquals(pending.incomingBossActorKey, state.finalStandBossActorKey)
        assertEquals(100, state.finalStandDecoyActorKeys[0])
    }

    @Test
    fun `final stand activation starts the retained orb grace`() {
        val state =
            encounter().apply {
                startIntro()
                startNormalCombat()
                beginFinalStand()
            }
        assertEquals(AkkhaState.FINAL_STAND_INTRO, state.state)
        state.activateFinalStand(105)
        assertEquals(AkkhaState.FINAL_STAND, state.state)
        assertEquals(105 + AkkhaTuning.FINAL_ORB_INITIAL_GRACE.value, state.nextFinalOrbAt)
    }

    @Test
    fun `team final threshold remains centralized`() {
        assertEquals(3, hitsBeforeFinalStandTeleport(1))
        assertEquals(4, hitsBeforeFinalStandTeleport(2))
        assertEquals(10, hitsBeforeFinalStandTeleport(8))
    }

    @Test
    fun `generation token rejects stale callbacks after reset and enrage`() {
        val state = encounter()
        val initial = state.token()
        state.reset()
        assertFalse(state.isCurrent(initial))
        state.startIntro()
        state.startNormalCombat()
        val normal = state.token()
        state.beginFinalStand()
        assertFalse(state.isCurrent(normal))
    }

    @Test
    fun `cleanup reset clears trail schedules and memory visibility metadata`() {
        val state = encounter()
        state.startTrailingOrbs(10)
        state.memoryHidden = true
        state.reset()
        assertEquals(-1, state.trailStartsAt)
        assertEquals(-1, state.trailEndsAt)
        assertFalse(state.memoryHidden)
        assertEquals(-1, state.memoryHideAt)
        assertEquals(-1, state.memoryReappearAt)
        assertEquals(null, state.combatQueues)
    }

    @Test
    fun `seeded special schedule is reproducible and detonate is team only`() {
        val a = encounter(99)
        val b = encounter(99)
        val first = AkkhaSpecialSchedule.select(2, false, null, a::nextInt)
        val second = AkkhaSpecialSchedule.select(2, false, null, b::nextInt)
        assertEquals(first, second)
        repeat(20) {
            assertNotEquals(
                AkkhaSpecial.DETONATE,
                AkkhaSpecialSchedule.select(1, false, null, a::nextInt).single(),
            )
        }
    }

    @Test
    fun `double trouble solo always compounds memory and trailing orbs`() {
        assertEquals(
            listOf(AkkhaSpecial.MEMORY_BLAST, AkkhaSpecial.TRAILING_ORBS),
            AkkhaSpecialSchedule.select(1, true, null) { 0 },
        )
    }

    @Test
    fun `arena geometry contains all butterfly validation endpoints`() {
        assertTrue(
            AkkhaArenaLayout.butterflyValidationEndpoints.all {
                AkkhaArenaLayout.contains(it.first, it.second)
            }
        )
        assertEquals(4, AkkhaArenaLayout.shadowAnchors.size)
    }

    @Test
    fun `trace required values are explicit and carry units and evidence`() {
        assertTrue(AkkhaTuning.traceRequired.isNotEmpty())
        AkkhaTuning.traceRequired.forEach { (_, tuned) ->
            assertEquals(AkkhaEvidenceConfidence.TRACE_REQUIRED, tuned.confidence)
            assertTrue(tuned.units.isNotBlank())
            assertTrue(tuned.evidence.isNotBlank())
        }
    }
}

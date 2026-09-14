package org.rsmod.content.minigames.toa

import dev.openrune.ServerCacheManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.rsmod.map.CoordGrid

class ToaKephriInvocationRuntimeTest {
    @Test
    fun `Dung Strike target selection is disabled at zero and rotates distinct group targets`() {
        assertEquals(emptyList(), KephriDungStrike.targetIndices(1, cursor = 0, requested = 0))
        assertEquals(listOf(0), KephriDungStrike.targetIndices(1, cursor = 0, requested = 2))
        assertEquals(listOf(2, 3), KephriDungStrike.targetIndices(4, cursor = 2, requested = 2))
        assertEquals(listOf(3, 0), KephriDungStrike.targetIndices(4, cursor = 3, requested = 2))
    }

    @Test
    fun `Dung Strike builds deterministic cardinal and diagonal walls`() {
        val center = CoordGrid(100, 100, 0)
        val east = KephriDungStrike.path(center, CoordGrid(103, 100, 0), CoordGrid(108, 100, 0))
        val diagonal = KephriDungStrike.path(center, CoordGrid(103, 103, 0), CoordGrid(108, 108, 0))

        assertEquals(6, east.size)
        assertEquals(CoordGrid(103, 100, 0), east.first())
        assertEquals(CoordGrid(108, 100, 0), east.last())
        assertEquals(CoordGrid(103, 103, 0), diagonal.first())
        assertEquals(CoordGrid(108, 108, 0), diagonal.last())
    }

    @Test
    fun `Medic swarm is slower and does not credit shield recovery`() {
        val runtime =
            KephriSwarmRuntime(
                actorKey = 1,
                healEligible = true,
                approachTile = CoordGrid(10, 10, 0),
                startsMovingAt = 5,
                medic = true,
                moveInterval = KephriInvocationRules.MEDIC_MOVE_INTERVAL,
            )

        assertTrue(runtime.medic)
        assertEquals(3, runtime.moveInterval)
        assertTrue(runtime.moveInterval > KephriSwarms.MOVE_INTERVAL)
    }

    @Test
    fun `Dung Strike forced movement stops at collision and uses donor timing`() {
        val center = CoordGrid(100, 100, 0)
        val start = CoordGrid(104, 100, 0)
        val step = KephriDungStrike.direction(center, start)
        val destination =
            KephriDungStrike.collisionSafeDestination(
                start,
                step,
                KephriDungStrike.remainingPushTiles(center, start),
            ) { from, _ ->
                from.x < 106
            }

        assertEquals(CoordGrid(106, 100, 0), destination)
        assertEquals(4, KephriDungStrike.STUN_TICKS)
        assertEquals(59, KephriDungStrike.movementClientCycles(2))
        assertEquals(89, KephriDungStrike.movementClientCycles(6))
    }

    @Test
    fun `Aerial Assault owns shield bomber profile without changing empty rules`() {
        val baseline = KephriInvocationRules.resolve(emptySet(), partySize = 1)
        val aerial =
            KephriInvocationRules.resolve(setOf(ToaInvocation.AERIAL_ASSAULT), partySize = 1)
        val center = CoordGrid(100, 100, 0)

        assertFalse(baseline.aerialAssault)
        assertTrue(aerial.aerialAssault)
        assertEquals(0, baseline.bombRadius)
        assertEquals(1, aerial.bombRadius)
        assertEquals(
            CoordGrid(109, 109, 0),
            KephriBomberScarabs.nearestSource(center, CoordGrid(108, 108, 0)),
        )
    }

    @Test
    fun `revision 240 contains and allowlists every Dung Strike asset`() {
        ToaCacheTestSupport.initRevision240()

        assertEquals("Dung", assertNotNull(ServerCacheManager.getObject(45504)).name)
        assertTrue(KephriDungStrike.PLAYER_THROW_ANIMATION in ToaAssets.sequences)
        assertTrue(KephriDungStrike.PLAYER_DUNG_MOVE_ANIMATION in ToaAssets.sequences)
        assertTrue(KephriDungStrike.DUNG_GRAPHIC in ToaAssets.spotanims)
        assertTrue(KephriDungStrike.PLAYER_FLIES_GRAPHIC in ToaAssets.spotanims)
        assertTrue(KephriDungStrike.PLAYER_STUN_GRAPHIC in ToaAssets.spotanims)
    }

    @Test
    fun levelZeroAttackCycleMatchesTrace() {
        var bombs = KephriAttackCycle.INITIAL_BOMBS
        var dung = true
        val attacks = mutableListOf<KephriAttackKind>()
        repeat(15) {
            val next = KephriAttackCycle.next(phase = 0, bombs, dung, agileCount = 0)
            attacks += next.kind
            bombs = next.attacksUntilSpecial
            dung = next.nextSpecialDung
        }
        assertEquals(
            listOf(
                KephriAttackKind.BOMB,
                KephriAttackKind.BOMB,
                KephriAttackKind.BOMB,
                KephriAttackKind.DUNG_STRIKE,
                KephriAttackKind.BOMB,
                KephriAttackKind.BOMB,
                KephriAttackKind.BOMB,
                KephriAttackKind.BOMB,
                KephriAttackKind.BOMB,
                KephriAttackKind.MASS_INCUBATION,
                KephriAttackKind.BOMB,
                KephriAttackKind.BOMB,
                KephriAttackKind.BOMB,
                KephriAttackKind.BOMB,
                KephriAttackKind.BOMB,
            ),
            attacks,
        )
    }

    @Test
    fun sixAgilesReplaceMassIncubation() {
        val decision =
            KephriAttackCycle.next(
                phase = 2,
                attacksUntilSpecial = 0,
                nextSpecialDung = false,
                agileCount = 6,
            )
        assertEquals(KephriAttackKind.DUNG_STRIKE, decision.kind)
    }

    @Test
    fun revision240BaselinePhaseAndTimingConstantsMatchTrace() {
        assertEquals(11719, KephriEncounter.ACTIVE)
        assertEquals(11720, KephriEncounter.RECOVERING)
        assertEquals(11721, KephriEncounter.FINAL)
        assertEquals(11722, KephriEncounter.DEAD)
        assertEquals(150, KephriEncounter.SHIELD_HITPOINTS)
        assertEquals(80, KephriEncounter.FINAL_HITPOINTS)
        assertEquals(3, KephriBombs.TARGET_LOCK_DELAY)
        assertEquals(4, KephriBombs.IMPACT_AFTER_LOCK)
        assertEquals(4, KephriEggs.SPAWN_DELAY)
        assertEquals(15, KephriEggs.LIFETIME)
        assertEquals(5, KephriDungStrike.TELEGRAPH_TICKS)
    }

    @Test
    fun levelZeroHitpointsScaleFromSoloThroughEightPlayers() {
        val shields = listOf(150, 285, 420, 510, 600, 690, 780, 870)
        val finals = listOf(80, 152, 224, 272, 320, 368, 416, 464)
        assertEquals(
            shields,
            (1..8).map { ToaRules.hitpoints(KephriEncounter.SHIELD_HITPOINTS, it, 0, 0) },
        )
        assertEquals(
            finals,
            (1..8).map { ToaRules.hitpoints(KephriEncounter.FINAL_HITPOINTS, it, 0, 0) },
        )
    }

    @Test
    fun massIncubationAndScarabProfilesMatchLevelZeroEvidence() {
        assertEquals(12, KephriEggs.offsets.size)
        assertEquals(2, KephriEggs.darkEggCount(1, livelyLarvae = false, roll = 0))
        assertEquals(6, KephriEggs.MASS_INCUBATION_AGILE_LIMIT)
        assertEquals(3, KephriScarabRanger.ATTACK_INTERVAL)
        assertEquals(6, KephriScarabRanger.POISON_DAMAGE)
        assertEquals(7, KephriScarabMelee.ATTACK_INTERVAL)
        assertEquals(6, KephriScarabMelee.HEAL_INTERVAL)
        assertEquals(2, KephriScarabMelee.healAmount(40, 40))
        assertEquals(8, KephriScarabMager.CHARGE_STEPS)
        assertEquals(3, KephriScarabMager.CHARGE_INTERVAL)
        assertEquals(40, KephriScarabMager.PRAYER_DAMAGE)
        assertEquals(4, KephriScarabAgile.ATTACK_INTERVAL)
        assertEquals(5, KephriScarabAgile.MAX_HIT)
    }

    @Test
    fun encounterResetClearsEveryKephriRuntimeCollection() {
        val state = ToaEncounterState()
        state.kephriActivated = true
        state.kephriRecovering = true
        state.kephriEggBatches +=
            KephriEggBatchRuntime(spawnsAt = 5, tiles = listOf(CoordGrid(2, 2, 0)), darkCount = 1)
        state.kephriDungPlacements += KephriDungPlacementRuntime(CoordGrid(3, 3, 0), 1, 2, 0)
        state.kephriDungTiles += CoordGrid(4, 4, 0)

        state.reset()

        assertFalse(state.kephriActivated)
        assertFalse(state.kephriRecovering)
        assertTrue(state.kephriEggBatches.isEmpty())
        assertTrue(state.kephriDungPlacements.isEmpty())
        assertTrue(state.kephriDungTiles.isEmpty())
    }
}

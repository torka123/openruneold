package org.rsmod.content.minigames.toa

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ToaApmekenEncounterTest {
    @Test
    fun levelZeroApmekenUsesTheCurrentEightWaveSoloRoster() {
        assertEquals(8, ApmekenWaves.WAVE_COUNT)
        assertEquals(3, ApmekenWaves.roster(1, partySize = 1, seed = 0).size)
        assertEquals(3, ApmekenWaves.roster(2, partySize = 1, seed = 0).size)
        assertEquals(3, ApmekenWaves.roster(3, partySize = 1, seed = 0).size)
        (4..8).forEach { wave ->
            assertEquals(5, ApmekenWaves.roster(wave, partySize = 1, seed = 0).size)
        }

        assertEquals(
            listOf(ApmekenBaboon.BRAWLER, ApmekenBaboon.BRAWLER, ApmekenBaboon.SHAMAN),
            ApmekenWaves.roster(1, partySize = 1, seed = 0),
        )
        assertEquals(
            listOf(ApmekenBaboon.THROWER, ApmekenBaboon.THROWER, ApmekenBaboon.VOLATILE),
            ApmekenWaves.roster(2, partySize = 1, seed = 0),
        )
        assertEquals(
            listOf(ApmekenBaboon.MAGE, ApmekenBaboon.CURSED, ApmekenBaboon.MAGE),
            ApmekenWaves.roster(3, partySize = 1, seed = 0),
        )
        assertTrue(ApmekenBaboon.STRONGER_THROWER in ApmekenWaves.roster(4, 1, 0))
        assertTrue(ApmekenBaboon.STRONGER_MAGE in ApmekenWaves.roster(5, 1, 0))
        assertTrue(ApmekenBaboon.STRONGER_BRAWLER in ApmekenWaves.roster(6, 1, 0))
    }

    @Test
    fun eachNonSoloWaveAddsOneCachedCombatBaboon() {
        assertEquals(ToaNpcs.apmekenWaveTypes, ApmekenBaboon.entries.map { it.npcId })
        (1..ApmekenWaves.WAVE_COUNT).forEach { wave ->
            val solo = ApmekenWaves.roster(wave, partySize = 1, seed = 42)
            val group = ApmekenWaves.roster(wave, partySize = 2, seed = 42)
            assertEquals(solo.size + 1, group.size)
            assertTrue(group.all { it.npcId in ToaNpcs.apmekenWaveTypes })
            assertFalse(ApmekenBaboon.THRALL in group)
        }
    }

    @Test
    fun specialChoicesAreDeterministicForARaidSeed() {
        assertEquals(ApmekenWaves.roster(4, 1, 99), ApmekenWaves.roster(4, 1, 99))
        assertNotEquals(ApmekenWaves.roster(4, 1, 99), ApmekenWaves.roster(4, 1, 100))
        assertTrue(
            (0L..100L).any { seed ->
                val specials = ApmekenWaves.roster(4, 1, seed).take(3)
                specials.toSet().size < specials.size
            }
        )
        assertFailsWith<IllegalArgumentException> { ApmekenWaves.roster(0, 1, 0) }
        assertFailsWith<IllegalArgumentException> { ApmekenWaves.roster(9, 1, 0) }
        assertFailsWith<IllegalArgumentException> { ApmekenWaves.roster(1, 0, 0) }
        assertFailsWith<IllegalArgumentException> { ApmekenWaves.roster(1, 9, 0) }
    }

    @Test
    fun wavesOneThroughSevenOverlapAtTwoNonThrallsAndFinalWaitsForThralls() {
        (1 until ApmekenWaves.WAVE_COUNT).forEach { wave ->
            assertEquals(2, ApmekenWaves.remainingThreshold(wave))
            assertEquals(
                ApmekenWaveProgression.WAIT,
                ApmekenWaves.progression(wave, ApmekenWavePopulation(3, 0)),
            )
            assertEquals(
                ApmekenWaveProgression.START_NEXT_WAVE,
                ApmekenWaves.progression(wave, ApmekenWavePopulation(2, 99)),
            )
        }

        assertEquals(0, ApmekenWaves.remainingThreshold(8))
        assertEquals(
            ApmekenWaveProgression.WAIT,
            ApmekenWaves.progression(8, ApmekenWavePopulation(1, 0)),
        )
        assertEquals(
            ApmekenWaveProgression.WAIT,
            ApmekenWaves.progression(8, ApmekenWavePopulation(0, 1)),
        )
        assertEquals(
            ApmekenWaveProgression.CHALLENGE_COMPLETE,
            ApmekenWaves.progression(8, ApmekenWavePopulation(0, 0)),
        )
    }

    @Test
    fun firstEntrantReceivesSightAndSeededRotationsExcludeTheCurrentHolder() {
        val first = ApmekenChallengeModel(seed = 101L, partySize = 4)
        val copy = ApmekenChallengeModel(seed = 101L, partySize = 4)

        assertEquals(2, first.enter(2))
        assertEquals(2, first.enter(0))
        assertEquals(2, first.enter(1))
        copy.enter(2)
        copy.enter(0)
        copy.enter(1)

        repeat(12) {
            val previous = first.sightPlayer
            val next = first.rotateSight(setOf(0, 1, 2))
            val copyNext = copy.rotateSight(setOf(0, 1, 2))
            assertEquals(copyNext, next)
            assertNotEquals(previous, next)
        }

        val solo = ApmekenChallengeModel(seed = 101L, partySize = 1)
        solo.enter(0)
        assertEquals(0, solo.rotateSight())
    }

    @Test
    fun seededQteSelectionIsReplayableAndNeverChoosesCorruptionSolo() {
        val groupTypes =
            (0L..99L)
                .map { seed ->
                    ApmekenChallengeModel(seed, partySize = 4)
                        .also { it.enter(0) }
                        .beginNextQte()
                        .type
                }
                .toSet()
        val soloTypes =
            (0L..99L)
                .map { seed ->
                    ApmekenChallengeModel(seed, partySize = 1)
                        .also { it.enter(0) }
                        .beginNextQte()
                        .type
                }
                .toSet()

        assertEquals(ApmekenQteType.entries.toSet(), groupTypes)
        assertEquals(setOf(ApmekenQteType.ROOF_SUPPORTS, ApmekenQteType.VENTS), soloTypes)

        val first = ApmekenChallengeModel(seed = 202L, partySize = 3).also { it.enter(1) }
        val copy = ApmekenChallengeModel(seed = 202L, partySize = 3).also { it.enter(1) }
        assertEquals(first.beginNextQte(), copy.beginNextQte())
        assertFailsWith<IllegalArgumentException> {
            ApmekenChallengeModel(0L, 1).also { it.enter(0) }.beginQte(ApmekenQteType.CORRUPTION)
        }
    }

    @Test
    fun fixtureRequirementsScaleToFourAndFalseOrDuplicateActionsDoNotAdvance() {
        val encounter = ApmekenChallengeModel(seed = 303L, partySize = 8)
        encounter.enter(0)
        val qte = encounter.beginQte(ApmekenQteType.ROOF_SUPPORTS)
        assertEquals(4, qte.requiredActions)

        val falseAction = encounter.actOnFixture(1, ApmekenQteType.VENTS, 0)
        assertEquals(ApmekenQteOutcome.FALSE_ACTION, falseAction.outcome)
        assertEquals(ApmekenQtePenalty.FALSE_ACTION, falseAction.penalty)
        assertEquals(setOf(1), falseAction.affectedPlayers)
        assertEquals(22, falseAction.baseDamagePerPlayer)
        assertEquals(0, encounter.activeQte?.completedActions)

        assertEquals(
            ApmekenQteOutcome.PROGRESS,
            encounter.actOnFixture(1, ApmekenQteType.ROOF_SUPPORTS, 0).outcome,
        )
        assertEquals(
            ApmekenQteOutcome.ALREADY_DONE,
            encounter.actOnFixture(2, ApmekenQteType.ROOF_SUPPORTS, 0).outcome,
        )
        assertEquals(1, encounter.activeQte?.completedActions)
        encounter.actOnFixture(2, ApmekenQteType.ROOF_SUPPORTS, 1)
        encounter.actOnFixture(3, ApmekenQteType.ROOF_SUPPORTS, 2)
        val completion = encounter.actOnFixture(4, ApmekenQteType.ROOF_SUPPORTS, 3)
        assertEquals(ApmekenQteOutcome.QTE_COMPLETED, completion.outcome)
        assertTrue(completion.snapshot?.complete == true)
        assertEquals(ApmekenQteOutcome.RESOLVED, encounter.resolveQte().outcome)
        assertNull(encounter.activeQte)

        val solo = ApmekenChallengeModel(seed = 303L, partySize = 1)
        solo.enter(0)
        assertEquals(1, solo.beginQte(ApmekenQteType.VENTS).requiredActions)
        assertEquals(
            ApmekenQteOutcome.QTE_COMPLETED,
            solo.actOnFixture(0, ApmekenQteType.VENTS, 3).outcome,
        )
    }

    @Test
    fun qteFailureDamagesTheWholePartyInProportionToMissedActions() {
        val encounter = ApmekenChallengeModel(seed = 404L, partySize = 4)
        encounter.enter(0)
        encounter.beginQte(ApmekenQteType.ROOF_SUPPORTS)
        encounter.actOnFixture(1, ApmekenQteType.ROOF_SUPPORTS, 0)

        val failure = encounter.resolveQte()
        assertEquals(ApmekenQteOutcome.FAILED, failure.outcome)
        assertEquals(ApmekenQtePenalty.MISSED_ACTIONS, failure.penalty)
        assertEquals(3, failure.missedActions)
        assertEquals(setOf(0, 1, 2, 3), failure.affectedPlayers)
        assertEquals(17, failure.baseDamagePerPlayer)
        assertNull(encounter.activeQte)
        assertEquals(ApmekenQteOutcome.NO_ACTIVE_QTE, encounter.resolveQte().outcome)

        val idle = ApmekenChallengeModel(seed = 404L, partySize = 2)
        idle.enter(0)
        val falseAction = idle.actOnFixture(1, ApmekenQteType.VENTS, 0)
        assertEquals(ApmekenQteOutcome.FALSE_ACTION, falseAction.outcome)
        assertEquals(22, falseAction.baseDamagePerPlayer)
    }

    @Test
    fun onlyTheSightHolderCanCleanseGroupCorruptionIndividuallyOrAsAStack() {
        val encounter = ApmekenChallengeModel(seed = 505L, partySize = 3)
        encounter.enter(2)
        encounter.enter(0)
        encounter.enter(1)
        val qte = encounter.beginQte(ApmekenQteType.CORRUPTION)
        assertEquals(2, qte.requiredActions)
        assertEquals(setOf(0, 1), qte.corruptedPlayers)

        val falseAction = encounter.cleanseCorruption(player = 0, target = 1)
        assertEquals(ApmekenQteOutcome.FALSE_ACTION, falseAction.outcome)
        assertEquals(setOf(0, 1), encounter.activeQte?.corruptedPlayers)

        val completion =
            encounter.cleanseCorruptionStack(player = 2, playersOnTile = setOf(0, 1, 2))
        assertEquals(ApmekenQteOutcome.QTE_COMPLETED, completion.outcome)
        assertTrue(completion.snapshot?.corruptedPlayers?.isEmpty() == true)
        assertEquals(ApmekenQteOutcome.RESOLVED, encounter.resolveQte().outcome)

        val individual = ApmekenChallengeModel(seed = 505L, partySize = 3)
        individual.enter(2)
        individual.enter(0)
        individual.enter(1)
        individual.beginQte(ApmekenQteType.CORRUPTION)
        assertEquals(
            ApmekenQteOutcome.PROGRESS,
            individual.cleanseCorruption(player = 2, target = 0).outcome,
        )
        assertEquals(
            ApmekenQteOutcome.QTE_COMPLETED,
            individual.cleanseCorruption(player = 2, target = 1).outcome,
        )
    }

    @Test
    fun resetRestoresSightAndQteRandomStreams() {
        val encounter = ApmekenChallengeModel(seed = 606L, partySize = 3)
        encounter.enter(1)
        encounter.enter(0)
        val firstRotation = encounter.rotateSight()
        val firstQte = encounter.beginNextQte().type

        encounter.reset()
        assertNull(encounter.sightPlayer)
        assertNull(encounter.activeQte)
        encounter.enter(1)
        encounter.enter(0)
        assertEquals(firstRotation, encounter.rotateSight())
        assertEquals(firstQte, encounter.beginNextQte().type)
    }

    @Test
    fun poisonedOrVenomedShamansUseMagicAndTheSeededThrallCapIsFourOrFive() {
        val shaman = ApmekenShamanState(seed = 707L, spawnOrdinal = 2)
        val copy = ApmekenShamanState(seed = 707L, spawnOrdinal = 2)
        assertEquals(copy.thrallLimit, shaman.thrallLimit)
        assertTrue(shaman.thrallLimit in 4..5)
        assertEquals(
            ApmekenShamanAction.MAGIC_ATTACK,
            shaman.nextAction(poisoned = true, venomed = false),
        )
        assertEquals(
            ApmekenShamanAction.MAGIC_ATTACK,
            shaman.nextAction(poisoned = false, venomed = true),
        )

        repeat(shaman.thrallLimit) {
            assertEquals(
                ApmekenShamanAction.SUMMON_THRALL,
                shaman.nextAction(poisoned = false, venomed = false),
            )
            assertTrue(shaman.recordThrallSummon())
        }
        assertEquals(
            ApmekenShamanAction.MAGIC_ATTACK,
            shaman.nextAction(poisoned = false, venomed = false),
        )
        assertFalse(shaman.recordThrallSummon())
    }

    @Test
    fun cursedVenomPoolStateSuppressesProtectedAndDuplicateTiles() {
        val pools = ApmekenVenomPools()
        val tile = ApmekenTile(10, 20)
        val protected = ApmekenTile(11, 20)

        assertEquals(ApmekenVenomPoolPlacement.PLACED, pools.place(tile, protectedTile = false))
        assertEquals(ApmekenVenomPoolPlacement.DUPLICATE, pools.place(tile, protectedTile = false))
        assertEquals(
            ApmekenVenomPoolPlacement.SUPPRESSED,
            pools.place(protected, protectedTile = true),
        )
        assertTrue(pools.contains(tile))
        assertFalse(pools.contains(protected))
        assertEquals(setOf(tile), pools.pools)
        assertEquals(listOf(6, 7, 8), (0..2).map(ApmekenVenomPools::contactDamage))
        assertTrue(ApmekenVenomPools.inflictsVenom(hasVenomImmunity = false))
        assertFalse(ApmekenVenomPools.inflictsVenom(hasVenomImmunity = true))
        assertTrue(pools.remove(tile))
        assertTrue(pools.pools.isEmpty())
        pools.place(tile, protectedTile = false)
        pools.clear()
        assertTrue(pools.pools.isEmpty())
    }

    @Test
    fun volatileExplosionUsesAThreeByThreeAreaAndOnlyKillsRoomBaboons() {
        assertEquals(
            ApmekenExplosionEffect.DAMAGE,
            ApmekenVolatileExplosion.effect(ApmekenExplosionTarget.PLAYER, 1, 1),
        )
        assertEquals(
            ApmekenExplosionEffect.INSTANT_KILL,
            ApmekenVolatileExplosion.effect(ApmekenExplosionTarget.BABOON, -1, 0),
        )
        assertEquals(
            ApmekenExplosionEffect.NONE,
            ApmekenVolatileExplosion.effect(ApmekenExplosionTarget.BABOON, 2, 0),
        )
        assertEquals(
            ApmekenExplosionEffect.NONE,
            ApmekenVolatileExplosion.effect(ApmekenExplosionTarget.PET, 0, 0),
        )
        assertEquals(
            ApmekenExplosionEffect.NONE,
            ApmekenVolatileExplosion.effect(ApmekenExplosionTarget.OTHER_NPC, 0, 0),
        )
        assertEquals(
            ApmekenExplosionEffect.NONE,
            ApmekenVolatileExplosion.effect(
                ApmekenExplosionTarget.BABOON,
                0,
                0,
                explosionSource = true,
            ),
        )
    }

    @Test
    fun retiringSightCancelsTheQteAndTransfersSightWithoutPenalisingTheParty() {
        val challenge = ApmekenChallengeModel(seed = 77L, partySize = 3)
        challenge.enter(0)
        challenge.enter(1)
        challenge.enter(2)
        val active = challenge.beginQte(ApmekenQteType.CORRUPTION)
        val retirement = challenge.retire(0)

        assertEquals(0, retirement.previousSight)
        assertEquals(1, retirement.currentSight)
        assertEquals(active, retirement.cancelledQte)
        assertEquals(1, challenge.sightPlayer)
        assertEquals(null, challenge.activeQte)
        val next = challenge.beginQte(ApmekenQteType.ROOF_SUPPORTS)
        assertEquals(2, next.requiredActions)
        val missed = challenge.resolveQte()
        assertEquals(setOf(1, 2), missed.affectedPlayers)
        assertFalse(0 in missed.affectedPlayers)
    }

    @Test
    fun retiringTheSameApmekenSlotTwiceIsIdempotent() {
        val challenge = ApmekenChallengeModel(seed = 88L, partySize = 2)
        challenge.enter(0)
        assertEquals(null, challenge.retire(0).currentSight)
        assertEquals(null, challenge.retire(0).cancelledQte)
        assertFailsWith<IllegalArgumentException> { challenge.enter(0) }
    }
}

package org.rsmod.content.minigames.toa

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ToaApmekenLiveRuntimeTest {
    @Test
    fun liveMovementPolicyKeepsCombatActiveAndPreventsFourTileCursedLoops() {
        assertEquals(1, ApmekenLiveMovementPolicy.attackRange(ApmekenBaboon.BRAWLER))
        assertEquals(8, ApmekenLiveMovementPolicy.attackRange(ApmekenBaboon.THROWER))
        assertEquals(8, ApmekenLiveMovementPolicy.attackRange(ApmekenBaboon.MAGE))
        assertEquals(8, ApmekenLiveMovementPolicy.attackRange(ApmekenBaboon.SHAMAN))
        assertEquals(64, ApmekenLiveMovementPolicy.ROOM_CHASE_RANGE)
        assertTrue(ApmekenLiveMovementPolicy.usesAutoCombat(ApmekenBaboon.THRALL))
        assertFalse(ApmekenLiveMovementPolicy.usesAutoCombat(ApmekenBaboon.CURSED))
        assertTrue(ApmekenLiveMovementPolicy.shouldRearm(false, true, true))
        assertTrue(ApmekenLiveMovementPolicy.shouldRearm(true, true, false))
        assertFalse(ApmekenLiveMovementPolicy.shouldRearm(true, true, true))

        assertEquals(
            ApmekenTile(25, 25),
            ApmekenLiveMovementPolicy.pursuitCandidates(ApmekenTile(24, 24), ApmekenTile(30, 27))
                .first(),
        )

        val key = 41L
        val firstStart = ApmekenTile(30, 30)
        val firstStep =
            ApmekenLiveMovementPolicy.cursedWanderCandidates(firstStart, key, tick = 12).first()
        val secondStep =
            ApmekenLiveMovementPolicy.cursedWanderCandidates(firstStep, key, tick = 13).first()
        assertEquals(firstStep.x - firstStart.x, secondStep.x - firstStep.x)
        assertEquals(firstStep.z - firstStart.z, secondStep.z - firstStep.z)
    }

    @Test
    fun initializationAndFirstWaveUsePacketCapturedAnchors() {
        val runtime = ToaApmekenLiveRuntime()
        val initialized = runtime.initialize("raid", seed = 11L, partySize = 1, startTick = 0)
        assertEquals(
            listOf(ApmekenLiveEvent.Initialized(1, 11L), ApmekenLiveEvent.WaveScheduled(1, 15)),
            initialized,
        )
        assertEquals(
            listOf(ApmekenLiveEvent.Initialized(1, 0L), ApmekenLiveEvent.WaveScheduled(1, 15)),
            runtime.initialize("nonzero", 0L, 1, 0, raidLevel = 150, pathLevel = 6),
        )

        val sight = runtime.enterPlayer("raid", player = 0, tick = 0)
        assertEquals(ApmekenLiveEvent.SightChanged(null, 0), sight.first())
        assertEquals(
            ApmekenRevision240Assets.sightGranted.id,
            sight.filterIsInstance<ApmekenLiveEvent.PlayerGraphic>().single().spotanimId,
        )

        val wave = runtime.tick("raid", input(15, 1))
        val started = wave.filterIsInstance<ApmekenLiveEvent.WaveStarted>().single()
        val spawns = wave.filterIsInstance<ApmekenLiveEvent.SpawnNpc>()
        assertEquals(1, started.wave)
        assertEquals(3, spawns.size)
        assertEquals(
            listOf(ApmekenTile(24, 24), ApmekenTile(40, 24), ApmekenTile(40, 40)),
            spawns.map { it.request.tile },
        )
        assertEquals(
            listOf(ApmekenLiveFacing.SOUTH, ApmekenLiveFacing.SOUTH, ApmekenLiveFacing.SOUTH),
            spawns.map { it.request.facing },
        )
        assertTrue(
            spawns.all { it.request.spawnSequence == ApmekenRevision240Assets.SPAWN_SEQUENCE }
        )
    }

    @Test
    fun wavesOverlapAtTheModelThresholdAndAllEightWavesCompleteDeterministically() {
        val runtime = ToaApmekenLiveRuntime()
        initializeEntered(runtime, "raid", seed = 12L, partySize = 1)
        runtime.tick("raid", input(15, 1))

        val firstKey = alive(runtime, "raid").first().npcKey
        val scheduled = runtime.onNpcKilled("raid", firstKey, tick = 16)
        assertEquals(ApmekenLiveEvent.WaveScheduled(2, 20), scheduled.single())
        assertEquals(2, alive(runtime, "raid").size)

        val secondWave = runtime.tick("raid", input(20, 1))
        assertEquals(2, secondWave.filterIsInstance<ApmekenLiveEvent.WaveStarted>().single().wave)
        assertEquals(
            2,
            alive(runtime, "raid").count { it.wave == 1 && it.type != ApmekenBaboon.THRALL },
        )
        assertEquals(3, alive(runtime, "raid").count { it.wave == 2 })

        alive(runtime, "raid").forEach { runtime.onNpcKilled("raid", it.npcKey, 21) }
        var nextSpawnTick = 25
        for (wave in 3..8) {
            val events = runtime.tick("raid", input(nextSpawnTick, 1))
            assertEquals(
                wave,
                events.filterIsInstance<ApmekenLiveEvent.WaveStarted>().single().wave,
            )
            if (wave < 8) {
                alive(runtime, "raid").forEach {
                    runtime.onNpcKilled("raid", it.npcKey, nextSpawnTick + 1)
                }
                nextSpawnTick += ApmekenLiveTuning.NEXT_WAVE_DELAY + 1
            }
        }

        val finalEvents = mutableListOf<ApmekenLiveEvent>()
        alive(runtime, "raid").forEach {
            finalEvents += runtime.onNpcKilled("raid", it.npcKey, nextSpawnTick + 1)
        }
        assertEquals(1, finalEvents.filterIsInstance<ApmekenLiveEvent.ChallengeComplete>().size)
        assertTrue(runtime.snapshot("raid")?.complete == true)
        assertNull(runtime.snapshot("raid")?.sightPlayer)
    }

    @Test
    fun packetCapturedSpawnSidesArePhaseSpecificAndGroupExtraUsesTheUnusedSide() {
        val soloFour = ApmekenWaves.spawns(4, partySize = 1, seed = 14L)
        assertEquals(
            listOf(
                ApmekenTile(24, 40),
                ApmekenTile(32, 24),
                ApmekenTile(32, 40),
                ApmekenTile(40, 24),
                ApmekenTile(40, 40),
            ),
            soloFour.map(ApmekenWaveSpawn::tile),
        )
        assertEquals(ApmekenBaboon.STRONGER_THROWER, soloFour[3].type)
        assertEquals(ApmekenBaboon.STRONGER_THROWER, soloFour[4].type)

        val grouped = ApmekenWaves.spawns(4, partySize = 8, seed = 14L)
        assertEquals(6, grouped.size)
        assertEquals(ApmekenTile(24, 24), grouped.last().tile)
        assertEquals(ApmekenBaboon.STRONGER_THROWER, grouped.last().type)
    }

    @Test
    fun roofQteUsesExactSupportsAnimationsGraphicsAndCompletesOnFourActions() {
        val seed = seedFor(ApmekenQteType.ROOF_SUPPORTS, partySize = 4)
        val runtime = ToaApmekenLiveRuntime()
        initializeEntered(runtime, "roof", seed, 4)
        runtime.tick("roof", input(15, 4))

        val startedEvents = runtime.tick("roof", input(30, 4))
        val started = startedEvents.filterIsInstance<ApmekenLiveEvent.QteStarted>().single()
        assertEquals(ApmekenQteType.ROOF_SUPPORTS, started.snapshot.type)
        assertEquals(4, started.snapshot.requiredActions)
        assertEquals(60, started.resolvesAt)
        assertEquals(4, startedEvents.filterIsInstance<ApmekenLiveEvent.ChangeObject>().size)
        assertEquals(
            ApmekenRevision240RuntimeLayout.roofInteractionTiles,
            startedEvents.filterIsInstance<ApmekenLiveEvent.MapGraphic>().map { it.tile },
        )
        assertTrue(
            startedEvents.filterIsInstance<ApmekenLiveEvent.MapGraphic>().all {
                it.visibleTo == started.snapshot.sightPlayer &&
                    it.height == ApmekenLiveTuning.QTE_WARNING_HEIGHT
            }
        )

        ApmekenRevision240RuntimeLayout.roofSupports.forEachIndexed { player, support ->
            val interaction =
                runtime.interactObject(
                    "roof",
                    ApmekenObjectInteraction(
                        player = player,
                        locId = ApmekenRevision240RuntimeLayout.roofSupportAlternateLoc.locId,
                        tile = support.tile,
                        hasHammer = true,
                    ),
                    tick = 31,
                )
            assertEquals(
                ApmekenRevision240Assets.REPAIR_PLAYER_SEQUENCE,
                interaction.filterIsInstance<ApmekenLiveEvent.PlayerAnimation>().single().sequenceId,
            )
            assertEquals(1, interaction.filterIsInstance<ApmekenLiveEvent.ChangeObject>().size)
        }
        assertTrue(runtime.snapshot("roof")?.activeQte?.complete == true)

        val resolved = runtime.tick("roof", input(60, 4))
        assertEquals(
            ApmekenLiveEvent.QteResolved(ApmekenQteType.ROOF_SUPPORTS, true, 0),
            resolved.filterIsInstance<ApmekenLiveEvent.QteResolved>().single(),
        )
        assertTrue(resolved.none { it is ApmekenLiveEvent.DamagePlayer })
        assertNull(runtime.snapshot("roof")?.activeQte)
    }

    @Test
    fun inventoryPotionUseCleansesCentredVentAndSameTileCorruptionStack() {
        val ventRuntime = ToaApmekenLiveRuntime()
        val ventSeed = seedFor(ApmekenQteType.VENTS, partySize = 1)
        initializeEntered(ventRuntime, "vent", ventSeed, 1)
        ventRuntime.tick("vent", input(15, 1))
        val ventStartedEvents = ventRuntime.tick("vent", input(30, 1))
        val ventStarted = ventStartedEvents.filterIsInstance<ApmekenLiveEvent.QteStarted>().single()
        assertEquals(ApmekenQteType.VENTS, ventStarted.snapshot.type)
        assertEquals(
            ApmekenRevision240RuntimeLayout.vents.mapIndexed { index, vent ->
                ApmekenLiveEvent.MapGraphic(
                    vent.tile,
                    if (index < 2) {
                        ApmekenRevision240Assets.roofWarning.id
                    } else {
                        ApmekenRevision240Assets.roofWarningAlternate.id
                    },
                    ventStarted.snapshot.sightPlayer,
                    ApmekenLiveTuning.QTE_WARNING_HEIGHT,
                )
            },
            ventStartedEvents.filterIsInstance<ApmekenLiveEvent.MapGraphic>(),
        )
        val vent = ApmekenRevision240RuntimeLayout.vents.first()
        val offVent =
            ventRuntime.pourNeutralisingPotion(
                "vent",
                ApmekenPotionPourInteraction(
                    player = 0,
                    tile = ApmekenTile(0, 0),
                    playersOnTile = setOf(0),
                    neutralisingPotions = 1,
                ),
                tick = 31,
            )
        assertEquals(
            ApmekenLiveFeedbackCode.STAND_ON_VENT,
            offVent.filterIsInstance<ApmekenLiveEvent.Feedback>().single().code,
        )
        assertTrue(offVent.none { it is ApmekenLiveEvent.SupplyTransfer })
        val neutralised =
            ventRuntime.pourNeutralisingPotion(
                "vent",
                ApmekenPotionPourInteraction(
                    player = 0,
                    tile = vent.tile,
                    playersOnTile = setOf(0),
                    neutralisingPotions = 1,
                ),
                tick = 32,
            )
        assertEquals(
            ApmekenLiveTuning.NEUTRALISING_POTION_ITEM,
            neutralised.filterIsInstance<ApmekenLiveEvent.SupplyTransfer>().single().itemId,
        )
        assertEquals(
            -1,
            neutralised.filterIsInstance<ApmekenLiveEvent.SupplyTransfer>().single().amount,
        )
        assertEquals(
            ApmekenQteOutcome.QTE_COMPLETED,
            neutralised.filterIsInstance<ApmekenLiveEvent.QteFeedback>().single().outcome,
        )

        val corruptionRuntime = ToaApmekenLiveRuntime()
        val corruptionSeed = seedFor(ApmekenQteType.CORRUPTION, partySize = 3)
        initializeEntered(corruptionRuntime, "corruption", corruptionSeed, 3)
        corruptionRuntime.tick("corruption", input(15, 3))
        val start = corruptionRuntime.tick("corruption", input(30, 3))
        val snapshot = start.filterIsInstance<ApmekenLiveEvent.QteStarted>().single().snapshot
        assertEquals(ApmekenQteType.CORRUPTION, snapshot.type)
        assertEquals(2, start.filterIsInstance<ApmekenLiveEvent.PlayerGraphic>().size)
        val cleansed =
            corruptionRuntime.pourNeutralisingPotion(
                "corruption",
                ApmekenPotionPourInteraction(
                    player = snapshot.sightPlayer,
                    tile = ApmekenTile(32, 32),
                    playersOnTile = setOf(0, 1, 2),
                    neutralisingPotions = 1,
                ),
                tick = 31,
            )
        assertEquals(
            -1,
            cleansed.filterIsInstance<ApmekenLiveEvent.SupplyTransfer>().single().amount,
        )
        assertEquals(
            ApmekenQteOutcome.QTE_COMPLETED,
            cleansed.filterIsInstance<ApmekenLiveEvent.QteFeedback>().single().outcome,
        )
        assertTrue(corruptionRuntime.snapshot("corruption")?.activeQte?.complete == true)
        val resolved = corruptionRuntime.tick("corruption", input(60, 3))
        assertEquals(
            ApmekenLiveEvent.QteResolved(ApmekenQteType.CORRUPTION, true, 0),
            resolved.filterIsInstance<ApmekenLiveEvent.QteResolved>().single(),
        )
    }

    @Test
    fun supplyCratesGrantOneHammerOrOneHundredStackablePotions() {
        val runtime = ToaApmekenLiveRuntime()
        initializeEntered(runtime, "supplies", seed = 5L, partySize = 1)
        val hammer =
            ApmekenRevision240RuntimeLayout.supplies.first { it.kind == ApmekenSupplyKind.HAMMERS }
        val potion =
            ApmekenRevision240RuntimeLayout.supplies.first { it.kind == ApmekenSupplyKind.POTIONS }
        assertEquals(
            ApmekenLiveEvent.SupplyTransfer(0, ApmekenLiveTuning.HAMMER_ITEM, 1),
            runtime
                .interactObject(
                    "supplies",
                    ApmekenObjectInteraction(0, hammer.source.locId, hammer.source.tile),
                    tick = 1,
                )
                .single(),
        )
        assertEquals(
            ApmekenLiveEvent.SupplyTransfer(
                0,
                ApmekenLiveTuning.NEUTRALISING_POTION_ITEM,
                ApmekenLiveTuning.NEUTRALISING_POTION_SUPPLY_AMOUNT,
            ),
            runtime
                .interactObject(
                    "supplies",
                    ApmekenObjectInteraction(0, potion.source.locId, potion.source.tile),
                    tick = 1,
                )
                .single(),
        )
    }

    @Test
    fun falseFixtureActionsAndMissedQtesReturnExplicitDamageEvents() {
        val runtime = ToaApmekenLiveRuntime()
        val seed = seedFor(ApmekenQteType.VENTS, partySize = 2)
        initializeEntered(runtime, "raid", seed, 2)
        runtime.tick("raid", input(15, 2))
        runtime.tick("raid", input(30, 2))

        val roof = ApmekenRevision240RuntimeLayout.roofSupports.first()
        val falseAction =
            runtime.interactObject(
                "raid",
                ApmekenObjectInteraction(
                    player = 0,
                    locId = roof.locId,
                    tile = roof.tile,
                    hasHammer = true,
                ),
                tick = 31,
            )
        assertEquals(
            22,
            falseAction.filterIsInstance<ApmekenLiveEvent.DamagePlayer>().single().baseDamage,
        )
        assertEquals(
            ApmekenLiveDamageReason.FALSE_QTE_ACTION,
            falseAction.filterIsInstance<ApmekenLiveEvent.DamagePlayer>().single().reason,
        )

        val resolved = runtime.tick("raid", input(60, 2))
        assertEquals(
            false,
            resolved.filterIsInstance<ApmekenLiveEvent.QteResolved>().single().success,
        )
        assertEquals(2, resolved.filterIsInstance<ApmekenLiveEvent.DamagePlayer>().size)
        assertTrue(
            resolved.filterIsInstance<ApmekenLiveEvent.DamagePlayer>().all {
                it.reason == ApmekenLiveDamageReason.MISSED_QTE
            }
        )
    }

    @Test
    fun shamanThrallCapIsDeterministicAndPoisonOrVenomSwitchesToMagic() {
        val runtime = ToaApmekenLiveRuntime()
        initializeEntered(runtime, "cap", seed = 707L, partySize = 1)
        val waveTick = advanceToWave(runtime, "cap", 1)
        val shaman = alive(runtime, "cap").single { it.type == ApmekenBaboon.SHAMAN }
        val cap = assertNotNull(shaman.thrallLimit)
        assertTrue(cap in 4..5)

        var tick = waveTick + ApmekenLiveTuning.SHAMAN_ACTION_INTERVAL
        var summons = 0
        repeat(cap + 1) {
            val events = runtime.tick("cap", input(tick, 1))
            summons +=
                events.filterIsInstance<ApmekenLiveEvent.SpawnNpc>().count {
                    it.request.npcId == ApmekenBaboon.THRALL.npcId
                }
            tick += ApmekenLiveTuning.SHAMAN_ACTION_INTERVAL
        }
        assertEquals(cap, summons)
        val cappedAttack = runtime.tick("cap", input(tick, 1))
        assertEquals(1, cappedAttack.filterIsInstance<ApmekenLiveEvent.NpcAttackRequest>().size)

        val suppressed = ToaApmekenLiveRuntime()
        initializeEntered(suppressed, "poison", seed = 708L, partySize = 1)
        val suppressedWaveTick = advanceToWave(suppressed, "poison", 1)
        val poisonedShaman = alive(suppressed, "poison").single { it.type == ApmekenBaboon.SHAMAN }
        val status =
            suppressed.onNpcHit(
                "poison",
                ApmekenNpcHitContext(
                    poisonedShaman.npcKey,
                    poisonedShaman.tile,
                    remainingHitpoints = 40,
                    poisoned = true,
                ),
                tick = suppressedWaveTick + 1,
            )
        assertEquals(1, status.filterIsInstance<ApmekenLiveEvent.ShamanSuppressionChanged>().size)
        val attack =
            suppressed.tick(
                "poison",
                input(suppressedWaveTick + ApmekenLiveTuning.SHAMAN_ACTION_INTERVAL, 1),
            )
        assertTrue(attack.none { it is ApmekenLiveEvent.SpawnNpc })
        assertEquals(
            ApmekenRevision240Assets.magicProjectile.id,
            attack.filterIsInstance<ApmekenLiveEvent.NpcAttackRequest>().single().projectileId,
        )
    }

    @Test
    fun cursedPoolsRespectProtectedTilesDamagePlayersAndExpire() {
        val runtime = ToaApmekenLiveRuntime()
        initializeEntered(runtime, "raid", seed = 22L, partySize = 1)
        val waveTick = advanceToWave(runtime, "raid", 3)
        val cursed = alive(runtime, "raid").single { it.type == ApmekenBaboon.CURSED }
        val safe = ApmekenTile(32, 24)
        val placedTick = waveTick + ApmekenLiveTuning.CURSED_POOL_START_DELAY
        val placed =
            runtime.tick(
                "raid",
                input(
                    tick = placedTick,
                    partySize = 1,
                    playerTiles = mapOf(0 to ApmekenTile(24, 24)),
                    npcTiles = mapOf(cursed.npcKey to safe),
                ),
            )
        val pool = placed.filterIsInstance<ApmekenLiveEvent.SpawnVenomPool>().single()
        assertEquals(safe, pool.tile)
        assertEquals(placedTick + ApmekenLiveTuning.CURSED_POOL_LIFETIME, pool.expiresAt)

        val contact =
            runtime.tick(
                "raid",
                input(
                    placedTick + 1,
                    1,
                    playerTiles = mapOf(0 to safe),
                    npcTiles = mapOf(cursed.npcKey to safe),
                ),
            )
        assertTrue(
            contact.filterIsInstance<ApmekenLiveEvent.DamagePlayer>().single().baseDamage in 6..8
        )
        assertEquals(
            ApmekenLiveTuning.VENOM_SEVERITY,
            contact.filterIsInstance<ApmekenLiveEvent.ApplyVenom>().single().severity,
        )

        val vent = ApmekenRevision240RuntimeLayout.vents.first().tile
        val protected =
            runtime.tick(
                "raid",
                input(
                    placedTick + 2,
                    1,
                    playerTiles = mapOf(0 to ApmekenTile(24, 24)),
                    npcTiles = mapOf(cursed.npcKey to vent),
                ),
            )
        assertTrue(protected.none { it is ApmekenLiveEvent.SpawnVenomPool })
        assertFalse(ApmekenRevision240RuntimeLayout.isVenomPlacementTile(vent))

        val expired =
            runtime.tick("raid", input(placedTick + ApmekenLiveTuning.CURSED_POOL_LIFETIME, 1))
        assertEquals(
            safe,
            expired.filterIsInstance<ApmekenLiveEvent.RemoveVenomPool>().single().tile,
        )
        assertTrue(runtime.snapshot("raid")?.venomPools?.isEmpty() == true)
    }

    @Test
    fun volatileChargeUsesNativeVisualsDamagesPlayersAndKillsNearbyBaboons() {
        val runtime = ToaApmekenLiveRuntime()
        initializeEntered(runtime, "raid", seed = 33L, partySize = 1)
        val waveTick = advanceToWave(runtime, "raid", 2)
        val volatile = alive(runtime, "raid").single { it.type == ApmekenBaboon.VOLATILE }
        val victim = alive(runtime, "raid").first { it.npcKey != volatile.npcKey }
        val centre = ApmekenTile(32, 24)
        val nearby = ApmekenTile(32, 25)

        runtime.tick(
            "raid",
            input(
                waveTick + 1,
                1,
                mapOf(0 to centre),
                mapOf(volatile.npcKey to centre, victim.npcKey to nearby),
            ),
        )
        val animation =
            runtime.tick(
                "raid",
                input(
                    waveTick + 2,
                    1,
                    mapOf(0 to centre),
                    mapOf(volatile.npcKey to centre, victim.npcKey to nearby),
                ),
            )
        assertEquals(
            ApmekenRevision240Assets.VOLATILE_EXPLODE_SEQUENCE,
            animation.filterIsInstance<ApmekenLiveEvent.NpcAnimation>().single().sequenceId,
        )
        val explosion =
            runtime.tick(
                "raid",
                input(
                    waveTick + 4,
                    1,
                    mapOf(0 to centre),
                    mapOf(volatile.npcKey to centre, victim.npcKey to nearby),
                ),
            )
        assertEquals(
            setOf(ApmekenRevision240Assets.fire.id, ApmekenRevision240Assets.volatileBody.id),
            explosion.filterIsInstance<ApmekenLiveEvent.MapGraphic>().mapTo(linkedSetOf()) {
                it.spotanimId
            },
        )
        assertTrue(
            explosion.filterIsInstance<ApmekenLiveEvent.DamagePlayer>().single().baseDamage in
                14..16
        )
        assertEquals(
            victim.npcKey,
            explosion.filterIsInstance<ApmekenLiveEvent.KillNpcRequest>().single().npcKey,
        )

        val finished =
            runtime.tick(
                "raid",
                input(waveTick + 5, 1, mapOf(0 to centre), mapOf(volatile.npcKey to centre)),
            )
        assertTrue(
            finished.filterIsInstance<ApmekenLiveEvent.KillNpcRequest>().any {
                it.npcKey == volatile.npcKey
            }
        )
    }

    private fun singlePlayerInput(tick: Int): ApmekenLiveTickInput =
        ApmekenLiveTickInput(
            tick = tick,
            activePlayers = setOf(0),
            playerTiles = mapOf(0 to ApmekenTile(24, 24)),
        )

    @Test
    fun retiredSlotBeforeEntryDoesNotDeadlockQteReadiness() {
        val runtime = ToaApmekenLiveRuntime()
        runtime.initialize("raid", seed = 101L, partySize = 2, startTick = 0)

        assertTrue(runtime.retirePlayer("raid", player = 1, tick = 0).isEmpty())
        runtime.enterPlayer("raid", player = 0, tick = 0)

        val events =
            runtime.tick(
                "raid",
                ApmekenLiveTickInput(
                    tick = ApmekenLiveTuning.QTE_CYCLE_TICKS,
                    activePlayers = setOf(0),
                    playerTiles = mapOf(0 to ApmekenTile(24, 24)),
                ),
            )

        assertEquals(1, events.filterIsInstance<ApmekenLiveEvent.QteStarted>().size)
        assertNotNull(runtime.snapshot("raid")?.activeQte)
    }

    @Test
    fun retiringDuringActiveQteCancelsWithoutDamageOrFailureResolution() {
        val runtime = ToaApmekenLiveRuntime()
        initializeEntered(runtime, "raid", seed = 102L, partySize = 2)
        val started =
            runtime
                .tick("raid", input(ApmekenLiveTuning.QTE_CYCLE_TICKS, 2))
                .filterIsInstance<ApmekenLiveEvent.QteStarted>()
                .single()

        val retired =
            runtime.retirePlayer("raid", player = 1, tick = ApmekenLiveTuning.QTE_CYCLE_TICKS + 1)

        assertEquals(
            ApmekenLiveEvent.QteCancelled(started.snapshot.type, retiredPlayer = 1),
            retired.filterIsInstance<ApmekenLiveEvent.QteCancelled>().single(),
        )
        assertTrue(retired.none { it is ApmekenLiveEvent.DamagePlayer })
        assertTrue(retired.none { it is ApmekenLiveEvent.QteResolved })
        assertNull(runtime.snapshot("raid")?.activeQte)
        assertNull(runtime.snapshot("raid")?.qteResolvesAt)
    }

    @Test
    fun retiringSightHolderTransfersSightBeforeSlotMappingCanBeRemoved() {
        val runtime = ToaApmekenLiveRuntime()
        runtime.initialize("raid", seed = 103L, partySize = 3, startTick = 0)
        repeat(3) { runtime.enterPlayer("raid", player = it, tick = 0) }

        val retired = runtime.retirePlayer("raid", player = 0, tick = 1)

        assertEquals(
            ApmekenLiveEvent.SightChanged(previousPlayer = 0, currentPlayer = 1),
            retired.filterIsInstance<ApmekenLiveEvent.SightChanged>().single(),
        )
        assertEquals(
            listOf(
                0 to ApmekenRevision240Assets.sightLost.id,
                1 to ApmekenRevision240Assets.sightGranted.id,
            ),
            retired.filterIsInstance<ApmekenLiveEvent.PlayerGraphic>().map {
                it.player to it.spotanimId
            },
        )
        assertEquals(1, runtime.snapshot("raid")?.sightPlayer)
    }

    @Test
    fun retirementIsIdempotentAndMustBeReappliedAfterRuntimeReset() {
        val runtime = ToaApmekenLiveRuntime()
        runtime.initialize("raid", seed = 104L, partySize = 2, startTick = 0)
        assertTrue(runtime.retirePlayer("raid", player = 1, tick = 0).isEmpty())
        assertTrue(runtime.retirePlayer("raid", player = 1, tick = 0).isEmpty())
        runtime.enterPlayer("raid", player = 0, tick = 0)
        assertEquals(
            1,
            runtime
                .tick("raid", singlePlayerInput(ApmekenLiveTuning.QTE_CYCLE_TICKS))
                .filterIsInstance<ApmekenLiveEvent.QteStarted>()
                .size,
        )

        runtime.cleanup("raid")
        runtime.initialize("raid", seed = 104L, partySize = 2, startTick = 31)
        runtime.enterPlayer("raid", player = 0, tick = 31)
        assertTrue(
            runtime.tick("raid", singlePlayerInput(61)).none { it is ApmekenLiveEvent.QteStarted }
        )

        assertTrue(runtime.retirePlayer("raid", player = 1, tick = 61).isEmpty())
        assertEquals(
            1,
            runtime
                .tick("raid", singlePlayerInput(61 + ApmekenLiveTuning.QTE_CYCLE_TICKS))
                .filterIsInstance<ApmekenLiveEvent.QteStarted>()
                .size,
        )
    }

    @Test
    fun cleanupIsIdempotentAndPerInstance() {
        val runtime = ToaApmekenLiveRuntime()
        initializeEntered(runtime, "first", 1L, 1)
        initializeEntered(runtime, "second", 2L, 1)
        runtime.tick("first", input(15, 1))
        runtime.tick("second", input(15, 1))

        val cleanup = runtime.cleanup("first")
        assertEquals(3, cleanup.filterIsInstance<ApmekenLiveEvent.DespawnNpcRequest>().size)
        assertEquals(4, cleanup.filterIsInstance<ApmekenLiveEvent.ChangeObject>().size)
        assertEquals(1, cleanup.filterIsInstance<ApmekenLiveEvent.CleanupComplete>().size)
        assertFalse(runtime.contains("first"))
        assertTrue(runtime.contains("second"))
        assertTrue(runtime.cleanup("first").isEmpty())
        assertNotNull(runtime.snapshot("second"))
    }

    @Test
    fun runtimeRejectsBackwardsTicksAndUnknownInstances() {
        val runtime = ToaApmekenLiveRuntime()
        runtime.initialize("raid", 0L, 1, 10)
        assertFailsWith<IllegalArgumentException> { runtime.enterPlayer("raid", 0, 9) }
        assertFailsWith<IllegalArgumentException> { runtime.enterPlayer("missing", 0, 10) }
    }

    private fun initializeEntered(
        runtime: ToaApmekenLiveRuntime,
        key: Any,
        seed: Long,
        partySize: Int,
    ) {
        runtime.initialize(key, seed, partySize, startTick = 0)
        repeat(partySize) { runtime.enterPlayer(key, it, tick = 0) }
    }

    private fun alive(runtime: ToaApmekenLiveRuntime, key: Any): List<ApmekenLiveActorSnapshot> =
        checkNotNull(runtime.snapshot(key)).actors.filter { it.alive }

    private fun advanceToWave(runtime: ToaApmekenLiveRuntime, key: Any, targetWave: Int): Int {
        var spawnTick = ApmekenLiveTuning.FIRST_WAVE_DELAY
        runtime.tick(key, input(spawnTick, 1))
        while (checkNotNull(runtime.snapshot(key)).currentWave < targetWave) {
            val killTick = spawnTick + 1
            alive(runtime, key).forEach { runtime.onNpcKilled(key, it.npcKey, killTick) }
            spawnTick = killTick + ApmekenLiveTuning.NEXT_WAVE_DELAY
            runtime.tick(key, input(spawnTick, 1))
        }
        return spawnTick
    }

    private fun seedFor(type: ApmekenQteType, partySize: Int): Long =
        (0L..1000L).first { seed ->
            val model = ApmekenChallengeModel(seed, partySize)
            model.enter(0)
            model.beginNextQte().type == type
        }

    private fun input(
        tick: Int,
        partySize: Int,
        playerTiles: Map<Int, ApmekenTile> =
            (0 until partySize).associateWith { ApmekenTile(24 + it, 24) },
        npcTiles: Map<Long, ApmekenTile> = emptyMap(),
    ): ApmekenLiveTickInput =
        ApmekenLiveTickInput(
            tick = tick,
            activePlayers = (0 until partySize).toSet(),
            playerTiles = playerTiles,
            npcTiles = npcTiles,
        )
}

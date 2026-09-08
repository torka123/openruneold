package org.rsmod.content.minigames.tob

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TobRulesTest {
    @Test
    fun `verzik pillars shield canonical and line occluded tiles`() {
        val source = org.rsmod.map.CoordGrid(30, 35, 0)
        val pillar = org.rsmod.map.CoordGrid(25, 30, 0)
        val canonical = setOf(org.rsmod.map.CoordGrid(25, 29, 0))
        assertTrue(
            isShieldedByVerzikPillar(
                source,
                5,
                org.rsmod.map.CoordGrid(25, 29, 0),
                pillar,
                3,
                canonical,
            )
        )
        assertTrue(
            isShieldedByVerzikPillar(
                source,
                5,
                org.rsmod.map.CoordGrid(24, 28, 0),
                pillar,
                3,
                canonical,
            )
        )
        assertFalse(
            isShieldedByVerzikPillar(
                source,
                5,
                org.rsmod.map.CoordGrid(31, 27, 0),
                pillar,
                3,
                canonical,
            )
        )
    }

    @Test
    fun `verzik pillar shielding never crosses planes`() {
        assertFalse(
            isShieldedByVerzikPillar(
                org.rsmod.map.CoordGrid(30, 35, 0),
                5,
                org.rsmod.map.CoordGrid(25, 29, 1),
                org.rsmod.map.CoordGrid(25, 30, 0),
                3,
                emptySet(),
            )
        )
    }

    @Test
    fun `verzik pillar health reaches zero without underflow`() {
        assertEquals(115, verzikPillarHealthAfterHit(185, 70))
        assertEquals(0, verzikPillarHealthAfterHit(40, 92))
        assertFailsWith<IllegalArgumentException> { verzikPillarHealthAfterHit(10, -1) }
    }

    @Test
    fun `phase two only slams cardinal yellow tiles and leaves red footprint and corners safe`() {
        val boss = org.rsmod.map.CoordGrid(100, 200, 0)
        for (bossSize in listOf(1, 3, 5, 7)) {
            val bossMaxX = boss.x + bossSize - 1
            val bossMaxZ = boss.z + bossSize - 1
            for (x in (boss.x - 2)..(boss.x + bossSize + 1)) {
                for (z in (boss.z - 2)..(boss.z + bossSize + 1)) {
                    val xDistance = maxOf(0, boss.x - x, x - bossMaxX)
                    val zDistance = maxOf(0, boss.z - z, z - bossMaxZ)
                    val expected =
                        when (xDistance + zDistance) {
                            1 -> VerzikPhaseTwoMeleePosition.SLAM
                            else -> VerzikPhaseTwoMeleePosition.OUTSIDE
                        }
                    assertEquals(
                        expected,
                        verzikPhaseTwoMeleePosition(
                            org.rsmod.map.CoordGrid(x, z, 0),
                            boss,
                            bossSize,
                        ),
                        "Unexpected phase-two melee classification at ($x, $z), size=$bossSize",
                    )
                }
            }
        }
        assertEquals(
            VerzikPhaseTwoMeleePosition.OUTSIDE,
            verzikPhaseTwoMeleePosition(org.rsmod.map.CoordGrid(103, 203, 1), boss, 7),
        )
        assertFalse(shouldVerzikBodySlam(0))
        assertTrue(shouldVerzikBodySlam(1))
        assertTrue(shouldVerzikBodySlam(2))
        assertTrue(shouldVerzikBodySlam(3))
        assertEquals(-1 to 0, verzikKnockbackVector(org.rsmod.map.CoordGrid(99, 203, 0), boss, 7))
    }

    @Test
    fun `phase two replacement keeps the carpet anchor and size three footprint`() {
        val transition = org.rsmod.map.CoordGrid(100, 200, 0)
        val active = verzikPhaseTwoActiveAnchor(transition)

        assertEquals(transition, active)
        for (x in active.x..(active.x + 2)) {
            for (z in active.z..(active.z + 2)) {
                assertEquals(
                    VerzikPhaseTwoMeleePosition.OUTSIDE,
                    verzikPhaseTwoMeleePosition(
                        org.rsmod.map.CoordGrid(x, z, active.level),
                        active,
                        bossSize = 3,
                    ),
                )
            }
        }
        assertEquals(
            VerzikPhaseTwoMeleePosition.SLAM,
            verzikPhaseTwoMeleePosition(
                org.rsmod.map.CoordGrid(active.x - 1, active.z + 1, active.level),
                active,
                bossSize = 3,
            ),
        )
    }

    @Test
    fun `phase three yellow pool only protects one player standing on its exact tile`() {
        val pool = org.rsmod.map.CoordGrid(100, 200, 0)
        val adjacent = org.rsmod.map.CoordGrid(101, 200, 0)
        val otherPlane = org.rsmod.map.CoordGrid(100, 200, 1)

        assertTrue(isProtectedByVerzikYellowPool(pool, mapOf(pool to 1)))
        assertFalse(isProtectedByVerzikYellowPool(pool, mapOf(pool to 2)))
        assertFalse(isProtectedByVerzikYellowPool(adjacent, mapOf(pool to 1)))
        assertFalse(isProtectedByVerzikYellowPool(otherPlane, mapOf(pool to 1)))
        assertFalse(isProtectedByVerzikYellowPool(pool, emptyMap()))
    }

    @Test
    fun `verzik sequence ids match their real phase actions`() {
        assertEquals(8109, TobAssets.VERZIK_PHASE_ONE_ATTACK)
        assertEquals(8110, TobAssets.VERZIK_PHASE_ONE_DEFEND)
        assertEquals(8111, TobAssets.VERZIK_PHASE_ONE_DEATH)
        assertEquals(8112, TobAssets.VERZIK_PHASE_TWO_SPAWN)
        assertEquals(8114, TobAssets.VERZIK_PHASE_TWO_ATTACK)
        assertEquals(8115, TobAssets.VERZIK_PHASE_TWO_LIGHTNING)
        assertEquals(8116, TobAssets.VERZIK_PHASE_TWO_MELEE)
    }

    @Test
    fun `normal verzik timings remain tick exact`() {
        assertEquals(14, VerzikNormalRules.PHASE_ONE_ATTACK_INTERVAL)
        assertEquals(5, VerzikNormalRules.PHASE_TWO_LIGHTNING_INTERVAL)
        assertFalse(VerzikNormalRules.lightningDue(4))
        assertTrue(VerzikNormalRules.lightningDue(5))
        assertFalse(VerzikNormalRules.lightningDue(6))
        assertTrue(VerzikNormalRules.lightningDue(10))
        assertEquals(14, VerzikNormalRules.PHASE_TWO_TRANSITION_TICKS)
        assertEquals(406, VerzikNormalRules.PHASE_TWO_TRANSITION_CLIENT_CYCLES)
        assertEquals(12, VerzikNormalRules.MATOMENOS_SUMMON_TICKS)
        assertEquals(8, VerzikNormalRules.MATOMENOS_DAMAGE_HEAL_TICKS)
        assertEquals(75, VerzikNormalRules.MATOMENOS_HITPOINTS)
        assertEquals(12, VerzikNormalRules.MATOMENOS_RESPAWN_DELAY)
        assertEquals(30, VerzikNormalRules.PHASE_TWO_KNOCKBACK_CLIENT_CYCLES)
        assertEquals(4, VerzikNormalRules.PHASE_THREE_AUTOS_BEFORE_SPECIAL)
        assertEquals(7, VerzikNormalRules.PHASE_THREE_ATTACK_INTERVAL)
        assertEquals(5, VerzikNormalRules.PHASE_THREE_ENRAGED_INTERVAL)
        assertEquals(7, VerzikNormalRules.YELLOW_PROJECTILE_TICK)
        assertEquals(11, VerzikNormalRules.YELLOW_IMPACT_TICK)
        assertEquals(15, VerzikNormalRules.YELLOW_END_TICK)
    }

    @Test
    fun `normal verzik damage snapshots and scaling are deterministic`() {
        assertEquals(137, VerzikNormalRules.phaseOneDamage(137, false))
        assertEquals(68, VerzikNormalRules.phaseOneDamage(137, true))
        assertEquals(74, VerzikNormalRules.greenBallDamage(99))
        assertEquals(750, VerzikNormalRules.scaledHitpoints(1000, 1))
        assertEquals(750, VerzikNormalRules.scaledHitpoints(1000, 3))
        assertEquals(875, VerzikNormalRules.scaledHitpoints(1000, 4))
        assertEquals(1000, VerzikNormalRules.scaledHitpoints(1000, 5))
    }

    @Test
    fun `phase two nylocas summon only rolls after every third lightning`() {
        assertFalse(shouldSummonVerzikNylocas(1, 0, false, 100))
        assertFalse(shouldSummonVerzikNylocas(2, 0, false, 100))
        assertTrue(shouldSummonVerzikNylocas(3, 0, false, 100))
        assertFalse(shouldSummonVerzikNylocas(3, 1, false, 100))
        assertFalse(shouldSummonVerzikNylocas(3, 0, true, 100))
        assertFalse(shouldSummonVerzikNylocas(3, 0, false, 35))
        assertTrue(shouldSummonVerzikNylocas(6, 0, false, 36))
    }

    @Test
    fun `verzik phase three enrages at exactly twenty percent`() {
        assertFalse(isVerzikPhaseThreeEnraged(21))
        assertTrue(isVerzikPhaseThreeEnraged(20))
        assertTrue(isVerzikPhaseThreeEnraged(0))
    }

    @Test
    fun `verzik bomb damage falls off by tile distance`() {
        assertEquals(64, verzikBombBaseDamage(0))
        assertEquals(32, verzikBombBaseDamage(1))
        assertEquals(16, verzikBombBaseDamage(2))
        assertEquals(8, verzikBombBaseDamage(3))
        assertEquals(0, verzikBombBaseDamage(4))
        assertEquals(0, verzikBombBaseDamage(-1))
    }

    @Test
    fun `athanatos only reacts to poison or venom equipment`() {
        assertTrue(isAthanatosPoisonSource("obj.dragon_dagger_p", null))
        assertTrue(isAthanatosPoisonSource("obj.dragon_dagger_p+", null))
        assertTrue(isAthanatosPoisonSource("obj.dragon_dagger_p++", null))
        assertTrue(isAthanatosPoisonSource("obj.toxic_blowpipe_loaded", null))
        assertTrue(isAthanatosPoisonSource(null, "obj.serpentine_helm_charged"))
        assertFalse(isAthanatosPoisonSource("obj.dragon_dagger", null))
        assertFalse(isAthanatosPoisonSource(null, null))
        assertEquals(0, verzikMatomenosCount(0))
        assertEquals(2, verzikMatomenosCount(1))
        assertEquals(2, verzikMatomenosCount(2))
        assertEquals(10845, TobNpcs.verzikMatomenos(TobMode.ENTRY))
        assertEquals(8385, TobNpcs.verzikMatomenos(TobMode.NORMAL))
        assertEquals(10862, TobNpcs.verzikMatomenos(TobMode.HARD))
    }

    @Test
    fun `phase two nylocas styles use their own attack and detonation sequences`() {
        assertEquals(TobAssets.NYLOCAS_MELEE_ATTACK, TobAssets.nylocasAttackAnimation(0))
        assertEquals(TobAssets.NYLOCAS_RANGED_CAST, TobAssets.nylocasAttackAnimation(1))
        assertEquals(TobAssets.NYLOCAS_MAGIC_CAST, TobAssets.nylocasAttackAnimation(2))

        val ids = TobNpcs.nylocasBigIncomingStyles(TobMode.NORMAL)
        assertEquals(
            TobAssets.NYLOCAS_MELEE_DETONATE,
            TobAssets.nylocasDetonateAnimation(ids[0], TobMode.NORMAL),
        )
        assertEquals(
            TobAssets.NYLOCAS_RANGED_DETONATE,
            TobAssets.nylocasDetonateAnimation(ids[1], TobMode.NORMAL),
        )
        assertEquals(
            TobAssets.NYLOCAS_MAGIC_DETONATE,
            TobAssets.nylocasDetonateAnimation(ids[2], TobMode.NORMAL),
        )
    }

    @Test
    fun `rooms retain canonical encounter order`() {
        assertEquals(
            listOf(
                TobRoom.MAIDEN,
                TobRoom.BLOAT,
                TobRoom.NYLOCAS,
                TobRoom.SOTETSEG,
                TobRoom.XARPUS,
                TobRoom.VERZIK,
                TobRoom.TREASURE,
            ),
            TobRoom.entries,
        )
    }

    @Test
    fun `room entrances use stable pre encounter staging tiles`() {
        assertEquals(50 to 30, TobRoom.MAIDEN.entryLocalX to TobRoom.MAIDEN.entryLocalZ)
        assertEquals(57 to 32, TobRoom.BLOAT.entryLocalX to TobRoom.BLOAT.entryLocalZ)
        assertEquals(32 to 59, TobRoom.NYLOCAS.entryLocalX to TobRoom.NYLOCAS.entryLocalZ)
        assertEquals(15 to 5, TobRoom.SOTETSEG.entryLocalX to TobRoom.SOTETSEG.entryLocalZ)
        assertEquals(34 to 25, TobRoom.XARPUS.entryLocalX to TobRoom.XARPUS.entryLocalZ)
        assertEquals(31 to 16, TobRoom.VERZIK.entryLocalX to TobRoom.VERZIK.entryLocalZ)
        assertEquals(37 to 18, TobRoom.TREASURE.entryLocalX to TobRoom.TREASURE.entryLocalZ)
    }

    @Test
    fun `instance locals resolve through copied source map squares`() {
        for (room in TobRoom.entries) {
            val segment = room.segments.first()
            val local = TobInstanceLayout.local(room)
            assertEquals(room.entrySourceLevel, local.level, room.key)
            assertEquals(segment.sourceRegion ushr 8, local.regionZoneX, room.key)
            assertEquals(segment.sourceRegion and 0xFF, local.regionZoneZ, room.key)
            assertEquals(room.entryLocalX, local.localX, room.key)
            assertEquals(room.entryLocalZ, local.localZ, room.key)
        }
        val maiden = TobInstanceLayout.local(TobRoom.MAIDEN)
        assertEquals(0, maiden.level)
        assertEquals(49, maiden.regionZoneX)
        assertEquals(69, maiden.regionZoneZ)
        assertEquals(50, maiden.localX)
        assertEquals(30, maiden.localZ)

        val xarpus = TobInstanceLayout.local(TobRoom.XARPUS)
        assertEquals(1, xarpus.level)
        assertEquals(49, xarpus.regionZoneX)
        assertEquals(68, xarpus.regionZoneZ)

        val maidenEast = TobInstanceLayout.local(TobRoom.MAIDEN, localX = 70, localZ = 10)
        assertEquals(0, maidenEast.level)
        assertEquals(50, maidenEast.regionZoneX)
        assertEquals(69, maidenEast.regionZoneZ)
        assertEquals(6, maidenEast.localX)
        assertEquals(10, maidenEast.localZ)

        val upperMaidenEast =
            TobInstanceLayout.local(TobRoom.MAIDEN, localX = 70, localZ = 10, sourceLevel = 1)
        assertEquals(1, upperMaidenEast.level)
        assertEquals(50, upperMaidenEast.regionZoneX)
        assertEquals(69, upperMaidenEast.regionZoneZ)
    }

    @Test
    fun `every donor room preserves its ground and upper floors`() {
        TobRoom.entries.forEach { room ->
            room.segments.forEach { segment ->
                assertEquals(0..1, TobInstanceLayout.copiedSourceLevels(room, segment))
            }
        }
        assertEquals(0, TobInstanceLayout.local(TobRoom.MAIDEN, boss = true).level)
        assertEquals(0, TobInstanceLayout.completion(TobRoom.MAIDEN).level)
        assertEquals(
            TobInstanceLayout.local(TobRoom.MAIDEN),
            TobInstanceLayout.spectator(TobRoom.MAIDEN),
        )
        assertEquals(0, TobInstanceLayout.spectator(TobRoom.MAIDEN).level)
    }

    @Test
    fun `maiden barrier crosses both directions on the donor ground plane`() {
        val barrier = org.rsmod.map.CoordGrid(100, 200, 0)
        val fromStaging = org.rsmod.map.CoordGrid(102, 200, 0)
        assertEquals(
            org.rsmod.map.CoordGrid(98, 200, 0),
            maidenBarrierDestination(fromStaging, barrier),
        )
        assertTrue(isEnteringMaidenArena(fromStaging, barrier))

        val fromArena = org.rsmod.map.CoordGrid(98, 200, 0)
        assertEquals(
            org.rsmod.map.CoordGrid(102, 200, 0),
            maidenBarrierDestination(fromArena, barrier),
        )
        assertFalse(isEnteringMaidenArena(fromArena, barrier))
    }

    @Test
    fun `maiden entry matches the donor entrance tile`() {
        assertEquals(50 to 30, TobRoom.MAIDEN.entryLocalX to TobRoom.MAIDEN.entryLocalZ)
        assertEquals(0, TobRoom.MAIDEN.entrySourceLevel)
    }

    @Test
    fun `completed challenge barriers preserve direct progression before Xarpus`() {
        assertTrue(completedBarrierAdvancesRoom(TobRoom.MAIDEN))
        assertTrue(completedBarrierAdvancesRoom(TobRoom.BLOAT))
        assertTrue(completedBarrierAdvancesRoom(TobRoom.NYLOCAS))
        assertTrue(completedBarrierAdvancesRoom(TobRoom.SOTETSEG))

        assertFalse(completedBarrierAdvancesRoom(TobRoom.XARPUS))
        assertFalse(completedBarrierAdvancesRoom(TobRoom.VERZIK))
        assertFalse(completedBarrierAdvancesRoom(TobRoom.TREASURE))
    }

    @Test
    fun `completed Xarpus barrier opens only the skeleton staging side`() {
        val arena = org.rsmod.map.CoordGrid(100, 192, 1)
        val exitCenter = org.rsmod.map.CoordGrid(100, 200, 1)
        for (x in 99..101) {
            val barrier = org.rsmod.map.CoordGrid(x, 200, 1)
            val player = org.rsmod.map.CoordGrid(x, 199, 1)
            val destination = maidenBarrierDestination(player, barrier)
            assertTrue(
                isXarpusStagingBarrierCrossing(player, destination, barrier, exitCenter, arena)
            )
        }

        val entranceBarrier = org.rsmod.map.CoordGrid(100, 184, 1)
        val entrancePlayer = org.rsmod.map.CoordGrid(100, 183, 1)
        assertFalse(
            isXarpusStagingBarrierCrossing(
                entrancePlayer,
                maidenBarrierDestination(entrancePlayer, entranceBarrier),
                entranceBarrier,
                exitCenter,
                arena,
            )
        )

        val stagingPlayer = org.rsmod.map.CoordGrid(100, 201, 1)
        assertFalse(
            isXarpusStagingBarrierCrossing(
                stagingPlayer,
                maidenBarrierDestination(stagingPlayer, exitCenter),
                exitCenter,
                exitCenter,
                arena,
            )
        )
    }

    @Test
    fun `shared challenge barrier is accepted in every combat room`() {
        assertTrue(TobRoom.MAIDEN.hasChallengeBarrier)
        assertTrue(TobRoom.BLOAT.hasChallengeBarrier)
        assertTrue(TobRoom.NYLOCAS.hasChallengeBarrier)
        assertTrue(TobRoom.SOTETSEG.hasChallengeBarrier)
        assertTrue(TobRoom.XARPUS.hasChallengeBarrier)
        assertTrue(TobRoom.VERZIK.hasChallengeBarrier)
        assertFalse(TobRoom.TREASURE.hasChallengeBarrier)
    }

    @Test
    fun `combat encounters start at the room barrier instead of the entry passage`() {
        assertTrue(
            shouldStartChallengeAtBarrier(
                TobRoom.NYLOCAS.ordinal,
                TobRoom.NYLOCAS.ordinal,
                TobRoomState.READY,
            )
        )
        assertFalse(
            shouldStartChallengeAtBarrier(
                TobRoom.NYLOCAS.ordinal,
                TobRoom.NYLOCAS.ordinal,
                TobRoomState.ACTIVE,
            )
        )
        assertFalse(
            shouldStartChallengeAtBarrier(
                TobRoom.VERZIK.ordinal,
                TobRoom.VERZIK.ordinal,
                TobRoomState.ACTIVE,
            )
        )
    }

    @Test
    fun `room progress is monotonic and only permits the next room`() {
        val source = TobRoomProgress(TobRoom.XARPUS, TobRoom.XARPUS.ordinal, TobRoomState.COMPLETE)
        val next = TobRoomProgress(TobRoom.VERZIK, TobRoom.VERZIK.ordinal, TobRoomState.LOCKED)
        val previous =
            TobRoomProgress(TobRoom.SOTETSEG, TobRoom.SOTETSEG.ordinal, TobRoomState.COMPLETE)
        assertTrue(canAdvanceTobRoom(source, next))
        next.state = TobRoomState.ACTIVE
        assertTrue(canAdvanceTobRoom(source, next))
        next.state = TobRoomState.COMPLETE
        assertFalse(canAdvanceTobRoom(source, next))
        assertFalse(canAdvanceTobRoom(source, previous))
        source.state = TobRoomState.ACTIVE
        next.state = TobRoomState.LOCKED
        assertFalse(canAdvanceTobRoom(source, next))
    }

    @Test
    fun `donor passages resolve from the players current room`() {
        assertEquals(
            TobRoom.BLOAT,
            TobPassage.forClick(TobLocs.FORMIDABLE_PASSAGE, TobRoom.MAIDEN)?.destination,
        )
        assertEquals(
            TobRoom.NYLOCAS,
            TobPassage.forClick(TobLocs.FORMIDABLE_PASSAGE, TobRoom.BLOAT)?.destination,
        )
        assertEquals(
            TobRoom.SOTETSEG,
            TobPassage.forClick(TobLocs.FORMIDABLE_PASSAGE, TobRoom.NYLOCAS)?.destination,
        )
        assertEquals(
            TobRoom.XARPUS,
            TobPassage.forClick(TobLocs.FORMIDABLE_PASSAGE, TobRoom.SOTETSEG)?.destination,
        )
        assertEquals(
            TobRoom.VERZIK,
            TobPassage.forClick(TobLocs.XARPUS_DOOR, TobRoom.XARPUS)?.destination,
        )
        assertEquals(
            TobRoom.TREASURE,
            TobPassage.forClick(TobLocs.TREASURE_ROOM, TobRoom.VERZIK)?.destination,
        )
        assertEquals(null, TobPassage.forClick(TobLocs.XARPUS_DOOR, TobRoom.MAIDEN))
    }

    @Test
    fun `collision blocked forward passages do not wait for an impossible arrival`() {
        assertFalse(tobPassageRequiresArrival(TobLocs.FORMIDABLE_PASSAGE))
        assertTrue(tobPassageRequiresArrival(TobLocs.XARPUS_DOOR))
        assertFalse(tobPassageRequiresArrival(TobLocs.TREASURE_ROOM))
    }

    @Test
    fun `suppressed raid npc death is reconciled only after repository removal`() {
        assertTrue(
            shouldReconcileSuppressedRaidNpcDeath(
                suppressRewards = true,
                isSlotAssigned = false,
                hitpoints = 0,
            )
        )
        assertFalse(
            shouldReconcileSuppressedRaidNpcDeath(
                suppressRewards = true,
                isSlotAssigned = true,
                hitpoints = 0,
            )
        )
        assertFalse(
            shouldReconcileSuppressedRaidNpcDeath(
                suppressRewards = false,
                isSlotAssigned = false,
                hitpoints = 0,
            )
        )
        assertFalse(
            shouldReconcileSuppressedRaidNpcDeath(
                suppressRewards = true,
                isSlotAssigned = false,
                hitpoints = 1,
            )
        )
    }

    @Test
    fun `tob combat owns the no-drop death queue before room completion`() {
        assertEquals(TobNpcDeathRoute.RAID_NO_DROPS, tobNpcDeathRoute(raidOwned = true))
        assertEquals(TobNpcDeathRoute.STANDARD_DROPS, tobNpcDeathRoute(raidOwned = false))
    }

    @Test
    fun `verzik opened passage preserves the animated throne anchor`() {
        assertEquals(32686, TobLocs.VERZIK_THRONE_VISIBLE)
        assertEquals(32736, TobLocs.VERZIK_THRONE_EMPTY)
        assertEquals(32737, TobLocs.VERZIK_THRONE_TRANSFORMING)
        assertEquals(32738, TobLocs.TREASURE_ROOM)
        assertEquals(6400, TobLocs.VERZIK_THRONE_VISIBLE_VARBIT)
        assertEquals(8108, TobAssets.VERZIK_THRONE_TRANSFORM)
        assertEquals(5, VerzikNormalRules.THRONE_OPEN_TICKS)
        assertEquals(30 to 35, TobRoom.VERZIK.bossLocalX to TobRoom.VERZIK.bossLocalZ)
        assertEquals(30 to 36, TobLocs.VERZIK_THRONE_LOCAL_X to TobLocs.VERZIK_THRONE_LOCAL_Z)
        assertEquals(
            org.rsmod.map.CoordGrid(30, 36, 0),
            verzikTreasurePassageAnchor(org.rsmod.map.CoordGrid(30, 36, 0)),
        )
        assertFalse(isVerzikEmptyThroneVisible(VerzikEncounterStage.DORMANT))
        assertFalse(isVerzikEmptyThroneVisible(VerzikEncounterStage.PHASE_ONE))
        assertTrue(isVerzikEmptyThroneVisible(VerzikEncounterStage.PHASE_ONE_TO_TWO))
        assertTrue(isVerzikEmptyThroneVisible(VerzikEncounterStage.PHASE_TWO))
        assertTrue(isVerzikEmptyThroneVisible(VerzikEncounterStage.PHASE_TWO_TO_THREE))
        assertTrue(isVerzikEmptyThroneVisible(VerzikEncounterStage.PHASE_THREE))
        assertTrue(isVerzikEmptyThroneVisible(VerzikEncounterStage.ENRAGED))
        assertTrue(isVerzikEmptyThroneVisible(VerzikEncounterStage.DYING))
        assertFalse(isVerzikEmptyThroneVisible(VerzikEncounterStage.COMPLETE))
    }

    @Test
    fun `monumental chest varbits select the cache native purple beam`() {
        assertEquals(
            linkedMapOf(33086 to 6450, 33087 to 6451, 33088 to 6452, 33089 to 6453, 33090 to 6454),
            TobLocs.rewardChestVarbits,
        )
        assertEquals(0, tobRewardChestState(isOwner = false, isUnique = false))
        assertEquals(1, tobRewardChestState(isOwner = false, isUnique = true))
        assertEquals(2, tobRewardChestState(isOwner = true, isUnique = false))
        assertEquals(3, tobRewardChestState(isOwner = true, isUnique = true))
    }

    @Test
    fun `reward chances match mode rates at maximum score`() {
        assertEquals(5, TobRewardRules.PURPLE_CHEST_DENOMINATOR)
        assertEquals(0.0, TobRewardRules.teamUniqueChance(TobMode.ENTRY, 32, 1))
        assertEquals(0.0, TobRewardRules.teamUniqueChance(TobMode.NORMAL, 0, 1))
        assertEquals(0.2, TobRewardRules.teamUniqueChance(TobMode.NORMAL, 32, 1), 0.0000001)
        assertEquals(0.2, TobRewardRules.teamUniqueChance(TobMode.HARD, 32, 1), 0.0000001)
        assertEquals(0.2, TobRewardRules.teamUniqueChance(TobMode.NORMAL, 16, 1), 0.0000001)
        assertTrue(TobRewardRules.isUniqueRoll(0.2, 1))
        assertFalse(TobRewardRules.isUniqueRoll(0.2, 2))
        assertFalse(TobRewardRules.isUniqueRoll(0.0, 1))
        assertTrue(TobRewardRules.isUniqueRoll(0.0, 5, forceUnique = true))
    }

    @Test
    fun `chest test command seeds unique eligibility without replacing earned score`() {
        assertEquals(1, tobChestTestScore(0))
        assertEquals(32, tobChestTestScore(32))
    }

    @Test
    fun `unique table weights and hard mode hilt adjustment are stable`() {
        assertEquals(19, TobRewardRules.normalUniques.sumOf { it.weight })
        assertEquals(18, TobRewardRules.hardUniques.sumOf { it.weight })
        assertEquals(22477, TobRewardRules.uniqueAtTicket(TobMode.NORMAL, 0).objId)
        assertEquals(22486, TobRewardRules.uniqueAtTicket(TobMode.NORMAL, 18).objId)
    }

    @Test
    fun `hard mode timers and common loot multipliers match party size`() {
        assertEquals(7000, TobRewardRules.hardTimeTicks(1))
        assertEquals(3500, TobRewardRules.hardTimeTicks(2))
        assertEquals(3000, TobRewardRules.hardTimeTicks(3))
        assertEquals(2700, TobRewardRules.hardTimeTicks(4))
        assertEquals(2500, TobRewardRules.hardTimeTicks(5))
        assertEquals(1.15, TobRewardRules.commonMultiplier(TobMode.HARD, false))
        assertEquals(1.30, TobRewardRules.commonMultiplier(TobMode.HARD, true))
    }

    @Test
    fun `boss scaling increases with team size and mode`() {
        val solo = TobScaling.hitpoints(1000, 1, TobMode.NORMAL, TobRoom.MAIDEN)
        val five = TobScaling.hitpoints(1000, 5, TobMode.NORMAL, TobRoom.MAIDEN)
        assertTrue(five > solo)
        assertEquals(1000, TobScaling.hitpoints(1000, 1, TobMode.ENTRY, TobRoom.MAIDEN))
        assertEquals(5000, TobScaling.hitpoints(1000, 5, TobMode.ENTRY, TobRoom.MAIDEN))
        assertTrue(TobScaling.hitpoints(1000, 1, TobMode.HARD, TobRoom.BLOAT) > solo)
    }

    @Test
    fun `maiden thresholds trigger once and in order`() {
        val state = MaidenState()
        assertEquals(null, state.threshold(71))
        assertEquals(70, state.threshold(70))
        assertEquals(null, state.threshold(69))
        assertEquals(50, state.threshold(40))
        assertEquals(30, state.threshold(20))
        assertEquals(null, state.threshold(0))
    }

    @Test
    fun `maiden blood and empowerment state matches encounter rules`() {
        val state = MaidenState()
        state.addPool(org.rsmod.map.CoordGrid(1, 2, 0), 10)
        state.empower()
        assertEquals(39, state.magicMaxHit())
        assertEquals(12, state.bloodDamage())
        state.expirePools(9)
        assertEquals(1, state.bloodPools.size)
        state.expirePools(10)
        assertTrue(state.bloodPools.isEmpty())
    }

    @Test
    fun `special encounter hits always use a legal strong queue delay`() {
        assertEquals(1, normalizeTobSpecialHitDelay(-1))
        assertEquals(1, normalizeTobSpecialHitDelay(0))
        assertEquals(1, normalizeTobSpecialHitDelay(1))
        assertEquals(3, normalizeTobSpecialHitDelay(3))
    }

    @Test
    fun `maiden matomenos heals for twice its remaining hitpoints`() {
        assertEquals(150, maidenMatomenosHealAmount(75))
        assertEquals(2, maidenMatomenosHealAmount(1))
        assertEquals(0, maidenMatomenosHealAmount(0))
    }

    @Test
    fun `removed or dead npcs cannot source theatre special hits`() {
        assertTrue(canQueueTobSpecialHit(sourceSlotAssigned = true, sourceHitpoints = 1))
        assertFalse(canQueueTobSpecialHit(sourceSlotAssigned = false, sourceHitpoints = 1))
        assertFalse(canQueueTobSpecialHit(sourceSlotAssigned = true, sourceHitpoints = 0))
        assertFalse(canQueueTobSpecialHit(sourceSlotAssigned = false, sourceHitpoints = 0))
    }

    @Test
    fun `bloat alternates walking and down phases`() {
        val state = BloatState()
        repeat(17) { assertEquals(BloatPhase.WALKING, state.tick()) }
        assertEquals(BloatPhase.DOWN, state.tick())
        repeat(30) { state.tick() }
        assertEquals(BloatPhase.DOWN, state.tick())
        assertTrue(state.stompNow())
        assertEquals(BloatPhase.WALKING, state.tick())
        assertEquals(BloatPhase.WALKING, state.phase)
        assertEquals(1, state.downs)
    }

    @Test
    fun `nylocas uses three waves and caps living spawns`() {
        val state = NylocasWaveState()
        assertEquals(3, state.totalWaves)
        assertTrue(state.canSpawn(11))
        assertFalse(state.canSpawn(12))
        state.nextWave(100)
        state.nextWave(104)
        state.nextWave(108)
        assertFalse(state.canSpawn(0))
        assertEquals(108, state.waveThreeStartedAt)
        assertEquals(125, state.bossSpawnAt)
        assertFalse(state.shouldSpawnBoss(124))
        assertTrue(state.shouldSpawnBoss(125))
        state.markBossSpawned()
        assertFalse(state.shouldSpawnBoss(126))
        val forms = intArrayOf(8355, 8357, 8356)
        assertEquals(8357, state.nextBossForm(8355, forms))
        assertEquals(8356, state.nextBossForm(8357, forms))
        assertEquals(8355, state.nextBossForm(8356, forms))
    }

    @Test
    fun `all mode Nylocas large variants split into matching fighting styles`() {
        TobMode.entries.forEach { mode ->
            val bigIncoming = TobNpcs.nylocasBigIncomingStyles(mode)
            val bigFighting = TobNpcs.nylocasBigFightingStyles(mode)
            val fighting = TobNpcs.nylocasFightingStyles(mode)
            for (style in 0..2) {
                assertEquals(style, TobNpcs.nylocasStyleIndex(bigIncoming[style], mode))
                assertEquals(style, TobNpcs.nylocasStyleIndex(bigFighting[style], mode))
                assertEquals(fighting[style], TobNpcs.nylocasSplitStyle(bigIncoming[style], mode))
                assertEquals(fighting[style], TobNpcs.nylocasSplitStyle(bigFighting[style], mode))
                assertEquals(null, TobNpcs.nylocasSplitStyle(fighting[style], mode))
            }
            assertEquals(12, TobNpcs.nylocasWaveStyles(mode).size)
        }
    }

    @Test
    fun `Vasilias weakness follows its active transmog form in every mode`() {
        TobMode.entries.forEach { mode ->
            val forms = TobNpcs.nylocasBossStyles(mode)
            val spawnId = forms[0]

            forms.forEachIndexed { expectedStyle, visibleId ->
                val activeId = TobNpcs.nylocasActiveStyleId(spawnId, visibleId, mode)
                assertEquals(expectedStyle, TobNpcs.nylocasStyleIndex(activeId, mode))
            }

            val smallNylocas = TobNpcs.nylocasStyles(mode)[1]
            assertEquals(smallNylocas, TobNpcs.nylocasActiveStyleId(smallNylocas, forms[2], mode))
        }
    }

    @Test
    fun `nylocas routes beside a supporting pillar and only arrives when adjacent`() {
        val pillar = org.rsmod.map.CoordGrid(100, 200, 1)
        val fromWest = org.rsmod.map.CoordGrid(90, 201, 1)
        val fromEast = org.rsmod.map.CoordGrid(110, 201, 1)
        val westApproach = nylocasPillarApproach(fromWest, 1, pillar, 3)
        val eastApproach = nylocasPillarApproach(fromEast, 1, pillar, 3)
        assertEquals(org.rsmod.map.CoordGrid(99, 201, 1), westApproach)
        assertEquals(org.rsmod.map.CoordGrid(103, 201, 1), eastApproach)
        assertEquals(1, squareChebyshevDistance(westApproach, 1, pillar, 3))
        assertEquals(1, squareChebyshevDistance(eastApproach, 1, pillar, 3))
        assertTrue(squareChebyshevDistance(fromWest, 1, pillar, 3) > 1)
    }

    @Test
    fun `sotetseg maze is deterministic connected and non repeating`() {
        val first = SotetsegMazeRules.generate(1234L)
        val second = SotetsegMazeRules.generate(1234L)
        assertEquals(first, second)
        assertEquals(MazeTile(0, 0), first.first())
        assertEquals(14, first.last().z)
        assertTrue(first.size >= 15)
        assertEquals(first.size, first.distinct().size)
        assertTrue(first.all { it.x in -5..5 && it.z in 0..14 })
        first.zipWithNext().forEach { (a, b) ->
            assertEquals(1, kotlin.math.abs(a.x - b.x) + kotlin.math.abs(a.z - b.z))
        }
    }

    @Test
    fun `xarpus and verzik phase machines progress without skipping`() {
        val xarpus = XarpusState(5, TobMode.NORMAL)
        assertEquals(18, xarpus.requiredExhumeds)
        assertEquals(4, xarpus.exhumedSpawnInterval)
        assertEquals(4, xarpus.healPerUnblockedTick)
        repeat(18) { assertEquals(XarpusPhase.EXHUMEDS, xarpus.completeExhumed()) }
        xarpus.forceCombat()
        assertEquals(XarpusPhase.COMBAT, xarpus.updateHealth(26))
        assertEquals(XarpusPhase.SCREECH, xarpus.updateHealth(25))
        assertEquals(XarpusStare.EAST, xarpus.rotateStare())

        val entry = XarpusState(1, TobMode.ENTRY)
        assertEquals(7, entry.requiredExhumeds)
        assertEquals(22, entry.screechPercent)

        val normal = VerzikState(TobMode.NORMAL)
        assertEquals(VerzikEncounterStage.DORMANT, normal.stage)
        assertEquals(VerzikSpecial.ELECTRIC, normal.nextSpecial())
        normal.activate()
        assertEquals(VerzikEncounterStage.PHASE_ONE, normal.stage)
        normal.beginTransition()
        assertEquals(VerzikEncounterStage.PHASE_ONE_TO_TWO, normal.stage)
        assertFalse(normal.completeTransition())
        assertEquals(VerzikEncounterStage.PHASE_TWO, normal.stage)
        assertEquals(VerzikPhase.TWO, normal.phase)
        assertFalse(normal.advance())
        assertEquals(VerzikPhase.THREE, normal.phase)
        normal.markEnraged()
        assertEquals(VerzikEncounterStage.ENRAGED, normal.stage)
        val normalCycle = List(4) { normal.nextSpecial() }
        assertEquals(
            listOf(
                VerzikSpecial.NYLOCAS,
                VerzikSpecial.WEBS,
                VerzikSpecial.YELLOW_POOLS,
                VerzikSpecial.GREEN_BALL,
            ),
            normalCycle,
        )

        val hard = VerzikState(TobMode.HARD)
        hard.advance()
        hard.advance()
        assertEquals(normalCycle, List(4) { hard.nextSpecial() })
        assertTrue(hard.advance())
    }

    @Test
    fun `verzik uses the correct seated transition and combat forms for every mode`() {
        val expected =
            mapOf(
                TobMode.ENTRY to listOf(10830, 10831, 10832, 10833, 10834, 10835, 10836),
                TobMode.NORMAL to listOf(8369, 8370, 8371, 8372, 8373, 8374, 8375),
                TobMode.HARD to listOf(10847, 10848, 10849, 10850, 10851, 10852, 10853),
            )
        expected.forEach { (mode, forms) ->
            val actual =
                listOf(TobNpcs.verzikInitial(mode)) +
                    TobNpcs.verzikPhases(mode)
                        .zip(TobNpcs.verzikTransitions(mode) + TobNpcs.verzikDeath(mode))
                        .flatMap { (phase, transition) -> listOf(phase, transition) }
            assertEquals(forms, actual)
            assertTrue(actual.all(TobNpcs.allCore::contains))
        }
    }

    @Test
    fun `custom encounter seconds round up to whole 600 millisecond game ticks`() {
        assertEquals(17, tobTicksForSeconds(10))
        assertEquals(NYLOCAS_WAVE_THREE_BOSS_DELAY_TICKS, tobTicksForSeconds(10))
        assertEquals(34, tobTicksForSeconds(20))
        assertEquals(XARPUS_RECOVERY_TICKS, tobTicksForSeconds(20))
        assertEquals(10, NYLOCAS_BOSS_FORM_TICKS)
        assertEquals(4, XARPUS_RECOVERY_HEAL)
    }

    @Test
    fun `donor noticeboard exposes all 45 stable party slots`() {
        assertEquals(45, TobPartyBoard.BOARD_SLOTS.size)
        assertEquals(45, TobPartyBoard.BOARD_SLOTS.toSet().size)
        assertEquals((0..44).toSet(), TobPartyBoard.BOARD_SLOTS.toSet())
    }

    @Test
    fun `donor lobby invite option is restricted to the recruiting bounds`() {
        assertTrue(isTobLobby(org.rsmod.map.CoordGrid(3640, 3201, 0)))
        assertTrue(isTobLobby(org.rsmod.map.CoordGrid(3680, 3235, 0)))
        assertFalse(isTobLobby(org.rsmod.map.CoordGrid(3639, 3201, 0)))
        assertFalse(isTobLobby(org.rsmod.map.CoordGrid(3681, 3235, 0)))
        assertFalse(isTobLobby(org.rsmod.map.CoordGrid(3640, 3200, 0)))
        assertFalse(isTobLobby(org.rsmod.map.CoordGrid(3680, 3236, 0)))
        assertFalse(isTobLobby(org.rsmod.map.CoordGrid(3640, 3201, 1)))
    }
}

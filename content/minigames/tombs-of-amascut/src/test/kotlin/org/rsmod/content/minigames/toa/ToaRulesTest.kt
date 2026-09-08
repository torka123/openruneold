package org.rsmod.content.minigames.toa

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ToaRulesTest {
    @Test
    fun `all forty four standard invocations are represented`() {
        assertEquals(44, ToaInvocation.entries.size)
        assertEquals(770, ToaInvocation.entries.sumOf(ToaInvocation::raidLevel))
    }

    @Test
    fun `raid start snapshot defensively copies locked collections`() {
        val invocations = linkedSetOf(ToaInvocation.QUIET_PRAYERS)
        val pathLevels = TOA_ACTIVE_PATHS.associateWithTo(linkedMapOf()) { 0 }
        val participantAccountHashes = linkedSetOf(101L, 202L)
        val snapshot =
            ToaRaidStartSnapshot.create(
                partySize = 2,
                raidLevel = 20,
                invocations = invocations,
                pathLevels = pathLevels,
                seed = 1234L,
                participantAccountHashes = participantAccountHashes,
            )

        invocations.clear()
        pathLevels[ToaPath.SCABARAS] = 4
        participantAccountHashes.clear()

        assertEquals(setOf(ToaInvocation.QUIET_PRAYERS), snapshot.invocations)
        assertEquals(0, snapshot.pathLevels.getValue(ToaPath.SCABARAS))
        assertEquals(setOf(101L, 202L), snapshot.participantAccountHashes)
        assertFailsWith<UnsupportedOperationException> {
            (snapshot.invocations as MutableSet<ToaInvocation>).clear()
        }
        assertFailsWith<UnsupportedOperationException> {
            (snapshot.pathLevels as MutableMap<ToaPath, Int>)[ToaPath.SCABARAS] = 4
        }
    }

    @Test
    fun `party list controller distinguishes make party from my party`() {
        assertEquals(-1, ToaPartyBoard.partyController(hasParty = false))
        assertEquals(0, ToaPartyBoard.partyController(hasParty = true))
        assertEquals(-1..-1, ToaPartyBoard.BUTTON_OP_SLOTS)
    }

    @Test
    fun `revision 240 party rows use distinct components with subcomponent zero`() {
        val expected = (0 until ToaPartyBoard.BOARD_SLOTS).map { (772 shl 16) or (17 + it) }
        assertEquals(expected, ToaPartyBoard.PARTY_LIST_ROW_COMPONENTS)
        expected.forEachIndexed { slot, component ->
            assertEquals(slot, ToaPartyBoard.partyListSlotFromComponent(component))
        }
        assertEquals(0..0, ToaPartyBoard.PARTY_LIST_ROW_OP_SLOTS)
        assertNull(ToaPartyBoard.partyListSlotFromComponent((772 shl 16) or 16))
        assertNull(ToaPartyBoard.partyListSlotFromComponent((772 shl 16) or 62))
        assertNull(ToaPartyBoard.partyListSlotFromComponent((774 shl 16) or 17))
    }

    @Test
    fun `revision 240 party detail events cover every handled root and row`() {
        assertEquals(
            setOf(4, 6, 7, 8, 9, 62, 63, 64, 69, 70, 71, 72, 83, 84).mapTo(mutableSetOf()) {
                (774 shl 16) or it
            },
            ToaPartyBoard.PARTY_DETAILS_ROOT_OP_COMPONENTS,
        )
        assertEquals(0 until TOA_MAX_PARTY_SIZE, ToaPartyBoard.PARTY_DETAILS_MEMBER_OP_SLOTS)
        assertEquals(0 until TOA_MAX_PARTY_SIZE, ToaPartyBoard.PARTY_DETAILS_APPLICANT_OP_SLOTS)
    }

    @Test
    fun `revision 240 invocation clicks use only explicit row roots`() {
        assertEquals((0..129 step 3).toSet(), ToaInvocation.interfaceSubcomponents)
        assertEquals(ToaInvocation.TRY_AGAIN, ToaInvocation.fromInterfaceSubcomponent(0))
        assertEquals(ToaInvocation.BOULDERDASH, ToaInvocation.fromInterfaceSubcomponent(111))
        assertEquals(ToaInvocation.ANCIENT_HASTE, ToaInvocation.fromInterfaceSubcomponent(114))
        assertEquals(ToaInvocation.ACCELERATION, ToaInvocation.fromInterfaceSubcomponent(117))
        assertEquals(ToaInvocation.PENETRATION, ToaInvocation.fromInterfaceSubcomponent(120))
        assertEquals(ToaInvocation.OVERCLOCKED, ToaInvocation.fromInterfaceSubcomponent(123))
        assertEquals(ToaInvocation.OVERCLOCKED_2, ToaInvocation.fromInterfaceSubcomponent(126))
        assertEquals(ToaInvocation.INSANITY, ToaInvocation.fromInterfaceSubcomponent(129))
        assertTrue(ToaInvocation.ANCIENT_HASTE.selectable)
        listOf(1, 2, 112, 113, 115, 116, 127, 128, 132, 135).forEach {
            assertNull(ToaInvocation.fromInterfaceSubcomponent(it))
        }
    }

    @Test
    fun `raid mode boundaries are stable`() {
        assertEquals(ToaMode.ENTRY, ToaRules.mode(0))
        assertEquals(ToaMode.ENTRY, ToaRules.mode(149))
        assertEquals(ToaMode.NORMAL, ToaRules.mode(150))
        assertEquals(ToaMode.NORMAL, ToaRules.mode(299))
        assertEquals(ToaMode.EXPERT, ToaRules.mode(300))
        assertEquals(ToaMode.EXPERT, ToaRules.mode(600))
    }

    @Test
    fun `wardens unlock requires Kephri and all four path bosses`() {
        val completedWithoutKephri = TOA_ACTIVE_PATHS - ToaPath.SCABARAS

        assertFalse(isToaWardensUnlocked(completedWithoutKephri))
        assertTrue(isToaWardensUnlocked(TOA_ACTIVE_PATHS))
    }

    @Test
    fun `shared eight player party hp scale uses ninety percent then sixty percent additions`() {
        assertEquals(1.0, ToaRules.partyHitpointsFactor(1))
        assertEquals(1.9, ToaRules.partyHitpointsFactor(2))
        assertEquals(2.8, ToaRules.partyHitpointsFactor(3))
        assertEquals(3.4, ToaRules.partyHitpointsFactor(4))
        assertEquals(4.6, ToaRules.partyHitpointsFactor(6))
        assertEquals(5.2, ToaRules.partyHitpointsFactor(7), 0.0000001)
        assertEquals(5.8, ToaRules.partyHitpointsFactor(8), 0.0000001)
        assertEquals(8, TOA_MAX_PARTY_SIZE)
    }

    @Test
    fun `every five raid levels add two percent and damage caps`() {
        assertEquals(1.0, ToaRules.raidStatFactor(0))
        assertEquals(1.02, ToaRules.raidStatFactor(5))
        assertEquals(2.2, ToaRules.raidStatFactor(300))
        assertEquals(2.5, ToaRules.damageFactor(600))
    }

    @Test
    fun `path levels apply eight then five percent`() {
        assertEquals(1.0, ToaRules.pathFactor(0), 0.0000001)
        assertEquals(1.08, ToaRules.pathFactor(1), 0.0000001)
        assertEquals(1.13, ToaRules.pathFactor(2), 0.0000001)
        assertEquals(1.18, ToaRules.pathFactor(3), 0.0000001)
    }

    @Test
    fun `attempt invocations are mutually exclusive`() {
        val set = ToaInvocationSet()
        set.toggle(ToaInvocation.TRY_AGAIN)
        set.toggle(ToaInvocation.HARDCORE_RUN)
        assertEquals(setOf(ToaInvocation.HARDCORE_RUN), set.all())
        assertEquals(1, ToaRules.attemptLimit(set.all()))
    }

    @Test
    fun `time and help families replace their sibling`() {
        val set = ToaInvocationSet()
        set.toggle(ToaInvocation.WALK_FOR_IT)
        set.toggle(ToaInvocation.SPRINT_FOR_IT)
        set.toggle(ToaInvocation.NEED_SOME_HELP)
        set.toggle(ToaInvocation.NO_HELP_NEEDED)
        assertTrue(ToaInvocation.SPRINT_FOR_IT in set)
        assertTrue(ToaInvocation.NO_HELP_NEEDED in set)
        assertEquals(25 * 100, ToaRules.timeLimitTicks(set.all()))
        assertEquals(0.10, ToaRules.supplyMultiplier(set.all()))
    }

    @Test
    fun `death removes twenty percent with a minimum one thousand deduction`() {
        assertEquals(8_000, ToaRules.deathPoints(10_000))
        assertEquals(4_000, ToaRules.deathPoints(5_000))
        assertEquals(3_999, ToaRules.deathPoints(4_999))
        assertEquals(0, ToaRules.deathPoints(500))
        assertEquals(0, ToaRules.deathPoints(-1))
    }

    @Test
    fun `reward level and unique chance follow published breakpoints`() {
        assertEquals(310.0, ToaRules.scaledRewardLevel(310))
        assertEquals(350.0, ToaRules.scaledRewardLevel(430))
        assertEquals(360.0, ToaRules.scaledRewardLevel(490))
        assertEquals(0.0, ToaRules.scaledRewardLevel(-1))
        assertEquals(0.01, ToaRules.uniqueChance(10_500, 0), 0.000000001)
        assertEquals(0.01, ToaRules.uniqueChance(3_700, 400), 0.000000001)
        assertEquals(0.55, ToaRules.uniqueChance(1_000_000, 600), 0.000000001)
    }

    @Test
    fun `path difficulty invocation initializes all paths`() {
        val levels = ToaRules.initialPathLevels(setOf(ToaInvocation.PATHMASTER))
        assertEquals(setOf(3), levels.values.toSet())
        assertEquals(4, levels.size)
    }

    @Test
    fun `walk the path levels two remaining paths deterministically`() {
        val a = ToaPath.entries.associateWith { 0 }.toMutableMap()
        val b = ToaPath.entries.associateWith { 0 }.toMutableMap()
        val first = ToaRules.levelRemainingPaths(a, setOf(ToaPath.SCABARAS), Random(42))
        val second = ToaRules.levelRemainingPaths(b, setOf(ToaPath.SCABARAS), Random(42))
        assertEquals(first, second)
        assertEquals(2, first.size)
        assertTrue(first.none { it == ToaPath.SCABARAS })
    }

    @Test
    fun `walk the path first completion only raises active remaining paths`() {
        val levels = TOA_ACTIVE_PATHS.associateWith { 0 }.toMutableMap()
        val raised = ToaRules.levelRemainingPaths(levels, setOf(ToaPath.SCABARAS), Random(42))
        assertEquals(2, raised.size)
        assertTrue(raised.all { it in TOA_ACTIVE_PATHS })
        assertEquals(ToaPath.entries.toSet(), levels.keys)
    }

    @Test
    fun `instance rooms retain native region sources and unique destinations`() {
        assertEquals(12, ToaRoom.entries.size)
        assertEquals(
            12,
            ToaRoom.entries.map { it.destinationZoneX to it.destinationZoneZ }.distinct().size,
        )
        assertEquals(
            listOf(
                ToaRoom.LOBBY,
                ToaRoom.SCABARAS,
                ToaRoom.KEPHRI,
                ToaRoom.HET,
                ToaRoom.AKKHA,
                ToaRoom.APMEKEN,
                ToaRoom.BABA,
                ToaRoom.CRONDIS,
                ToaRoom.ZEBAK,
                ToaRoom.WARDENS,
                ToaRoom.TUMEKEN_WARDEN,
                ToaRoom.REWARD,
            ),
            TOA_ACTIVE_ROOMS,
        )
        assertEquals(setOf(0, 1), TOA_ACTIVE_ROOMS.map(ToaRoom::sourceMaxLevel).toSet())
        assertTrue(ToaPath.entries.all { it.challenge.sourceMaxLevel == 0 })
        assertEquals(14164, ToaRoom.KEPHRI.sourceRegion)
        assertEquals(15698, ToaRoom.CRONDIS.sourceRegion)
        assertEquals(15700, ToaRoom.ZEBAK.sourceRegion)
        assertNotEquals(ToaRoom.REWARD.sourceRegion, ToaRoom.LOBBY.sourceRegion)
    }

    @Test
    fun `instance teleports resolve original source map squares and floors`() {
        assertEquals(
            org.rsmod.api.instances.RegionLocal(0, 55, 80, 31, 42),
            ToaInstanceLayout.local(ToaRoom.LOBBY),
        )
        assertEquals(
            org.rsmod.api.instances.RegionLocal(0, 55, 82, 2, 31),
            ToaInstanceLayout.local(ToaRoom.SCABARAS),
        )
        assertEquals(
            org.rsmod.api.instances.RegionLocal(0, 55, 84, 15, 32),
            ToaInstanceLayout.local(ToaRoom.KEPHRI),
        )
        assertEquals(
            org.rsmod.api.instances.RegionLocal(0, 57, 82, 50, 31),
            ToaInstanceLayout.local(ToaRoom.HET),
        )
        assertEquals(
            org.rsmod.api.instances.RegionLocal(1, 57, 84, 48, 32),
            ToaInstanceLayout.local(ToaRoom.AKKHA),
        )
        assertEquals(
            org.rsmod.api.instances.RegionLocal(0, 59, 82, 16, 31),
            ToaInstanceLayout.local(ToaRoom.APMEKEN),
        )
        assertEquals(
            org.rsmod.api.instances.RegionLocal(0, 59, 84, 14, 31),
            ToaInstanceLayout.local(ToaRoom.BABA),
        )
        assertEquals(
            org.rsmod.api.instances.RegionLocal(0, 61, 82, 50, 31),
            ToaInstanceLayout.local(ToaRoom.CRONDIS),
        )
        assertEquals(
            org.rsmod.api.instances.RegionLocal(0, 61, 84, 54, 31),
            ToaInstanceLayout.local(ToaRoom.ZEBAK),
        )
        assertEquals(
            org.rsmod.api.instances.RegionLocal(1, 59, 80, 31, 56),
            ToaInstanceLayout.local(ToaRoom.WARDENS),
        )
        assertEquals(
            org.rsmod.api.instances.RegionLocal(1, 61, 80, 31, 41),
            ToaInstanceLayout.local(ToaRoom.TUMEKEN_WARDEN),
        )
        assertEquals(
            org.rsmod.api.instances.RegionLocal(0, 57, 80, 33, 49),
            ToaInstanceLayout.local(ToaRoom.REWARD),
        )
        assertFailsWith<IllegalArgumentException> {
            ToaInstanceLayout.roomLocal(ToaRoom.BABA, 64, 32)
        }
    }

    @Test
    fun `current path objects include every challenge before its guardian`() {
        assertEquals(ToaPath.entries.toSet(), ToaLocs.pathEntrances.values.toSet())
        assertEquals(TOA_ACTIVE_PATHS, ToaLocs.pathEntrances.values.toSet())
        assertTrue(ToaLocs.disabledPathEntrances.isEmpty())
        assertTrue(ToaLocs.disabledBossCrystals.isEmpty())
        assertEquals(ToaRoom.ZEBAK, ToaPath.CRONDIS.boss)
        assertTrue(ToaPath.entries.all { it.challenge in TOA_ACTIVE_ROOMS })
        assertEquals(ToaPath.SCABARAS, ToaLocs.bossCrystals[45505])
        assertEquals(ToaPath.CRONDIS, ToaLocs.bossCrystals[45506])
        assertEquals(ToaPath.APMEKEN, ToaLocs.bossCrystals[45754])
        assertEquals(ToaPath.HET, ToaLocs.bossCrystals[45866])
    }

    @Test
    fun `donor lobby and completion route uses native objects`() {
        assertEquals(46068, ToaLocs.GROUPING_OBELISK)
        assertEquals(46089, ToaLocs.START)
        assertEquals(46167, ToaLocs.WARDENS_PATH_LOCKED)
        assertEquals(46168, ToaLocs.WARDENS_PATH)
        assertEquals(45579, ToaLocs.WARDENS_CRYSTAL)
        assertEquals(45138, ToaLocs.REWARD_ENTRY)
        assertEquals(
            org.rsmod.api.instances.RegionLocal(1, 61, 80, 32, 34),
            ToaInstanceLayout.rewardEntryLocal(),
        )
        assertEquals(setOf(45128, 45453), ToaLocs.leaveRaid)
        assertEquals(setOf(45129, 45144, 45543, 45844, 46055), ToaLocs.returnToLobby)
    }

    @Test
    fun `akkha regular forms use one matching overhead`() {
        assertEquals(listOf(11790, 11791, 11792), (0..2).map(::akkhaFormId))
        assertEquals(org.rsmod.game.hit.HitType.Melee, akkhaProtectedHitType(0))
        assertEquals(org.rsmod.game.hit.HitType.Ranged, akkhaProtectedHitType(1))
        assertEquals(org.rsmod.game.hit.HitType.Magic, akkhaProtectedHitType(2))
    }

    @Test
    fun `akkha special cadence is seven attacks or six with stay vigilant`() {
        assertEquals(7, akkhaAttacksBeforeSpecial(stayVigilant = false))
        assertEquals(6, akkhaAttacksBeforeSpecial(stayVigilant = true))
    }

    @Test
    fun `Akkha autos use captured impact timing and magic impact asset`() {
        assertEquals(1, akkhaImpactDelay(ToaCombatStyle.MELEE))
        assertEquals(3, akkhaImpactDelay(ToaCombatStyle.RANGED))
        assertEquals(3, akkhaImpactDelay(ToaCombatStyle.MAGIC))
        assertEquals(2254, AkkhaAssets.MAGIC_IMPACT)
    }

    @Test
    fun `akkha memory blast visits only adjacent quadrants`() {
        repeat(AkkhaQuadrant.entries.size) { start ->
            val sequence = akkhaMemorySequence(start, 8)
            sequence.zipWithNext().forEach { (first, second) ->
                val distance = kotlin.math.abs(first.ordinal - second.ordinal)
                assertTrue(distance == 1 || distance == AkkhaQuadrant.entries.lastIndex)
            }
        }
    }

    @Test
    fun `akkha quadrants use the native clockwise element layout`() {
        assertEquals(AkkhaQuadrant.BURN, AkkhaArenaLayout.quadrant(31, 33))
        assertEquals(AkkhaQuadrant.DARKNESS, AkkhaArenaLayout.quadrant(33, 33))
        assertEquals(AkkhaQuadrant.LIGHTNING, AkkhaArenaLayout.quadrant(33, 31))
        assertEquals(AkkhaQuadrant.FREEZE, AkkhaArenaLayout.quadrant(31, 31))
    }

    @Test
    fun `akkha detonation swaps plus and diagonal shapes`() {
        val origin = org.rsmod.map.CoordGrid(100, 100, 0)
        assertTrue(
            isAkkhaDetonationTile(org.rsmod.map.CoordGrid(101, 100, 0), origin, diagonal = false)
        )
        assertFalse(
            isAkkhaDetonationTile(org.rsmod.map.CoordGrid(101, 101, 0), origin, diagonal = false)
        )
        assertTrue(
            isAkkhaDetonationTile(org.rsmod.map.CoordGrid(101, 101, 0), origin, diagonal = true)
        )
        assertFalse(
            isAkkhaDetonationTile(org.rsmod.map.CoordGrid(101, 100, 0), origin, diagonal = true)
        )
    }

    @Test
    fun `akkha shadows are stationary before repository registration`() {
        assertTrue(AkkhaShadowLifecycle.requiresStationarySpawn(AkkhaAssets.SHADOW, ToaNpcRole.ADD))
        assertTrue(
            AkkhaShadowLifecycle.requiresStationarySpawn(AkkhaAssets.SHADOW_ENRAGE, ToaNpcRole.ADD)
        )
        assertTrue(
            AkkhaShadowLifecycle.requiresStationarySpawn(AkkhaAssets.HEADBAR, ToaNpcRole.HAZARD)
        )
        assertFalse(
            AkkhaShadowLifecycle.requiresStationarySpawn(AkkhaAssets.SHADOW, ToaNpcRole.BOSS)
        )
        assertFalse(AkkhaShadowLifecycle.requiresStationarySpawn(ToaNpcs.KEPHRI, ToaNpcRole.ADD))
    }

    @Test
    fun `akkha shadows use native anchor tiles and outward initial facings`() {
        val center = org.rsmod.map.CoordGrid(1_000, 2_000, 1)
        val expected =
            mapOf(
                AkkhaQuadrant.BURN to
                    (org.rsmod.map.CoordGrid(994, 2_004, 1) to
                        org.rsmod.game.map.Direction.NorthWest),
                AkkhaQuadrant.DARKNESS to
                    (org.rsmod.map.CoordGrid(1_005, 2_004, 1) to
                        org.rsmod.game.map.Direction.NorthEast),
                AkkhaQuadrant.LIGHTNING to
                    (org.rsmod.map.CoordGrid(1_005, 1_993, 1) to
                        org.rsmod.game.map.Direction.SouthEast),
                AkkhaQuadrant.FREEZE to
                    (org.rsmod.map.CoordGrid(994, 1_993, 1) to
                        org.rsmod.game.map.Direction.SouthWest),
            )

        expected.forEach { (quadrant, expectation) ->
            assertEquals(expectation.first, AkkhaShadowLifecycle.spawnTile(center, quadrant))
            assertEquals(expectation.second, AkkhaShadowLifecycle.spawnDirection(quadrant))
        }
    }

    @Test
    fun `akkha hourglass timers occupy all four native outer corners`() {
        val center = org.rsmod.map.CoordGrid(1_000, 2_000, 1)
        assertEquals(
            org.rsmod.map.CoordGrid(991, 2_008, 1),
            AkkhaShadowLifecycle.hourglassTile(center, AkkhaQuadrant.BURN),
        )
        assertEquals(
            org.rsmod.map.CoordGrid(1_009, 2_008, 1),
            AkkhaShadowLifecycle.hourglassTile(center, AkkhaQuadrant.DARKNESS),
        )
        assertEquals(
            org.rsmod.map.CoordGrid(1_009, 1_990, 1),
            AkkhaShadowLifecycle.hourglassTile(center, AkkhaQuadrant.LIGHTNING),
        )
        assertEquals(
            org.rsmod.map.CoordGrid(991, 1_990, 1),
            AkkhaShadowLifecycle.hourglassTile(center, AkkhaQuadrant.FREEZE),
        )
    }

    @Test
    fun `akkha final stand restores twenty percent and swaps after the centralized threshold`() {
        assertEquals(200, akkhaFinalHitpoints(1_000))
        assertEquals(3, akkhaFinalSwapHits(1))
        assertEquals(6, akkhaFinalSwapHits(4))
    }

    @Test
    fun `every subset of four paths only unlocks wardens when complete`() {
        val paths = TOA_ACTIVE_PATHS.toList()
        repeat(1 shl paths.size) { mask ->
            val completed = paths.filterIndexed { index, _ -> mask and (1 shl index) != 0 }.toSet()
            assertEquals(
                completed.size == paths.size,
                isToaWardensUnlocked(completed),
                "mask=$mask",
            )
        }
    }

    @Test
    fun `path boss thresholds and native support ids are preserved`() {
        assertEquals(listOf(80, 60, 40, 20), AkkhaTuning.SHADOW_THRESHOLDS.value)
        assertEquals(20, AkkhaTuning.FINAL_STAND_HEAL.value)
        assertEquals(11804, AkkhaAssets.ENRAGE_ORB)
        assertEquals(2, KephriEncounter.RECOVERY_PHASES)
        assertEquals(3, KephriEncounter.FINAL_PHASE)
        assertEquals(listOf(2 to 3, 1 to 3), BabaTuning.ROLLING_THRESHOLDS.value)
        assertEquals(11783, BabaAssets.WEAK_BOULDER)
        assertEquals(listOf(85, 70, 55, 40), ZebakTuning.thresholds.toList())
        assertEquals(11738, ZebakAssets.WAVE)
    }

    @Test
    fun `kephri recovery waves bombs and overlords follow native phase rules`() {
        assertEquals(KephriSwarms.Wave(28, 18), KephriSwarms.wave(1))
        assertEquals(KephriSwarms.Wave(24, 14), KephriSwarms.wave(2))
        assertEquals(KephriSwarms.Wave(0, 0), KephriSwarms.wave(3))
        assertEquals(
            listOf(4, 4, 3, 3, 2, 2, 1, 1),
            listOf(0, 3, 4, 7, 8, 11, 12, 27).map(KephriSwarms::spawnInterval),
        )
        val start = org.rsmod.map.CoordGrid(100, 200, 0)
        val destination = org.rsmod.map.CoordGrid(104, 197, 0)
        assertEquals(
            org.rsmod.map.CoordGrid(101, 199, 0),
            KephriSwarms.nextStep(start, destination),
        )
        assertEquals(1, KephriSwarms.MOVE_INTERVAL)
        assertEquals(0, KephriBombs.radius(aerialAssault = false))
        assertEquals(1, KephriBombs.radius(aerialAssault = true))
        val tile = org.rsmod.map.CoordGrid(100, 200, 0)
        assertEquals(1, KephriBombs.tiles(tile, 0).size)
        assertEquals(9, KephriBombs.tiles(tile, 1).size)
        assertEquals(8, KephriArenaLayout.swarmLanes.size)
        assertEquals(listOf(KephriScarabRanger.NPC), KephriOverlords.ids(1, moreOverlords = false))
        assertEquals(
            listOf(KephriScarabMelee.NPC, KephriScarabMager.NPC),
            KephriOverlords.ids(2, moreOverlords = false),
        )
        assertEquals(2, KephriOverlords.ids(1, moreOverlords = true).size)
        assertEquals(3, KephriOverlords.ids(2, moreOverlords = true).size)
        assertEquals(1, KephriScarabMelee.ATTACK_RANGE)
        assertEquals(8, KephriScarabRanger.ATTACK_RANGE)
        assertEquals(10, KephriScarabMager.ATTACK_RANGE)
        assertEquals(9587, KephriScarabMelee.ANIMATION)
        assertEquals(9588, KephriScarabRanger.ANIMATION)
        assertEquals(2152, KephriScarabRanger.PROJECTILE)
    }

    @Test
    fun `Wardens use revision 240 phase and core definitions`() {
        assertEquals(org.rsmod.game.hit.HitType.Magic, ElidinisWarden.requiredStyle(11753))
        assertEquals(org.rsmod.game.hit.HitType.Ranged, ElidinisWarden.requiredStyle(11754))
        assertEquals(11771, WardenCores.ELIDINIS)
        assertEquals(11770, WardenCores.TUMEKEN)
        assertEquals(11761, ElidinisWarden.FINAL_FORM)
        assertEquals(11762, TumekenWarden.FINAL_FORM)
        assertEquals(listOf(80, 60, 40, 20), TumekenWarden.siphonThresholds.toList())
        assertEquals(5, TumekenWarden.ENRAGE_PERCENT)
    }
}

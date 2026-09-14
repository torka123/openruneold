package org.rsmod.content.minigames.toa

import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.framework
import org.mockito.Mockito.mockingDetails
import org.rsmod.annotations.InternalApi
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.hit.HitType
import org.rsmod.game.proj.ProjAnim
import org.rsmod.map.CoordGrid

/** Native execution of the retained Zebak tuning, RNG and packet ordering, not new mechanics. */
@OptIn(InternalApi::class)
class ZebakNativeCombatTest {
    @Test
    fun `first ordinary waits ten ticks and native AI outside LateCycle cannot consume it`() {
        for (pathLevel in 0..6) {
            ToaNativeCombatFixture().use { f ->
                val player = f.player()
                val raid = f.start(player)
                raid.pathLevels[ToaPath.CRONDIS] = pathLevel
                enterArena(f, player)
                val body = assertNotNull(raid.zebak.body)
                val encounter = f.bossEncounters.of(body)
                val entered = f.clock.cycle
                val first = entered + 10
                assertEquals(first, body.actionDelay)
                assertFalse(body.isBusy)
                assertTrue(body.movementLocked)
                f.attack(body, player)
                f.dispatchLateCycle(entered + 2)
                assertEquals(ZebakState.EMERGING, raid.zebak.state)
                f.dispatchLateCycle(entered + 3)
                assertEquals(ZebakState.NORMAL, raid.zebak.state)
                f.dispatchLateCycle(first - 1)
                f.advance(1)
                f.attack(body, player, op = true)
                assertEquals(0, encounter.attackTracesAfter(0).size)
                f.dispatchLateCycle()
                assertEquals(1, encounter.attackTracesAfter(0).size)
                assertEquals(first, encounter.lastAbilityTick)
                assertEquals(first + ZebakTuning.attackInterval(pathLevel, false), body.actionDelay)
                assertFalse(body.isBusy, "Ordinary native access must finish synchronously")
                assertNull(body.mode, "Explicit native dispatch must not enable mobile AI")
                f.attack(body, player)
                f.dispatchLateCycle()
                assertEquals(1, encounter.attackTracesAfter(0).size)
                f.dispatchLateCycle(body.actionDelay)
                assertEquals(2, encounter.attackTracesAfter(0).size)
                assertTrue(f.selectionRandom.bounds.isEmpty(), "Use the existing Zebak RNG")
                assertTrue(
                    f.accessRandom.bounds.isEmpty(),
                    "Payload must not add accuracy/damage RNG",
                )
            }
            framework().clearInlineMocks()
        }
    }

    @Test
    fun `native ordinary and split fanout match the old shared RNG stream across paths and seeds`() {
        for (pathLevel in listOf(0, 2, 4, 6)) for (seed in 1L..8L) {
            ToaNativeCombatFixture().use { f ->
                val player = f.player()
                val raid = f.start(player, seed = seed)
                raid.pathLevels[ToaPath.CRONDIS] = pathLevel
                enterArena(f, player)
                val body = assertNotNull(raid.zebak.body)
                val encounter = f.bossEncounters.of(body)
                val entered = f.clock.cycle
                val oracle = LegacyRandom(seed xor 0x5A4542414BL)
                oracle.nextInt(2) // firstSpecial is selected when the raid session is constructed.
                val splits = mutableListOf<Pair<Int, ZebakAttackStyle>>()
                val commits = mutableListOf<Int>()
                var nextAuto = entered + 10
                var turn = 0
                val rangedMax = ToaRules.maxHit(16, raid.raidLevel, pathLevel)
                val meleeMax = ToaRules.maxHit(38, raid.raidLevel, pathLevel)
                for (tick in entered + 1..entered + 43) {
                    val due = splits.filter { it.first == tick }
                    due.forEach { oracle.nextInt(rangedMax + 1) }
                    splits.removeAll(due.toSet())
                    var expectedStyle: ZebakAttackStyle? = null
                    if (tick == nextAuto) {
                        val melee = turn++ % 3 == 1
                        player.coords =
                            if (melee) meleeTile(f, raid, body) else tile(f, raid, 37, 32)
                        expectedStyle =
                            if (melee) {
                                oracle.nextInt(meleeMax + 1)
                                oracle.nextInt(ZebakTuning.MELEE_BLEED_CHANCE)
                                ZebakAttackStyle.MELEE
                            } else {
                                if (oracle.nextInt(2) == 0) ZebakAttackStyle.RANGED
                                else ZebakAttackStyle.MAGIC
                            }
                        if (!melee) splits += tick + 4 to expectedStyle
                        commits += tick
                        nextAuto += ZebakTuning.attackInterval(pathLevel, false)
                    }
                    f.dispatchLateCycle(tick)
                    val label = "path=$pathLevel seed=$seed tick=$tick"
                    assertFalse(body.isBusy, label)
                    assertEquals(
                        commits,
                        encounter.attackTracesAfter(0).map { it.commitTick },
                        label,
                    )
                    if (expectedStyle == ZebakAttackStyle.MELEE) {
                        assertEquals("melee", encounter.lastAbilityName, label)
                    } else if (expectedStyle != null) {
                        assertEquals(
                            expectedStyle,
                            raid.zebak.splitProjectiles.single { it.splitAt == tick + 4 }.style,
                            label,
                        )
                    }
                }
                assertEquals(
                    oracle.nextInt(1_000_003),
                    raid.zebak.nextInt(1_000_003),
                    "Shared stream after overlapping split and ordinary opportunities: path=$pathLevel seed=$seed",
                )
                assertTrue(f.selectionRandom.bounds.isEmpty())
                assertTrue(f.accessRandom.bounds.isEmpty())
            }
            framework().clearInlineMocks()
        }
    }

    @Test
    fun `empty targets retry in one tick without style draw then nearest melee overrides retained primary`() {
        ToaNativeCombatFixture().use { f ->
            val leader = f.player()
            val member = f.player(2)
            f.manager.createParty(leader)
            f.joinParty(leader, member)
            val raid = f.start(leader, partySize = 2, seed = 31)
            enterArena(f, leader)
            val body = assertNotNull(raid.zebak.body)
            val encounter = f.bossEncounters.of(body)
            f.dispatchLateCycle(raid.zebak.emergesAt)
            val first = body.actionDelay
            raid.participant(leader.uid)!!.spectating = true
            raid.participant(member.uid)!!.spectating = true
            // Keep both connected: native pause rules still let this empty-target opportunity run.
            f.dispatchLateCycle(first)
            assertEquals(first + 1, body.actionDelay)
            assertEquals(0, encounter.attackTracesAfter(0).size)
            raid.participant(leader.uid)!!.spectating = false
            raid.participant(member.uid)!!.spectating = false
            leader.coords = tile(f, raid, 38, 32)
            member.coords = meleeTile(f, raid, body)
            raid.zebak.primaryTarget = leader.uid
            val oracle = LegacyRandom(31L xor 0x5A4542414BL)
            oracle.nextInt(2)
            val damage = oracle.nextInt(ToaRules.maxHit(38, raid.raidLevel, 0) + 1)
            oracle.nextInt(ZebakTuning.MELEE_BLEED_CHANCE)
            f.dispatchLateCycle(first + 1)
            assertEquals(member.uid, raid.zebak.primaryTarget)
            assertEquals("melee", encounter.lastAbilityName)
            assertEquals(damage, f.hits(member).single().second.damage)
            assertTrue(f.hits(leader).isEmpty())
            assertEquals(oracle.nextInt(997), raid.zebak.nextInt(997))
        }
    }

    @Test
    fun `distance target retains primary and nearest ties use locked roster order`() {
        ToaNativeCombatFixture().use { f ->
            val leader = f.player()
            val member = f.player(2)
            f.manager.createParty(leader)
            f.joinParty(leader, member)
            val raid = f.start(leader, partySize = 2)
            enterArena(f, leader)
            val body = assertNotNull(raid.zebak.body)
            leader.coords = tile(f, raid, 36, 32)
            member.coords = leader.coords
            f.dispatchLateCycle(raid.zebak.emergesAt)
            f.dispatchLateCycle(body.actionDelay)
            assertEquals(leader.uid, raid.zebak.primaryTarget)
            raid.zebak.primaryTarget = member.uid
            leader.coords = tile(f, raid, 32, 32)
            f.dispatchLateCycle(body.actionDelay)
            assertEquals(member.uid, raid.zebak.primaryTarget)
            raid.participant(member.uid)!!.spectating = true
            f.dispatchLateCycle(body.actionDelay)
            assertEquals(leader.uid, raid.zebak.primaryTarget)
        }
    }

    @Test
    fun `due fanout draws precede threshold preparation and special deadline replaces ordinary lock`() {
        for (special in ZebakSpecial.entries) {
            ToaNativeCombatFixture().use { f ->
                val player = f.player()
                val raid = f.start(player, seed = 73)
                enterArena(f, player)
                val body = assertNotNull(raid.zebak.body)
                val encounter = f.bossEncounters.of(body)
                f.dispatchLateCycle(raid.zebak.emergesAt)
                val first = body.actionDelay
                f.dispatchLateCycle(first)
                val oracle = LegacyRandom(73L xor 0x5A4542414BL)
                oracle.nextInt(2)
                oracle.nextInt(2)
                val splitDamage = oracle.nextInt(17)
                val jugCount = ZebakTuning.jugCount(oracle.nextInt(ZebakTuning.JUG_COUNT_ROLLS))
                raid.zebak.firstSpecial = special
                f.hitNpc(player, body, body.baseHitpointsLvl * 16 / 100)
                body.actionDelay = first + 100 // Transition must replace, not max with, this lock.
                f.dispatchLateCycle(first + 4)
                assertEquals(splitDamage, raid.encounter.pendingHits.single().damage)
                assertEquals(jugCount, raid.zebak.pendingJugCount)
                val delay = if (special == ZebakSpecial.WAVES) 14 else 10
                assertEquals(first + 4 + delay, body.actionDelay)
                assertEquals(
                    body.actionDelay - encounter.expectedAttackRate(),
                    encounter.lastAbilityTick,
                )
                assertEquals(1, encounter.attackTracesAfter(0).size)
                assertEquals(oracle.nextInt(997), raid.zebak.nextInt(997))
                assertFalse(body.isBusy)
            }
            framework().clearInlineMocks()
        }
    }

    @Test
    fun `enrage and completed waves replace ordinary deadline at original LateCycle boundary`() {
        for (pathLevel in listOf(0, 2, 4, 6)) {
            ToaNativeCombatFixture().use { f ->
                val player = f.player()
                val raid = f.start(player)
                raid.pathLevels[ToaPath.CRONDIS] = pathLevel
                enterArena(f, player)
                val body = assertNotNull(raid.zebak.body)
                val encounter = f.bossEncounters.of(body)
                f.dispatchLateCycle(raid.zebak.emergesAt)
                body.hitpoints = body.baseHitpointsLvl / 4
                body.actionDelay = f.clock.cycle + 100
                f.dispatchLateCycle()
                val enrageDue = f.clock.cycle + ZebakTuning.attackInterval(pathLevel, true)
                assertEquals(ZebakState.ENRAGED, raid.zebak.state)
                assertEquals(enrageDue, body.actionDelay)
                assertEquals(0, encounter.attackTracesAfter(0).size)
                f.dispatchLateCycle(enrageDue)
                assertEquals(1, encounter.attackTracesAfter(0).size)
                assertFalse(body.isBusy)
                assertNotNull(raid.zebak.phases).enter(ZebakPhase.WAVE_ROW_3)
                body.actionDelay = f.clock.cycle + 100
                f.dispatchLateCycle()
                assertEquals(ZebakState.ENRAGED, raid.zebak.state)
                assertEquals(f.clock.cycle + 3, body.actionDelay)
                f.dispatchLateCycle(body.actionDelay - 1)
                assertEquals(1, encounter.attackTracesAfter(0).size)
                f.dispatchLateCycle(body.actionDelay)
                assertEquals(2, encounter.attackTracesAfter(0).size)
                assertFalse(body.isBusy)
            }
            framework().clearInlineMocks()
        }
    }

    @Test
    fun `split uses new target positions and participation with impact anchored to initial launch`() {
        ToaNativeCombatFixture().use { f ->
            val leader = f.player()
            val member = f.player(2)
            f.manager.createParty(leader)
            f.joinParty(leader, member)
            val raid = f.start(leader, partySize = 2, seed = 55)
            enterArena(f, leader)
            val body = assertNotNull(raid.zebak.body)
            leader.coords = tile(f, raid, 37, 32)
            member.coords = tile(f, raid, 38, 32)
            raid.participant(member.uid)!!.spectating = true
            f.dispatchLateCycle(raid.zebak.emergesAt)
            val launch = body.actionDelay
            f.dispatchLateCycle(launch)
            val initial = projectiles(f).single()
            assertEquals(0, initial.sourceIndex)
            assertEquals(0, initial.targetIndex)
            assertEquals(tile(f, raid, 21, 32), initial.startCoord)
            assertEquals(tile(f, raid, 29, 32), initial.endCoord)
            assertEquals(60, initial.startTime)
            assertEquals(120, initial.endTime)
            assertTrue(
                raid.encounter.pendingHits.isEmpty(),
                "No launch-time damage draw or target hit",
            )
            raid.participant(leader.uid)!!.spectating = true
            raid.participant(member.uid)!!.spectating = false
            member.coords = tile(f, raid, 33, 35)
            f.dispatchLateCycle(launch + 3)
            assertEquals(1, projectiles(f).size)
            f.dispatchLateCycle(launch + 4)
            val fanout = projectiles(f).last()
            assertEquals(-(member.slotId + 1), fanout.targetIndex)
            assertEquals(member.coords, fanout.endCoord)
            val pending = raid.encounter.pendingHits.single()
            assertEquals(member.uid, pending.target)
            assertEquals(member.coords, pending.tile)
            assertEquals(launch + 8, pending.landsAt)
            assertFalse(pending.requireTargetOnTile)
            assertEquals(100, pending.prayerReductionPercent)
            member.coords = tile(f, raid, 35, 36)
            val prayer =
                if (pending.hitType == HitType.Ranged) "varbit.prayer_protectfrommissiles"
                else "varbit.prayer_protectfrommagic"
            VarPlayerIntMapSetter.set(member, prayer, 1)
            f.dispatchLateCycle(launch + 8)
            assertEquals(0, f.hits(member).single().second.damage)
            assertTrue(f.hits(leader).isEmpty())
        }
    }

    @Test
    fun `roar excludes ordinary access and emerging still returns on overdue emergence tick`() {
        for (state in listOf(ZebakState.EMERGING, ZebakState.ROAR)) {
            ToaNativeCombatFixture().use { f ->
                val player = f.player()
                val raid = f.start(player)
                enterArena(f, player)
                val body = assertNotNull(raid.zebak.body)
                val encounter = f.bossEncounters.of(body)
                if (state == ZebakState.ROAR) {
                    f.advance(body.actionDelay - f.clock.cycle)
                    assertNotNull(raid.zebak.phases).enter(ZebakPhase.ROAR_START)
                }
                f.dispatchLateCycle(body.actionDelay)
                assertEquals(0, encounter.attackTracesAfter(0).size)
                assertTrue(projectiles(f).isEmpty())
                assertFalse(body.isBusy)
                if (state == ZebakState.EMERGING) {
                    assertEquals(ZebakState.NORMAL, raid.zebak.state)
                    f.dispatchLateCycle(f.clock.cycle + 1)
                    assertEquals(1, encounter.attackTracesAfter(0).size)
                }
            }
            framework().clearInlineMocks()
        }
    }

    @Test
    fun `preparation and active waves retain ordinary opportunities without leaving native access busy`() {
        for (state in listOf(ZebakState.PREP_ROAR, ZebakState.PREP_WAVES, ZebakState.WAVES)) {
            ToaNativeCombatFixture().use { f ->
                val player = f.player()
                val raid = f.start(player)
                enterArena(f, player)
                val body = assertNotNull(raid.zebak.body)
                val encounter = f.bossEncounters.of(body)
                f.dispatchLateCycle(raid.zebak.emergesAt)
                f.advance(body.actionDelay - f.clock.cycle)
                assertNotNull(raid.zebak.phases)
                    .enter(
                        when (state) {
                            ZebakState.PREP_ROAR -> ZebakPhase.PREP_ROAR
                            ZebakState.PREP_WAVES -> ZebakPhase.PREP_WAVES
                            else -> ZebakPhase.WAVE_START
                        }
                    )
                f.dispatchLateCycle(body.actionDelay)
                assertEquals(state, raid.zebak.state)
                assertEquals(1, encounter.attackTracesAfter(0).size)
                assertEquals(1, raid.zebak.splitProjectiles.size)
                assertFalse(body.isBusy, state.name)
                f.attack(body, player)
                assertEquals(1, encounter.attackTracesAfter(0).size)
            }
            framework().clearInlineMocks()
        }
    }

    @Test
    fun `default world processing cannot launch split or expire helper before retained LateCycle slots`() {
        ToaNativeCombatFixture().use { f ->
            val player = f.player()
            val raid = f.start(player)
            enterArena(f, player)
            val body = assertNotNull(raid.zebak.body)
            val queues = assertNotNull(raid.zebak.attackQueues)
            f.dispatchLateCycle(raid.zebak.emergesAt)
            val launch = body.actionDelay
            f.dispatchLateCycle(launch)
            assertEquals(1, raid.zebak.splitProjectiles.size)
            f.advance(4)
            f.queueProcess.process()
            assertEquals(1, projectiles(f).size)
            assertEquals(1, raid.zebak.splitProjectiles.size)
            assertTrue(raid.zebak.splitVisuals.isEmpty())
            f.dispatchLateCycle()
            assertEquals(2, projectiles(f).size)
            assertTrue(raid.zebak.splitProjectiles.isEmpty())
            val visual = raid.zebak.splitVisuals.single()
            assertEquals(launch + 7, visual.removesAt)
            assertEquals(raid.instance.id, f.instances.instanceForNpc(visual.npc))
            f.dispatchLateCycle(launch + 6)
            assertTrue(visual.npc.isSlotAssigned)
            f.advance(1)
            f.queueProcess.process()
            assertTrue(
                visual.npc.isSlotAssigned,
                "Default world dispatch cannot expire this helper",
            )
            f.dispatchLateCycle()
            assertFalse(visual.npc.isSlotAssigned)
            assertFalse(raid.npcRoles.containsKey(visual.npc))
            assertTrue(raid.zebak.splitVisuals.isEmpty())
            assertFalse(queues.isClosed)
        }
    }

    @Test
    fun `overdue native split batch keeps insertion RNG order and expires old visual in same LateCycle`() {
        ToaNativeCombatFixture().use { f ->
            val player = f.player()
            val seed = 61L
            val raid = f.start(player, seed = seed)
            raid.pathLevels[ToaPath.CRONDIS] = 4
            enterArena(f, player)
            val body = assertNotNull(raid.zebak.body)
            f.dispatchLateCycle(raid.zebak.emergesAt)
            body.hitpoints = body.baseHitpointsLvl / 4
            f.dispatchLateCycle()
            val first = body.actionDelay
            f.dispatchLateCycle(first)
            f.dispatchLateCycle(first + 2)
            val splits = raid.zebak.splitProjectiles.toList()
            assertEquals(listOf(first + 4, first + 6), splits.map { it.splitAt })
            val oracle = LegacyRandom(seed xor 0x5A4542414BL)
            oracle.nextInt(2)
            val firstStyle =
                if (oracle.nextInt(2) == 0) ZebakAttackStyle.RANGED else ZebakAttackStyle.MAGIC
            val secondStyle =
                if (oracle.nextInt(2) == 0) ZebakAttackStyle.RANGED else ZebakAttackStyle.MAGIC
            val maxHit = ToaRules.maxHit(16, raid.raidLevel, 4)
            val damages = listOf(oracle.nextInt(maxHit + 1), oracle.nextInt(maxHit + 1))
            oracle.nextInt(2) // The overdue ordinary opportunity follows both fanout draws.
            f.advance(5)
            f.queueProcess.process()
            assertEquals(2, projectiles(f).size)
            assertEquals(splits, raid.zebak.splitProjectiles)
            f.dispatchLateCycle()
            assertEquals(
                listOf(first + 8, first + 10),
                raid.encounter.pendingHits.map { it.landsAt },
            )
            assertEquals(damages, raid.encounter.pendingHits.map { it.damage })
            val fanout = projectiles(f).drop(2).take(2)
            assertEquals(
                listOf(firstStyle, secondStyle).map {
                    if (it == ZebakAttackStyle.RANGED) ZebakAssets.RANGE_SPLIT
                    else ZebakAssets.MAGIC_SPLIT
                },
                fanout.map { it.spotanim },
            )
            assertTrue(fanout.all { it.targetIndex == -(player.slotId + 1) })
            assertEquals(first + 9, raid.zebak.splitVisuals.single().removesAt)
            assertEquals(1, f.npcList.count { it.id == ZebakAssets.SPLIT_HELPER })
            assertEquals(
                1,
                raid.zebak.splitProjectiles.size,
                "Only the new ordinary launch remains",
            )
            assertEquals(oracle.nextInt(997), raid.zebak.nextInt(997))
        }
    }

    @Test
    fun `split and helper owners survive preparation then enrage cleanup until original expiry`() {
        for (special in ZebakSpecial.entries) {
            ToaNativeCombatFixture().use { f ->
                val player = f.player()
                val raid = f.start(player)
                enterArena(f, player)
                val body = assertNotNull(raid.zebak.body)
                val queues = assertNotNull(raid.zebak.attackQueues)
                f.dispatchLateCycle(raid.zebak.emergesAt)
                val launch = body.actionDelay
                f.dispatchLateCycle(launch)
                raid.zebak.firstSpecial = special
                f.hitNpc(player, body, body.baseHitpointsLvl * 16 / 100)
                f.dispatchLateCycle()
                assertEquals(
                    if (special == ZebakSpecial.WAVES) ZebakState.PREP_WAVES
                    else ZebakState.PREP_ROAR,
                    raid.zebak.state,
                )
                assertSame(queues, raid.zebak.attackQueues)
                assertFalse(queues.isClosed)
                assertEquals(1, raid.zebak.splitProjectiles.size)
                f.dispatchLateCycle(launch + 4)
                assertEquals(1, projectiles(f).count { it.targetIndex == -(player.slotId + 1) })
                val visual = raid.zebak.splitVisuals.single()
                body.hitpoints = body.baseHitpointsLvl / 4
                f.dispatchLateCycle(launch + 5)
                assertEquals(ZebakState.ENRAGED, raid.zebak.state)
                assertFalse(queues.isClosed)
                assertTrue(visual.npc.isSlotAssigned)
                assertEquals(launch + 7, visual.removesAt)
                f.dispatchLateCycle(launch + 7)
                assertFalse(visual.npc.isSlotAssigned)
                assertTrue(raid.zebak.splitVisuals.isEmpty())
                assertFalse(queues.isClosed)
            }
            framework().clearInlineMocks()
        }
    }

    @Test
    fun `death deletion and room reset close live helper and pending split native owners`() {
        for (retirement in listOf("death", "delete", "reset")) {
            ToaNativeCombatFixture().use { f ->
                val player = f.player()
                val raid = f.start(player)
                raid.pathLevels[ToaPath.CRONDIS] = 4
                enterArena(f, player)
                val body = assertNotNull(raid.zebak.body)
                val queues = assertNotNull(raid.zebak.attackQueues)
                f.dispatchLateCycle(raid.zebak.emergesAt)
                body.hitpoints = body.baseHitpointsLvl / 4
                f.dispatchLateCycle()
                val launch = body.actionDelay
                f.dispatchLateCycle(launch)
                f.dispatchLateCycle(launch + 2)
                f.dispatchLateCycle(launch + 4)
                val visual = raid.zebak.splitVisuals.single()
                assertEquals(2, raid.zebak.splitProjectiles.size)
                val projectileCount = projectiles(f).size
                when (retirement) {
                    "death" -> f.manager.handleNpcKill(player, body)
                    "delete" -> f.npcs.del(body, Int.MAX_VALUE)
                    "reset" -> enterArena(f, player)
                }
                assertTrue(queues.isClosed, retirement)
                assertTrue(queues.pendingSplits.isEmpty(), retirement)
                assertTrue(queues.pendingVisuals.isEmpty(), retirement)
                assertFalse(visual.npc.isSlotAssigned, retirement)
                assertFalse(raid.npcRoles.containsKey(visual.npc), retirement)
                assertEquals(
                    if (retirement == "reset") 1 else 0,
                    f.worldQueues.size,
                    "Only a reset's fresh emergence edge may remain: $retirement",
                )
                f.advance(20)
                f.queueProcess.process()
                queues.processSplits()
                queues.processVisuals()
                assertEquals(projectileCount, projectiles(f).size, retirement)
                if (retirement == "reset") {
                    val fresh = assertNotNull(raid.zebak.attackQueues)
                    assertNotSame(queues, fresh)
                    assertFalse(fresh.isClosed)
                    f.dispatchLateCycle() // Preserve the existing overdue emergence return.
                    f.dispatchLateCycle(f.clock.cycle + 1)
                    assertEquals(1, fresh.pendingSplits.size)
                }
            }
            framework().clearInlineMocks()
        }
    }

    @Test
    fun `room teardown retires native dispatch and pending split generation cannot hit replacement raid`() {
        ToaNativeCombatFixture().use { f ->
            val player = f.player()
            val raid = f.start(player)
            enterArena(f, player)
            val body = assertNotNull(raid.zebak.body)
            val encounter = f.bossEncounters.of(body)
            f.dispatchLateCycle(raid.zebak.emergesAt)
            val launch = body.actionDelay
            f.dispatchLateCycle(launch)
            assertEquals(1, raid.zebak.splitProjectiles.size)
            val projectilesBefore = projectiles(f).size
            f.instances.terminate(raid.instance)
            assertTrue(encounter.isEnded)
            assertNull(f.bossEncounters.existing(body))
            assertFalse(body.isSlotAssigned)
            assertTrue(raid.zebak.splitProjectiles.isEmpty())
            val fresh = f.start(player)
            enterArena(f, player)
            f.dispatchLateCycle(launch + 4)
            assertEquals(projectilesBefore, projectiles(f).size)
            assertTrue(f.hits(player).isEmpty())
            assertNotSame(raid, fresh)
        }
    }

    @Test
    fun `native logout before split removes departed recipient without changing remaining cadence`() {
        ToaNativeCombatFixture().use { f ->
            val leader = f.player()
            val member = f.player(2)
            f.manager.createParty(leader)
            f.joinParty(leader, member)
            val raid = f.start(leader, partySize = 2)
            enterArena(f, leader)
            val body = assertNotNull(raid.zebak.body)
            f.dispatchLateCycle(raid.zebak.emergesAt)
            val launch = body.actionDelay
            f.dispatchLateCycle(launch)
            f.events.publish(SessionStateEvent.Logout(member))
            f.dispatchLateCycle(launch + 4)
            assertEquals(listOf(leader.uid), raid.encounter.pendingHits.map { it.target })
            assertEquals(launch + 7, body.actionDelay)
            assertTrue(f.hits(member).isEmpty())
        }
    }

    @Test
    fun `retained death callback clears split payloads and excludes ordinary access during death animation`() {
        ToaNativeCombatFixture().use { f ->
            val player = f.player()
            val raid = f.start(player)
            enterArena(f, player)
            val body = assertNotNull(raid.zebak.body)
            val encounter = f.bossEncounters.of(body)
            f.dispatchLateCycle(raid.zebak.emergesAt)
            val launch = body.actionDelay
            f.dispatchLateCycle(launch)
            assertEquals(1, raid.zebak.splitProjectiles.size)
            val projectilesBefore = projectiles(f).size
            f.manager.handleNpcKill(player, body)
            assertEquals(ZebakState.DYING, raid.zebak.state)
            assertTrue(raid.zebak.splitProjectiles.isEmpty())
            assertTrue(raid.encounter.pendingHits.isEmpty())
            f.attack(body, player)
            f.dispatchLateCycle(launch + 3)
            assertEquals(1, encounter.attackTracesAfter(0).size)
            assertEquals(projectilesBefore, projectiles(f).size)
            assertFalse(body.isBusy)
            assertTrue(f.hits(player).isEmpty())
        }
    }

    private fun enterArena(f: ToaNativeCombatFixture, player: Player): ToaRaidSession {
        val raid = f.enterZebak(player)
        // The developer route enters the waiting area (54,31). These attack tests also model
        // crossing into the existing fight entry (37,32), retaining native membership/room state.
        val arena =
            assertNotNull(
                f.instances.localCoord(raid.instance, ToaInstanceLayout.fightLocal(ToaRoom.ZEBAK))
            )
        raid.participants.values
            .mapNotNull { it.uid.resolve(f.players) }
            .forEach { PathingEntityCommon.telejump(it, f.collision, arena) }
        return raid
    }

    private fun tile(f: ToaNativeCombatFixture, raid: ToaRaidSession, x: Int, z: Int): CoordGrid =
        assertNotNull(
            f.instances.localCoord(raid.instance, ToaInstanceLayout.roomLocal(ToaRoom.ZEBAK, x, z))
        )

    private fun meleeTile(f: ToaNativeCombatFixture, raid: ToaRaidSession, body: Npc): CoordGrid =
        tile(f, raid, ZebakRoomLayout.BODY_LOCAL_X + body.size, ZebakRoomLayout.BODY_LOCAL_Z + 3)

    private fun projectiles(f: ToaNativeCombatFixture): List<ProjAnim> =
        mockingDetails(f.world).invocations.flatMap { it.arguments.filterIsInstance<ProjAnim>() }

    private class LegacyRandom(private var state: Long) {
        fun nextInt(bound: Int): Int {
            state = state xor (state shl 13)
            state = state xor (state ushr 7)
            state = state xor (state shl 17)
            return ((state and Long.MAX_VALUE) % bound).toInt()
        }
    }

    @AfterEach fun releaseMocks() = framework().clearInlineMocks()

    companion object {
        @BeforeAll
        @JvmStatic
        fun cache() {
            ToaCacheTestSupport.initRevision240()
        }
    }
}

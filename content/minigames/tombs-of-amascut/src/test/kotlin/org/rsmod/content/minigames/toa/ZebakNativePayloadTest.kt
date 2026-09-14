package org.rsmod.content.minigames.toa

import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.framework
import org.mockito.Mockito.mockingDetails
import org.rsmod.annotations.InternalApi
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.hit.HitType
import org.rsmod.game.proj.ProjAnim
import org.rsmod.map.CoordGrid

/** Retained runtime/stream oracle at 15166e776; no additional OSRS timing assumptions. */
@OptIn(InternalApi::class)
class ZebakNativePayloadTest {
    @Test
    fun `native blood branch and target rolls retain shared RNG roster order and independent cadence`() {
        for (seed in 1L..8L) {
            ToaNativeCombatFixture().use { f ->
                val leader = f.player()
                val member = f.player(2)
                val raid = start(f, leader, seed, member, thinners = seed % 2L == 0L)
                val body = assertNotNull(raid.zebak.body)
                val encounter = f.bossEncounters.of(body)
                val first = f.clock.cycle + 20
                val oracle = LegacyRandom(seed xor 0x5A4542414BL)
                oracle.nextInt(2)
                val targets = listOf(leader, member)
                for (turn in 0..3) {
                    val due = first + 20 * turn
                    val choice = oracle.nextInt(2)
                    val target = if (choice == 0) targets[oracle.nextInt(2)] else null
                    val previousClouds = raid.zebak.bloodClouds.keys.toSet()
                    f.dispatchLateCycle(due)
                    val label = "seed=$seed turn=$turn"
                    if (target != null) {
                        assertEquals(-(target.slotId + 1), projectiles(f).last().targetIndex, label)
                        val hits = raid.encounter.pendingHits.filter { it.landsAt == due + 2 }
                        assertEquals(targets.map { it.uid }, hits.map { it.target }, label)
                        assertTrue(hits.all { it.zebakBloodHealing && it.hitType == HitType.Magic })
                        assertTrue(
                            hits.all { !it.requireTargetOnTile && it.prayerReductionPercent == 100 }
                        )
                    } else {
                        val clouds =
                            raid.zebak.bloodClouds.filterKeys { it !in previousClouds }.values
                        val count = if (seed % 2L == 0L) 3 else 1
                        assertEquals(count, clouds.size, label)
                        assertEquals(
                            List(count) { targets[it % 2].uid },
                            clouds.map { it.target },
                            label,
                        )
                        assertEquals(
                            List(count) { 38 },
                            clouds.map { it.hitpoints },
                            "Spawn tick decay: $label",
                        )
                        assertTrue(clouds.all { it.contactReady }, label)
                    }
                    assertTrue(encounter.attackTracesAfter(0).isEmpty(), label)
                    assertEquals(Int.MAX_VALUE, body.actionDelay, label)
                }
                assertEquals(oracle.nextInt(1_000_003), raid.zebak.nextInt(1_000_003))
                assertTrue(f.selectionRandom.bounds.isEmpty())
                assertTrue(f.accessRandom.bounds.isEmpty())
            }
            framework().clearInlineMocks()
        }
    }

    @Test
    fun `blood owner waits for eligible targets and re-arms from overdue dispatch across phases`() {
        ToaNativeCombatFixture().use { f ->
            val player = f.player()
            val seed = seedFor(ZebakBloodChoice.BARRAGE)
            val raid = start(f, player, seed)
            val participant = assertNotNull(raid.participant(player.uid))
            val body = assertNotNull(raid.zebak.body)
            participant.spectating = true
            f.advance(100)
            f.queueProcess.process()
            f.dispatchLateCycle()
            assertTrue(projectiles(f).isEmpty())
            assertTrue(raid.zebak.bloodClouds.isEmpty())
            participant.spectating = false
            // Blood is independent of ordinary/special phase identity, as in the old handler.
            assertNotNull(raid.zebak.phases).enter(ZebakPhase.ROAR_START)
            f.dispatchLateCycle()
            val accepted = f.clock.cycle
            assertEquals(1, projectiles(f).size)
            assertEquals(accepted + 2, raid.encounter.pendingHits.single().landsAt)
            assertNotNull(raid.zebak.phases).enter(ZebakPhase.ENRAGED)
            val packets = projectiles(f).size
            f.dispatchLateCycle(accepted + 19)
            assertEquals(packets, projectiles(f).size)
            assertTrue(raid.zebak.bloodClouds.isEmpty())
            f.dispatchLateCycle(accepted + 20)
            assertTrue(projectiles(f).size > packets || raid.zebak.bloodClouds.isNotEmpty())
            assertTrue(f.bossEncounters.of(body).attackTracesAfter(0).isEmpty())
            val oracle = LegacyRandom(seed xor 0x5A4542414BL)
            oracle.nextInt(2)
            repeat(2) { if (oracle.nextInt(2) == 0) oracle.nextInt(1) }
            assertEquals(oracle.nextInt(997), raid.zebak.nextInt(997))
        }
    }

    @Test
    fun `cloud contacts wait for range retain four tick cooldown and preserve launch prayer healing`() {
        for (protected in listOf(false, true)) {
            ToaNativeCombatFixture().use { f ->
                val player = f.player()
                val raid = start(f, player, seedFor(ZebakBloodChoice.CLOUDS))
                val body = assertNotNull(raid.zebak.body)
                val first = f.clock.cycle + 20
                f.dispatchLateCycle(first)
                val cloud = raid.zebak.bloodClouds.values.single()
                assertEquals(38, cloud.hitpoints)
                assertTrue(cloud.contactReady)
                assertTrue(f.hits(player).isEmpty())
                VarPlayerIntMapSetter.set(
                    player,
                    "varbit.prayer_protectfrommagic",
                    if (protected) 1 else 0,
                )
                body.hitpoints = body.baseHitpointsLvl - 100
                cloud.npc.coords = player.coords
                f.dispatchLateCycle(first + 1)
                val damage = if (protected) 0 else ToaRules.maxHit(12, raid.raidLevel, 0)
                assertEquals(damage, f.hits(player).single().second.damage)
                assertEquals(
                    body.baseHitpointsLvl - 100 + ZebakInvocationRules.healAmount(damage, false),
                    body.hitpoints,
                )
                assertFalse(cloud.contactReady)
                cloud.npc.coords = tile(f, raid, 29, 32)
                f.dispatchLateCycle(first + 5)
                assertTrue(cloud.contactReady, "Due readiness remains while outside contact radius")
                assertEquals(1, f.hits(player).size)
                cloud.npc.coords = player.coords
                f.dispatchLateCycle(first + 6)
                assertEquals(2, f.hits(player).size)
                assertFalse(cloud.contactReady)
                f.dispatchLateCycle(first + 9)
                assertEquals(2, f.hits(player).size)
                f.dispatchLateCycle(first + 10)
                assertEquals(3, f.hits(player).size)
                assertTrue(f.hits(player).all { it.second.damage == damage })
            }
            framework().clearInlineMocks()
        }
    }

    @Test
    fun `cloud expiry waits through no-target pause then decays before removal without contact`() {
        ToaNativeCombatFixture().use { f ->
            val player = f.player()
            val raid = start(f, player, seedFor(ZebakBloodChoice.CLOUDS))
            val first = f.clock.cycle + 20
            f.dispatchLateCycle(first)
            val cloud = raid.zebak.bloodClouds.values.single()
            assertEquals(38, cloud.hitpoints)
            assertNotNull(raid.participant(player.uid)).spectating = true
            cloud.npc.coords = player.coords
            f.advance(40)
            f.queueProcess.process()
            f.dispatchLateCycle()
            assertFalse(cloud.expired)
            assertEquals(38, cloud.hitpoints)
            assertTrue(cloud.npc.isSlotAssigned)
            assertNotNull(raid.participant(player.uid)).spectating = false
            f.dispatchLateCycle()
            assertEquals(36, cloud.hitpoints, "One retained decay precedes due expiry")
            assertTrue(cloud.expired)
            assertFalse(cloud.npc.isSlotAssigned)
            assertFalse(raid.zebak.bloodClouds.containsKey(cloud.npc.hashCode()))
            assertNull(cloud.expiryTask)
            assertNull(cloud.contactTask)
            assertFalse(cloud.contactReady)
            assertTrue(f.hits(player).isEmpty(), "Expiry precedes contact even on a matching tile")
        }
    }

    @Test
    fun `crocodile bite readiness survives range misses and real logout rejoin without stale identity`() {
        ToaNativeCombatFixture().use { f ->
            val player = f.player()
            val raid = start(f, player, blood = false)
            val state = raid.zebak
            val start = f.clock.cycle
            player.coords = tile(f, raid, 37, 45)
            state.playerState.getOrPut(player.uid) { ZebakPlayerRuntime() }.swimming = true
            f.dispatchLateCycle()
            val crocodile = state.waterCrocodiles.getValue(player.uid)
            assertFalse(crocodile.biteReady)
            f.advance(5)
            f.queueProcess.process()
            assertFalse(crocodile.biteReady)
            f.dispatchLateCycle()
            assertTrue(crocodile.biteReady, "Out-of-range bite remains due")
            assertTrue(f.hits(player).isEmpty())
            f.events.publish(SessionStateEvent.Logout(player))
            f.players.remove(player.slotId)
            f.advance(10)
            f.dispatchLateCycle()
            val replacement = replacement(f, player)
            assertTrue(f.manager.openLobby(replacement))
            assertSame(crocodile, state.waterCrocodiles.getValue(replacement.uid))
            assertEquals(replacement.uid, crocodile.target)
            replacement.coords = tile(f, raid, 37, 45)
            crocodile.npc.coords = replacement.coords
            f.dispatchLateCycle()
            assertEquals(1, f.hits(replacement).size)
            assertTrue(f.hits(player).isEmpty())
            assertFalse(crocodile.biteReady)
            val bitten = f.clock.cycle
            f.dispatchLateCycle(bitten + 4)
            assertEquals(1, f.hits(replacement).size)
            f.dispatchLateCycle(bitten + 5)
            assertEquals(2, f.hits(replacement).size)
            assertEquals(listOf(18, 18), f.hits(replacement).map { it.second.damage })
            assertTrue(bitten > start + 5)
            replacement.coords = tile(f, raid, 37, 32)
            f.dispatchLateCycle(bitten + 6)
            assertFalse(crocodile.npc.isSlotAssigned)
            assertNull(crocodile.biteTask)
            assertTrue(state.waterCrocodiles.isEmpty())
        }
    }

    @Test
    fun `cloud readiness retains the same runtime through last-player logout and account rebind`() {
        ToaNativeCombatFixture().use { f ->
            val player = f.player()
            val raid = start(f, player, seedFor(ZebakBloodChoice.CLOUDS))
            f.dispatchLateCycle(f.clock.cycle + 20)
            val cloud = raid.zebak.bloodClouds.values.single()
            cloud.npc.coords = player.coords
            f.dispatchLateCycle(f.clock.cycle + 1)
            assertFalse(cloud.contactReady)
            f.events.publish(SessionStateEvent.Logout(player))
            f.players.remove(player.slotId)
            f.advance(5)
            f.queueProcess.process()
            f.dispatchLateCycle()
            assertFalse(cloud.contactReady)
            val resumed = replacement(f, player)
            assertTrue(f.manager.openLobby(resumed))
            resumed.coords = tile(f, raid, 37, 32)
            cloud.npc.coords = resumed.coords
            assertSame(cloud, raid.zebak.bloodClouds.getValue(cloud.npc.hashCode()))
            assertEquals(resumed.uid, cloud.target)
            f.dispatchLateCycle()
            assertEquals(1, f.hits(resumed).size)
            assertFalse(cloud.contactReady)
        }
    }

    @Test
    fun `death deletion reset and termination cancel every blood cloud and crocodile callback`() {
        for (retirement in listOf("death", "delete", "reset", "terminate")) {
            ToaNativeCombatFixture().use { f ->
                val leader = f.player()
                val swimmer = f.player(2)
                val raid = start(f, leader, seedFor(ZebakBloodChoice.CLOUDS), swimmer)
                val state = raid.zebak
                swimmer.coords = tile(f, raid, 37, 45)
                state.playerState.getOrPut(swimmer.uid) { ZebakPlayerRuntime() }.swimming = true
                f.dispatchLateCycle(f.clock.cycle + 20)
                val cloud = state.bloodClouds.values.single()
                val crocodile = state.waterCrocodiles.getValue(swimmer.uid)
                val queues = assertNotNull(state.payloadQueues)
                val body = assertNotNull(state.body)
                val packets = projectiles(f).size
                when (retirement) {
                    "death" -> f.manager.handleNpcKill(leader, body)
                    "delete" -> f.npcs.del(body, Int.MAX_VALUE)
                    "reset" -> f.enterZebak(leader)
                    "terminate" -> f.instances.terminate(raid.instance)
                }
                assertTrue(queues.isClosed, retirement)
                assertNull(cloud.expiryTask, retirement)
                assertNull(cloud.contactTask, retirement)
                assertNull(crocodile.biteTask, retirement)
                assertFalse(cloud.contactReady, retirement)
                assertFalse(crocodile.biteReady, retirement)
                assertEquals(if (retirement == "reset") 2 else 0, f.worldQueues.size, retirement)
                f.advance(100)
                f.queueProcess.process()
                queues.processBlood()
                queues.processCloudReadiness()
                queues.processCrocodileReadiness()
                assertEquals(packets, projectiles(f).size, retirement)
                assertTrue(f.hits(swimmer).isEmpty(), retirement)
            }
            framework().clearInlineMocks()
        }
    }

    private fun start(
        f: ToaNativeCombatFixture,
        leader: Player,
        seed: Long = 42,
        member: Player? = null,
        thinners: Boolean = false,
        blood: Boolean = true,
    ): ToaRaidSession {
        f.manager.createParty(leader)
        if (member != null) f.joinParty(leader, member)
        if (blood) assertTrue(f.manager.toggleInvocation(leader, "not_just_a_head"))
        if (thinners) assertTrue(f.manager.toggleInvocation(leader, "blood_thinners"))
        val raid = f.start(leader, if (member == null) 1 else 2, seed)
        f.enterZebak(leader)
        listOfNotNull(leader, member).forEachIndexed { index, player ->
            PathingEntityCommon.telejump(player, f.collision, tile(f, raid, 37 + index, 32))
        }
        f.dispatchLateCycle(raid.zebak.emergesAt)
        // Isolate independent blood cadence while retaining the actual ordinary native gate.
        assertNotNull(raid.zebak.body).actionDelay = Int.MAX_VALUE
        return raid
    }

    private fun replacement(f: ToaNativeCombatFixture, departed: Player): Player =
        f.player(9).apply {
            uuid = departed.uuid
            accountHash = departed.accountHash
            accountId = departed.accountId
            characterId = departed.characterId
            assignUid()
        }

    private fun tile(f: ToaNativeCombatFixture, raid: ToaRaidSession, x: Int, z: Int): CoordGrid =
        assertNotNull(
            f.instances.localCoord(raid.instance, ToaInstanceLayout.roomLocal(ToaRoom.ZEBAK, x, z))
        )

    private fun projectiles(f: ToaNativeCombatFixture): List<ProjAnim> =
        mockingDetails(f.world).invocations.flatMap { it.arguments.filterIsInstance<ProjAnim>() }

    private fun seedFor(choice: ZebakBloodChoice): Long =
        (1L..100L).first { seed ->
            val oracle = LegacyRandom(seed xor 0x5A4542414BL)
            oracle.nextInt(2)
            oracle.nextInt(2) == choice.ordinal
        }

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

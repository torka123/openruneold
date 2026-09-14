package org.rsmod.content.minigames.toa

import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.framework
import org.mockito.Mockito.mockingDetails
import org.rsmod.annotations.InternalApi
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.hit.HitType
import org.rsmod.game.proj.ProjAnim

/**
 * Native admission, registered Obelisk kill hook, LateCycle dispatch and real shield/core returns.
 */
@OptIn(InternalApi::class)
class WardenPhaseTwoNativeCombatTest {
    @Test
    fun `native admission preserves god rates acceleration first modify and owned launch gate`() {
        val cases =
            listOf(
                Triple(WardenGod.ELIDINIS, false, 7),
                Triple(WardenGod.TUMEKEN, false, 8),
                Triple(WardenGod.TUMEKEN, true, 4),
            )
        for ((god, accelerated, rate) in cases) {
            ToaNativeCombatFixture().use { f ->
                val player = f.player()
                val party = f.manager.createParty(player)
                if (accelerated) assertTrue(party.invocations.toggle(ToaInvocation.ACCELERATION))
                f.start(player)
                val raid = enterPhaseTwo(f, listOf(player), god)
                val source = body(raid)
                val native = f.bossEncounters.of(source)
                val due = f.clock.cycle + raid.encounter.wardensRuleset.phaseTwoFirstAttackDelay
                val label = "$god acceleration=$accelerated"
                assertEquals(due, source.actionDelay, label)
                assertEquals(rate, native.expectedAttackRate(), label)
                assertNull(f.manager.combatProfile(source), "No generic profile: $label")
                far(f, source, player)

                f.hitNpc(player, source, 1, incomingStyle(source))
                assertEquals(1, raid.encounter.wardenPhaseTwoShieldCharge, label)
                assertEquals(0, native.phaseAbilityCount, "First Modify: $label")
                assertEquals(0, raid.encounter.attackCounter, label)
                assertEquals(due, source.actionDelay, label)
                f.dispatchLateCycle(due - 1)
                assertEquals(0, native.phaseAbilityCount, label)
                f.advance(1)
                f.attack(source, player)
                f.attack(source, player, op = true)
                assertEquals(0, native.phaseAbilityCount, "External AI has no token: $label")
                f.dispatchLateCycle()
                assertEquals(1, native.phaseAbilityCount, label)
                assertEquals(1, raid.encounter.attackCounter, label)
                assertTracking(raid, player, due + 2, HitType.Ranged)
                assertEquals(due + rate, source.actionDelay, label)
                f.dispatchLateCycle()
                f.attack(source, player)
                assertEquals(1, native.phaseAbilityCount, "No duplicate dispatch: $label")
                f.dispatchLateCycle(source.actionDelay)
                assertEquals(2, native.phaseAbilityCount, label)
                assertTracking(raid, player, f.clock.cycle + 2, HitType.Magic)
                assertTrue(f.selectionRandom.bounds.isEmpty(), label)
                assertTrue(f.accessRandom.bounds.isEmpty(), label)
            }
        }
    }

    @Test
    fun `independently due Obelisk projectiles precede the same tick body and duo target selection`() {
        ToaNativeCombatFixture().use { f ->
            val leader = f.player()
            val partner = f.player(2)
            f.manager.createParty(leader)
            f.joinParty(leader, partner)
            f.start(leader, partySize = 2)
            val raid = enterPhaseTwo(f, listOf(leader, partner), WardenGod.ELIDINIS)
            val source = body(raid)
            far(f, source, leader, partner)
            val due = source.actionDelay
            // Keep the independent Obelisk active and choose its directly observable skull turn.
            raid.encounter.wardenObeliskSpecialStep = WardenObeliskSpecial.LIGHTNING_SKULL.ordinal
            raid.encounter.wardenNextPhaseTwoObeliskAttackAt = due
            clearInvocations(f.world)
            f.dispatchLateCycle(due)
            assertEquals(
                listOf(
                    WardenPhaseTwoMechanics.LIGHTNING_SKULL_PROJECTILE,
                    WardenPhaseTwoMechanics.LIGHTNING_SKULL_PROJECTILE,
                    WardenPhaseTwoMechanics.RANGED_PROJECTILE,
                ),
                projectiles(f).map { it.spotanim },
            )
            assertEquals(3, raid.encounter.wardenObeliskSpecialStep)
            assertEquals(
                due + raid.encounter.wardensRuleset.phaseTwoObeliskAttackInterval,
                raid.encounter.wardenNextPhaseTwoObeliskAttackAt,
            )
            assertTracking(raid, partner, due + 2, HitType.Ranged)
            assertEquals(1, raid.encounter.attackCounter)
            assertEquals(1, f.bossEncounters.of(source).phaseAbilityCount)
            assertEquals(1, raid.encounter.wardenDamageRollCounter)
        }
    }

    @Test
    fun `actual shield and core replacement retains seventh special target parity and style history`() {
        ToaNativeCombatFixture().use { f ->
            val leader = f.player()
            val partner = f.player(2)
            f.manager.createParty(leader)
            f.joinParty(leader, partner)
            f.start(leader, partySize = 2)
            val raid = enterPhaseTwo(f, listOf(leader, partner), WardenGod.TUMEKEN)
            val first = body(raid)
            val original = f.bossEncounters.of(first)
            far(f, first, leader, partner)
            repeat(6) { index ->
                f.dispatchLateCycle(first.actionDelay)
                val number = index + 1
                assertTracking(
                    raid,
                    if (number % 2 == 0) leader else partner,
                    f.clock.cycle + 2,
                    if (number % 2 == 0) HitType.Magic else HitType.Ranged,
                )
                assertEquals(number, raid.encounter.attackCounter)
            }
            val second = returnCore(f, raid, leader)
            assertFalse(first.isSlotAssigned)
            assertTrue(original.isEnded)
            assertNotSame(first, second)
            assertEquals(TumekenWarden.MAGIC_FORM, second.id)
            assertEquals(6, phases(f, second).acceptedBeforeBody)
            assertEquals(0, f.bossEncounters.of(second).phaseAbilityCount)
            assertEquals(6, phases(f, second).actionCount)
            far(f, second, leader, partner)
            val returnDue = second.actionDelay
            f.dispatchLateCycle(returnDue - 1)
            assertEquals(6, raid.encounter.attackCounter)
            clearInvocations(f.world)
            f.dispatchLateCycle(returnDue)
            assertEquals(7, raid.encounter.attackCounter)
            assertEquals(1, f.bossEncounters.of(second).phaseAbilityCount)
            assertEquals(1, raid.encounter.style)
            assertEquals(
                listOf(
                    WardenPhaseTwoMechanics.DIVINE_RANGED_PROJECTILE,
                    WardenPhaseTwoMechanics.DIVINE_RANGED_PROJECTILE,
                ),
                projectiles(f).map { it.spotanim },
            )
            assertTracking(raid, leader, returnDue + 3, HitType.Ranged)
            assertTracking(raid, partner, returnDue + 3, HitType.Ranged)
            assertEquals(8, raid.encounter.wardenDamageRollCounter)

            val third = returnCore(f, raid, leader)
            assertEquals(TumekenWarden.RANGED_FORM, third.id)
            assertEquals(7, phases(f, third).acceptedBeforeBody)
            assertEquals(1, raid.encounter.style, "Core return retains divine style history")
            far(f, third, leader, partner)
            f.dispatchLateCycle(third.actionDelay)
            assertEquals(8, raid.encounter.attackCounter)
            assertTracking(raid, leader, f.clock.cycle + 2, HitType.Magic)
            assertEquals(1, f.bossEncounters.of(third).phaseAbilityCount)
            assertEquals(9, raid.encounter.wardenDamageRollCounter)
        }
    }

    @Test
    fun `native body deletion and departure cancel old dispatch and core work before fresh admission`() {
        ToaNativeCombatFixture().use { f ->
            val player = f.player()
            f.start(player)
            val raid = enterPhaseTwo(f, listOf(player), WardenGod.ELIDINIS)
            val source = body(raid)
            PathingEntityCommon.telejump(player, f.collision, source.coords.translateX(1))
            f.dispatchLateCycle(source.actionDelay)
            val hit = f.hits(player).single().second
            val old = f.bossEncounters.of(source)
            assertEquals(1, old.phaseAbilityCount)
            // This fixture operation is native Delete plus the registered completion hook.
            val completion = f.nativeDeath(source, player)
            assertTrue(old.isEnded)
            assertFalse(source.isSlotAssigned)
            assertEquals(ToaWardenStage.CORE, raid.encounter.wardenStage)
            val hp = player.hitpoints
            f.processHit(player, hit)
            assertEquals(hp, player.hitpoints, "A queued melee hit cannot land from a deleted body")
            f.events.publish(SessionStateEvent.Logout(player))
            assertNull(f.manager.raidFor(player))
            clearInvocations(f.world)
            f.dispatchLateCycle(f.clock.cycle + 30)
            assertEquals(1, old.phaseAbilityCount)
            assertTrue(mockingDetails(f.world).invocations.isEmpty())
            f.instances.terminate(raid.instance)
            f.dispatchLateCycle(f.clock.cycle + 30)
            assertTrue(mockingDetails(f.world).invocations.isEmpty())
            assertTrue(raid.spawnedNpcs.isEmpty())

            f.start(player)
            val freshRaid = enterPhaseTwo(f, listOf(player), WardenGod.ELIDINIS)
            val fresh = body(freshRaid)
            assertNotSame(source, fresh)
            assertEquals(0, phases(f, fresh).actionCount)
            f.death.dispatchDeferredKill(completion)
            assertSame(fresh, body(freshRaid))
            assertEquals(ToaWardenStage.PHASE_TWO, freshRaid.encounter.wardenStage)
            assertEquals(0, freshRaid.encounter.attackCounter)
            f.processHit(player, hit)
            assertEquals(hp, player.hitpoints, "Reentry cannot revive the former body's melee hit")
        }
    }

    private fun enterPhaseTwo(
        f: ToaNativeCombatFixture,
        members: List<Player>,
        god: WardenGod,
    ): ToaRaidSession {
        val leader = members.first()
        assertTrue(f.manager.devEnterRoom(leader, "wardens"))
        val raid = assertNotNull(f.manager.raidFor(leader))
        members.forEach { assertTrue(f.manager.startWardensFight(it)) }
        // Choose only the retained P1 charge outcome; use the real transition and actor
        // replacement.
        raid.encounter.wardenElidinisCharge = if (god == WardenGod.ELIDINIS) 1 else 0
        raid.encounter.wardenTumekenCharge = if (god == WardenGod.TUMEKEN) 1 else 0
        val obelisk = raid.npcRoles.entries.single { it.value == ToaNpcRole.OBELISK }.key
        f.nativeDeath(obelisk, leader)
        assertEquals(ToaWardenStage.PHASE_ONE_TRANSITION, raid.encounter.wardenStage)
        f.dispatchLateCycle(raid.encounter.wardenTransitionEndsAt)
        assertEquals(ToaWardenStage.PHASE_TWO, raid.encounter.wardenStage)
        assertEquals(god, raid.encounter.wardenPhaseTwoGod)
        return raid
    }

    private fun returnCore(f: ToaNativeCombatFixture, raid: ToaRaidSession, player: Player): Npc {
        val previous = body(raid)
        val count = raid.encounter.attackCounter
        f.hitNpc(player, previous, WardenCores.PHASE_TWO_SHIELD_HITPOINTS, incomingStyle(previous))
        assertEquals(ToaWardenStage.CORE, raid.encounter.wardenStage)
        assertFalse(previous.isSlotAssigned)
        f.dispatchLateCycle(raid.encounter.wardenCoreSpawnsAt)
        val core = raid.npcRoles.entries.single { it.value == ToaNpcRole.CORE }.key
        val returnAt = raid.encounter.wardenCoreReturnsAt
        f.dispatchLateCycle(returnAt - 1)
        assertTrue(core.isSlotAssigned)
        assertEquals(ToaWardenStage.CORE, raid.encounter.wardenStage)
        f.dispatchLateCycle(returnAt)
        assertFalse(core.isSlotAssigned)
        assertEquals(ToaWardenStage.PHASE_TWO, raid.encounter.wardenStage)
        assertEquals(count, raid.encounter.attackCounter)
        return body(raid).also {
            assertEquals(
                returnAt + raid.encounter.wardensRuleset.phaseTwoRecoveryAttackDelay,
                it.actionDelay,
            )
        }
    }

    private fun body(raid: ToaRaidSession): Npc =
        raid.npcRoles.entries.single { it.value == ToaNpcRole.WARDEN_PHASE }.key

    private fun phases(f: ToaNativeCombatFixture, npc: Npc): WardenPhaseTwoCombatPhases =
        assertNotNull(f.bossEncounters.of(npc).stateOrNull(WARDEN_PHASE_TWO_COMBAT))

    private fun incomingStyle(npc: Npc): HitType =
        assertNotNull(ElidinisWarden.requiredStyle(npc.id) ?: TumekenWarden.requiredStyle(npc.id))

    private fun far(f: ToaNativeCombatFixture, source: Npc, vararg players: Player) {
        players.forEachIndexed { index, player ->
            PathingEntityCommon.telejump(player, f.collision, source.coords.translate(6, index))
        }
    }

    private fun assertTracking(raid: ToaRaidSession, player: Player, landsAt: Int, type: HitType) {
        val pending =
            raid.encounter.pendingHits.single { it.target == player.uid && it.landsAt == landsAt }
        assertEquals(type, pending.hitType)
        assertFalse(pending.requireTargetOnTile)
        assertEquals(80, pending.prayerReductionPercent)
    }

    private fun projectiles(f: ToaNativeCombatFixture): List<ProjAnim> =
        mockingDetails(f.world).invocations.flatMap { it.arguments.filterIsInstance<ProjAnim>() }

    @AfterEach fun releaseMocks() = framework().clearInlineMocks()

    companion object {
        @JvmStatic
        @BeforeAll
        fun initializeCache() {
            ToaCacheTestSupport.initRevision240()
        }
    }
}

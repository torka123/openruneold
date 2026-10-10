package org.rsmod.content.minigames.toa

import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.framework
import org.mockito.Mockito.mockingDetails
import org.rsmod.annotations.InternalApi
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.hit.HitType
import org.rsmod.game.proj.ProjAnim

/** Source-preserving native acceptance of the reconciled +5/+7 core and +3 room handoff. */
@OptIn(InternalApi::class)
class WardenCoreNativeTest {
    @Test
    fun `both gods keep native ejection timing actual spawn exposure and exactly one form return`() {
        for (god in WardenGod.entries) {
            try {
                ToaNativeCombatFixture().use { f ->
                    val player = f.player()
                    f.start(player)
                    val raid = enterPhaseTwo(f, player, god)
                    val state = raid.encounter
                    val owner = assertNotNull(state.wardenCoreQueues)
                    for ((index, exposure) in listOf(21, 29, 37).withIndex()) {
                        val previous = body(raid)
                        val native = f.bossEncounters.of(previous)
                        eject(f, raid, player)
                        val ejected = f.clock.cycle
                        assertFalse(previous.isSlotAssigned)
                        assertTrue(native.isEnded)
                        assertFalse(owner.isClosed, "Discarding the shield body retains its session presentation")
                        assertEquals(ejected + 5, owner.projectileAt)
                        assertEquals(ejected + 7, owner.spawnAt)
                        clearInvocations(f.world)
                        if (index == 0) {
                            f.dispatchLateCycle(ejected + 4)
                            assertTrue(projectiles(f).isEmpty())
                            assertTrue(cores(raid).isEmpty())
                            f.advance(1)
                            f.queueProcess.process()
                            assertTrue(projectiles(f).isEmpty())
                            f.dispatchLateCycle()
                            assertEquals(listOf(WardenPhaseTwoMechanics.coreProjectile(god)), projectiles(f).map { it.spotanim })
                            assertEquals(-1, owner.projectileAt)
                            assertEquals(ejected + 7, owner.spawnAt)
                            f.dispatchLateCycle(ejected + 6)
                            assertTrue(cores(raid).isEmpty())
                            f.dispatchLateCycle(ejected + 7)
                        } else {
                            // The second exposure deliberately resumes late. Both pending native
                            // ejection entries run in one dispatch, with exposure anchored here.
                            f.dispatchLateCycle(ejected + if (index == 1) 12 else 7)
                            assertEquals(listOf(WardenPhaseTwoMechanics.coreProjectile(god)), projectiles(f).map { it.spotanim })
                        }
                        val core = cores(raid).single()
                        val spawned = f.clock.cycle
                        assertEquals(if (god == WardenGod.ELIDINIS) WardenCores.ELIDINIS else WardenCores.TUMEKEN, core.id)
                        assertEquals(spawned + exposure, owner.returnAt)
                        assertEquals(-1, owner.spawnAt)
                        assertEquals(WardenCores.coreHitpoints(state.wardenPhaseTwoRemaining), core.hitpoints)

                        if (index < 2) {
                            val maxHit = if (index == 0) 270 else 180
                            val previousHidden = state.wardenPhaseTwoRemaining
                            VarPlayerIntMapSetter.set(player, "varp.com_maxhit", maxHit)
                            val hit = f.hitNpc(player, core, 1, HitType.Melee)
                            assertEquals(maxHit, hit.damage, "Native core melee uses the current maximum hit")
                            assertEquals(previousHidden - maxHit * 5, state.wardenPhaseTwoRemaining)
                        }
                        val returnAt = owner.returnAt
                        f.dispatchLateCycle(returnAt - 1)
                        assertTrue(core.isSlotAssigned)
                        f.dispatchLateCycle(returnAt)
                        assertFalse(core.isSlotAssigned)
                        assertEquals(ToaWardenStage.PHASE_TWO, state.wardenStage)
                        assertEquals(index + 1, state.wardenCoreDowns)
                        assertEquals(-1, owner.returnAt)
                        val replacement = body(raid)
                        assertNotEquals(previous.id, replacement.id)
                        assertEquals(returnAt + state.wardensRuleset.phaseTwoRecoveryAttackDelay, replacement.actionDelay)
                        assertEquals(returnAt + state.wardensRuleset.phaseTwoObeliskAttackInterval, state.wardenNextPhaseTwoObeliskAttackAt)
                        f.dispatchLateCycle()
                        assertSame(replacement, body(raid))
                        assertEquals(index + 1, state.wardenCoreDowns)
                    }
                }
            } finally {
                framework().clearInlineMocks()
            }
        }
    }

    @Test
    fun `native core kill on the return tick cancels retreat and hands off to phase three once at plus three`() {
        ToaNativeCombatFixture().use { f ->
            val player = f.player()
            f.start(player)
            val raid = enterPhaseTwo(f, player, WardenGod.TUMEKEN)
            eject(f, raid, player)
            f.dispatchLateCycle(raid.encounter.wardenCoreSpawnsAt)
            val core = cores(raid).single()
            val state = raid.encounter
            val owner = assertNotNull(state.wardenCoreQueues)
            val oldReturn = owner.returnAt
            val completion = NpcDeathKillContext(player, core, f.death.nextLootTrackerEventId(), core.coords)
            f.advance(oldReturn - f.clock.cycle - 1)
            VarPlayerIntMapSetter.set(player, "varp.com_maxhit", core.hitpoints)
            f.hitNpc(player, core, 1, HitType.Melee)
            assertEquals(oldReturn, f.clock.cycle)
            assertEquals(0, state.wardenPhaseTwoRemaining)
            assertEquals(1, state.wardenCoreDowns)
            assertEquals(ToaWardenStage.PHASE_THREE_TRANSITION, state.wardenStage)
            assertFalse(core.isSlotAssigned)
            assertEquals(-1, owner.returnAt)
            assertEquals(oldReturn + 3, owner.phaseThreeAt)
            f.dispatchLateCycle()
            f.death.dispatchDeferredKill(completion)
            assertEquals(1, state.wardenCoreDowns)
            assertEquals(ToaRoom.WARDENS, raid.currentRoom)
            f.dispatchLateCycle(oldReturn + 2)
            assertEquals(ToaWardenStage.PHASE_THREE_TRANSITION, state.wardenStage)
            f.advance(1)
            f.queueProcess.process()
            assertEquals(ToaRoom.WARDENS, raid.currentRoom)
            f.dispatchLateCycle()
            assertEquals(ToaRoom.TUMEKEN_WARDEN, raid.currentRoom)
            assertEquals(ToaWardenStage.PHASE_THREE, state.wardenStage)
            assertEquals(-1, owner.phaseThreeAt)
            assertEquals(1, state.wardenCoreDowns)
            val finalBody = body(raid)
            assertEquals(ElidinisWarden.FINAL_FORM, finalBody.id)
            assertEquals(oldReturn + 3 + state.wardensRuleset.phaseThreeInitialSlamDelay, state.wardenNextSlamAt)
            f.dispatchLateCycle()
            assertSame(finalBody, body(raid))
            assertEquals(1, state.wardenCoreDowns)
        }
    }

    @Test
    fun `paused missing body and missing core keep deadlines until reset or native session retirement`() {
        ToaNativeCombatFixture().use { f ->
            val player = f.player()
            f.start(player)
            var raid = enterPhaseTwo(f, player, WardenGod.ELIDINIS)
            eject(f, raid, player)
            val owner = assertNotNull(raid.encounter.wardenCoreQueues)
            val projectileAt = owner.projectileAt
            val spawnAt = owner.spawnAt
            f.events.publish(SessionStateEvent.Logout(player))
            clearInvocations(f.world)
            f.dispatchLateCycle(spawnAt + 20)
            assertEquals(projectileAt, owner.projectileAt)
            assertEquals(spawnAt, owner.spawnAt)
            assertTrue(projectiles(f).isEmpty())
            assertTrue(f.manager.rejoin(player, raid))
            val ejectedBody = raid.spawnedNpcs.single { it.id == ElidinisWarden.CORE_EJECTED }
            removeNpc(f, raid, ejectedBody)
            f.dispatchLateCycle()
            assertEquals(projectileAt, owner.projectileAt)
            assertEquals(spawnAt, owner.spawnAt)
            assertTrue(cores(raid).isEmpty())
            assertTrue(projectiles(f).isEmpty())

            invoke(f, "resetEncounter", raid)
            assertTrue(owner.isClosed)
            assertEquals(-1, owner.projectileAt)
            assertEquals(-1, owner.spawnAt)
            raid = enterPhaseTwo(f, player, WardenGod.ELIDINIS)
            eject(f, raid, player)
            f.dispatchLateCycle(raid.encounter.wardenCoreSpawnsAt)
            val fresh = assertNotNull(raid.encounter.wardenCoreQueues)
            assertNotSame(owner, fresh)
            val returnAt = fresh.returnAt
            removeNpc(f, raid, cores(raid).single())
            f.dispatchLateCycle(returnAt + 10)
            assertEquals(ToaWardenStage.CORE, raid.encounter.wardenStage)
            assertEquals(returnAt, fresh.returnAt, "A missing core does not consume its return")
            assertEquals(0, raid.encounter.wardenCoreDowns)
            f.instances.terminate(raid.instance)
            assertTrue(fresh.isClosed)
            assertEquals(-1, fresh.returnAt)
            clearInvocations(f.world)
            f.dispatchLateCycle(f.clock.cycle + 50)
            assertTrue(raid.spawnedNpcs.isEmpty())
            assertTrue(projectiles(f).isEmpty())
        }
    }

    private fun enterPhaseTwo(f: ToaNativeCombatFixture, player: Player, god: WardenGod): ToaRaidSession {
        assertTrue(f.manager.devEnterRoom(player, "wardens"))
        val raid = assertNotNull(f.manager.raidFor(player))
        assertTrue(f.manager.startWardensFight(player))
        // Retain the native P1 kill/transition; set only its independently tested charge outcome.
        raid.encounter.wardenElidinisCharge = if (god == WardenGod.ELIDINIS) 1 else 0
        raid.encounter.wardenTumekenCharge = if (god == WardenGod.TUMEKEN) 1 else 0
        val obelisk = raid.npcRoles.entries.single { it.value == ToaNpcRole.OBELISK }.key
        f.nativeDeath(obelisk, player)
        f.dispatchLateCycle(raid.encounter.wardenTransitionEndsAt)
        assertEquals(ToaWardenStage.PHASE_TWO, raid.encounter.wardenStage)
        assertEquals(god, raid.encounter.wardenPhaseTwoGod)
        return raid
    }

    private fun eject(f: ToaNativeCombatFixture, raid: ToaRaidSession, player: Player) {
        val body = body(raid)
        val style = assertNotNull(ElidinisWarden.requiredStyle(body.id) ?: TumekenWarden.requiredStyle(body.id))
        f.hitNpc(player, body, WardenCores.PHASE_TWO_SHIELD_HITPOINTS, style)
        assertEquals(ToaWardenStage.CORE, raid.encounter.wardenStage)
    }

    private fun body(raid: ToaRaidSession): Npc =
        raid.npcRoles.entries.single { it.value == ToaNpcRole.WARDEN_PHASE }.key

    private fun cores(raid: ToaRaidSession): List<Npc> =
        raid.npcRoles.entries.filter { it.value == ToaNpcRole.CORE }.map { it.key }

    private fun removeNpc(f: ToaNativeCombatFixture, raid: ToaRaidSession, npc: Npc) {
        val method = ToaRaidManager::class.java.getDeclaredMethod("removeNpc", ToaRaidSession::class.java, Npc::class.java)
        method.isAccessible = true
        method.invoke(f.manager, raid, npc)
        assertFalse(npc.isSlotAssigned)
    }

    private fun invoke(f: ToaNativeCombatFixture, name: String, raid: ToaRaidSession) {
        val method = ToaRaidManager::class.java.getDeclaredMethod(name, ToaRaidSession::class.java)
        method.isAccessible = true
        method.invoke(f.manager, raid)
    }

    private fun projectiles(f: ToaNativeCombatFixture): List<ProjAnim> =
        mockingDetails(f.world).invocations.flatMap { it.arguments.filterIsInstance<ProjAnim>() }

    @AfterEach fun releaseMocks() = framework().clearInlineMocks()

    companion object {
        @BeforeAll
        @JvmStatic
        fun cache() {
            ToaCacheTestSupport.initRevision240()
        }
    }
}

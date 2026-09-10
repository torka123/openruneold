package org.rsmod.content.bosses.royaltitans

import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.*
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.player.hit.processor.DamageOnlyPlayerHitProcessor
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.hit.Hit
import org.rsmod.game.hit.HitType

/** Execute production registrations and NvPCombat; do not infer behavior from the new spec. */
class RoyalTitansElementalCombatTest {
    @AfterEach fun clearMocks() = framework().clearInlineMocks()

    @Test
    fun `both elementals preserve native style accuracy max hit animation and one tick impact`() {
        for (id in listOf(14150, 14151)) {
            val f = RoyalTitansTestWorld(random = MaximumRoll)
            val player = f.enter()
            val npc = adjacent(f, player, id)
            val style = if (id == 14150) MeleeAttackType.Slash else MeleeAttackType.Stab
            `when`(f.accuracy.rollMeleeAccuracy(npc, player, style, MaximumRoll)).thenReturn(true)
            val start = f.clock.cycle
            f.attack(npc, player)
            verify(f.accuracy).rollMeleeAccuracy(npc, player, style, MaximumRoll)
            val hit = hits(player).single()
            assertEquals(HitType.Melee, hit.type)
            assertEquals(4, hit.damage)
            assertEquals(1, hitQueues(player).single().remainingCycles)
            assertEquals(start + 4, npc.actionDelay)
            assertEquals(if (id == 14150) 11947 else 11950, npc.pendingSequence.id)
            assertEquals(99, player.hitpoints, "A launched hit is not immediate damage")
            f.tick()
            with(DamageOnlyPlayerHitProcessor(f.events, f.npcList, f.players)) {
                player.process(hit)
            }
            assertEquals(95, player.hitpoints)
            framework().clearInlineMocks()
        }
    }

    @Test
    fun `launch prayer and failed accuracy retain native zero damage and four tick recovery`() {
        for (protected in listOf(false, true)) {
            val f = RoyalTitansTestWorld(random = MaximumRoll)
            val player = f.enter()
            val npc = adjacent(f, player, 14151)
            `when`(f.accuracy.rollMeleeAccuracy(npc, player, MeleeAttackType.Stab, MaximumRoll))
                .thenReturn(protected)
            VarPlayerIntMapSetter.set(
                player,
                "varbit.prayer_protectfrommelee",
                if (protected) 1 else 0,
            )
            f.attack(npc, player)
            val hit = hits(player).single()
            assertEquals(0, hit.damage)
            assertEquals(f.clock.cycle + 4, npc.actionDelay)
            VarPlayerIntMapSetter.set(player, "varbit.prayer_protectfrommelee", 0)
            assertEquals(0, hit.damage, "Do not resample launch prayer at impact")
            framework().clearInlineMocks()
        }
    }

    @Test
    fun `action lock prevents another elemental hit before the fourth tick`() {
        val f = RoyalTitansTestWorld()
        val player = f.enter()
        val npc = adjacent(f, player, 14151)
        f.attack(npc, player)
        val start = f.clock.cycle
        repeat(3) {
            f.tick()
            f.attack(npc, player)
            assertEquals(1, hits(player).size)
        }
        f.tick()
        f.attack(npc, player)
        assertEquals(2, hits(player).size)
        assertEquals(start + 8, npc.actionDelay)
    }

    @Test
    fun `duo membership gates elemental attacks and departure retires already launched hits`() {
        val f = RoyalTitansTestWorld()
        val owner = f.enter()
        val partner = f.newPlayer(2)
        assertTrue(f.manager.join(partner, owner.displayName))
        val npc = adjacent(f, owner, 14150)
        val outsider = f.newPlayer(3)
        outsider.coords = owner.coords
        f.attack(npc, outsider)
        assertTrue(hits(outsider).isEmpty())
        partner.coords = owner.coords
        f.attack(npc, partner)
        val hit = hits(partner).single()
        assertTrue(hit.isValidNow())
        assertTrue(f.manager.escape(partner))
        assertTrue(npc.isSlotAssigned, "The owner's room still exists")
        assertFalse(hit.isValidNow())
    }

    @Test
    fun `a launched elemental melee hit survives source death during its retained animation`() {
        val f = RoyalTitansTestWorld(random = MaximumRoll)
        val player = f.enter()
        val npc = adjacent(f, player, 14151)
        `when`(f.accuracy.rollMeleeAccuracy(npc, player, MeleeAttackType.Stab, MaximumRoll))
            .thenReturn(true)
        f.attack(npc, player)
        val hit = hits(player).single()
        npc.hitpoints = 0
        f.manager.finishElementalDeath(npc)
        assertTrue(npc.isSlotAssigned)
        assertTrue(hit.isValidNow())
        with(DamageOnlyPlayerHitProcessor(f.events, f.npcList, f.players)) { player.process(hit) }
        assertEquals(95, player.hitpoints)
    }

    private fun adjacent(f: RoyalTitansTestWorld, player: Player, id: Int): Npc {
        val npc = f.summon(player).first { it.id == id }
        // Finish the spawn presentation before dispatching the normal native attack event.
        f.tick(3)
        PathingEntityCommon.telejump(npc, f.collision, player.coords.translate(0, -1))
        npc.resetMovement()
        npc.actionDelay = f.clock.cycle
        return npc
    }

    @Test
    fun `native deletion retires the elemental DSL identity and pending hit`() {
        val f = RoyalTitansTestWorld(random = MaximumRoll)
        val player = f.enter()
        val npc = adjacent(f, player, 14151)
        `when`(f.accuracy.rollMeleeAccuracy(npc, player, MeleeAttackType.Stab, MaximumRoll))
            .thenReturn(true)
        f.attack(npc, player)
        val hit = hits(player).single()
        val encounter = assertNotNull(f.bossDeps.encounterRegistry.existing(npc))
        assertEquals(1, encounter.phaseAbilityCount)
        assertEquals("melee", encounter.lastAbilityName)
        assertTrue(hit.isValidNow())
        f.npcs.del(npc, Int.MAX_VALUE)
        assertTrue(encounter.isEnded)
        assertNull(f.bossDeps.encounterRegistry.existing(npc))
        assertFalse(hit.isValidNow())
        with(DamageOnlyPlayerHitProcessor(f.events, f.npcList, f.players)) { player.process(hit) }
        assertEquals(99, player.hitpoints)
    }

    @Test
    fun `instance teardown retires only that rooms elemental encounters`() {
        val f = RoyalTitansTestWorld()
        val first = f.enter()
        val second = f.newPlayer(2)
        assertTrue(f.manager.enter(second))
        // Unlock both rooms on the same native frame, before their patrols diverge.
        f.tick(6)
        f.hit(first, f.npc(first, RoyalTitansAssets.BRANDA_ACTIVE), 100)
        f.hit(second, f.npc(second, RoyalTitansAssets.BRANDA_ACTIVE), 100)
        f.tick(9)
        val firstNpc = f.npc(first, 14150)
        val secondNpc = f.npc(second, 14151)
        PathingEntityCommon.telejump(firstNpc, f.collision, first.coords.translate(0, -1))
        PathingEntityCommon.telejump(secondNpc, f.collision, second.coords.translate(0, -1))
        firstNpc.resetMovement()
        secondNpc.resetMovement()
        f.attack(firstNpc, first)
        f.attack(secondNpc, second)
        val firstEncounter = assertNotNull(f.bossDeps.encounterRegistry.existing(firstNpc))
        val secondEncounter = assertNotNull(f.bossDeps.encounterRegistry.existing(secondNpc))
        assertNotSame(firstEncounter, secondEncounter)
        assertTrue(f.instances.terminate(assertNotNull(f.instances.sessionForPlayer(first))))
        assertTrue(firstEncounter.isEnded)
        assertFalse(secondEncounter.isEnded)
        assertSame(secondEncounter, f.bossDeps.encounterRegistry.existing(secondNpc))
        assertTrue(secondNpc.isSlotAssigned)
        assertFalse(firstNpc.isSlotAssigned)
    }

    private fun hits(player: Player): List<Hit> = hitQueues(player).map { it.args as Hit }

    private fun hitQueues(player: Player): List<org.rsmod.game.queue.PlayerQueueList.Queue> {
        val iterator = player.queueList.iterator() ?: return emptyList()
        return try {
            buildList {
                while (iterator.hasNext()) iterator.next().takeIf { it.args is Hit }?.let(::add)
            }
        } finally {
            iterator.cleanUp()
        }
    }

    private object MaximumRoll : GameRandom {
        override fun of(maxExclusive: Int) = maxExclusive - 1

        override fun of(minInclusive: Int, maxInclusive: Int) = maxInclusive

        override fun randomDouble() = 0.99
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() = RoyalTitansCombatIntegrationTest.cache()
    }
}

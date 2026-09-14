package org.rsmod.content.minigames.toa

import dev.openrune.ServerCacheManager
import kotlin.random.Random
import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.framework
import org.rsmod.annotations.InternalApi
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType

/** Actual native AI/NvP integration, preserving the checkpoint's AkkhaTuning and raid callbacks. */
@OptIn(InternalApi::class)
class ToaAkkhaNativeCombatTest {
    @Test
    fun `all three styles use native six tick cadence and retained impact delays without selection draws`() {
        for (style in AkkhaStyle.entries) {
            ToaNativeCombatFixture().use { f ->
                val player = f.player()
                f.start(player)
                val raid = f.enterAkkha(player)
                val npc = f.boss(raid)
                setStyle(raid, npc, style)
                adjacent(player, npc)
                f.hitNpc(player, npc, 0)
                assertEquals(
                    0,
                    f.bossEncounters.of(npc).phaseAbilityCount,
                    "Modify-first must not consume a launch",
                )
                val ready = f.clock.cycle
                val maximum = assertNotNull(f.manager.combatProfile(npc)).maxHit
                f.attack(npc, player)
                val encounter = f.bossEncounters.of(npc)
                val hit = f.hits(player).single()
                assertEquals(akkhaImpactDelay(ToaCombatStyle.valueOf(style.name)), hit.first)
                assertEquals(style.hitType, hit.second.type)
                assertEquals(maximum, hit.second.damage)
                assertEquals(style.name.lowercase(), encounter.currentPhaseName)
                assertEquals(player.coords, npc.pendingFaceSquare)
                assertEquals(1, raid.akkha.attackCounter)
                assertEquals(ready, raid.akkha.lastEmittedAutoAt)
                assertEquals(ready + 6, npc.actionDelay)
                f.attack(npc, player, op = true)
                f.advance(5)
                f.attack(npc, player)
                assertEquals(1, f.hits(player).size)
                f.advance(1)
                f.attack(npc, player, op = true)
                assertEquals(2, raid.akkha.attackCounter)
                assertEquals(2, encounter.phaseAbilityCount)
                assertEquals(
                    listOf(ready, ready + 6),
                    encounter.attackTracesAfter(0).map { it.commitTick },
                )
                assertEquals(listOf(maximum + 1, maximum + 1), f.accessRandom.bounds)
                assertTrue(f.selectionRandom.bounds.isEmpty())
            }
            framework().clearInlineMocks()
        }
    }

    @Test
    fun `melee distance rejection and invalid target rejection retain native gate and facing order`() {
        ToaNativeCombatFixture().use { f ->
            val player = f.player()
            f.start(player)
            val raid = f.enterAkkha(player)
            val npc = f.boss(raid)
            player.coords = npc.coords.translate(-4, 0)
            val oldFace = npc.pendingFaceSquare
            val oldMode = npc.mode
            f.attack(npc, player)
            assertSame(oldMode, npc.mode)
            assertEquals(oldFace, npc.pendingFaceSquare, "Out-of-melee-range returns before face")
            assertEquals(0, raid.akkha.attackCounter)
            assertTrue(f.hits(player).isEmpty())
            adjacent(player, npc)
            player.hidden = true
            npc.actionDelay = f.clock.cycle + 1
            f.attack(npc, player)
            assertSame(
                oldMode,
                npc.mode,
                "Held native cooldown returns before invalid-target reset",
            )
            f.advance(1)
            f.attack(npc, player)
            assertNull(npc.mode)
            assertEquals(player.coords, npc.pendingFaceSquare, "Face precedes NvP target rejection")
            assertEquals(0, f.bossEncounters.of(npc).phaseAbilityCount)
            assertTrue(f.accessRandom.bounds.isEmpty())
            assertTrue(f.selectionRandom.bounds.isEmpty())
            player.hidden = false
            npc.apPlayer2(player, AiPlayerInteractions(f.events, f.players))
            f.attack(npc, player)
            assertEquals(1, raid.akkha.attackCounter)
            assertEquals(f.clock.cycle + 6, npc.actionDelay)
        }
    }

    @Test
    fun `native readiness runs due trailing special before hidden target rejection then resumes the new style`() {
        ToaNativeCombatFixture().use { f ->
            val player = f.player()
            f.start(player)
            val raid = f.enterAkkha(player)
            val npc = f.boss(raid)
            adjacent(player, npc)
            val first = f.clock.cycle
            repeat(AkkhaTuning.BASE_AUTOS_PER_SPECIAL.value) { index ->
                if (index > 0) f.advance(6)
                f.attack(npc, player)
            }
            val count = AkkhaTuning.BASE_AUTOS_PER_SPECIAL.value
            assertEquals(count, raid.akkha.attackCounter)
            val encounter = f.bossEncounters.of(npc)
            val oldTraces = encounter.attackTracesAfter(0).toList()
            raid.akkha.lastSpecial =
                AkkhaSpecial.MEMORY_BLAST // Solo eligible pool leaves trailing.
            player.hidden = true
            f.advance(5)
            f.attack(npc, player)
            assertEquals(count, raid.akkha.attackCounter)
            f.advance(1)
            f.attack(npc, player)
            assertEquals(AkkhaSpecial.TRAILING_ORBS, raid.akkha.lastSpecial)
            assertEquals(AkkhaStyle.RANGED, raid.akkha.style)
            assertEquals(AkkhaState.NORMAL_COMBAT, raid.akkha.state)
            assertEquals(0, raid.akkha.attackCounter)
            assertEquals(first + (count + 1) * 6, npc.actionDelay)
            assertEquals(oldTraces, encounter.attackTracesAfter(0))
            player.hidden = false
            f.advance(5)
            f.attack(npc, player)
            assertEquals(count, f.hits(player).size)
            f.advance(1)
            f.attack(npc, player)
            assertEquals(1, raid.akkha.attackCounter)
            assertEquals("ranged", encounter.currentPhaseName)
            assertEquals(
                1,
                encounter.phaseAbilityCount,
                "Native phase count restarts on form change",
            )
            assertEquals(HitType.Ranged, f.hits(player).last().second.type)
            assertEquals(f.clock.cycle + 6, npc.actionDelay)
            assertTrue(f.selectionRandom.bounds.isEmpty())
        }
    }

    @Test
    fun `party extras and keep back consume retained raid RNG before primary damage and keep the original target`() {
        for (style in listOf(AkkhaStyle.RANGED, AkkhaStyle.MAGIC)) {
            ToaNativeCombatFixture().use { f ->
                val leader = f.player()
                val partner = f.player(2)
                val party = f.manager.createParty(leader)
                assertTrue(party.invocations.toggle(ToaInvocation.KEEP_BACK))
                f.joinParty(leader, partner)
                f.start(leader, partySize = 2)
                val raid = f.enterAkkha(leader)
                val npc = f.boss(raid)
                setStyle(raid, npc, style)
                adjacent(leader, npc)
                partner.coords = leader.coords.translate(0, 1)
                val maximum = assertNotNull(f.manager.combatProfile(npc)).maxHit
                val keepBack =
                    ToaRules.maxHit(AkkhaTuning.KEEP_BACK_MAX_HIT.value, raid.raidLevel, 0)
                val oracle = Random(raid.startSnapshot.seed xor raid.akkha.generation.toLong())
                val extra = oracle.nextInt(maximum + 1)
                val leaderMelee = oracle.nextInt(keepBack + 1)
                val partnerMelee = oracle.nextInt(keepBack + 1)
                f.attack(npc, leader)
                assertEquals(listOf(1, 3), f.hits(leader).map { it.first })
                assertEquals(
                    listOf(HitType.Melee, style.hitType),
                    f.hits(leader).map { it.second.type },
                )
                assertEquals(listOf(leaderMelee, maximum), f.hits(leader).map { it.second.damage })
                assertEquals(listOf(3, 1), f.hits(partner).map { it.first })
                assertEquals(listOf(extra, partnerMelee), f.hits(partner).map { it.second.damage })
                assertEquals(oracle.nextInt(1_000_003), raid.akkha.nextInt(1_000_003))
                assertEquals(listOf(leader), f.accuracyTargets)
                assertEquals(listOf(maximum + 1), f.accessRandom.bounds)
                assertEquals(leader.uid, raid.akkha.target)
                assertEquals(1, raid.akkha.attackCounter)
                assertTrue(f.selectionRandom.bounds.isEmpty())
            }
            framework().clearInlineMocks()
        }
    }

    @Test
    fun `three native prayer modifiers retain launch snapshot and impact prayer drain`() {
        for (style in AkkhaStyle.entries) {
            ToaNativeCombatFixture().use { f ->
                val player = f.player()
                val party = f.manager.createParty(player)
                assertTrue(party.invocations.toggle(ToaInvocation.DEADLY_PRAYERS))
                assertTrue(party.invocations.toggle(ToaInvocation.QUIET_PRAYERS))
                f.start(player)
                val raid = f.enterAkkha(player)
                val npc = f.boss(raid)
                setStyle(raid, npc, style)
                adjacent(player, npc)
                val maximum = assertNotNull(f.manager.combatProfile(npc)).maxHit
                val prayer =
                    when (style) {
                        AkkhaStyle.MELEE -> "varbit.prayer_protectfrommelee"
                        AkkhaStyle.RANGED -> "varbit.prayer_protectfrommissiles"
                        AkkhaStyle.MAGIC -> "varbit.prayer_protectfrommagic"
                    }
                val reduction =
                    ToaRules.prayerReductionPercent(
                        if (style == AkkhaStyle.MELEE) 75 else 85,
                        raid.startSnapshot.invocations,
                    )
                VarPlayerIntMapSetter.set(player, prayer, 1)
                f.attack(npc, player)
                val protected = f.hits(player).single().second
                assertEquals(maximum * (100 - reduction) / 100, protected.damage)
                VarPlayerIntMapSetter.set(player, prayer, 0)
                f.advance(akkhaImpactDelay(ToaCombatStyle.valueOf(style.name)))
                f.processHit(player, protected)
                assertEquals(99 - protected.damage, player.hitpoints)
                assertEquals(99, player.stat("stat.prayer"))
                f.advance(6 - akkhaImpactDelay(ToaCombatStyle.valueOf(style.name)))
                f.attack(npc, player)
                val unprotected = f.hits(player).last().second
                assertEquals(maximum, unprotected.damage)
                VarPlayerIntMapSetter.set(player, prayer, 1)
                f.advance(akkhaImpactDelay(ToaCombatStyle.valueOf(style.name)))
                f.processHit(player, unprotected)
                assertEquals(99 - protected.damage - maximum, player.hitpoints)
                assertEquals(99 - maximum / 5, player.stat("stat.prayer"))
            }
            framework().clearInlineMocks()
        }
    }

    @Test
    fun `melee rejects source death while ranged magic flight survives until native actor deletion`() {
        for (style in AkkhaStyle.entries) {
            ToaNativeCombatFixture().use { f ->
                val player = f.player()
                f.start(player)
                val raid = f.enterAkkha(player)
                val npc = f.boss(raid)
                setStyle(raid, npc, style)
                adjacent(player, npc)
                f.attack(npc, player)
                val hit = f.hits(player).single().second
                val encounter = f.bossEncounters.of(npc)
                npc.hitpoints = 0
                assertEquals(style != AkkhaStyle.MELEE, hit.isValidNow())
                f.processHit(player, hit)
                assertEquals(
                    if (style == AkkhaStyle.MELEE) 99 else 99 - hit.damage,
                    player.hitpoints,
                )
                f.npcs.del(npc, Int.MAX_VALUE)
                assertTrue(encounter.isEnded)
                assertFalse(hit.isValidNow())
                val hp = player.hitpoints
                f.processHit(player, hit)
                assertEquals(hp, player.hitpoints)
            }
            framework().clearInlineMocks()
        }
    }

    private fun setStyle(raid: ToaRaidSession, npc: Npc, style: AkkhaStyle) {
        raid.akkha.style = style
        npc.transmog(requireNotNull(ServerCacheManager.getNpc(style.npcId)), Int.MAX_VALUE)
        npc.apRangeOverride = akkhaAttackRange(style)
    }

    private fun adjacent(player: Player, npc: Npc) {
        player.coords = npc.coords.translate(-1, 0)
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

package org.rsmod.content.minigames.clanmayhem

import java.util.UUID
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClanMayhemRulesTest {
    @Test
    fun `v2 timeline is exact and authoritative`() {
        assertEquals(1_000, ClanMayhemConfig.registrationTicks)
        assertEquals(2_500, ClanMayhemConfig.activeMatchTicks)
        assertEquals(100, ClanMayhemConfig.respawnPenaltyTicks)
        assertEquals(listOf(500, 1_000, 1_500, 2_000), ClanMayhemConfig.crateTicks)
        assertEquals(250, ClanMayhemTimelineEvent.BOSS_WARNING.atTick)
        assertEquals(300, ClanMayhemTimelineEvent.BOSS_SPAWN.atTick)
        assertEquals(2_400, ClanMayhemTimelineEvent.LETHAL_FOG.atTick)
        assertEquals(2_500, ClanMayhemTimelineEvent.MATCH_END.atTick)
    }

    @Test
    fun `admin test time accepts minutes and clock notation`() {
        assertEquals(800, parseMayhemElapsedTicks("8"))
        assertEquals(850, parseMayhemElapsedTicks("8:30"))
        assertEquals(2_400, parseMayhemElapsedTicks("24:00"))
        assertEquals(2_500, parseMayhemElapsedTicks("25:00"))
        assertEquals("8:30", formatMayhemElapsedTicks(850))
        assertNull(parseMayhemElapsedTicks("8:60"))
        assertNull(parseMayhemElapsedTicks("25:01"))
        assertNull(parseMayhemElapsedTicks("later"))
    }

    @Test
    fun `admin test clock only fast forwards and continues ticking`() {
        val clock = ClanMayhemMatchClock(startedAtTick = 100)

        assertEquals(50, clock.elapsed(currentTick = 150))
        assertTrue(clock.fastForwardTo(currentTick = 150, targetElapsedTicks = 800))
        assertEquals(800, clock.elapsed(currentTick = 150))
        assertEquals(801, clock.elapsed(currentTick = 151))
        assertFalse(clock.fastForwardTo(currentTick = 151, targetElapsedTicks = 700))
        assertEquals(801, clock.elapsed(currentTick = 151))
    }

    @Test
    fun `timeline callbacks fire once and reject an old match id`() {
        val id = UUID.randomUUID()
        val timeline = ClanMayhemTimeline(id)
        assertTrue(timeline.due(UUID.randomUUID(), 2_500).isEmpty())
        assertEquals(ClanMayhemTimelineEvent.entries, timeline.due(id, 2_500))
        assertTrue(timeline.due(id, 2_500).isEmpty())
    }

    @Test
    fun `entry requires both inventory and equipment to be empty`() {
        assertTrue(ClanMayhemEntryRules.canEnter(inventoryEmpty = true, equipmentEmpty = true))
        assertFalse(ClanMayhemEntryRules.canEnter(inventoryEmpty = false, equipmentEmpty = true))
        assertFalse(ClanMayhemEntryRules.canEnter(inventoryEmpty = true, equipmentEmpty = false))
        assertFalse(ClanMayhemEntryRules.canEnter(inventoryEmpty = false, equipmentEmpty = false))
    }

    @Test
    fun `trio and five rosters require exact ready counts`() {
        val trio = team(3, ready = true)
        val five = team(5, ready = true)
        assertTrue(
            trio.hasExactRoster(
                ClanMayhemFormat(ClanMayhemTeamSize.TRIO, ClanMayhemKitTier.WELFARE)
            )
        )
        assertFalse(
            trio.hasExactRoster(
                ClanMayhemFormat(ClanMayhemTeamSize.FIVE, ClanMayhemKitTier.WELFARE)
            )
        )
        assertTrue(
            five.hasExactRoster(ClanMayhemFormat(ClanMayhemTeamSize.FIVE, ClanMayhemKitTier.MAX))
        )
        five.roster.values.first().ready = false
        assertFalse(
            five.hasExactRoster(ClanMayhemFormat(ClanMayhemTeamSize.FIVE, ClanMayhemKitTier.MAX))
        )
    }

    @Test
    fun `minimum clan requirement is enforced unless explicit debug override is active`() {
        val format = ClanMayhemFormat(ClanMayhemTeamSize.TRIO, ClanMayhemKitTier.MID)
        val teams = listOf(team(3), team(3))
        assertFalse(ClanMayhemRosterRules.canStart(teams, format))
        assertTrue(ClanMayhemRosterRules.canStart(teams, format, debugOverride = true))
        assertTrue(ClanMayhemRosterRules.canStart(listOf(team(1)), format, debugOverride = true))
    }

    @Test
    fun `friendly fire respawn box and protection block combat`() {
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        assertFalse(
            ClanMayhemCombatRules.canAttack(
                first,
                first,
                ClanMayhemParticipantState.ARENA,
                ClanMayhemParticipantState.ARENA,
                false,
                false,
                true,
            )
        )
        assertFalse(
            ClanMayhemCombatRules.canAttack(
                first,
                second,
                ClanMayhemParticipantState.RESPAWN_BOX,
                ClanMayhemParticipantState.ARENA,
                false,
                false,
                true,
            )
        )
        assertFalse(
            ClanMayhemCombatRules.canAttack(
                first,
                second,
                ClanMayhemParticipantState.ARENA,
                ClanMayhemParticipantState.ARENA,
                true,
                false,
                true,
            )
        )
        assertTrue(
            ClanMayhemCombatRules.canAttack(
                first,
                second,
                ClanMayhemParticipantState.ARENA,
                ClanMayhemParticipantState.ARENA,
                false,
                false,
                true,
            )
        )
    }

    @Test
    fun `score and all seven tiebreakers are ordered deterministically`() {
        val a =
            team(3)
                .copy(
                    clanName = "A",
                    score =
                        ClanMayhemTeamScore(
                            pvpKills = 4,
                            bossObjective = false,
                            enemyDamage = 900,
                            deaths = 5,
                            cratesClaimed = 2,
                            keysOpened = 2,
                            finalKillTick = 100,
                        ),
                )
        val b =
            team(3)
                .copy(
                    clanName = "B",
                    score =
                        ClanMayhemTeamScore(
                            pvpKills = 0,
                            bossObjective = true,
                            enemyDamage = 2_000,
                            deaths = 0,
                            cratesClaimed = 4,
                            keysOpened = 4,
                            finalKillTick = 1,
                        ),
                )
        assertEquals(5, b.score.matchPoints)
        assertEquals(
            "B",
            (ClanMayhemStandings.resolve(listOf(a, b)) as ClanMayhemWinnerResolution.Winner)
                .team
                .clanName,
        )

        b.score.pvpKills = 4
        assertEquals(
            "B",
            (ClanMayhemStandings.resolve(listOf(a, b)) as ClanMayhemWinnerResolution.Winner)
                .team
                .clanName,
        )
        b.score.bossObjective = false
        assertEquals(4, b.score.matchPoints)
        assertEquals(
            "B",
            (ClanMayhemStandings.resolve(listOf(a, b)) as ClanMayhemWinnerResolution.Winner)
                .team
                .clanName,
        )
    }

    @Test
    fun `genuinely identical teams remain an explicit tie for central rng resolution`() {
        val score =
            ClanMayhemTeamScore(
                pvpKills = 2,
                enemyDamage = 100,
                deaths = 2,
                cratesClaimed = 1,
                keysOpened = 1,
                finalKillTick = 80,
            )
        val result =
            ClanMayhemStandings.resolve(
                listOf(team(3).copy(score = score.copy()), team(3).copy(score = score.copy()))
            )
        assertEquals(2, assertIs<ClanMayhemWinnerResolution.GenuineTie>(result).teams.size)
    }

    @Test
    fun `result gate prevents two same tick winners`() {
        val gate = ClanMayhemResultGate()
        var commits = 0
        assertTrue(gate.commitOnce { commits++ })
        assertFalse(gate.commitOnce { commits++ })
        assertEquals(1, commits)
    }

    @Test
    fun `boss and location rolls are independent and always use three bosses and four nodes`() {
        val seenBosses = mutableSetOf<ClanMayhemBoss>()
        val seenNodes = mutableSetOf<String>()
        val random = Random(42)
        repeat(500) {
            val roll = ClanMayhemBossRules.roll(random)
            seenBosses += roll.boss
            seenNodes += roll.node.id
            assertTrue(roll.bossRoll in 0..2)
            assertTrue(roll.locationRoll in 0..3)
        }
        assertEquals(ClanMayhemBoss.entries.toSet(), seenBosses)
        assertEquals(setOf("north", "east", "south", "west"), seenNodes)
    }

    @Test
    fun `boss ownership requires fifteen percent and uses total damage not last hit`() {
        val a = UUID.randomUUID()
        val b = UUID.randomUUID()
        assertNull(ClanMayhemBossRules.owner(1_000, mapOf(a to 149, b to 100)))
        assertEquals(a, ClanMayhemBossRules.owner(1_000, mapOf(a to 600, b to 400)))
        assertEquals(b, ClanMayhemBossRules.owner(1_000, mapOf(a to 200, b to 201)))
    }

    @Test
    fun `normal npc can create one ghost and ghost can never chain`() {
        val encounter = MayhemSupplyNpcEncounter(UUID.randomUUID())
        assertTrue(encounter.killNormal(0))
        assertEquals(MayhemSupplyNpcStage.GHOST, encounter.stage)
        assertFalse(encounter.killNormal(0))
        assertTrue(encounter.killGhost())
        assertEquals(MayhemSupplyNpcStage.DEAD, encounter.stage)
        assertFalse(encounter.killGhost())
    }

    @Test
    fun `fog removes normal and ghost npcs without reward lifecycle`() {
        val normal = MayhemSupplyNpcEncounter(UUID.randomUUID())
        val ghost = MayhemSupplyNpcEncounter(UUID.randomUUID()).also { it.killNormal(0) }
        assertTrue(normal.consumeByFog())
        assertTrue(ghost.consumeByFog())
        assertEquals(MayhemSupplyNpcStage.FOG_REMOVED, normal.stage)
        assertEquals(MayhemSupplyNpcStage.FOG_REMOVED, ghost.stage)
        assertFalse(normal.consumeByFog())
    }

    @Test
    fun `crate always rolls one weapon and matching armour and excludes voidwaker`() {
        val random = Random(7)
        for (tier in ClanMayhemKitTier.entries) repeat(100) {
            val roll = ClanMayhemCrateTables.roll(tier, random)
            val table = ClanMayhemCrateTables.table(tier, roll.style)
            assertTrue(roll.weapon in table.weapons)
            assertTrue(roll.armour in table.armour)
            assertFalse("voidwaker" in roll.weapon)
            assertFalse("voidwaker" in roll.armour)
        }
    }

    @Test
    fun `point awards follow placement kill cap boss and daily victory rules`() {
        assertEquals(
            41,
            ClanMayhemPointRules.award(
                1,
                99,
                bossOwningClan = true,
                firstVictoryToday = true,
                active = true,
            ),
        )
        assertEquals(
            15,
            ClanMayhemPointRules.award(
                2,
                0,
                bossOwningClan = false,
                firstVictoryToday = true,
                active = true,
            ),
        )
        assertEquals(
            0,
            ClanMayhemPointRules.award(
                1,
                8,
                bossOwningClan = true,
                firstVictoryToday = true,
                active = false,
            ),
        )
    }

    @Test
    fun `permanent reward generator is bounded and counts stack quantities`() {
        val generator =
            ClanMayhemRewardGenerator(
                table =
                    listOf(
                        MayhemRewardItem("obj.dragon_bones", 3_000, 20..40),
                        MayhemRewardItem("obj.death_rune", 200, 250..500),
                        MayhemRewardItem("obj.barrows_dharok_head_100", 80_000, 1..1),
                    ),
                fallback =
                    MayhemRewardPackage(listOf(MayhemRewardStack("obj.dragon_bones", 40, 3_000))),
            )
        repeat(250) {
            val reward = generator.generate(Random(it))
            assertTrue(reward.totalValue in 100_000..220_000, "value=${reward.totalValue}")
            assertTrue(
                reward.stacks.none { stack ->
                    ClanMayhemRewardGenerator.forbiddenPermanentReward(stack.internalName)
                }
            )
        }
        assertEquals(
            140_000,
            MayhemRewardPackage(
                    listOf(MayhemRewardStack("a", 1, 60_000), MayhemRewardStack("b", 2, 40_000))
                )
                .totalValue,
        )
    }

    private fun team(size: Int, ready: Boolean = true): ClanMayhemTeam {
        val team = ClanMayhemTeam(UUID.randomUUID(), UUID.randomUUID(), "Clan")
        repeat(size) { index ->
            team.roster[index] = ClanMayhemParticipant(index, index, index.toLong(), 0, ready)
        }
        return team
    }
}

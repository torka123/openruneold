package org.rsmod.content.minigames.toa

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.rsmod.game.entity.player.PlayerUid

class WardensRulesetInvocationTest {
    @Test
    fun `empty and unrelated snapshots retain the exact level zero profile`() {
        assertSame(WardensRuleset.BASELINE, WardensRuleset.from(emptySet()))
        assertSame(WardensRuleset.BASELINE, WardensRuleset.from(setOf(ToaInvocation.QUIET_PRAYERS)))
        assertEquals(1, WardensRuleset.BASELINE.phaseOneChargePerOrb)
        assertEquals(8, WardensRuleset.BASELINE.phaseTwoWardenAttackInterval)
        assertEquals(40, WardensRuleset.BASELINE.phaseTwoObeliskAttackInterval)
        assertEquals(7, WardensRuleset.BASELINE.phaseThreeFloorSlamInterval)
        assertEquals(6, WardensRuleset.BASELINE.phaseFourLightningInterval)
        assertEquals(18, WardensRuleset.BASELINE.phaseFourCollapseInterval(0))
    }

    @Test
    fun `Ancient Haste changes only unblocked phase one orb charge`() {
        val rules = WardensRuleset.from(setOf(ToaInvocation.ANCIENT_HASTE))

        assertEquals(2, rules.phaseOneChargePerOrb)
        assertEquals(0, rules.phaseOneOrbCharge(blocked = true))
        assertEquals(2, rules.phaseOneOrbCharge(blocked = false))
        assertEquals(2, rules.phaseOneEnergyPulseInterval)
        assertEquals(2, rules.phaseOnePassiveChargeInterval)
        assertEquals(8, rules.phaseTwoWardenAttackInterval)
        assertEquals(7, rules.phaseThreeFloorSlamInterval)
    }

    @Test
    fun `Acceleration independently speeds the phase two Warden and Obelisk clocks`() {
        val rules = WardensRuleset.from(setOf(ToaInvocation.ACCELERATION))

        assertEquals(4, rules.phaseTwoWardenAttackInterval)
        assertEquals(30, rules.phaseTwoObeliskAttackInterval)
        assertEquals(1, rules.phaseOneChargePerOrb)
        assertFalse(rules.penetration)
        assertEquals(7, rules.phaseThreeFloorSlamInterval)
    }

    @Test
    fun `Penetration adds Obelisk damage and leaves non Obelisk mechanics unchanged`() {
        val rules = WardensRuleset.from(setOf(ToaInvocation.PENETRATION))

        assertTrue(rules.penetration)
        assertEquals(50, rules.phaseTwoObeliskDamage(45))
        assertEquals(0, rules.phaseTwoObeliskDamage(-1))
        assertEquals(8, rules.phaseTwoWardenAttackInterval)
        assertEquals(40, rules.phaseTwoObeliskAttackInterval)
        assertEquals(7, rules.phaseThreeFloorSlamInterval)
        assertEquals(45, WardensRuleset.BASELINE.phaseTwoObeliskDamage(45))
        assertEquals(0, WardensRuleset.BASELINE.phaseTwoObeliskDamage(-1))
    }

    @Test
    fun `Overclocked chain chooses the highest active slam speed without cumulative subtraction`() {
        assertEquals(7, WardensRuleset.from(emptySet()).phaseThreeFloorSlamInterval)
        assertEquals(
            6,
            WardensRuleset.from(setOf(ToaInvocation.OVERCLOCKED)).phaseThreeFloorSlamInterval,
        )
        assertEquals(
            4,
            WardensRuleset.from(setOf(ToaInvocation.OVERCLOCKED, ToaInvocation.OVERCLOCKED_2))
                .phaseThreeFloorSlamInterval,
        )
        assertEquals(
            3,
            WardensRuleset.from(
                    setOf(
                        ToaInvocation.OVERCLOCKED,
                        ToaInvocation.OVERCLOCKED_2,
                        ToaInvocation.INSANITY,
                    )
                )
                .phaseThreeFloorSlamInterval,
        )
    }

    @Test
    fun `baseline siphon failure damage uses the level zero value`() {
        assertEquals(11, WardensRuleset.BASELINE.energySiphonFailureDamagePerTarget)
    }

    @Test
    fun `Insanity changes siphon reset and every documented enrage clock`() {
        val rules = WardensRuleset.from(setOf(ToaInvocation.INSANITY))

        assertFalse(rules.resetsSlamAfterSiphons)
        assertEquals(33, rules.energySiphonFailureDamagePerTarget)
        assertEquals(5, rules.energySiphonDuration(1))
        assertEquals(19, rules.energySiphonDuration(8))
        assertEquals(3, rules.phaseThreePhantomAttackInterval)
        assertEquals(1, rules.phaseFourFirstLightningDelay)
        assertEquals(2, rules.phaseFourLightningWarningTicks)
        assertEquals(3, rules.phaseFourLightningInterval)
        assertEquals(3, rules.phaseFourPhantomAttackInterval)
        assertEquals(2, rules.phaseFourFirstFloorWarningTicks)
        assertEquals(8, rules.phaseFourCollapseInterval(0))
    }

    @Test
    fun `Wardens rules are captured from the immutable raid start snapshot`() {
        val mutableInvocations = linkedSetOf(ToaInvocation.ANCIENT_HASTE)
        val snapshot =
            ToaRaidStartSnapshot.create(
                partySize = 1,
                raidLevel = 10,
                invocations = mutableInvocations,
                pathLevels = TOA_ACTIVE_PATHS.associateWith { 0 },
                seed = 0x57415244454eL,
                participantAccountHashes = setOf(101L),
            )
        val party = ToaParty(PlayerUid(1))
        val raid = ToaRaidSession(party, snapshot, startedAt = 100)

        mutableInvocations.clear()
        party.invocations.clear()

        assertEquals(setOf(ToaInvocation.ANCIENT_HASTE), raid.startSnapshot.invocations)
        assertEquals(2, raid.encounter.wardensRuleset.phaseOneChargePerOrb)
        assertEquals(8, raid.encounter.wardensRuleset.phaseTwoWardenAttackInterval)
        assertEquals(10, raid.raidLevel)
        assertEquals(10, raid.rewardRaidLevel)
        assertEquals(ToaMode.ENTRY, raid.mode)
        assertEquals(null, raid.timerStartedAt)
        assertFalse(raid.timerExpired)

        raid.rewardRaidLevel = 300
        raid.timerStartedAt = 120
        raid.timerExpired = true

        assertEquals(10, raid.raidLevel)
        assertEquals(300, raid.rewardRaidLevel)
        assertEquals(ToaMode.EXPERT, raid.mode)
        assertEquals(120, raid.timerStartedAt)
        assertTrue(raid.timerExpired)
    }

    @Test
    fun `all Wardens switches compose without enabling unrelated effects`() {
        val all =
            setOf(
                ToaInvocation.ANCIENT_HASTE,
                ToaInvocation.ACCELERATION,
                ToaInvocation.PENETRATION,
                ToaInvocation.OVERCLOCKED,
                ToaInvocation.OVERCLOCKED_2,
                ToaInvocation.INSANITY,
            )

        assertEquals(WardensRuleset.MAXIMUM, WardensRuleset.from(all))
    }
}

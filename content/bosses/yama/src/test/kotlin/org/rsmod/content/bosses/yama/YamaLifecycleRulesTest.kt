package org.rsmod.content.bosses.yama

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class YamaLifecycleRulesTest {
    @Test
    fun `empty Yama instances are destroyed immediately`() {
        assertTrue(yamaSpec().destroyWhenEmpty)
    }

    @Test
    fun `all encounters roll normal rewards before a separate contract reward`() {
        YamaContract.entries.forEach { contract ->
            val stages = YamaLifecycleRules.rewardStages(contract)
            assertEquals(YamaRewardStage.NORMAL_TABLE, stages.first(), contract.name)
            if (contract in setOf(YamaContract.NONE, YamaContract.CUSTOM)) {
                assertEquals(listOf(YamaRewardStage.NORMAL_TABLE), stages, contract.name)
            } else {
                assertEquals(
                    listOf(YamaRewardStage.NORMAL_TABLE, YamaRewardStage.CONTRACT_REWARD),
                    stages,
                    contract.name,
                )
            }
        }
    }

    @Test
    fun `reward drops share the throne return positions`() {
        assertEquals(31 to 38, YamaLifecycleRules.rewardLocalCoordinates(0))
        assertEquals(33 to 38, YamaLifecycleRules.rewardLocalCoordinates(1))
    }

    @Test
    fun `only the leaders selected contract becomes the encounter contract`() {
        val pending = mapOf(1L to YamaContract.HARMONY, 2L to YamaContract.CATALYST)

        assertEquals(YamaContract.HARMONY, YamaLifecycleRules.selectedContract(pending, 1L))
        assertEquals(YamaContract.CATALYST, YamaLifecycleRules.selectedContract(pending, 2L))
        assertEquals(YamaContract.NONE, YamaLifecycleRules.selectedContract(pending, 3L))
    }

    @Test
    fun `party eligibility enforces solo contracts and allows eligible leader contracts`() {
        YamaContract.entries.forEach { contract ->
            assertTrue(YamaLifecycleRules.canStartWithParty(contract, 1), contract.name)
            assertEquals(
                contract.duoAllowed,
                YamaLifecycleRules.canStartWithParty(contract, 2),
                contract.name,
            )
        }
        assertFalse(YamaLifecycleRules.canStartWithParty(YamaContract.NONE, 0))
        assertFalse(YamaLifecycleRules.canStartWithParty(YamaContract.NONE, 3))
    }

    @Test
    fun `entry costs distinguish normal item and custom pizza contracts`() {
        assertEquals(null, YamaLifecycleRules.entryCost(YamaContract.NONE))
        YamaContract.itemContracts.forEach { contract ->
            assertEquals(
                YamaContractEntryCost(requireNotNull(contract.itemId), 1),
                YamaLifecycleRules.entryCost(contract),
                contract.name,
            )
        }
        assertEquals(
            YamaContractEntryCost(YamaAssets.CERT_PINEAPPLE_PIZZA, 100),
            YamaLifecycleRules.entryCost(YamaContract.CUSTOM),
        )
    }

    @Test
    fun `only the consuming leader receives a returnable contract on victory`() {
        assertTrue(YamaLifecycleRules.shouldReturnContract(YamaContract.HARMONY, 1L, 1L))
        assertFalse(YamaLifecycleRules.shouldReturnContract(YamaContract.HARMONY, 1L, 2L))
        assertFalse(YamaLifecycleRules.shouldReturnContract(YamaContract.HARMONY, null, 1L))
        assertFalse(YamaLifecycleRules.shouldReturnContract(YamaContract.OATHPLATE, 1L, 1L))
        assertFalse(YamaLifecycleRules.shouldReturnContract(YamaContract.CUSTOM, 1L, 1L))
    }

    @Test
    fun `leaving clears incoming and outgoing invitations plus selected contract`() {
        val invitations = mutableMapOf(10L to 1L, 1L to 20L, 30L to 40L)
        val contracts = mutableMapOf(1L to YamaContract.CATALYST, 2L to YamaContract.SHARD)

        YamaLifecycleRules.clearPendingLobbyState(invitations, contracts, setOf(1L))

        assertEquals(mapOf(30L to 40L), invitations)
        assertFalse(contracts.containsKey(1L))
        assertEquals(YamaContract.SHARD, contracts[2L])
    }

    @Test
    fun `aborting a party clears every participant pending state`() {
        val invitations = mutableMapOf(10L to 1L, 11L to 2L, 12L to 99L)
        val contracts = mutableMapOf(1L to YamaContract.WORM, 2L to YamaContract.CUSTOM)

        YamaLifecycleRules.clearPendingLobbyState(invitations, contracts, setOf(1L, 2L))

        assertEquals(mapOf(12L to 99L), invitations)
        assertTrue(contracts.isEmpty())
    }
}

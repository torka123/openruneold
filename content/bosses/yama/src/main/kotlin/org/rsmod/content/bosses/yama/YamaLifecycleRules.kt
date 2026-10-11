package org.rsmod.content.bosses.yama

internal enum class YamaRewardStage {
    NORMAL_TABLE,
    CONTRACT_REWARD,
}

internal data class YamaContractEntryCost(val itemId: Int, val quantity: Int)

internal object YamaLifecycleRules {
    private const val FIRST_REWARD_LOCAL_X = 31
    private const val REWARD_SLOT_X_SPACING = 2
    private const val REWARD_LOCAL_Z = 38

    fun rewardStages(contract: YamaContract): List<YamaRewardStage> = buildList {
        add(YamaRewardStage.NORMAL_TABLE)
        if (contract !in setOf(YamaContract.NONE, YamaContract.CUSTOM)) {
            add(YamaRewardStage.CONTRACT_REWARD)
        }
    }

    fun rewardLocalCoordinates(slot: Int): Pair<Int, Int> {
        require(slot in 0..1) { "Yama reward slot must be 0 or 1: $slot" }
        return FIRST_REWARD_LOCAL_X + slot * REWARD_SLOT_X_SPACING to REWARD_LOCAL_Z
    }

    fun selectedContract(
        pendingContracts: Map<Long, YamaContract>,
        leaderHash: Long,
    ): YamaContract = pendingContracts[leaderHash] ?: YamaContract.NONE

    fun canStartWithParty(contract: YamaContract, partySize: Int): Boolean =
        partySize in 1..2 && (partySize == 1 || contract.duoAllowed)

    fun entryCost(contract: YamaContract): YamaContractEntryCost? =
        when (contract) {
            YamaContract.NONE -> null
            YamaContract.CUSTOM ->
                YamaContractEntryCost(
                    itemId = YamaAssets.CERT_PINEAPPLE_PIZZA,
                    quantity = YamaRules.customOfferingCount(),
                )
            else -> YamaContractEntryCost(itemId = requireNotNull(contract.itemId), quantity = 1)
        }

    fun shouldReturnContract(
        contract: YamaContract,
        supplierHash: Long?,
        recipientHash: Long,
    ): Boolean =
        contract.returnedOnVictory &&
            contract.itemId != null &&
            supplierHash != null &&
            supplierHash == recipientHash

    fun clearPendingLobbyState(
        invitations: MutableMap<Long, Long>,
        pendingContracts: MutableMap<Long, YamaContract>,
        accountHashes: Collection<Long>,
    ) {
        if (accountHashes.isEmpty()) return
        val accounts = accountHashes.toSet()
        pendingContracts.keys.removeAll(accounts)
        invitations.entries.removeAll { (target, leader) ->
            target in accounts || leader in accounts
        }
    }
}

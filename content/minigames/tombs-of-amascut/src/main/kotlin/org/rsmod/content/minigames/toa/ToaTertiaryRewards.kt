package org.rsmod.content.minigames.toa

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.account.character.main.CharacterAccountRepository
import org.rsmod.api.account.saver.AccountSavingService

internal const val TOA_PENDING_REWARD_PERSISTENCE_KEY = "toa_pending_reward_v1"

internal data class ToaTertiaryProfile(
    val completions: Int = 0,
    val threadReceived: Boolean = false,
    val ownedJewels: Set<Int> = emptySet(),
    val completionCounts: Map<ToaMode, Int> = emptyMap(),
    val bestTicks: Int = Int.MAX_VALUE,
    val eliteClueAllowed: Boolean = true,
    val guardianUnlocked: Boolean = false,
) {
    init {
        require(completions >= 0)
        require(ownedJewels.all(ToaTertiaryRewardRules.jewelIds::contains))
        require(completionCounts.values.all { it >= 0 })
        require(bestTicks >= 0)
    }

    fun completionCount(mode: ToaMode): Int = completionCounts[mode] ?: 0
}

internal data class ToaJewelReward(val jewelId: Int, val partisanId: Int)

internal data class ToaCompletionPersistence(
    val completionPersistenceKey: String,
    val completions: Int,
    val bestTicks: Int,
)

/** Current OSRS tertiary rates expressed as exact integer ticket spaces. */
internal object ToaTertiaryRewardRules {
    const val THREAD_OF_ELIDINIS = 27279
    const val BREACH_OF_THE_SCARAB = 27283
    const val EYE_OF_THE_CORRUPTOR = 27285
    const val JEWEL_OF_THE_SUN = 27289
    const val JEWEL_OF_AMASCUT = 30893
    const val TUMEKENS_GUARDIAN = 27352
    const val ELITE_CLUE_SCROLL = 12073
    const val ELITE_CLUE_SCROLL_BOX = 24365

    const val KERIS_PARTISAN_OF_BREACHING = 27284
    const val KERIS_PARTISAN_OF_CORRUPTION = 27287
    const val KERIS_PARTISAN_OF_THE_SUN = 27291
    const val KERIS_PARTISAN_OF_AMASCUT = 30891

    const val THREAD_INITIAL_TICKET_BOUND = 150
    const val THREAD_REPEAT_TICKET_BOUND = 50
    const val JEWEL_TICKET_BOUND = 3_750
    const val CLUE_TICKET_BOUND = 100

    val jewels =
        listOf(
            ToaJewelReward(BREACH_OF_THE_SCARAB, KERIS_PARTISAN_OF_BREACHING),
            ToaJewelReward(EYE_OF_THE_CORRUPTOR, KERIS_PARTISAN_OF_CORRUPTION),
            ToaJewelReward(JEWEL_OF_THE_SUN, KERIS_PARTISAN_OF_THE_SUN),
            ToaJewelReward(JEWEL_OF_AMASCUT, KERIS_PARTISAN_OF_AMASCUT),
        )

    val jewelIds: Set<Int> = jewels.mapTo(linkedSetOf(), ToaJewelReward::jewelId)

    fun threadTicketBound(profile: ToaTertiaryProfile): Int =
        if (profile.threadReceived) THREAD_REPEAT_TICKET_BOUND else THREAD_INITIAL_TICKET_BOUND

    fun threadWinningTickets(profile: ToaTertiaryProfile): Int =
        if (profile.threadReceived) 1 else 15 + 2 * profile.completions.coerceAtMost(15)

    fun threadRolled(profile: ToaTertiaryProfile, ticket: Int): Boolean {
        require(ticket in 0 until threadTicketBound(profile))
        return ticket < threadWinningTickets(profile)
    }

    /**
     * Four 1/50 base jewel chances share one roll. Their linear bad-luck mitigation reaches twelve
     * total winning fiftieths at 75 completions while a jewel is missing. Once all four are
     * possessed, OSRS reverts the combined chance to the unscaled base 4/50.
     */
    fun jewelWinningTickets(profile: ToaTertiaryProfile): Int {
        if (profile.ownedJewels.size == jewels.size) return 300
        return 300 + 8 * profile.completions.coerceAtMost(75)
    }

    fun jewelRolled(profile: ToaTertiaryProfile, ticket: Int): Boolean {
        require(ticket in 0 until JEWEL_TICKET_BOUND)
        return ticket < jewelWinningTickets(profile)
    }

    fun jewelCandidates(profile: ToaTertiaryProfile): List<ToaJewelReward> {
        val missing = jewels.filterNot { it.jewelId in profile.ownedJewels }
        return missing.ifEmpty { jewels }
    }

    fun jewelAt(profile: ToaTertiaryProfile, ticket: Int): ToaJewelReward {
        val candidates = jewelCandidates(profile)
        require(ticket in candidates.indices)
        return candidates[ticket]
    }

    fun clueWinningTickets(eligiblePoints: Int): Int =
        (eligiblePoints.coerceAtLeast(0) / 2_000).coerceAtMost(25)

    fun clueRolled(eligiblePoints: Int, ticket: Int): Boolean {
        require(ticket in 0 until CLUE_TICKET_BOUND)
        return ticket < clueWinningTickets(eligiblePoints)
    }

    fun petScaledRaidLevel(raidLevel: Int): Int {
        val level = raidLevel.coerceAtLeast(0)
        return when {
            level <= 400 -> level
            level <= 550 -> 400 + (level - 400) / 3
            else -> 450
        }
    }

    fun petPointsPerPercent(raidLevel: Int): Int = 350_000 - 700 * petScaledRaidLevel(raidLevel)

    fun petTicketBound(raidLevel: Int): Int = petPointsPerPercent(raidLevel) * 100

    fun petRolled(eligiblePoints: Int, raidLevel: Int, ticket: Int): Boolean {
        require(ticket in 0 until petTicketBound(raidLevel))
        return ticket < eligiblePoints.coerceAtLeast(0)
    }
}

internal object ToaRewardAccountIdentity {
    fun characterId(accountHash: Long): Int {
        val value = accountHash and 0xFFFF_FFFFL
        require(value in 1..Int.MAX_VALUE.toLong()) {
            "Invalid character identity in ToA account hash $accountHash."
        }
        return value.toInt()
    }
}

/** Preserves every existing participant roll and fills only missing party entitlements. */
internal object ToaRewardPlanCompletion {
    fun complete(
        participantAccountHashes: Collection<Long>,
        existing: Map<Long, ToaRewardRoll>,
        generated: Map<Long, ToaRewardRoll>,
    ): Map<Long, ToaRewardRoll> {
        val participants = participantAccountHashes.toCollection(linkedSetOf())
        require(existing.keys.all(participants::contains))
        require(generated.keys.all(participants::contains))
        require(existing.keys.intersect(generated.keys).isEmpty())
        val plan =
            participants.associateWithTo(linkedMapOf()) { accountHash ->
                existing[accountHash]
                    ?: generated[accountHash]
                    ?: error("Missing ToA reward entitlement for account $accountHash")
            }
        require(plan.values.count(ToaRewardRoll::unique) <= 1) {
            "A ToA party reward plan cannot contain multiple tradeable uniques."
        }
        return plan
    }
}

/** Writes the complete party entitlement in one database transaction, including offline members. */
@Singleton
internal class ToaRewardEntitlementPersistence
@Inject
constructor(
    private val accountSaving: AccountSavingService,
    private val accounts: CharacterAccountRepository,
) {
    fun persistAll(
        plan: Map<Long, ToaRewardRoll>,
        completionUpdates: Map<Long, ToaCompletionPersistence> = emptyMap(),
        afterCommit: () -> Unit,
    ) {
        require(plan.isNotEmpty())
        val encoded = plan.mapValues { ToaPendingRewardCodec.encode(it.value) }
        accountSaving.withTransactionBlockingAndThen(
            block = { connection ->
                encoded.forEach { (accountHash, payload) ->
                    val characterId = ToaRewardAccountIdentity.characterId(accountHash)
                    accounts.upsertPersistentCharacterAttr(
                        connection = connection,
                        characterId = characterId,
                        persistenceKey = TOA_PENDING_REWARD_PERSISTENCE_KEY,
                        value = payload,
                    )
                    completionUpdates[accountHash]?.let { update ->
                        accounts.upsertPersistentCharacterAttr(
                            connection,
                            characterId,
                            update.completionPersistenceKey,
                            update.completions,
                        )
                        accounts.upsertPersistentCharacterAttr(
                            connection,
                            characterId,
                            "toa_best_ticks",
                            update.bestTicks,
                        )
                    }
                }
            },
            afterCommit = { afterCommit() },
        )
    }
}

/**
 * Joins delivery and finalization into the synchronous full-account transaction supplied by the
 * account service. Tests inject the same boundary to exercise process-crash cut points.
 */
internal object ToaCrashSafeClaimCommit {
    fun commit(
        commitMutation: ((() -> Boolean), (() -> Unit)) -> Boolean,
        deliver: () -> Boolean,
        afterDelivery: () -> Unit = {},
        finalizeInMemory: () -> Unit,
        rollback: () -> Unit,
    ): Boolean {
        val mutation: () -> Boolean = {
            if (!deliver()) {
                false
            } else {
                afterDelivery()
                finalizeInMemory()
                true
            }
        }
        return commitMutation(mutation, rollback)
    }
}

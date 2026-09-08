@file:OptIn(dev.openrune.types.util.UncheckedType::class)

package org.rsmod.content.minigames.toa

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.InvScope
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.account.saver.AccountSavingService
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.collectionlog.CollectionLogAwardKind
import org.rsmod.api.collectionlog.CollectionLogAwardSource
import org.rsmod.api.collectionlog.CollectionLogService
import org.rsmod.api.collectionlog.PlayerCollectionLogLoad
import org.rsmod.api.instances.RaidRewardClaim
import org.rsmod.api.instances.RaidRewardClaimResult
import org.rsmod.api.invtx.add
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.output.mes
import org.rsmod.api.random.GameRandom
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory

internal data class WeightedToaUnique(val objId: Int, val weight: Int, val minimumLevel: Int = 0)

internal data class ToaCommonReward(
    val objId: Int,
    val divisor: Int,
    val noted: Boolean = false,
    val fixedQuantity: Int? = null,
)

/** Survives logout and restarts so an unclaimed raid reward can be recovered in the lobby. */
internal val TOA_PENDING_REWARD: AttributeKey<MutableList<Int>> =
    AttributeKey(persistenceKey = TOA_PENDING_REWARD_PERSISTENCE_KEY)

internal val TOA_GUARDIAN_UNLOCKED: AttributeKey<Boolean> =
    AttributeKey(persistenceKey = "toa_tumekens_guardian_unlocked_v1")

internal object ToaRewardRules {
    // Integer tenths preserve every five-level interpolation step without floating-point tickets.
    val uniques =
        listOf(
            WeightedToaUnique(26219, 70, 50), // Osmumten's fang
            WeightedToaUnique(25975, 70, 50), // Lightbearer
            WeightedToaUnique(25985, 30, 150), // Elidinis' ward
            WeightedToaUnique(27226, 20, 150), // Masori mask
            WeightedToaUnique(27229, 20, 150), // Masori body
            WeightedToaUnique(27232, 20, 150), // Masori chaps
            WeightedToaUnique(27277, 10, 150), // Tumeken's shadow
        )

    val untradeableFallbacks =
        listOf(
            ToaTertiaryRewardRules.THREAD_OF_ELIDINIS,
            ToaTertiaryRewardRules.BREACH_OF_THE_SCARAB,
            ToaTertiaryRewardRules.EYE_OF_THE_CORRUPTOR,
            ToaTertiaryRewardRules.JEWEL_OF_THE_SUN,
            ToaTertiaryRewardRules.JEWEL_OF_AMASCUT,
        )

    const val FOSSILISED_DUNG = 27216
    const val COMMON_ROLLS = 3

    // Every entry has equal 1/27 selection weight. Item IDs are the unnoted/base definitions.
    val commonRewards =
        listOf(
            ToaCommonReward(995, 1), // Coins
            ToaCommonReward(560, 20), // Death rune
            ToaCommonReward(566, 40), // Soul rune
            ToaCommonReward(444, 90, noted = true), // Gold ore
            ToaCommonReward(11232, 100), // Dragon dart tip
            ToaCommonReward(6332, 180, noted = true), // Mahogany logs
            ToaCommonReward(1623, 200, noted = true), // Sapphire
            ToaCommonReward(1621, 250, noted = true), // Emerald
            ToaCommonReward(2357, 250, noted = true), // Gold bar
            ToaCommonReward(3138, 250, noted = true), // Potato cactus
            ToaCommonReward(383, 250, noted = true), // Raw shark
            ToaCommonReward(1619, 300, noted = true), // Ruby
            ToaCommonReward(1617, 400, noted = true), // Diamond
            ToaCommonReward(389, 450, noted = true), // Raw manta ray
            ToaCommonReward(6016, 600, noted = true), // Cactus spine
            ToaCommonReward(1631, 600, noted = true), // Dragonstone
            ToaCommonReward(1391, 1_100, noted = true), // Battlestaff
            ToaCommonReward(5935, 1_100, noted = true), // Coconut milk
            ToaCommonReward(27272, 1_100, noted = true), // Lily of the Sands
            ToaCommonReward(5296, 2_000), // Toadflax seed
            ToaCommonReward(5295, 2_500), // Ranarr seed
            ToaCommonReward(5304, 3_200), // Torstol seed
            ToaCommonReward(5300, 3_200), // Snapdragon seed
            ToaCommonReward(1149, 4_000, noted = true), // Dragon med helm
            ToaCommonReward(5316, 6_500), // Magic seed
            ToaCommonReward(26390, 7_500), // Blood essence
            ToaCommonReward(27293, 1, fixedQuantity = 1), // Cache of runes
        )

    val totalUniqueWeight: Int
        get() = uniques.sumOf(WeightedToaUnique::weight)

    fun uniqueTable(raidLevel: Int): List<WeightedToaUnique> {
        val level = (raidLevel.coerceIn(0, 600) / 5) * 5
        val (fang, lightbearer) =
            when {
                level <= 300 -> 70 to 70
                level <= 350 -> {
                    val delta = (level - 300) / 5
                    (70 - delta) to (70 - delta)
                }
                level <= 400 -> (60 - ((level - 350) * 2 / 5)) to (60 - ((level - 350) / 5))
                level <= 450 -> 40 to (50 - ((level - 400) / 5))
                level <= 500 -> (40 - ((level - 450) / 5)) to (40 - ((level - 450) / 10))
                else -> 30 to 35
            }
        return uniques.map { unique ->
            when (unique.objId) {
                26219 -> unique.copy(weight = fang)
                25975 -> unique.copy(weight = lightbearer)
                else -> unique
            }
        }
    }

    fun totalUniqueWeight(raidLevel: Int): Int =
        uniqueTable(raidLevel).sumOf(WeightedToaUnique::weight)

    fun uniqueAtTicket(ticket: Int): WeightedToaUnique = uniqueAtTicket(300, ticket)

    fun uniqueAtTicket(raidLevel: Int, ticket: Int): WeightedToaUnique {
        val table = uniqueTable(raidLevel)
        val total = table.sumOf(WeightedToaUnique::weight)
        require(ticket in 0 until total)
        var cursor = 0
        return table.first {
            cursor += it.weight
            ticket < cursor
        }
    }

    fun commonQuantity(points: Int, raidLevel: Int, reward: ToaCommonReward): Int {
        reward.fixedQuantity?.let {
            return it
        }
        val eligiblePoints = points.coerceAtLeast(0).toLong()
        val level = raidLevel.coerceIn(0, 600)
        val quantity =
            if (level >= 300) {
                val percent = 115L + ((level - 300) / 5)
                eligiblePoints * percent / (reward.divisor.toLong() * 100L)
            } else {
                eligiblePoints / reward.divisor
            }
        return quantity.coerceAtLeast(1L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    fun commonRewardAt(ticket: Int, points: Int, raidLevel: Int): ToaReward {
        require(ticket in commonRewards.indices)
        val reward = commonRewards[ticket]
        return ToaReward(
            objId = reward.objId,
            count = commonQuantity(points, raidLevel, reward),
            unique = false,
            noted = reward.noted,
        )
    }

    fun commonRolls(tickets: List<Int>, points: Int, raidLevel: Int): List<ToaReward> {
        require(tickets.size == COMMON_ROLLS)
        return tickets.map { commonRewardAt(it, points, raidLevel) }
    }

    fun lowPointReward(): ToaReward = ToaReward(FOSSILISED_DUNG, 1, unique = false, noted = true)

    fun requiresBypass(raidLevel: Int, unique: WeightedToaUnique): Boolean =
        raidLevel < unique.minimumLevel

    fun assignedUniqueReward(
        raidLevel: Int,
        unique: WeightedToaUnique,
        bypassSucceeded: Boolean,
        fallbackObjId: Int?,
    ): ToaReward {
        if (!requiresBypass(raidLevel, unique) || bypassSucceeded) {
            return ToaReward(unique.objId, 1, unique = true)
        }
        require(fallbackObjId in untradeableFallbacks)
        return ToaReward(requireNotNull(fallbackObjId), 1, unique = false)
    }
}

internal object ToaPendingRewardCodec {
    private const val MAGIC_V1 = 0x544F_4101
    private const val MAGIC_V2 = 0x544F_4102
    private const val MAGIC_V3 = 0x544F_4103

    fun encode(roll: ToaRewardRoll): MutableList<Int> {
        val claimId = roll.claimId
        if (claimId == null && !roll.pet) {
            return buildList {
                    add(MAGIC_V2)
                    add(if (roll.unique) 1 else 0)
                    add(roll.rewards.size)
                    addRewards(roll.rewards)
                }
                .toMutableList()
        }
        requireNotNull(claimId) { "A pet-bearing reward must have a durable claim identity." }
        return buildList {
                add(MAGIC_V3)
                add(if (roll.unique) 1 else 0)
                add(if (roll.pet) 1 else 0)
                addLong(claimId.raidMostSignificantBits)
                addLong(claimId.raidLeastSignificantBits)
                addLong(claimId.accountHash)
                add(roll.rewards.size)
                addRewards(roll.rewards)
            }
            .toMutableList()
    }

    fun decode(values: List<Int>?): ToaRewardRoll? =
        runCatching {
                if (values == null) return null
                var index = 0
                fun next(): Int = values[index++]
                fun nextLong(): Long =
                    (next().toLong() shl 32) or (next().toLong() and 0xFFFF_FFFFL)
                val magic =
                    next().also { require(it == MAGIC_V1 || it == MAGIC_V2 || it == MAGIC_V3) }
                val unique = next().also { require(it in 0..1) } == 1
                val pet = if (magic == MAGIC_V3) next().also { require(it in 0..1) } == 1 else false
                val claimId =
                    if (magic == MAGIC_V3) {
                        ToaRewardClaimId(nextLong(), nextLong(), nextLong())
                    } else {
                        null
                    }
                val count = next().also { require(it in 1..16) }
                val rewards =
                    List(count) {
                        val objId = next().also { require(it > 0) }
                        val amount = next().also { require(it > 0) }
                        val rewardUnique = next().also { require(it in 0..1) } == 1
                        val rewardNoted =
                            if (magic == MAGIC_V2 || magic == MAGIC_V3) {
                                next().also { require(it in 0..1) } == 1
                            } else {
                                false
                            }
                        ToaReward(objId, amount, rewardUnique, rewardNoted)
                    }
                require(index == values.size)
                require(unique == rewards.any(ToaReward::unique))
                ToaRewardRoll(rewards, unique, pet, claimId)
            }
            .getOrNull()

    private fun MutableList<Int>.addRewards(rewards: List<ToaReward>) {
        require(rewards.size in 1..16)
        rewards.forEach { reward ->
            add(reward.objId)
            add(reward.count)
            add(if (reward.unique) 1 else 0)
            add(if (reward.noted) 1 else 0)
        }
    }

    private fun MutableList<Int>.addLong(value: Long) {
        add((value ushr 32).toInt())
        add(value.toInt())
    }
}

@Singleton
internal class ToaRewardService
@Inject
constructor(
    private val random: GameRandom,
    private val players: PlayerList,
    private val collectionLog: CollectionLogService,
    private val entitlements: ToaRewardEntitlementPersistence,
    private val accountSaving: AccountSavingService,
) {
    private val log = InlineLogger()
    private val claiming = mutableSetOf<PlayerUid>()

    fun verifyDefinitions() {
        (ToaRewardRules.uniques.map(WeightedToaUnique::objId) +
                ToaRewardRules.untradeableFallbacks +
                ToaRewardRules.commonRewards.map(ToaCommonReward::objId) +
                listOf(
                    ToaRewardRules.FOSSILISED_DUNG,
                    ToaTertiaryRewardRules.ELITE_CLUE_SCROLL,
                    ToaTertiaryRewardRules.ELITE_CLUE_SCROLL_BOX,
                    ToaTertiaryRewardRules.TUMEKENS_GUARDIAN,
                    ToaTertiaryRewardRules.KERIS_PARTISAN_OF_BREACHING,
                    ToaTertiaryRewardRules.KERIS_PARTISAN_OF_CORRUPTION,
                    ToaTertiaryRewardRules.KERIS_PARTISAN_OF_THE_SUN,
                    ToaTertiaryRewardRules.KERIS_PARTISAN_OF_AMASCUT,
                ))
            .distinct()
            .forEach {
                requireNotNull(ServerCacheManager.getItem(it)) {
                    "Missing ToA reward definition $it"
                }
                RSCM.getReverseMapping(RSCMType.OBJ, it)
            }
        val rewardInterface =
            requireNotNull(ServerCacheManager.getInterface(REWARD_INTERFACE)) {
                "Missing native revision-240 ToA reward interface $REWARD_INTERFACE"
            }
        REWARD_COMPONENTS.forEach { packed ->
            val component = packed and 0xFFFF
            requireNotNull(rewardInterface.components[component]) {
                "Missing native revision-240 ToA reward component $REWARD_INTERFACE:$component"
            }
            RSCM.getReverseMapping(RSCMType.COMPONENT, packed)
        }
    }

    fun snapshotTertiaryProfile(player: Player): ToaTertiaryProfile {
        val permanentInventories =
            player.invMap.values.filter { inventory -> inventory.type.scope == InvScope.Perm }
        fun owns(objId: Int): Boolean =
            permanentInventories.any { inventory -> inventory.objs.any { it?.id == objId } }

        val ownedJewels =
            ToaTertiaryRewardRules.jewels
                .filter { jewel -> owns(jewel.jewelId) || owns(jewel.partisanId) }
                .mapTo(linkedSetOf(), ToaJewelReward::jewelId)
        val threadReceived =
            (collectionLog.load(player) as? PlayerCollectionLogLoad.Ready)?.let { loaded ->
                collectionLog.engine.quantity(
                    loaded.state,
                    ToaTertiaryRewardRules.THREAD_OF_ELIDINIS,
                ) > 0
            } == true
        val completionCounts =
            mapOf(
                ToaMode.ENTRY to player.attr.getOrDefault(TOA_ENTRY_COMPLETIONS, 0),
                ToaMode.NORMAL to player.attr.getOrDefault(TOA_NORMAL_COMPLETIONS, 0),
                ToaMode.EXPERT to player.attr.getOrDefault(TOA_EXPERT_COMPLETIONS, 0),
            )
        return ToaTertiaryProfile(
            completions = completionCounts.values.sum(),
            threadReceived = threadReceived,
            ownedJewels = ownedJewels,
            completionCounts = completionCounts,
            bestTicks = player.attr.getOrDefault(TOA_BEST_TICKS, Int.MAX_VALUE),
            eliteClueAllowed =
                !owns(ToaTertiaryRewardRules.ELITE_CLUE_SCROLL) &&
                    !owns(ToaTertiaryRewardRules.ELITE_CLUE_SCROLL_BOX),
            guardianUnlocked = player.attr[TOA_GUARDIAN_UNLOCKED] == true,
        )
    }

    fun prepare(raid: ToaRaidSession) {
        val participants = raid.participants.eligible()
        if (participants.isEmpty()) return
        val allPersonalPoints =
            raid.participants.values.associateWith { ToaRules.lootEligiblePoints(it.points) }
        val personalPoints = participants.associateWith { allPersonalPoints.getValue(it) }
        // Departed players retain their contribution to the party's purple chance, but only
        // reward-eligible players may own or receive the completed raid's reward.
        val totalPoints = allPersonalPoints.values.sum()
        val rewardRaidLevel = raid.rewardRaidLevel
        val claimIds =
            participants.associateWith { participant ->
                ToaRewardClaimId(
                    raid.party.id.mostSignificantBits,
                    raid.party.id.leastSignificantBits,
                    participant.accountHash,
                )
            }
        val plan = linkedMapOf<Long, ToaRewardRoll>()
        val previousPlan = raid.rewardPlan.orEmpty()
        participants.forEach { participant ->
            val existing = previousPlan[participant.accountHash] ?: participant.pendingReward
            if (existing != null) {
                val claimId = claimIds.getValue(participant)
                plan[participant.accountHash] =
                    if (existing.claimId == claimId) existing else existing.copy(claimId = claimId)
            }
        }

        val missing = participants.filterNot { plan.containsKey(it.accountHash) }
        val existingUnique = plan.values.any(ToaRewardRoll::unique)
        val ownerPoints = missing.sumOf { personalPoints.getValue(it) }
        val chance = ToaRules.uniqueChance(totalPoints, rewardRaidLevel)
        val uniqueRolled =
            !existingUnique &&
                missing.isNotEmpty() &&
                random.of(0..999_999) < (chance * 1_000_000).toInt()
        val owner =
            if (uniqueRolled && ownerPoints > 0) {
                ToaRules.ownerTicket(missing, random.of(0 until ownerPoints))
            } else {
                null
            }
        val assignedUnique =
            owner?.let {
                val weight = ToaRewardRules.totalUniqueWeight(rewardRaidLevel)
                ToaRewardRules.uniqueAtTicket(rewardRaidLevel, random.of(0 until weight))
            }
        val bypassRequired =
            assignedUnique?.let { ToaRewardRules.requiresBypass(rewardRaidLevel, it) } == true
        val bypassSucceeded = !bypassRequired || random.of(0 until 50) == 0
        val fallback =
            if (assignedUnique != null && bypassRequired && !bypassSucceeded) {
                ToaRewardRules.untradeableFallbacks[
                        random.of(ToaRewardRules.untradeableFallbacks.indices)]
            } else {
                null
            }
        val assignedReward =
            assignedUnique?.let {
                ToaRewardRules.assignedUniqueReward(rewardRaidLevel, it, bypassSucceeded, fallback)
            }
        val trueUnique = assignedReward?.takeIf(ToaReward::unique)

        val generated = linkedMapOf<Long, ToaRewardRoll>()
        missing.forEach { participant ->
            val points = personalPoints.getValue(participant)
            val baseRewards =
                when {
                    points == 0 -> listOf(ToaRewardRules.lowPointReward())
                    participant === owner && assignedUnique != null ->
                        listOf(requireNotNull(assignedReward))
                    else -> rollCommons(points, rewardRaidLevel)
                }
            val tertiary = rollTertiaries(participant.tertiaryProfile, points, rewardRaidLevel)
            generated[participant.accountHash] =
                ToaRewardRoll(
                    rewards = baseRewards + tertiary.rewards,
                    unique = participant === owner && trueUnique != null,
                    pet = tertiary.pet,
                    claimId = claimIds.getValue(participant),
                )
        }
        val completedPlan =
            ToaRewardPlanCompletion.complete(
                participantAccountHashes = participants.map(ToaParticipant::accountHash),
                existing = plan,
                generated = generated,
            )
        plan.clear()
        plan.putAll(completedPlan)
        raid.rewardPlan = plan.toMap()

        val mode = raid.mode
        val completionAttribute = completionAttribute(mode)
        val completionPersistenceKey = requireNotNull(completionAttribute.persistenceKey)
        val timerStart = raid.timerStartedAt ?: raid.startedAt
        val elapsed = ((raid.completedAt ?: raid.startedAt) - timerStart).coerceAtLeast(0)
        val completionUpdates =
            participants.filterNot(ToaParticipant::completionRecorded).associate { participant ->
                participant.accountHash to
                    ToaCompletionPersistence(
                        completionPersistenceKey = completionPersistenceKey,
                        completions = participant.tertiaryProfile.completionCount(mode) + 1,
                        bestTicks = minOf(participant.tertiaryProfile.bestTicks, elapsed),
                    )
            }

        entitlements.persistAll(plan, completionUpdates) {
            participants.forEach { participant ->
                val roll = plan.getValue(participant.accountHash)
                participant.pendingReward = roll
                participant.rewardClaimed = false
                completionUpdates[participant.accountHash]?.let { update ->
                    participant.completionRecorded = true
                    participant.uid
                        .resolve(players)
                        ?.takeIf { it.accountHash == participant.accountHash }
                        ?.let { player ->
                            player.attr[completionAttribute] = update.completions
                            player.attr[TOA_BEST_TICKS] = update.bestTicks
                        }
                }
                participant.uid
                    .resolve(players)
                    ?.takeIf { it.accountHash == participant.accountHash }
                    ?.let { store(it, roll) }
            }
        }
        log.info {
            "[ToA] rewards party=${raid.party.id} level=$rewardRaidLevel points=$totalPoints " +
                "chance=$chance participants=${plan.size} assigned=${assignedUnique?.objId} " +
                "unique=${trueUnique?.objId} fallback=" +
                assignedReward?.takeUnless(ToaReward::unique)?.objId
        }
    }

    private fun completionAttribute(mode: ToaMode): AttributeKey<Int> =
        when (mode) {
            ToaMode.ENTRY -> TOA_ENTRY_COMPLETIONS
            ToaMode.NORMAL -> TOA_NORMAL_COMPLETIONS
            ToaMode.EXPERT -> TOA_EXPERT_COMPLETIONS
        }

    private fun rollTertiaries(
        profile: ToaTertiaryProfile,
        points: Int,
        raidLevel: Int,
    ): ToaTertiaryRoll {
        val rewards = mutableListOf<ToaReward>()
        val threadBound = ToaTertiaryRewardRules.threadTicketBound(profile)
        if (ToaTertiaryRewardRules.threadRolled(profile, random.of(0 until threadBound))) {
            rewards += ToaReward(ToaTertiaryRewardRules.THREAD_OF_ELIDINIS, 1, unique = false)
        }
        val jewelTicket = random.of(0 until ToaTertiaryRewardRules.JEWEL_TICKET_BOUND)
        if (ToaTertiaryRewardRules.jewelRolled(profile, jewelTicket)) {
            val candidates = ToaTertiaryRewardRules.jewelCandidates(profile)
            val jewel = ToaTertiaryRewardRules.jewelAt(profile, random.of(candidates.indices))
            rewards += ToaReward(jewel.jewelId, 1, unique = false)
        }
        // The base OSRS clue formula is exact. The Elite Combat Achievement x1.05 modifier is
        // intentionally omitted until OpenRune has real per-account CA state (the current helper
        // is a hard-coded stub and cannot safely represent eligibility).
        if (
            profile.eliteClueAllowed &&
                ToaTertiaryRewardRules.clueRolled(
                    points,
                    random.of(0 until ToaTertiaryRewardRules.CLUE_TICKET_BOUND),
                )
        ) {
            rewards += ToaReward(ToaTertiaryRewardRules.ELITE_CLUE_SCROLL, 1, unique = false)
        }
        val pet =
            !profile.guardianUnlocked &&
                ToaTertiaryRewardRules.petRolled(
                    points,
                    raidLevel,
                    random.of(0 until ToaTertiaryRewardRules.petTicketBound(raidLevel)),
                )
        return ToaTertiaryRoll(rewards, pet)
    }

    fun pending(player: Player): ToaRewardRoll? =
        ToaPendingRewardCodec.decode(player.attr[TOA_PENDING_REWARD])

    fun hasStored(player: Player): Boolean = pending(player) != null

    fun sync(player: Player, raid: ToaRaidSession): ToaRewardRoll? {
        val participant = raid.participant(player.uid) ?: return pending(player)
        val roll = participant.pendingReward ?: pending(player)
        if (roll != null) {
            participant.pendingReward = roll
            participant.rewardClaimed = false
            store(player, roll)
        }
        return roll
    }

    fun pending(player: Player, raid: ToaRaidSession): ToaRewardRoll? = sync(player, raid)

    fun claimToInventory(player: Player, raid: ToaRaidSession): Boolean =
        claimToInventoryAttempt(player, raid).result == RaidRewardClaimResult.Claimed

    fun claimToInventoryForLeave(player: Player, raid: ToaRaidSession): RaidRewardClaimResult =
        claimToInventoryAttempt(player, raid).result

    fun detachToStorage(player: Player, raid: ToaRaidSession): Boolean {
        val participant = raid.participant(player.uid) ?: return false
        val roll = participant.pendingReward ?: pending(player) ?: return false
        store(player, roll)
        participant.pendingReward = null
        participant.rewardClaimed = true
        player.mes("Your unclaimed Tombs reward is being held in the Rewards Niche.")
        return true
    }

    fun claimStoredToInventory(player: Player): Boolean {
        val roll = pending(player)
        if (roll == null) {
            player.mes("You have no Tombs reward waiting.")
            return false
        }
        if (!claiming.add(player.uid)) return false
        return try {
            commitClaim(
                player,
                participant = null,
                roll,
                player.inv,
                "inventory",
                "You need enough inventory space for your entire Tombs reward.",
            ) == ToaClaimCommitOutcome.Claimed
        } finally {
            claiming.remove(player.uid)
        }
    }

    private fun claimToInventoryAttempt(
        player: Player,
        raid: ToaRaidSession,
    ): RaidRewardClaim<ToaRewardRoll> {
        val participant =
            raid.participant(player.uid)
                ?: return RaidRewardClaim(RaidRewardClaimResult.NothingToClaim)
        if (!claiming.add(player.uid)) {
            player.mes("Your Tombs reward is already being processed.")
            return RaidRewardClaim(RaidRewardClaimResult.Busy)
        }
        try {
            if (participant.rewardClaimed) {
                player.mes("You have already searched your sarcophagus.")
                return RaidRewardClaim(RaidRewardClaimResult.AlreadyClaimed)
            }
            val roll =
                participant.pendingReward
                    ?: run {
                        player.mes("You have no Tombs reward waiting.")
                        return RaidRewardClaim(RaidRewardClaimResult.NothingToClaim)
                    }
            return when (
                commitClaim(
                    player,
                    participant,
                    roll,
                    player.inv,
                    "inventory",
                    "You need enough inventory space for your entire Tombs reward. " +
                        "Free some space and try again.",
                )
            ) {
                ToaClaimCommitOutcome.Claimed ->
                    RaidRewardClaim(RaidRewardClaimResult.Claimed, roll)
                ToaClaimCommitOutcome.DestinationFull ->
                    RaidRewardClaim(RaidRewardClaimResult.InventoryFull)
                ToaClaimCommitOutcome.PersistenceFailed ->
                    RaidRewardClaim(RaidRewardClaimResult.Busy)
            }
        } finally {
            claiming.remove(player.uid)
        }
    }

    fun claimToBank(player: Player, raid: ToaRaidSession, bank: Inventory): Boolean {
        val participant = raid.participant(player.uid) ?: return false
        if (!claiming.add(player.uid)) return false
        return try {
            if (participant.rewardClaimed) {
                player.mes("You have already searched your sarcophagus.")
                return false
            }
            val roll =
                participant.pendingReward
                    ?: run {
                        player.mes("You have no Tombs reward waiting.")
                        return false
                    }
            commitClaim(
                player,
                participant,
                roll,
                bank,
                "bank",
                "Your bank does not have enough room for all of these rewards.",
            ) == ToaClaimCommitOutcome.Claimed
        } finally {
            claiming.remove(player.uid)
        }
    }

    fun claimStoredToBank(player: Player, bank: Inventory): Boolean {
        val roll = pending(player)
        if (roll == null) {
            player.mes("You have no Tombs reward waiting.")
            return false
        }
        if (!claiming.add(player.uid)) return false
        return try {
            commitClaim(
                player,
                participant = null,
                roll,
                bank,
                "bank",
                "Your bank does not have enough room for all of these rewards.",
            ) == ToaClaimCommitOutcome.Claimed
        } finally {
            claiming.remove(player.uid)
        }
    }

    fun discard(player: Player, raid: ToaRaidSession): Boolean {
        val participant = raid.participant(player.uid) ?: return false
        if (!claiming.add(player.uid)) return false
        return try {
            if (participant.rewardClaimed || participant.pendingReward == null) return false
            val snapshot = captureClaimSnapshot(player, destination = null, participant)
            val committed =
                try {
                    accountSaving.commitPlayerMutationBlocking(
                        player,
                        mutation = {
                            participant.rewardClaimed = true
                            participant.pendingReward = null
                            player.attr.remove(TOA_PENDING_REWARD)
                            true
                        },
                        rollback = { restoreClaimSnapshot(player, null, participant, snapshot) },
                    )
                } catch (cause: Throwable) {
                    log.error(cause) { "Could not durably discard ToA reward for $player" }
                    player.mes("Your reward could not be discarded safely. Please try again.")
                    false
                }
            if (committed) player.mes("You discard your Tombs of Amascut reward.")
            committed
        } finally {
            claiming.remove(player.uid)
        }
    }

    fun discardStored(player: Player): Boolean {
        if (!hasStored(player) || !claiming.add(player.uid)) return false
        return try {
            val snapshot = captureClaimSnapshot(player, destination = null, participant = null)
            val committed =
                try {
                    accountSaving.commitPlayerMutationBlocking(
                        player,
                        mutation = {
                            player.attr.remove(TOA_PENDING_REWARD)
                            true
                        },
                        rollback = { restoreClaimSnapshot(player, null, null, snapshot) },
                    )
                } catch (cause: Throwable) {
                    log.error(cause) { "Could not durably discard stored ToA reward for $player" }
                    player.mes("Your reward could not be discarded safely. Please try again.")
                    false
                }
            if (committed) player.mes("You discard your Tombs of Amascut reward.")
            committed
        } finally {
            claiming.remove(player.uid)
        }
    }

    private fun commitClaim(
        player: Player,
        participant: ToaParticipant?,
        roll: ToaRewardRoll,
        destination: Inventory,
        destinationName: String,
        destinationFullMessage: String,
    ): ToaClaimCommitOutcome {
        val snapshot = captureClaimSnapshot(player, destination, participant)
        val committed =
            try {
                ToaCrashSafeClaimCommit.commit(
                    commitMutation = { mutation, rollback ->
                        accountSaving.commitPlayerMutationBlocking(player, mutation, rollback)
                    },
                    deliver = {
                        val transaction =
                            player.invTransaction(destination) {
                                val selected = select(destination)
                                roll.rewards.forEach { reward ->
                                    add(
                                        selected,
                                        reward.objId,
                                        reward.count,
                                        strict = true,
                                        cert = reward.noted,
                                    )
                                }
                            }
                        !transaction.failure
                    },
                    finalizeInMemory = {
                        recordCollectionLog(player, roll)
                        participant?.rewardClaimed = true
                        participant?.pendingReward = null
                        player.attr.remove(TOA_PENDING_REWARD)
                    },
                    rollback = { restoreClaimSnapshot(player, destination, participant, snapshot) },
                )
            } catch (cause: Throwable) {
                log.error(cause) { "Could not durably commit ToA reward for $player" }
                player.mes(
                    "Your Tombs reward could not be saved safely. Nothing was claimed; try again."
                )
                return ToaClaimCommitOutcome.PersistenceFailed
            }
        if (!committed) {
            player.mes(destinationFullMessage)
            return ToaClaimCommitOutcome.DestinationFull
        }
        showClaimMessages(player, roll, destinationName)
        return ToaClaimCommitOutcome.Claimed
    }

    private fun recordCollectionLog(player: Player, roll: ToaRewardRoll) {
        val eventId = collectionEventId(player, roll)
        collectionLog.recordBatch(
            player = player,
            awards = roll.rewards.map { reward -> reward.objId to reward.count },
            source =
                CollectionLogAwardSource(CollectionLogAwardKind.RaidReward, "tombs-of-amascut"),
            eventId = eventId,
        )
        if (roll.pet) {
            player.attr[TOA_GUARDIAN_UNLOCKED] = true
            collectionLog.record(
                player = player,
                itemId = ToaTertiaryRewardRules.TUMEKENS_GUARDIAN,
                amount = 1,
                source =
                    CollectionLogAwardSource(CollectionLogAwardKind.PetGrant, "tumekens-guardian"),
                eventId = "$eventId:pet",
            )
        }
    }

    private fun collectionEventId(player: Player, roll: ToaRewardRoll): String {
        roll.claimId?.let { claim ->
            return "${claim.raidMostSignificantBits}:${claim.raidLeastSignificantBits}:" +
                claim.accountHash
        }
        val completions =
            player.attr.getOrDefault(TOA_ENTRY_COMPLETIONS, 0) +
                player.attr.getOrDefault(TOA_NORMAL_COMPLETIONS, 0) +
                player.attr.getOrDefault(TOA_EXPERT_COMPLETIONS, 0)
        val payload = ToaPendingRewardCodec.encode(roll).joinToString(",")
        return "legacy:${player.uid}:$completions:$payload"
    }

    private fun captureClaimSnapshot(
        player: Player,
        destination: Inventory?,
        participant: ToaParticipant?,
    ): ToaClaimRollbackSnapshot =
        ToaClaimRollbackSnapshot(
            destinationObjs =
                destination?.objs?.map { obj -> obj?.let { InvObj(it) } }?.toTypedArray(),
            hadPendingReward = player.attr.has(TOA_PENDING_REWARD),
            pendingReward = player.attr[TOA_PENDING_REWARD]?.toMutableList(),
            hadCollectionLog = player.attr.has(CollectionLogService.STATE),
            collectionLogState = copyPersistenceValue(player.attr[CollectionLogService.STATE]),
            hadGuardianUnlock = player.attr.has(TOA_GUARDIAN_UNLOCKED),
            guardianUnlocked = player.attr[TOA_GUARDIAN_UNLOCKED],
            participantPendingReward = participant?.pendingReward,
            participantRewardClaimed = participant?.rewardClaimed,
        )

    private fun restoreClaimSnapshot(
        player: Player,
        destination: Inventory?,
        participant: ToaParticipant?,
        snapshot: ToaClaimRollbackSnapshot,
    ) {
        destination?.let { inventory ->
            val objs = requireNotNull(snapshot.destinationObjs)
            require(objs.size == inventory.size)
            objs.forEachIndexed { slot, obj -> inventory[slot] = obj?.let { InvObj(it) } }
            inventory.modifiedSlots.set(0, inventory.size)
        }
        if (snapshot.hadPendingReward) {
            player.attr[TOA_PENDING_REWARD] = requireNotNull(snapshot.pendingReward).toMutableList()
        } else {
            player.attr.remove(TOA_PENDING_REWARD)
        }
        if (snapshot.hadCollectionLog) {
            player.attr[CollectionLogService.STATE] = requireNotNull(snapshot.collectionLogState)
        } else {
            player.attr.remove(CollectionLogService.STATE)
        }
        if (snapshot.hadGuardianUnlock) {
            player.attr[TOA_GUARDIAN_UNLOCKED] = requireNotNull(snapshot.guardianUnlocked)
        } else {
            player.attr.remove(TOA_GUARDIAN_UNLOCKED)
        }
        if (participant != null) {
            participant.pendingReward = snapshot.participantPendingReward
            participant.rewardClaimed = requireNotNull(snapshot.participantRewardClaimed)
        }
    }

    private fun copyPersistenceValue(value: Any?): Any? =
        when (value) {
            is List<*> -> value.map { copyPersistenceValue(it) }.toMutableList()
            is Map<*, *> ->
                value.entries.associateTo(linkedMapOf()) { (key, entryValue) ->
                    key to copyPersistenceValue(entryValue)
                }
            else -> value
        }

    private fun store(player: Player, roll: ToaRewardRoll) {
        player.attr[TOA_PENDING_REWARD] = ToaPendingRewardCodec.encode(roll)
    }

    private fun showClaimMessages(player: Player, roll: ToaRewardRoll, destination: String) {
        roll.rewards.forEach { reward ->
            val colour = if (reward.unique) "aa00ff" else "ff9900"
            player.mes(
                "<col=$colour>${reward.count} x ${ServerCacheManager.getItem(reward.objId)?.name}</col>"
            )
        }
        if (roll.pet) {
            player.mes("<col=ff0000>You have a funny feeling like you're being followed.</col>")
            player.mes(
                "Tumeken's guardian has been permanently unlocked. " +
                    "The follower-call UI is not yet available on this server."
            )
        }
        player.mes(
            when {
                roll.unique ->
                    "A purple light shines as you claim your unique reward to your $destination!"
                roll.rewards.any { it.objId == ToaRewardRules.FOSSILISED_DUNG } ->
                    "Your contribution was too low for normal Tombs rewards."
                else -> "You claim your Tombs rewards to your $destination."
            }
        )
    }

    private fun rollCommons(points: Int, raidLevel: Int): List<ToaReward> {
        val tickets =
            List(ToaRewardRules.COMMON_ROLLS) { random.of(ToaRewardRules.commonRewards.indices) }
        return ToaRewardRules.commonRolls(tickets, points, raidLevel)
    }

    private data class ToaTertiaryRoll(val rewards: List<ToaReward>, val pet: Boolean)

    private enum class ToaClaimCommitOutcome {
        Claimed,
        DestinationFull,
        PersistenceFailed,
    }

    private data class ToaClaimRollbackSnapshot(
        val destinationObjs: Array<InvObj?>?,
        val hadPendingReward: Boolean,
        val pendingReward: MutableList<Int>?,
        val hadCollectionLog: Boolean,
        val collectionLogState: Any?,
        val hadGuardianUnlock: Boolean,
        val guardianUnlocked: Boolean?,
        val participantPendingReward: ToaRewardRoll?,
        val participantRewardClaimed: Boolean?,
    )

    companion object {
        const val REWARD_INTERFACE = 771
        const val REWARD_ITEMS_COMPONENT = (REWARD_INTERFACE shl 16) or 10
        const val REWARD_BANK_COMPONENT = (REWARD_INTERFACE shl 16) or 4
        const val REWARD_INVENTORY_COMPONENT = (REWARD_INTERFACE shl 16) or 6
        const val REWARD_DISCARD_COMPONENT = (REWARD_INTERFACE shl 16) or 8
        val REWARD_COMPONENTS =
            setOf(
                REWARD_ITEMS_COMPONENT,
                REWARD_BANK_COMPONENT,
                REWARD_INVENTORY_COMPONENT,
                REWARD_DISCARD_COMPONENT,
            )
    }
}

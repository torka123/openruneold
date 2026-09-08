package org.rsmod.content.minigames.toa

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.interf.IfButtonOp
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.math.floor
import kotlin.math.max
import kotlin.random.Random
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.invtx.invDel
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stopInvTransmit
import org.rsmod.content.other.consumables.ConsumableActivityAccess
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory

internal enum class ToaSupplyBundle(val displayName: String) {
    LIFE("Life"),
    CHAOS("Chaos"),
    POWER("Power"),
}

internal object ToaSupplyCheckpointRules {
    const val FIRST = 0
    const val SECOND = 1

    fun availableCheckpoint(
        claimed: Set<Int>,
        forfeited: MutableSet<Int>,
        completedPathCount: Int,
        allPathsComplete: Boolean,
    ): Int? {
        if (completedPathCount > FIRST_CHECKPOINT_PATHS && FIRST !in claimed) {
            forfeited += FIRST
        }
        if (allPathsComplete) {
            return SECOND.takeIf { it !in claimed && it !in forfeited }
        }
        return FIRST.takeIf {
            completedPathCount == FIRST_CHECKPOINT_PATHS && it !in claimed && it !in forfeited
        }
    }

    /** Compatibility helper used by the original deterministic checkpoint test. */
    fun canClaim(claimed: Int, completedPathCount: Int, allPathsComplete: Boolean): Boolean =
        when (claimed) {
            0 -> completedPathCount == FIRST_CHECKPOINT_PATHS
            1 -> allPathsComplete
            else -> false
        }

    private const val FIRST_CHECKPOINT_PATHS = 2
}

internal enum class ToaSupplyOwnership {
    CURRENT_RAID,
    FOREIGN_RAID,
    UNOWNED,
}

internal object ToaSupplyOwnershipRules {
    private const val MARKER_MASK = -0x1000000
    private const val MARKER = 0x5A000000
    private const val PAYLOAD_MASK = 0x00FFFFFF

    fun varsForInstance(instanceId: Long): Int {
        val mixed = (instanceId xor (instanceId ushr 24) xor (instanceId ushr 48)).toInt()
        val payload = mixed and PAYLOAD_MASK
        return MARKER or if (payload == 0) 1 else payload
    }

    fun isTagged(vars: Int): Boolean = vars and MARKER_MASK == MARKER

    fun classify(vars: Int, activeOwnershipVars: Int): ToaSupplyOwnership =
        when {
            vars == activeOwnershipVars && isTagged(vars) -> ToaSupplyOwnership.CURRENT_RAID
            isTagged(vars) -> ToaSupplyOwnership.FOREIGN_RAID
            else -> ToaSupplyOwnership.UNOWNED
        }

    /** Foreign/discarded ToA objects are audit-only and can never become raid supplies again. */
    fun canRestoreQuarantined(recordedOwnershipVars: Int, activeOwnershipVars: Int): Boolean {
        classify(recordedOwnershipVars, activeOwnershipVars)
        return false
    }
}

internal object ToaSupplyInvocationRules {
    fun scaled(value: Int, invocations: Set<ToaInvocation>, fractionalRoll: Double = 1.0): Int {
        if (value <= 0) return 0
        require(fractionalRoll in 0.0..1.0)
        val exact = value * ToaRules.supplyMultiplier(invocations)
        val base = floor(exact).toInt()
        val stochastic = if (fractionalRoll < exact - base) 1 else 0
        return max(1, base + stochastic)
    }

    fun givesHoneyLocust(invocations: Set<ToaInvocation>): Boolean =
        ToaInvocation.ON_A_DIET !in invocations

    fun hasFullSupplies(invocations: Set<ToaInvocation>): Boolean =
        ToaRules.supplyMultiplier(invocations) == 1.0
}

internal data class ToaSupplyStack(val objId: Int, val count: Int) {
    init {
        require(count > 0)
    }
}

internal data class ToaSupplySelection(
    val checkpoint: Int,
    val displays: Map<ToaSupplyBundle, Inventory>,
)

internal data class ToaSupplyScope(val instanceId: Long, val accountHash: Long)

internal data class ToaSupplyEscrowKey(val objId: Int, val vars: Int)

internal data class ToaSupplyQuarantineScope(val accountHash: Long, val ownershipVars: Int)

private class ToaSupplyAccountState(val ownershipVars: Int) {
    val claimed = linkedSetOf<Int>()
    val forfeited = linkedSetOf<Int>()
    val offers = mutableMapOf<Int, Map<ToaSupplyBundle, List<ToaSupplyStack>>>()
    val contents = linkedMapOf<Int, Int>()
    val escrow = linkedMapOf<ToaSupplyEscrowKey, Int>()
    val issuedChallengeItems = linkedMapOf<ToaSupplyEscrowKey, Int>()
    var pendingHoneyLocust = 0
}

@Singleton
internal class ToaSupplies
@Inject
constructor(private val activityAccess: ConsumableActivityAccess) {
    private val states = mutableMapOf<ToaSupplyScope, ToaSupplyAccountState>()
    private val quarantined =
        mutableMapOf<ToaSupplyQuarantineScope, MutableMap<ToaSupplyEscrowKey, Int>>()

    fun verifyDefinitions() {
        REVISION_ITEM_IDS.forEach { id ->
            requireNotNull(ServerCacheManager.getItem(id)) { "Missing ToA supply item $id" }
        }
        STALE_SAFE_ITEM_IDS.forEach { id ->
            requireNotNull(ServerCacheManager.getItem(id)) { "Missing ToA temporary item $id" }
        }
        listOf(SELECTION_INTERFACE, CONTAINER_INTERFACE).forEach { id ->
            requireNotNull(ServerCacheManager.getInterface(id)) {
                "Missing ToA supply interface $id"
            }
        }
        SELECTION_COMPONENTS.forEach { packed ->
            val interfaceId = packed ushr 16
            val component = packed and 0xFFFF
            requireNotNull(
                ServerCacheManager.getInterface(interfaceId)?.components?.get(component)
            ) {
                "Missing ToA supply component $interfaceId:$component"
            }
        }
        CONTAINER_COMPONENTS.forEach { packed ->
            val interfaceId = packed ushr 16
            val component = packed and 0xFFFF
            requireNotNull(
                ServerCacheManager.getInterface(interfaceId)?.components?.get(component)
            ) {
                "Missing ToA supplies component $interfaceId:$component"
            }
        }
        VIRTUAL_INVENTORY_IDS.forEach { id ->
            requireNotNull(ServerCacheManager.getInventory(id)) {
                "Missing ToA supply virtual inventory $id"
            }
        }
    }

    fun ownershipVars(raid: ToaRaidSession): Int = ownershipVars(raid.instance.id.value)

    fun isTemporaryOwnershipVars(vars: Int): Boolean = isToaOwnershipVars(vars)

    fun begin(player: Player, raid: ToaRaidSession) {
        state(player, raid)
        enterActivity(player, raid)
    }

    fun enterActivity(player: Player, raid: ToaRaidSession) {
        activityAccess.enterRaid(player, ACTIVITY_KEY, ownershipVars(raid))
    }

    fun leaveActivity(player: Player, raid: ToaRaidSession): Boolean =
        activityAccess.leaveRaid(player, ACTIVITY_KEY, ownershipVars(raid))

    fun open(player: Player, raid: ToaRaidSession): Boolean {
        if (raid.participant(player.accountHash) == null) return false
        enterActivity(player, raid)
        val state = state(player, raid)
        val checkpoint = availableCheckpoint(state, raid)
        if (checkpoint == null) {
            player.mes("The Helpful Spirit has no supplies ready for you yet.")
            return false
        }
        state.offers.getOrPut(checkpoint) { rollAllBundles(raid, checkpoint, player.accountHash) }
        return true
    }

    fun selection(player: Player, raid: ToaRaidSession): ToaSupplySelection? {
        val state = stateOrNull(player, raid) ?: return null
        val checkpoint = availableCheckpoint(state, raid) ?: return null
        val offers =
            state.offers.getOrPut(checkpoint) {
                rollAllBundles(raid, checkpoint, player.accountHash)
            }
        return ToaSupplySelection(
            checkpoint = checkpoint,
            displays =
                ToaSupplyBundle.entries.associateWith { bundle ->
                    virtualInventory(
                        VIRTUAL_INVENTORY_BY_BUNDLE.getValue(bundle),
                        offers.getValue(bundle).map {
                            ownedObj(it.objId, it.count, state.ownershipVars)
                        },
                    )
                },
        )
    }

    fun canClaim(player: Player, raid: ToaRaidSession): Boolean {
        val state = stateOrNull(player, raid) ?: return false
        return raid.participant(player.accountHash) != null &&
            availableCheckpoint(state, raid) != null
    }

    fun claimBundle(
        access: ProtectedAccess,
        raid: ToaRaidSession,
        bundle: ToaSupplyBundle,
    ): Boolean {
        val player = access.player
        val state = stateOrNull(player, raid)
        val checkpoint = state?.let { availableCheckpoint(it, raid) }
        if (state == null || checkpoint == null || raid.participant(player.accountHash) == null) {
            player.mes("The Helpful Spirit has no supplies ready for you yet.")
            return false
        }
        val offer =
            state.offers
                .getOrPut(checkpoint) { rollAllBundles(raid, checkpoint, player.accountHash) }
                .getValue(bundle)
        if (!hasOwnedContainer(player, state.ownershipVars)) {
            val container = ownedObj(SUPPLIES_CONTAINER, 1, state.ownershipVars)
            if (access.invAdd(access.inv, container).failure) {
                player.mes("You need a free inventory space for the Supplies container.")
                return false
            }
        }
        offer.forEach { stack -> state.contents.merge(stack.objId, stack.count, Int::plus) }
        state.claimed += checkpoint
        state.offers.remove(checkpoint)
        player.mes("You claim the ${bundle.displayName} supply bundle from the Helpful Spirit.")
        return true
    }

    fun containerInventory(player: Player, raid: ToaRaidSession): Inventory? {
        val state = stateOrNull(player, raid) ?: return null
        if (!hasOwnedContainer(player, state.ownershipVars) || state.contents.isEmpty()) return null
        return virtualInventory(
            CONTAINER_VIRTUAL_INVENTORY,
            state.contents.map { (id, count) -> ownedObj(id, count, state.ownershipVars) },
        )
    }

    fun withdrawSlot(
        access: ProtectedAccess,
        raid: ToaRaidSession,
        slot: Int,
        op: IfButtonOp,
    ): Boolean {
        val state = stateOrNull(access.player, raid) ?: return false
        val entry = state.contents.entries.elementAtOrNull(slot) ?: return false
        if (op == IfButtonOp.Op4) {
            state.contents.remove(entry.key)
            access.player.mes("You discard those raid supplies.")
            removeEmptyContainer(access, state)
            return true
        }
        if (op == IfButtonOp.Op9) return false
        val requested =
            when (op) {
                IfButtonOp.Op1 -> 1
                IfButtonOp.Op2 -> 5
                IfButtonOp.Op3 -> Int.MAX_VALUE
                else -> return false
            }
        val amount = minOf(requested, entry.value)
        val obj = ownedObj(entry.key, amount, state.ownershipVars)
        if (access.invAdd(access.inv, obj).failure) {
            access.player.mes("You do not have enough inventory space.")
            return false
        }
        val remaining = entry.value - amount
        if (remaining == 0) state.contents.remove(entry.key)
        else state.contents[entry.key] = remaining
        removeEmptyContainer(access, state)
        return true
    }

    fun withdrawFromContainer(access: ProtectedAccess, raid: ToaRaidSession, amount: Int): Int {
        require(amount > 0)
        val state = stateOrNull(access.player, raid) ?: return 0
        var remaining = amount
        var withdrawn = 0
        while (remaining > 0 && state.contents.isNotEmpty()) {
            val first = state.contents.entries.first()
            if (access.invAdd(access.inv, ownedObj(first.key, 1, state.ownershipVars)).failure)
                break
            if (first.value == 1) state.contents.remove(first.key)
            else state.contents[first.key] = first.value - 1
            withdrawn++
            remaining--
        }
        if (withdrawn == 0 && state.contents.isNotEmpty()) {
            access.player.mes("You do not have enough inventory space.")
        }
        removeEmptyContainer(access, state)
        return withdrawn
    }

    fun resupply(access: ProtectedAccess, raid: ToaRaidSession): Int {
        val state = stateOrNull(access.player, raid) ?: return 0
        var stored = 0
        access.inv.indices.toList().forEach { slot ->
            val obj = access.inv[slot] ?: return@forEach
            if (obj.id !in TOA_CONSUMABLE_IDS || obj.vars != state.ownershipVars) return@forEach
            val type = RSCM.getReverseMapping(RSCMType.OBJ, obj.id)
            if (access.invDel(access.inv, type, obj.count, slot = slot).success) {
                state.contents.merge(obj.id, obj.count, Int::plus)
                stored += obj.count
            }
        }
        if (stored == 0) access.player.mes("You have no raid supplies to store.")
        return stored
    }

    fun storeSupplySlot(access: ProtectedAccess, raid: ToaRaidSession, slot: Int): Boolean {
        val state = stateOrNull(access.player, raid) ?: return false
        val obj = access.inv[slot] ?: return false
        if (obj.id !in TOA_CONSUMABLE_IDS || obj.vars != state.ownershipVars) {
            access.player.mes("That item does not belong to this raid.")
            return false
        }
        val type = RSCM.getReverseMapping(RSCMType.OBJ, obj.id)
        if (access.invDel(access.inv, type, obj.count, slot = slot).failure) return false
        state.contents.merge(obj.id, obj.count, Int::plus)
        return true
    }

    /**
     * Handles the revision-240 fifth option locally so temporary ToA objects are destroyed and
     * never materialize as ground items. Only the exact active-raid ownership token is accepted.
     */
    fun discardTemporarySlot(access: ProtectedAccess, raid: ToaRaidSession, slot: Int): Boolean {
        val state = stateOrNull(access.player, raid) ?: return false
        val obj = access.inv[slot] ?: return false
        if (obj.id !in LOCAL_DISCARD_GUARD_IDS) return false
        if (obj.vars != state.ownershipVars) return false
        if (obj.id == SUPPLIES_CONTAINER) state.contents.clear()
        val type = RSCM.getReverseMapping(RSCMType.OBJ, obj.id)
        if (access.invDel(access.inv, type, obj.count, slot = slot).failure) return false
        decrementIssuedChallengeItem(raid, access.player.accountHash, obj.id, obj.vars, obj.count)
        access.player.mes(
            if (obj.id == SUPPLIES_CONTAINER) "You destroy the Supplies container and its contents."
            else "You discard the temporary raid item."
        )
        return true
    }

    fun forfeitFirstCheckpoint(raid: ToaRaidSession, accountHash: Long): Boolean {
        val state = state(raid, accountHash)
        if (ToaSupplyCheckpointRules.FIRST in state.claimed) return false
        return state.forfeited.add(ToaSupplyCheckpointRules.FIRST)
    }

    fun forfeitAvailableCheckpoint(player: Player, raid: ToaRaidSession): Boolean {
        val state = stateOrNull(player, raid) ?: return false
        val checkpoint = availableCheckpoint(state, raid) ?: return false
        state.offers.remove(checkpoint)
        return state.forfeited.add(checkpoint)
    }

    fun forfeitUnclaimedBeforeWardens(raid: ToaRaidSession): Int {
        var forfeited = 0
        raid.participants.values.forEach { participant ->
            val state = state(raid, participant.accountHash)
            listOf(ToaSupplyCheckpointRules.FIRST, ToaSupplyCheckpointRules.SECOND).forEach {
                checkpoint ->
                if (checkpoint !in state.claimed && state.forfeited.add(checkpoint)) forfeited++
                state.offers.remove(checkpoint)
            }
        }
        return forfeited
    }

    fun recordIssuedChallengeItem(
        raid: ToaRaidSession,
        accountHash: Long,
        itemId: Int,
        count: Int,
        vars: Int = ownershipVars(raid),
    ) {
        require(count > 0)
        state(raid, accountHash)
            .issuedChallengeItems
            .merge(ToaSupplyEscrowKey(itemId, vars), count, Int::plus)
    }

    fun grantChallengeItem(
        player: Player,
        raid: ToaRaidSession,
        itemId: Int,
        count: Int = 1,
    ): Boolean {
        require(itemId in CHALLENGE_ITEM_IDS || itemId == ApmekenLiveTuning.HAMMER_ITEM)
        require(count > 0)
        val vars = ownershipVars(raid)
        if (player.invAdd(player.inv, itemId, count, vars = vars, strict = true).failure) {
            return false
        }
        recordIssuedChallengeItem(raid, player.accountHash, itemId, count, vars)
        return true
    }

    fun ownedChallengeItemCount(player: Player, raid: ToaRaidSession, itemId: Int): Int {
        val vars = ownershipVars(raid)
        return player.inv.sumOf { obj ->
            if (obj?.id == itemId && obj.vars == vars) obj.count else 0
        }
    }

    fun hasOwnedChallengeItem(player: Player, raid: ToaRaidSession, itemId: Int): Boolean =
        ownedChallengeItemCount(player, raid, itemId) > 0

    fun consumeChallengeItem(
        player: Player,
        raid: ToaRaidSession,
        itemId: Int,
        count: Int = 1,
    ): Boolean {
        require(count > 0)
        val vars = ownershipVars(raid)
        if (ownedChallengeItemCount(player, raid, itemId) < count) return false
        var remaining = count
        player.inv.indices.toList().forEach { slot ->
            if (remaining == 0) return@forEach
            val obj = player.inv[slot] ?: return@forEach
            if (obj.id != itemId || obj.vars != vars) return@forEach
            val amount = minOf(remaining, obj.count)
            if (player.invDel(player.inv, itemId, amount, slot = slot).success) remaining -= amount
        }
        if (remaining != 0) return false
        decrementIssuedChallengeItem(raid, player.accountHash, itemId, vars, count)
        return true
    }

    fun removeIssuedChallengeItems(player: Player, raid: ToaRaidSession, itemId: Int): Int {
        val count = ownedChallengeItemCount(player, raid, itemId)
        if (count == 0) return 0
        check(consumeChallengeItem(player, raid, itemId, count))
        return count
    }

    fun queueWipeSupplies(raid: ToaRaidSession) {
        if (!ToaSupplyInvocationRules.givesHoneyLocust(raid.startSnapshot.invocations)) return
        raid.participants.eligible().forEach { participant ->
            state(raid, participant.accountHash).pendingHoneyLocust++
        }
    }

    fun claimPendingWipeSupplies(player: Player, raid: ToaRaidSession): Boolean {
        if (raid.participant(player.accountHash)?.rewardEligible != true) return false
        val state = stateOrNull(player, raid) ?: return false
        val count = state.pendingHoneyLocust
        if (count <= 0) return false
        if (
            player
                .invAdd(player.inv, HONEY_LOCUST, count, vars = state.ownershipVars, strict = true)
                .failure
        ) {
            player.mes("You need more inventory space for the honey locust.")
            return false
        }
        state.pendingHoneyLocust = 0
        player.mes(
            if (count == 1) "Osmumten gives you a honey locust after the wipe."
            else "Osmumten gives you $count honey locusts after the wipes."
        )
        return true
    }

    /**
     * Removes temporary inventory items before logout persistence and keeps them only in memory. A
     * clean server restart intentionally loses this escrow; [sanitizeStaleLogin] removes any
     * previously persisted temporary objects on the next login.
     */
    fun suspendForDisconnect(player: Player, raid: ToaRaidSession): Int {
        val state = stateOrNull(player, raid) ?: return 0
        val stashed = stashBackpack(player, state)
        sanitizeInventories(player, state)
        clearVirtualInventories(player)
        activityAccess.leaveRaid(player, ACTIVITY_KEY, state.ownershipVars)
        return stashed
    }

    /** Restores only escrow belonging to this exact raid instance and account. */
    fun resume(player: Player, raid: ToaRaidSession): Boolean {
        val state = stateOrNull(player, raid) ?: return false
        enterActivity(player, raid)
        restoreEscrow(player, state)
        if (state.contents.isNotEmpty() && !hasOwnedContainer(player, state.ownershipVars)) {
            player.invAdd(
                player.inv,
                SUPPLIES_CONTAINER,
                1,
                vars = state.ownershipVars,
                strict = true,
            )
        }
        return true
    }

    /** Idempotent terminal cleanup for an online player in one raid. */
    fun terminalCleanup(player: Player, raid: ToaRaidSession) {
        val state = stateOrNull(player, raid)
        sanitizeInventories(player, state)
        clearVirtualInventories(player)
        states.remove(scope(player, raid))
        quarantined.keys.removeAll { it.accountHash == player.accountHash }
        activityAccess.leaveRaid(player, ACTIVITY_KEY, state?.ownershipVars)
    }

    /** Idempotent terminal cleanup when only the offline account identity is available. */
    fun terminalCleanup(raid: ToaRaidSession, accountHash: Long) {
        states.remove(scope(raid, accountHash))
        quarantined.keys.removeAll { it.accountHash == accountHash }
    }

    /** Clears every in-memory claim, bag, escrow, and issued-item ledger for a finished raid. */
    fun terminalCleanupRaid(raid: ToaRaidSession) {
        val instanceId = raid.instance.id.value
        states.keys.removeAll { it.instanceId == instanceId }
        val vars = ownershipVars(raid)
        val accounts = raid.participants.values.mapTo(hashSetOf()) { it.accountHash }
        quarantined.keys.removeAll { it.ownershipVars == vars && it.accountHash in accounts }
    }

    /**
     * Login sanitizer for a restart/crash path. It intentionally excludes ordinary item 2347; that
     * hammer is removed only when tagged with our ownership marker or present in a live ledger.
     */
    fun sanitizeStaleLogin(player: Player): Int {
        val removed = sanitizeInventories(player, state = null)
        clearVirtualInventories(player)
        return removed
    }

    /** Existing manager compatibility: terminally cleans every ToA scope for this account. */
    fun cleanup(player: Player) {
        val accountStates =
            states.filterKeys { it.accountHash == player.accountHash }.values.toList()
        accountStates.forEach { sanitizeInventories(player, it) }
        if (accountStates.isEmpty()) sanitizeInventories(player, state = null)
        clearVirtualInventories(player)
        states.keys.removeAll { it.accountHash == player.accountHash }
        quarantined.keys.removeAll { it.accountHash == player.accountHash }
        activityAccess.leaveRaid(player, ACTIVITY_KEY)
    }

    internal fun snapshotContents(raid: ToaRaidSession, accountHash: Long): Map<Int, Int> =
        stateOrNull(raid, accountHash)?.contents?.toMap().orEmpty()

    internal fun snapshotEscrow(
        raid: ToaRaidSession,
        accountHash: Long,
    ): Map<ToaSupplyEscrowKey, Int> = stateOrNull(raid, accountHash)?.escrow?.toMap().orEmpty()

    private fun rollAllBundles(
        raid: ToaRaidSession,
        checkpoint: Int,
        accountHash: Long,
    ): Map<ToaSupplyBundle, List<ToaSupplyStack>> =
        Random(
                raid.startSnapshot.seed xor
                    accountHash xor
                    (checkpoint.toLong() shl 32) xor
                    SUPPLY_SEED_SALT
            )
            .let { rng -> ToaSupplyBundle.entries.associateWith { rollBundle(raid, it, rng) } }

    private fun rollBundle(
        raid: ToaRaidSession,
        bundle: ToaSupplyBundle,
        rng: Random,
    ): List<ToaSupplyStack> {
        val invocations = raid.startSnapshot.invocations
        fun scaled(id: Int, amount: Int): ToaSupplyStack? =
            ToaSupplyInvocationRules.scaled(amount, invocations, rng.nextDouble())
                .takeIf { it > 0 }
                ?.let { ToaSupplyStack(id, it) }

        return when (bundle) {
            ToaSupplyBundle.LIFE -> {
                var scarabs = rng.nextInt(1, 4)
                var ambrosia = rng.nextInt(1, 3)
                val onDiet = ToaInvocation.ON_A_DIET in invocations
                if (onDiet && ToaSupplyInvocationRules.hasFullSupplies(invocations)) {
                    scarabs += ON_DIET_REPLACEMENT_SCARABS
                    ambrosia += ON_DIET_REPLACEMENT_AMBROSIA
                }
                buildList {
                    scaled(NECTAR_4, 5)?.let(::add)
                    scaled(TEARS_4, 5)?.let(::add)
                    if (!onDiet) scaled(SILK_DRESSING_2, rng.nextInt(1, 4))?.let(::add)
                    scaled(BLESSED_CRYSTAL_SCARAB_2, scarabs)?.let(::add)
                    scaled(AMBROSIA_2, ambrosia)?.let(::add)
                }
            }
            ToaSupplyBundle.CHAOS ->
                buildList {
                    scaled(NECTAR_4, rng.nextInt(1, 9))?.let(::add)
                    scaled(TEARS_4, rng.nextInt(0, 7))?.let(::add)
                    scaled(SMELLING_SALTS_2, rng.nextInt(0, 3))?.let(::add)
                    if (rng.nextInt(CHAOS_RARE_ROLL) == 0) {
                        scaled(AMBROSIA_2, 1)?.let(::add)
                    }
                    if (rng.nextInt(CHAOS_RARE_ROLL) == 0) {
                        scaled(LIQUID_ADRENALINE_2, 1)?.let(::add)
                    }
                }
            ToaSupplyBundle.POWER ->
                listOfNotNull(scaled(SMELLING_SALTS_2, 2), scaled(LIQUID_ADRENALINE_2, 1))
        }
    }

    private fun availableCheckpoint(state: ToaSupplyAccountState, raid: ToaRaidSession): Int? =
        ToaSupplyCheckpointRules.availableCheckpoint(
            claimed = state.claimed,
            forfeited = state.forfeited,
            completedPathCount = raid.completedPaths.size,
            allPathsComplete = raid.completedPaths.containsAll(TOA_ACTIVE_PATHS),
        )

    private fun state(player: Player, raid: ToaRaidSession): ToaSupplyAccountState =
        state(raid, player.accountHash)

    private fun state(raid: ToaRaidSession, accountHash: Long): ToaSupplyAccountState =
        states.getOrPut(scope(raid, accountHash)) { ToaSupplyAccountState(ownershipVars(raid)) }

    private fun stateOrNull(player: Player, raid: ToaRaidSession): ToaSupplyAccountState? =
        stateOrNull(raid, player.accountHash)

    private fun stateOrNull(raid: ToaRaidSession, accountHash: Long): ToaSupplyAccountState? =
        states[scope(raid, accountHash)]

    private fun scope(player: Player, raid: ToaRaidSession): ToaSupplyScope =
        scope(raid, player.accountHash)

    private fun scope(raid: ToaRaidSession, accountHash: Long): ToaSupplyScope =
        ToaSupplyScope(raid.instance.id.value, accountHash)

    private fun ownershipVars(instanceId: Long): Int {
        return ToaSupplyOwnershipRules.varsForInstance(instanceId)
    }

    private fun isToaOwnershipVars(vars: Int): Boolean = ToaSupplyOwnershipRules.isTagged(vars)

    private fun hasOwnedContainer(player: Player, ownershipVars: Int): Boolean =
        player.inv.any { it?.id == SUPPLIES_CONTAINER && it.vars == ownershipVars }

    private fun removeEmptyContainer(access: ProtectedAccess, state: ToaSupplyAccountState) {
        if (state.contents.isNotEmpty()) return
        val slot =
            access.inv.indices.firstOrNull {
                val obj = access.inv[it]
                obj?.id == SUPPLIES_CONTAINER && obj.vars == state.ownershipVars
            } ?: return
        access.invDel(
            access.inv,
            RSCM.getReverseMapping(RSCMType.OBJ, SUPPLIES_CONTAINER),
            1,
            slot = slot,
        )
    }

    private fun stashBackpack(player: Player, state: ToaSupplyAccountState): Int {
        var stashed = 0
        val issuedRemaining = state.issuedChallengeItems.toMutableMap()
        player.inv.indices.toList().forEach { slot ->
            val obj = player.inv[slot] ?: return@forEach
            if (
                obj.vars != state.ownershipVars &&
                    (isToaOwnershipVars(obj.vars) || obj.id in STALE_SAFE_ITEM_IDS)
            ) {
                if (player.invDel(player.inv, obj.id, obj.count, slot = slot).success) {
                    quarantined
                        .getOrPut(ToaSupplyQuarantineScope(player.accountHash, obj.vars)) {
                            linkedMapOf()
                        }
                        .merge(ToaSupplyEscrowKey(obj.id, obj.vars), obj.count, Int::plus)
                    stashed += obj.count
                }
                return@forEach
            }
            if (obj.id == SUPPLIES_CONTAINER) {
                if (
                    obj.vars == state.ownershipVars &&
                        player.invDel(player.inv, obj.id, obj.count, slot = slot).success
                ) {
                    stashed += obj.count
                }
                return@forEach
            }
            val issuedKey = ToaSupplyEscrowKey(obj.id, obj.vars)
            val issuedCount = issuedRemaining[issuedKey] ?: 0
            val safe = obj.vars == state.ownershipVars
            val count = if (safe) obj.count else minOf(obj.count, issuedCount)
            if (count <= 0) return@forEach
            if (player.invDel(player.inv, obj.id, count, slot = slot).failure) return@forEach
            state.escrow.merge(ToaSupplyEscrowKey(obj.id, obj.vars), count, Int::plus)
            if (!safe) issuedRemaining[issuedKey] = issuedCount - count
            stashed += count
        }
        return stashed
    }

    private fun restoreEscrow(player: Player, state: ToaSupplyAccountState): Int {
        var restored = 0
        state.escrow.entries.toList().forEach { (key, count) ->
            if (
                player.invAdd(player.inv, key.objId, count, vars = key.vars, strict = true).success
            ) {
                state.escrow.remove(key)
                restored += count
            }
        }
        if (state.escrow.isNotEmpty()) {
            player.mes("Make inventory space to recover the rest of your raid supplies.")
        }
        return restored
    }

    private fun sanitizeInventories(player: Player, state: ToaSupplyAccountState?): Int {
        var removed = 0
        val issuedRemaining = state?.issuedChallengeItems?.toMutableMap().orEmpty().toMutableMap()
        player.invMap.values
            .distinctBy { System.identityHashCode(it) }
            .forEach { inventory ->
                inventory.indices.toList().forEach { slot ->
                    val obj = inventory[slot] ?: return@forEach
                    val issuedKey = ToaSupplyEscrowKey(obj.id, obj.vars)
                    val issued = issuedRemaining[issuedKey] ?: 0
                    val safe = obj.id in STALE_SAFE_ITEM_IDS || isToaOwnershipVars(obj.vars)
                    val count = if (safe) obj.count else minOf(obj.count, issued)
                    if (count <= 0) return@forEach
                    if (player.invDel(inventory, obj.id, count, slot = slot).success) {
                        removed += count
                        if (!safe) issuedRemaining[issuedKey] = issued - count
                    }
                }
            }
        return removed
    }

    private fun decrementIssuedChallengeItem(
        raid: ToaRaidSession,
        accountHash: Long,
        itemId: Int,
        vars: Int,
        count: Int,
    ) {
        val issued = stateOrNull(raid, accountHash)?.issuedChallengeItems ?: return
        val key = ToaSupplyEscrowKey(itemId, vars)
        val remaining = (issued[key] ?: return) - count
        if (remaining <= 0) issued.remove(key) else issued[key] = remaining
    }

    private fun ownedObj(id: Int, count: Int, vars: Int): InvObj {
        val type = requireNotNull(ServerCacheManager.getItem(id)) { "Missing ToA supply item $id" }
        return InvObj(type, count, vars)
    }

    private fun clearVirtualInventories(player: Player) {
        player.invMap.values
            .filter { it.type.id in VIRTUAL_INVENTORY_IDS }
            .toList()
            .forEach(player::stopInvTransmit)
    }

    private fun virtualInventory(id: Int, objs: List<InvObj>): Inventory {
        val type = requireNotNull(ServerCacheManager.getInventory(id))
        require(objs.size <= type.size) {
            "ToA virtual inventory $id has ${objs.size} entries but only ${type.size} slots."
        }
        return Inventory(type, arrayOfNulls<InvObj>(type.size)).also { inventory ->
            objs.forEachIndexed { slot, obj -> inventory[slot] = obj }
        }
    }

    internal companion object {
        const val ACTIVITY_KEY = "tombs_of_amascut"

        const val SELECTION_INTERFACE = 777
        const val LIFE_DISPLAY_COMPONENT = (SELECTION_INTERFACE shl 16) or 4
        const val LIFE_SELECT_COMPONENT = (SELECTION_INTERFACE shl 16) or 6
        const val CHAOS_DISPLAY_COMPONENT = (SELECTION_INTERFACE shl 16) or 7
        const val CHAOS_SELECT_COMPONENT = (SELECTION_INTERFACE shl 16) or 9
        const val POWER_DISPLAY_COMPONENT = (SELECTION_INTERFACE shl 16) or 10
        const val POWER_SELECT_COMPONENT = (SELECTION_INTERFACE shl 16) or 12

        const val CONTAINER_INTERFACE = 778
        const val CONTAINER_CLOSE_COMPONENT = (CONTAINER_INTERFACE shl 16) or 2
        const val CONTAINER_GRID_COMPONENT = (CONTAINER_INTERFACE shl 16) or 5

        const val LIFE_VIRTUAL_INVENTORY = 807
        const val CHAOS_VIRTUAL_INVENTORY = 808
        const val POWER_VIRTUAL_INVENTORY = 809
        const val CONTAINER_VIRTUAL_INVENTORY = 810

        const val SUPPLIES_CONTAINER = 27314
        const val NECTAR_4 = 27315
        const val NECTAR_3 = 27317
        const val NECTAR_2 = 27319
        const val NECTAR_1 = 27321
        const val SILK_DRESSING_2 = 27323
        const val SILK_DRESSING_1 = 27325
        const val TEARS_4 = 27327
        const val TEARS_3 = 27329
        const val TEARS_2 = 27331
        const val TEARS_1 = 27333
        const val BLESSED_CRYSTAL_SCARAB_2 = 27335
        const val BLESSED_CRYSTAL_SCARAB_1 = 27337
        const val LIQUID_ADRENALINE_2 = 27339
        const val LIQUID_ADRENALINE_1 = 27341
        const val SMELLING_SALTS_2 = 27343
        const val SMELLING_SALTS_1 = 27345
        const val AMBROSIA_2 = 27347
        const val AMBROSIA_1 = 27349
        const val HONEY_LOCUST = 27351

        // Compatibility aliases for older ToA source/tests; all point to the corrected full dose.
        const val NECTAR_LIFE = NECTAR_4
        const val NECTAR_CHAOS = NECTAR_4
        const val SILK_DRESSING = SILK_DRESSING_2
        const val TEARS_LIFE = TEARS_4
        const val TEARS_CHAOS = TEARS_4
        const val LIQUID_ADRENALINE = LIQUID_ADRENALINE_2
        const val SMELLING_SALTS = SMELLING_SALTS_2
        const val AMBROSIA = AMBROSIA_2

        private const val CHAOS_RARE_ROLL = 10
        private const val ON_DIET_REPLACEMENT_SCARABS = 2
        private const val ON_DIET_REPLACEMENT_AMBROSIA = 1
        private const val SUPPLY_SEED_SALT = 0x535550504C494553L

        internal val TOA_CONSUMABLE_IDS =
            linkedSetOf(
                NECTAR_4,
                NECTAR_3,
                NECTAR_2,
                NECTAR_1,
                SILK_DRESSING_2,
                SILK_DRESSING_1,
                TEARS_4,
                TEARS_3,
                TEARS_2,
                TEARS_1,
                BLESSED_CRYSTAL_SCARAB_2,
                BLESSED_CRYSTAL_SCARAB_1,
                LIQUID_ADRENALINE_2,
                LIQUID_ADRENALINE_1,
                SMELLING_SALTS_2,
                SMELLING_SALTS_1,
                AMBROSIA_2,
                AMBROSIA_1,
            )

        internal val REVISION_ITEM_IDS =
            TOA_CONSUMABLE_IDS + setOf(SUPPLIES_CONTAINER, HONEY_LOCUST)

        internal val CHALLENGE_ITEM_IDS =
            setOf(
                CrondisAssets.WATER_CONTAINER,
                HetLiveAssets.MIRROR_ITEM,
                ApmekenLiveTuning.NEUTRALISING_POTION_ITEM,
            )

        internal val STALE_SAFE_ITEM_IDS =
            TOA_CONSUMABLE_IDS + CHALLENGE_ITEM_IDS + setOf(SUPPLIES_CONTAINER, HONEY_LOCUST)

        internal val LOCAL_DISCARD_GUARD_IDS =
            REVISION_ITEM_IDS + CHALLENGE_ITEM_IDS + setOf(ApmekenLiveTuning.HAMMER_ITEM)

        internal val VIRTUAL_INVENTORY_IDS =
            setOf(
                LIFE_VIRTUAL_INVENTORY,
                CHAOS_VIRTUAL_INVENTORY,
                POWER_VIRTUAL_INVENTORY,
                CONTAINER_VIRTUAL_INVENTORY,
            )

        internal val VIRTUAL_INVENTORY_BY_BUNDLE =
            mapOf(
                ToaSupplyBundle.LIFE to LIFE_VIRTUAL_INVENTORY,
                ToaSupplyBundle.CHAOS to CHAOS_VIRTUAL_INVENTORY,
                ToaSupplyBundle.POWER to POWER_VIRTUAL_INVENTORY,
            )

        internal val DISPLAY_COMPONENT_BY_BUNDLE =
            mapOf(
                ToaSupplyBundle.LIFE to LIFE_DISPLAY_COMPONENT,
                ToaSupplyBundle.CHAOS to CHAOS_DISPLAY_COMPONENT,
                ToaSupplyBundle.POWER to POWER_DISPLAY_COMPONENT,
            )

        internal val SELECT_COMPONENT_BY_BUNDLE =
            mapOf(
                ToaSupplyBundle.LIFE to LIFE_SELECT_COMPONENT,
                ToaSupplyBundle.CHAOS to CHAOS_SELECT_COMPONENT,
                ToaSupplyBundle.POWER to POWER_SELECT_COMPONENT,
            )

        internal val SELECTION_COMPONENTS =
            DISPLAY_COMPONENT_BY_BUNDLE.values.toSet() + SELECT_COMPONENT_BY_BUNDLE.values

        internal val CONTAINER_COMPONENTS =
            setOf(CONTAINER_CLOSE_COMPONENT, CONTAINER_GRID_COMPONENT)
    }
}

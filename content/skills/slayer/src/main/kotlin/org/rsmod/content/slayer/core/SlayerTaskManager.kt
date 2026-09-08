package org.rsmod.content.slayer.core

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.DBRowType
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcServerType
import kotlin.math.floor
import kotlin.math.min
import kotlin.random.Random
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.area.checker.isInWilderness
import org.rsmod.api.config.refs.BaseParams
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.statAdvance
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.table.Tuple3
import org.rsmod.api.table.slayer.SlayerMasterTaskRow
import org.rsmod.api.table.slayer.SlayerMastersRow
import org.rsmod.api.table.slayer.SlayerTaskRow
import org.rsmod.api.table.slayer.SlayerUnlockRow
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

object SlayerTaskManager {

    val tasks: Map<SlayerMastersRow, List<SlayerMasterTaskRow>> = run {
        val tasksByMaster = SlayerMasterTaskRow.all().groupBy(SlayerMasterTaskRow::masterId)
        SlayerMastersRow.all().associateWith { tasksByMaster[it.masterId].orEmpty() }
    }

    val slayerTargets = SlayerTaskRow.all()

    val slayerMasterNpcs = tasks.keys.flatMap { it.npcIds }

    fun getCurrentSlayerTask(access: ProtectedAccess): SlayerTaskRow? {
        return getCurrentSlayerTask(access.player)
    }

    fun getCurrentSlayerTask(player: Player): SlayerTaskRow? {
        val targetId = player.vars["varp.slayer_target"]
        if (targetId == 0) return null
        return slayerTargets.find { it.id == targetId }
    }

    fun resetTask(access: ProtectedAccess) {
        clearAssignedTask(access.player)
        VarPlayerIntMapSetter.set(access.player, "varbit.slayer_master", 0)
    }

    fun resetTask(player: Player) {
        clearAssignedTask(player)
        VarPlayerIntMapSetter.set(player, "varbit.slayer_master", 0)
    }

    private fun clearAssignedTask(player: Player) {
        SlayerAssignmentChoices.clearOffers(player)
        MortimerAssignment.resetTaskExtras(player)
        VarPlayerIntMapSetter.set(player, "varp.slayer_target", 0)
        VarPlayerIntMapSetter.set(player, "varp.slayer_count", 0)
        VarPlayerIntMapSetter.set(player, "varp.slayer_count_original", 0)
        VarPlayerIntMapSetter.set(player, "varp.slayer_area", 0)
        VarPlayerIntMapSetter.set(player, CanonicalSlayerRegistry.CATEGORY_VARP, 0)
    }

    fun getCurrentAssignedMaster(player: Player): SlayerMastersRow? {
        val masterId = player.vars["varbit.slayer_master"]
        if (masterId == 0) return null
        return tasks.keys.find { it.masterId == masterId }
    }

    fun getFocusedMaster(player: Player): SlayerMastersRow? {
        val masterId = player.vars["varbit.slayer_master_in_focus"]
        if (masterId == 0) return null
        return tasks.keys.find { it.masterId == masterId }
    }

    fun decreaseTask(
        player: Player,
        npc: Npc,
        extraCountDecrement: Int = 0,
        skipCountDecrement: Boolean = false,
    ): Boolean {
        val taskId = player.vars["varp.slayer_target"]
        if (taskId == 0) return false

        val current = player.vars["varp.slayer_count"]
        if (current <= 0) return false

        if (!countsAsCurrentTaskKill(player, npc)) return false

        val decrement =
            when {
                skipCountDecrement -> 0
                else -> 1 + extraCountDecrement.coerceAtLeast(0)
            }

        val newCount = (current - decrement).coerceAtLeast(0)
        VarPlayerIntMapSetter.set(player, "varp.slayer_count", newCount)

        val xp = slayerXpForKill(npc)
        if (xp > 0) {
            val boosted =
                if (MortimerAssignment.isMortimer(getCurrentAssignedMaster(player))) {
                    val modifier = MortimerAssignment.activeModifier(player)
                    if (modifier?.id == MortimerModifiers.MODIFIER_XP) {
                        xp * (100 + modifier.absoluteValue) / 100.0
                    } else {
                        xp
                    }
                } else {
                    xp
                }
            player.statAdvance("stat.slayer", boosted)
        }

        if (newCount == 0) {
            completeTask(player)
        }
        return true
    }

    private fun completeTask(player: Player) {
        val taskId = player.vars["varp.slayer_target"]
        val category = CanonicalSlayerRegistry.get(player.vars[CanonicalSlayerRegistry.CATEGORY_VARP])
        val wasBossTask = category?.boss ?: (taskId != 0 && SlayerBossTasks.isBossTask(taskId))
        val master = getCurrentAssignedMaster(player) ?: return clearAssignedTask(player)
        val totalTasksDone = player.vars[VARBIT_TOTAL_TASKS_DONE] + 1
        VarPlayerIntMapSetter.set(player, VARBIT_TOTAL_TASKS_DONE, totalTasksDone)

        val pointsToAdd =
            if (MortimerAssignment.isMortimer(master)) {
                MortimerAssignment.onTaskComplete(player)
            } else {
                val streak =
                    if (isWildernessMaster(master)) {
                        val next = player.vars["varbit.slayer_wildy_streak"] + 1
                        VarPlayerIntMapSetter.set(player, "varbit.slayer_wildy_streak", next)
                        next
                    } else {
                        val next = player.vars["varbit.slayer_streak"] + 1
                        VarPlayerIntMapSetter.set(player, "varbit.slayer_streak", next)
                        next
                    }
                SlayerPointRules.pointsForCompletedTask(master.masterId, streak, totalTasksDone)
            }

        if (pointsToAdd > 0) {
            val currentPoints = player.vars["varbit.slayer_points"]
            VarPlayerIntMapSetter.set(player, "varbit.slayer_points", currentPoints + pointsToAdd)
        }

        if (wasBossTask) {
            player.statAdvance("stat.slayer", SlayerBossTasks.BOSS_COMPLETION_BONUS_XP)
        }

        player.mes(
            "You've completed $totalTasksDone task${if (totalTasksDone == 1) "" else "s"} and " +
                "received $pointsToAdd points, giving you a total of " +
                "${player.vars["varbit.slayer_points"]}; return to a Slayer master."
        )

        clearAssignedTask(player)
    }

    fun isTaskNpc(npc: Npc, taskId: Int): Boolean = isTaskNpcType(npc.visType, taskId)

    fun isTaskNpcType(type: NpcServerType, taskId: Int): Boolean {
        if (type.slayerTaskIds.isNotEmpty()) {
            return taskId in type.slayerTaskIds
        }
        val category = type.paramOrNull(BaseParams.slayer_task_id)
        if (category != null && category > 0) {
            return category == taskId
        }
        return false
    }

    private val mappedTaskIds: Set<Int> by lazy {
        ServerCacheManager.getNpcs()
            .values
            .flatMap { type ->
                val ids =
                    type.slayerTaskIds.takeIf { it.isNotEmpty() }
                        ?: intArrayOf(type.paramOrNull(BaseParams.slayer_task_id) ?: 0)
                ids.filter { it > 0 }
            }
            .toSet()
    }

    fun isRuntimeAssignableTask(task: SlayerTaskRow): Boolean {
        if (task.nameLowercase.lowercase() in BLOCKED_UNSPAWNED_TASK_NAMES) return false
        if (task.id !in mappedTaskIds) return false
        return !SlayerBossTasks.isBossTask(task.id) || SlayerBossTasks.isEncounterSupported(task)
    }

    fun countsAsTaskKill(npc: Npc, taskId: Int): Boolean =
        isTaskNpc(npc, taskId) || isSuperiorForTask(npc.visType, taskId)

    fun countsAsCurrentTaskKill(player: Player, npc: Npc): Boolean {
        val categoryId = player.vars[CanonicalSlayerRegistry.CATEGORY_VARP]
        return if (categoryId > 0) {
            org.rsmod.api.config.refs.CanonicalSlayerCategoryMembership.matches(categoryId, npc.visType.id)
        } else {
            countsAsTaskKill(npc, player.vars["varp.slayer_target"])
        }
    }

    fun isSuperiorForTask(type: NpcServerType, taskId: Int): Boolean =
        superiorNpcIdsByTaskId[taskId]?.contains(type.id) == true

    private val superiorNpcIdsByTaskId: Map<Int, Set<Int>> by lazy { buildSuperiorNpcIdsByTask() }

    private fun buildSuperiorNpcIdsByTask(): Map<Int, Set<Int>> {
        val map = mutableMapOf<Int, MutableSet<Int>>()
        for ((_, type) in ServerCacheManager.getNpcs()) {
            val superiorId =
                type.paramOrNull<NpcServerType>(BaseParams.slayer_superior)?.id ?: continue
            val taskIds =
                type.slayerTaskIds.takeIf { it.isNotEmpty() }
                    ?: intArrayOf(type.paramOrNull(BaseParams.slayer_task_id) ?: continue)
            for (taskId in taskIds) {
                map.getOrPut(taskId) { mutableSetOf() }.add(superiorId)
            }
        }
        return map
    }

    fun slayerXpForKill(npc: Npc): Double {
        val paramXp = npc.visType.paramOrNull(BaseParams.slayer_experience)
        if (paramXp != null && paramXp > 0) {
            return paramXp.toDouble()
        }
        return slayerXpForKill(npc.visType)
    }

    fun slayerXpForKill(type: NpcServerType): Double {
        val hpCap = min(type.hitpoints, SLAYER_XP_HP_CAP)
        val averageLevel = floor((type.attack + type.strength + type.defence + hpCap) / 4.0).toInt()

        val stabDef = type.paramOrNull(BaseParams.defence_stab) ?: 0
        val slashDef = type.paramOrNull(BaseParams.defence_slash) ?: 0
        val crushDef = type.paramOrNull(BaseParams.defence_crush) ?: 0
        val averageDefBonus = floor((stabDef + slashDef + crushDef) / 3.0).toInt()

        val strengthBonus = type.paramOrNull(BaseParams.melee_strength) ?: 0

        val stabAtt = type.paramOrNull(BaseParams.attack_stab) ?: 0
        val slashAtt = type.paramOrNull(BaseParams.attack_slash) ?: 0
        val crushAtt = type.paramOrNull(BaseParams.attack_crush) ?: 0
        val attackBonus = floor((stabAtt + slashAtt + crushAtt) / 3.0).toInt()

        val inner =
            floor(
                    (39.0 * averageLevel * (averageDefBonus + strengthBonus + attackBonus)) /
                        SLAYER_XP_DIVISOR
                )
                .toInt()
        val multiplier = 1.0 + (inner / 40.0)
        return type.hitpoints * multiplier
    }

    fun isWildernessMaster(master: SlayerMastersRow): Boolean =
        master.npcIds.any { it.id == "npc.slayer_master_7".asRSCM() }

    fun isMortimerMaster(master: SlayerMastersRow): Boolean = MortimerAssignment.isMortimer(master)

    fun isOnWildernessSlayerTask(player: Player): Boolean =
        player.vars[CanonicalSlayerRegistry.CATEGORY_VARP] > 0 || getCurrentAssignedMaster(player)?.let(::isWildernessMaster) == true

    fun hasPendingAssignmentChoice(access: ProtectedAccess): Boolean =
        SlayerAssignmentChoices.hasPendingChoice(access)

    fun countsKillTowardTask(player: Player, npc: Npc, areaChecker: AreaChecker): Boolean {
        val categoryId = player.vars[CanonicalSlayerRegistry.CATEGORY_VARP]
        if (categoryId > 0) {
            return CanonicalSlayerRegistry.isAuthorizedKill(categoryId, npc.visType.id, npc.coords, areaChecker)
        }
        if (isOnWildernessSlayerTask(player) && !npc.coords.isInWilderness(areaChecker)) return false
        return KonarSlayerAreas.countsKillInTaskArea(player, npc, areaChecker)
    }

    fun hasUnlockedReward(player: Player, bit: Int): Boolean {
        val varp = rewardVarp(bit)
        val mask = rewardMask(bit)
        return (player.vars[varp] and mask) != 0
    }

    fun isCombatCheckEnabled(access: ProtectedAccess): Boolean = slayerCombatCheckEnabled(access)

    fun setCombatCheckEnabled(access: ProtectedAccess, enabled: Boolean) {
        VarPlayerIntMapSetter.set(
            access.player,
            "varbit.slayer_combat_check",
            if (enabled) 1 else 0,
        )
    }

    fun slayerStreak(access: ProtectedAccess): Int = access.vars["varbit.slayer_streak"]

    fun slayerWildyStreak(access: ProtectedAccess): Int = access.vars["varbit.slayer_wildy_streak"]

    fun setSlayerStreak(access: ProtectedAccess, value: Int) {
        VarPlayerIntMapSetter.set(access.player, "varbit.slayer_streak", value)
    }

    fun rollAssignment(
        protected: ProtectedAccess,
        master: SlayerMastersRow,
        skipBossTasks: Boolean = false,
        bypassCombatCheck: Boolean = false,
    ): AssignmentRoll? = null

    fun rollDistinctAssignments(
        protected: ProtectedAccess,
        master: SlayerMastersRow,
        count: Int,
        bypassCombatCheck: Boolean = false,
    ): List<AssignmentRoll.Regular> = emptyList()

    fun currentCategory(player: Player): CanonicalSlayerRegistry.Category? =
        CanonicalSlayerRegistry.get(player.vars[CanonicalSlayerRegistry.CATEGORY_VARP])

    fun currentTaskDisplayName(player: Player): String? =
        currentCategory(player)?.displayName ?: getCurrentSlayerTask(player)?.nameUppercase


    fun migrateSavedTask(player: Player): SavedTaskMigration {
        val taskId = player.vars["varp.slayer_target"]
        val remaining = player.vars["varp.slayer_count"]
        val storedCategoryId = player.vars[CanonicalSlayerRegistry.CATEGORY_VARP]
        if (taskId == 0 || remaining <= 0) {
            if (taskId != 0 || remaining != 0 || storedCategoryId != 0) {
                clearAssignedTask(player)
                VarPlayerIntMapSetter.set(player, "varbit.slayer_master", 0)
                return SavedTaskMigration.ClearedInvalid
            }
            return SavedTaskMigration.NoTask
        }

        val stored = CanonicalSlayerRegistry.get(storedCategoryId)
        if (stored != null && stored.baseTaskId == taskId) {
            VarPlayerIntMapSetter.set(player, "varbit.slayer_master", CanonicalSlayerRegistry.PRIMARY_MASTER_ID)
            return SavedTaskMigration.Preserved
        }

        val migrated = CanonicalSlayerRegistry.migrateLegacyTask(taskId)
        if (migrated == null) {
            clearAssignedTask(player)
            VarPlayerIntMapSetter.set(player, "varbit.slayer_master", 0)
            player.mes("Your old Slayer assignment was outside the new Wilderness roster and has been cleared without affecting points or streaks.")
            return SavedTaskMigration.ClearedInvalid
        }

        VarPlayerIntMapSetter.set(player, CanonicalSlayerRegistry.CATEGORY_VARP, migrated.id)
        VarPlayerIntMapSetter.set(player, "varbit.slayer_master", CanonicalSlayerRegistry.PRIMARY_MASTER_ID)
        player.mes("Your Slayer assignment has been migrated to ${migrated.displayName} in the Wilderness roster.")
        return SavedTaskMigration.Migrated
    }

    fun canonicalBackingTask(category: CanonicalSlayerRegistry.Category): SlayerMasterTaskRow? =
        tasks.values.asSequence().flatten().firstOrNull { it.task.id == category.baseTaskId }

    fun rollCanonicalCategories(
        access: ProtectedAccess,
        master: SlayerMastersRow,
        count: Int,
    ): List<CanonicalSlayerRegistry.Category> {
        if (!CanonicalSlayerRegistry.isEnabledMaster(master.masterId)) return emptyList()
        return CanonicalSlayerRegistry.rollDistinct(
            slayerLevel = access.statBase("stat.slayer"),
            blockedTaskIds = blockedTaskIds(access, master),
            count = count,
        ).filter { canonicalBackingTask(it) != null }
    }

    fun canAcceptCanonicalCategory(
        access: ProtectedAccess,
        master: SlayerMastersRow,
        category: CanonicalSlayerRegistry.Category,
    ): Boolean =
        getCurrentSlayerTask(access) == null &&
            CanonicalSlayerRegistry.isEnabledMaster(master.masterId) &&
            access.statBase("stat.slayer") >= category.slayerRequirement &&
            category.baseTaskId !in blockedTaskIds(access, master) &&
            canonicalBackingTask(category) != null

    fun applyCanonicalAssignment(
        access: ProtectedAccess,
        master: SlayerMastersRow,
        category: CanonicalSlayerRegistry.Category,
        amount: Int,
    ): Boolean {
        if (!canAcceptCanonicalCategory(access, master, category)) return false
        val player = access.player
        val clamped = amount.coerceIn(category.minAmount, category.maxAmount)
        SlayerAssignmentChoices.clearOffers(player)
        MortimerAssignment.resetTaskExtras(player)
        VarPlayerIntMapSetter.set(player, "varbit.slayer_master", CanonicalSlayerRegistry.PRIMARY_MASTER_ID)
        VarPlayerIntMapSetter.set(player, "varp.slayer_count_original", clamped)
        VarPlayerIntMapSetter.set(player, "varp.slayer_count", clamped)
        VarPlayerIntMapSetter.set(player, "varp.slayer_target", category.baseTaskId)
        VarPlayerIntMapSetter.set(player, "varp.slayer_area", 0)
        VarPlayerIntMapSetter.set(player, CanonicalSlayerRegistry.CATEGORY_VARP, category.id)
        recordLastAssignment(
            player = player,
            masterId = CanonicalSlayerRegistry.PRIMARY_MASTER_ID,
            taskId = category.baseTaskId,
            konarAreaId = 0,
            count = clamped,
            categoryId = category.id,
        )
        return true
    }

    fun applyRegularAssignment(
        protected: ProtectedAccess,
        master: SlayerMastersRow,
        masterTask: SlayerMasterTaskRow,
        amount: Int,
    ) = Unit

    fun assignTask(protected: ProtectedAccess, masterNpcId: String): Boolean {
        val master = findMaster(masterNpcId) ?: return false
        val category = rollCanonicalCategories(protected, master, 1).singleOrNull() ?: return false
        return applyCanonicalAssignment(
            protected,
            master,
            category,
            CanonicalSlayerRegistry.rollAmount(category),
        )
    }

    fun assignBossTask(
        protected: ProtectedAccess,
        masterNpcId: String,
        taskId: Int,
        amount: Int,
    ): Boolean = false

    fun rollCapePerkOffer(protected: ProtectedAccess, masterNpcId: String): CapePerkOffer? {
        if (!SlayerCapePerk.hasSlayerCape(protected) || !SlayerCapePerk.rollPerkProc()) return null

        val master = findMaster(masterNpcId) ?: return null
        if (!CanonicalSlayerRegistry.isEnabledMaster(master.masterId)) return null
        val lastMasterId = protected.vars[VARP_LAST_MASTER]
        if (lastMasterId == 0 || lastMasterId != master.masterId) return null

        val category = CanonicalSlayerRegistry.get(protected.vars[CanonicalSlayerRegistry.LAST_CATEGORY_VARP])
            ?: return null
        return CapePerkOffer(taskName = category.displayName)
    }

    fun assignPreviousTask(protected: ProtectedAccess, masterNpcId: String): AssignPreviousResult {
        val master = findMaster(masterNpcId) ?: return AssignPreviousResult.Failed
        if (!CanonicalSlayerRegistry.isEnabledMaster(master.masterId)) return AssignPreviousResult.Failed
        if (protected.vars[VARP_LAST_MASTER] != master.masterId) return AssignPreviousResult.Failed
        val category = CanonicalSlayerRegistry.get(protected.vars[CanonicalSlayerRegistry.LAST_CATEGORY_VARP])
            ?: return AssignPreviousResult.Failed
        if (category.baseTaskId in blockedTaskIds(protected, master)) {
            protected.mes(BLOCKED_PREVIOUS_TASK_MESSAGE)
            return AssignPreviousResult.BlockedPrevious
        }
        return if (applyCanonicalAssignment(protected, master, category, CanonicalSlayerRegistry.rollAmount(category))) {
            AssignPreviousResult.Success
        } else {
            AssignPreviousResult.Failed
        }
    }

    fun findMasterByNpc(npcId: String): SlayerMastersRow? = findMaster(npcId)

    fun findMasterById(masterId: Int): SlayerMastersRow? =
        tasks.keys.firstOrNull { it.masterId == masterId }

    fun canAcceptStoredAssignment(
        access: ProtectedAccess,
        master: SlayerMastersRow,
        masterTask: SlayerMasterTaskRow,
    ): Boolean {
        if (getCurrentSlayerTask(access) != null) return false
        if (!meetsMasterRequirements(access, master)) return false
        if (tasks[master].orEmpty().none { it.task.id == masterTask.task.id }) return false
        return isEligibleTask(
            access = access,
            masterTask = masterTask,
            blockedTaskIds = blockedTaskIds(access, master),
            combatLevel = access.player.combatLevel,
            skipCombatCheck = isWildernessMaster(master),
        )
    }

    fun isCurrentTaskIneligible(access: ProtectedAccess): Boolean {
        val taskId = access.vars["varp.slayer_target"]
        if (taskId == 0) return false
        val category = currentCategory(access.player) ?: return true
        val master = getCurrentAssignedMaster(access.player) ?: return true
        return !CanonicalSlayerRegistry.isEnabledMaster(master.masterId) ||
            taskId != category.baseTaskId ||
            access.statBase("stat.slayer") < category.slayerRequirement ||
            category.baseTaskId in blockedTaskIds(access, master)
    }

    private fun findMaster(npcId: String): SlayerMastersRow? =
        tasks.keys.firstOrNull { master ->
            master.npcIds.any { it.id == npcId.asRSCM(RSCMType.NPC) }
        }

    private fun meetsMasterRequirements(
        access: ProtectedAccess,
        master: SlayerMastersRow,
    ): Boolean {
        if (!CanonicalSlayerRegistry.isEnabledMaster(master.masterId)) return false
        val slayerLevel = access.statBase("stat.slayer")
        val combatLevel = access.player.combatLevel
        if (MortimerAssignment.isMortimer(master)) {
            if (slayerLevel < master.slayerLevel) return false
            if (slayerLevel >= 99 && SlayerCapePerk.hasSlayerCape(access)) return true
            return combatLevel >= master.combatLevel
        }
        return slayerLevel >= master.slayerLevel && combatLevel >= master.combatLevel
    }

    fun assignmentUnavailableMessage(access: ProtectedAccess, master: SlayerMastersRow): String {
        val slayerLevel = access.statBase("stat.slayer")
        val combatLevel = access.player.combatLevel
        return when {
            MortimerAssignment.isMortimer(master) &&
                slayerLevel >= 99 &&
                !SlayerCapePerk.hasSlayerCape(access) &&
                combatLevel < master.combatLevel ->
                "You need a combat level of at least ${master.combatLevel} to get a task from me, unless you show me a Slayer cape."
            slayerLevel < master.slayerLevel ->
                "You need a Slayer level of at least ${master.slayerLevel} to get a task from me."
            combatLevel < master.combatLevel ->
                "You need a combat level of at least ${master.combatLevel} to get a task from me."
            else -> "I don't have any tasks you're able to take right now."
        }
    }

    fun blockedTaskIds(access: ProtectedAccess, master: SlayerMastersRow): Set<Int> =
        blockedTaskIds(access.player, master)

    fun blockedTaskIds(player: Player, master: SlayerMastersRow): Set<Int> {
        val blocked = mutableSetOf<Int>()
        for (slot in master.blockVarbits.indices) {
            val varbit = RSCM.getReverseMapping(RSCMType.VARBIT, master.blockVarbits[slot])
            val taskId = player.vars[varbit]
            if (taskId != 0) {
                blocked += taskId
            }
        }
        return blocked
    }

    private fun rollWeightedTask(
        master: SlayerMastersRow,
        masterTasks: List<SlayerMasterTaskRow>,
        blockedTaskIds: Set<Int>,
        protected: ProtectedAccess,
        bypassCombatCheck: Boolean = false,
        includeBossTasks: Boolean = true,
    ): SlayerMasterTaskRow? {
        val combatLevel = protected.player.combatLevel
        val skipCombatCheck = isWildernessMaster(master) || bypassCombatCheck
        val eligible =
            masterTasks.filter {
                isEligibleTask(protected, it, blockedTaskIds, combatLevel, skipCombatCheck) &&
                    (includeBossTasks || !SlayerBossTasks.isBossTask(it.task.id))
            }
        if (eligible.isEmpty()) return null

        val totalWeight = eligible.sumOf { it.weight }
        if (totalWeight <= 0) return null

        var roll = Random.nextInt(totalWeight) + 1
        return eligible.first {
            roll -= it.weight
            roll <= 0
        }
    }

    private fun isEligibleTask(
        access: ProtectedAccess,
        masterTask: SlayerMasterTaskRow,
        blockedTaskIds: Set<Int>,
        combatLevel: Int,
        skipCombatCheck: Boolean,
    ): Boolean {
        if (masterTask.task.id in blockedTaskIds) return false
        if (!isRuntimeAssignableTask(masterTask.task)) return false
        if (
            masterTask.masterId == KONAR_MASTER_ID &&
                KonarSlayerAreas.eligibleAreas(access.player, masterTask).isEmpty()
        )
            return false

        masterTask.taskUnlock?.let { unlock ->
            if (!hasUnlockedReward(access, unlock.bit)) return false
        }

        val task = masterTask.task
        task.blockUnlock?.let { block -> if (hasUnlockedReward(access, block.bit)) return false }

        if (!skipCombatCheck && slayerCombatCheckEnabled(access)) {
            task.minComlevel?.let { minCombat -> if (combatLevel < minCombat) return false }
        }

        if (
            task.minStatRequirementAll.any { (reqLevel, statType) ->
                access.statBase(statType.internalName) < reqLevel
            }
        ) {
            return false
        }

        val anyStatReqs = task.minStatRequirementAny
        if (anyStatReqs.isEmpty()) return true
        return anyStatReqs.any { (reqLevel, statType) ->
            access.statBase(statType.internalName) >= reqLevel
        }
    }

    private fun rollTaskAmount(access: ProtectedAccess, masterTask: SlayerMasterTaskRow): Int {
        val (min, max) =
            resolveTaskAmounts(access, masterTask.task, masterTask.minAmount, masterTask.maxAmount)
        return Random.nextInt(max - min + 1) + min
    }

    private fun applyAssignedTask(
        protected: ProtectedAccess,
        master: SlayerMastersRow,
        task: SlayerMasterTaskRow,
        amount: Int,
        konarAreaId: Int? = null,
    ) {
        val player = protected.player
        VarPlayerIntMapSetter.set(player, "varbit.slayer_master", master.masterId)
        VarPlayerIntMapSetter.set(player, "varp.slayer_count_original", amount)
        VarPlayerIntMapSetter.set(player, "varp.slayer_count", amount)
        VarPlayerIntMapSetter.set(player, "varp.slayer_target", task.task.id)
        VarPlayerIntMapSetter.set(player, CanonicalSlayerRegistry.CATEGORY_VARP, 0)

        val assignedKonarArea =
            if (master.masterId == KONAR_MASTER_ID) {
                val areaId = KonarSlayerAreas.resolveTaskArea(player, task, konarAreaId)
                if (areaId != null) {
                    VarPlayerIntMapSetter.set(player, "varp.slayer_area", areaId)
                } else {
                    VarPlayerIntMapSetter.set(player, "varp.slayer_area", 0)
                }
                areaId ?: 0
            } else {
                VarPlayerIntMapSetter.set(player, "varp.slayer_area", 0)
                0
            }

        recordLastAssignment(player, master.masterId, task.task.id, assignedKonarArea, amount)

    }

    private fun recordLastAssignment(
        player: Player,
        masterId: Int,
        taskId: Int,
        konarAreaId: Int,
        count: Int,
        categoryId: Int = 0,
    ) {
        VarPlayerIntMapSetter.set(player, VARP_LAST_TARGET, taskId)
        VarPlayerIntMapSetter.set(player, VARP_LAST_MASTER, masterId)
        VarPlayerIntMapSetter.set(player, VARP_LAST_AREA, konarAreaId)
        VarPlayerIntMapSetter.set(player, VARP_LAST_COUNT, count)
        VarPlayerIntMapSetter.set(player, CanonicalSlayerRegistry.LAST_CATEGORY_VARP, categoryId)
    }

    private fun slayerCombatCheckEnabled(access: ProtectedAccess): Boolean {
        val state = access.vars["varp.slayer_player_state"]
        return state == 0 || state and 1 != 0
    }

    private fun resolveTaskAmounts(
        access: ProtectedAccess,
        task: SlayerTaskRow,
        baseMin: Int,
        baseMax: Int,
    ): Pair<Int, Int> {
        var minAmount = baseMin
        var maxAmount = baseMax
        applyAmountExtension(access, task.extensionMinMax) { min, max ->
            minAmount = min
            maxAmount = max
        }
        applyAmountExtension(access, task.extensionAdditive) { min, max ->
            minAmount += min
            maxAmount += max
        }

        return minAmount to maxAmount
    }

    private inline fun applyAmountExtension(
        access: ProtectedAccess,
        extensions: List<Tuple3<DBRowType, Int, Int>>,
        apply: (min: Int, max: Int) -> Unit,
    ) {
        val ext = extensions.firstOrNull() ?: return
        if (!hasUnlockedReward(access, SlayerUnlockRow.getRow(ext.t0.id).bit)) return
        apply(ext.t1, ext.t2)
    }

    private fun rewardVarp(bit: Int): String =
        if (bit < 32) "varp.slayer_rewards_unlocks" else "varp.slayer_rewards_unlocks1"

    private fun rewardMask(bit: Int): Int = 1 shl (bit % 32)

    fun unlockReward(access: ProtectedAccess, bit: Int) {
        val varp = rewardVarp(bit)
        val mask = rewardMask(bit)
        VarPlayerIntMapSetter.set(access.player, varp, access.vars[varp] or mask)
    }

    fun hasUnlockedReward(access: ProtectedAccess, bit: Int): Boolean =
        hasUnlockedReward(access.player, bit)

    private const val TURAEL_MASTER_ID = 1
    private const val KONAR_MASTER_ID = 8
    private const val SLAYER_XP_HP_CAP = 2000
    private const val SLAYER_XP_DIVISOR = 200_000

    private val BLOCKED_UNSPAWNED_TASK_NAMES = setOf("custodian stalkers", "gryphons", "aquanites")

    private const val VARP_LAST_TARGET = "varp.slayer_last_target"
    private const val VARP_LAST_MASTER = "varp.slayer_last_master"
    private const val VARP_LAST_AREA = "varp.slayer_last_area"
    private const val VARP_LAST_COUNT = "varp.slayer_last_count"
    private const val VARBIT_TOTAL_TASKS_DONE = "varbit.slayer_total_tasks_done"

    const val BLOCKED_PREVIOUS_TASK_MESSAGE =
        "You could not be reassigned your previous Slayer task because you have blocked that task."

    data class CapePerkOffer(val taskName: String)

    enum class SavedTaskMigration {
        NoTask,
        Preserved,
        Migrated,
        ClearedInvalid,
    }

    enum class AssignPreviousResult {
        Success,
        BlockedPrevious,
        Failed,
    }

    fun isUntrainedSlayer(player: Player): Boolean =
        player.statBase("stat.slayer") == 1 && player.statMap.getXP("stat.slayer") == 0
}

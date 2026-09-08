package org.rsmod.content.slayer

import dev.openrune.ServerCacheManager
import dev.openrune.definition.Definition
import dev.openrune.types.NpcServerType
import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.config.refs.BaseParams
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onCommand
import org.rsmod.api.table.slayer.SlayerMasterTaskRow
import org.rsmod.api.table.slayer.SlayerMastersRow
import org.rsmod.api.table.slayer.SlayerTaskRow
import org.rsmod.content.slayer.core.CanonicalSlayerRegistry
import org.rsmod.api.table.slayer.SlayerAreaRow
import org.rsmod.content.slayer.core.SlayerTaskManager
import org.rsmod.content.slayer.teleport.SlayerTaskTeleportRegistry
import org.rsmod.content.slayer.teleport.SlayerTeleportResolution
import org.rsmod.game.cheat.Cheat
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Administrator-only diagnostics for the cache-backed Slayer task system. */
internal class SlayerAdminCommands
@Inject
constructor(private val teleportRegistry: SlayerTaskTeleportRegistry) : PluginScript() {
    override fun ScriptContext.startup() {
        onCommand("slayeraudit") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Summarise revision-240 Slayer task and NPC coverage"
            invalidArgs = "Use as ::slayeraudit"
            cheat { if (args.isEmpty()) audit(this) }
        }
        onCommand("slayerteleportaudit") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Audit revision-240 Slayer gem teleport coverage"
            invalidArgs = "Use as ::slayerteleportaudit"
            cheat { if (args.isEmpty()) teleportAudit(this) }
        }
        onCommand("testslayerteleport") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Resolve a canonical Slayer gem destination without changing your task"
            invalidArgs = "Use as ::testslayerteleport canonical-category"
            cheat { testSlayerTeleport(this) }
        }
        onCommand("rslayer") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Reset your current Slayer assignment for testing"
            invalidArgs = "Use as ::rslayer"
            cheat { if (args.isEmpty()) resetSlayerTask(this) }
        }
        onCommand("setslayertask") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Set a validated Slayer task for testing"
            invalidArgs =
                "Use as ::setslayertask task-key amount [master-id-or-key] [location-id-or-key]"
            cheat { setTask(this) }
        }
        onCommand("slayernpcs") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "List cache NPCs in a Slayer task category"
            invalidArgs = "Use as ::slayernpcs task-key"
            cheat { listNpcs(this) }
        }
        onCommand("slayertestnpc") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Inspect one cache NPC's Slayer and combat metadata"
            invalidArgs = "Use as ::slayertestnpc npc-id"
            cheat { inspectNpc(this) }
        }
    }

    private fun audit(cheat: Cheat) =
        with(cheat) {
            val masters = SlayerTaskManager.tasks.keys
            val tasks = SlayerTaskManager.slayerTargets
            val npcTypes = ServerCacheManager.getNpcs().values
            val mapped = npcTypes.filter { taskIds(it).isNotEmpty() }
            val mappedTaskIds = mapped.flatMap { taskIds(it).asIterable() }.toSet()
            val missingTaskMappings = tasks.count { it.id !in mappedTaskIds }
            val explicitAttackAnimations = mapped.count { it.hasParam(BaseParams.attack_anim) }
            val genericCombatFallbacks = mapped.size - explicitAttackAnimations

            player.mes(
                "Slayer audit: ${masters.size} runtime master roles, " +
                    "${masters.sumOf { it.npcIds.size }} master NPC variants, ${tasks.size} task rows."
            )
            player.mes(
                "NPC coverage: ${mapped.size} mapped NPC definitions across ${mappedTaskIds.size} task IDs; " +
                    "$missingTaskMappings task IDs have no NPC mapping."
            )
            player.mes(
                "Combat metadata: $explicitAttackAnimations mapped NPCs define attack animations; " +
                    "$genericCombatFallbacks use the generic fallback."
            )
            player.mes(
                "Canonical Wiki manifest: 9 OSRS roles, 13 replacement variants, 148 concrete keys, " +
                    "363 entries currently BLOCKED pending full per-NPC verification."
            )
        }

    private fun teleportAudit(cheat: Cheat) =
        with(cheat) {
            val report = teleportRegistry.audit()
            player.mes(
                "Canonical Slayer teleport audit: ${report.totalCanonicalTasks} categories; " +
                    "${report.wildernessTasksMapped} safe destinations mapped."
            )
            player.mes(
                "Regular: ${report.normalTasksMapped} mapped. " +
                    "Boss/entrance: ${report.bossEntrancesMapped} mapped."
            )
            player.mes(
                "Missing: ${report.tasksWithoutDestination.size}; " +
                    "duplicate category keys: ${report.duplicateTaskKeys.size}."
            )
            player.mes(
                "Invalid destinations: ${report.unloadedDestinations.size} unloaded, " +
                    "${report.blockedDestinations.size} blocked/occupied, " +
                    "${report.unsupportedInstanceDestinations.size} instanced, " +
                    "${report.invalidDestinationPlanes.size} invalid planes."
            )
            emitAuditList(this, "Canonical destinations missing", report.tasksWithoutDestination)
            emitAuditList(this, "Duplicate categories", report.duplicateTaskKeys)
            emitAuditList(this, "Unloaded destinations", report.unloadedDestinations)
            emitAuditList(this, "Blocked destinations", report.blockedDestinations)
            emitAuditList(this, "Instanced destinations", report.unsupportedInstanceDestinations)
            emitAuditList(this, "Invalid destination planes", report.invalidDestinationPlanes)
        }

    private fun testSlayerTeleport(cheat: Cheat) =
        with(cheat) {
            val requested = args.firstOrNull()
            val key = requested?.let(::normalize)
            val category =
                requested?.toIntOrNull()?.let(CanonicalSlayerRegistry::get)
                    ?: CanonicalSlayerRegistry.categories.firstOrNull {
                        normalize(it.key) == key || normalize(it.displayName) == key
                    }
            if (category == null) {
                player.mes("Unknown canonical Slayer category '${requested.orEmpty()}'.")
                return@with
            }
            val task = SlayerTaskManager.canonicalBackingTask(category)
            if (task == null) {
                player.mes("Canonical category ${category.displayName} has no cache-backed task row.")
                return@with
            }
            val resolution = teleportRegistry.resolveCanonical(task.task, category)
            when (resolution) {
                is SlayerTeleportResolution.Success -> {
                    val dest = resolution.teleport
                    player.mes(
                        "Resolved ${dest.taskKey} -> ${dest.destination}; ${dest.description}."
                    )
                    player.mes(
                        "Wilderness=${dest.wildernessLevel}, entranceOnly=${dest.entranceOnly}, " +
                            "sourceNpc=${dest.sourceNpcId ?: "none"}."
                    )
                    player.mes(
                        "Region/collision/NPC validation=${teleportRegistry.isSafeDestination(dest.destination)}."
                    )
                    player.mes("Your live Slayer assignment was not modified.")
                }
                is SlayerTeleportResolution.Missing ->
                    player.mes("No canonical mapping for ${category.displayName}.")
                is SlayerTeleportResolution.Unsafe ->
                    player.mes("Canonical mapping is unsafe for ${category.displayName}.")
            }
        }

    private fun resetSlayerTask(cheat: Cheat) =
        with(cheat) {
            val task = SlayerTaskManager.getCurrentSlayerTask(player)
            val remaining = player.vars["varp.slayer_count"]
            SlayerTaskManager.resetTask(player)
            if (task == null) {
                player.mes("Your Slayer assignment is already clear.")
            } else {
                player.mes(
                    "Reset Slayer assignment: ${task.nameLowercase} " +
                        "(${remaining.coerceAtLeast(0)} remaining)."
                )
            }
            player.mes("Slayer points, streaks, unlocks, and blocked tasks were not changed.")
        }

    private fun emitAuditList(cheat: Cheat, label: String, values: List<String>) {
        if (values.isEmpty()) return
        values.chunked(4).forEachIndexed { index, chunk ->
            cheat.player.mes("$label ${index + 1}: ${chunk.joinToString()}")
        }
    }

    private fun resolveTestArea(input: String): SlayerAreaRow? {
        input.toIntOrNull()?.let { id ->
            SlayerAreaRow.all().find { it.areaId == id }?.let { return it }
        }
        val key = normalize(input)
        return SlayerAreaRow.all().find {
            normalize(it.areaText) == key ||
                normalize(it.areaNameInHelper) == key ||
                normalize(teleportRegistry.areaKey(it)) == key
        }
    }

    private fun setTask(cheat: Cheat) =
        with(cheat) {
            val requested = args.getOrNull(0)
            val amount = args.getOrNull(1)?.toIntOrNull()
            if (requested == null || amount == null) {
                player.mes("Use as ::setslayertask canonical-category amount")
                return@with
            }
            val normalized = normalize(requested)
            val category =
                requested.toIntOrNull()?.let(CanonicalSlayerRegistry::get)
                    ?: CanonicalSlayerRegistry.categories.firstOrNull {
                        normalize(it.key) == normalized || normalize(it.displayName) == normalized
                    }
            if (category == null) {
                player.mes("Unknown or unauthorized canonical Slayer category '$requested'.")
                return@with
            }
            if (amount !in category.minAmount..category.maxAmount) {
                player.mes("${category.displayName} assignments must be ${category.minAmount}-${category.maxAmount} kills.")
                return@with
            }
            VarPlayerIntMapSetter.set(player, "varbit.slayer_master", CanonicalSlayerRegistry.PRIMARY_MASTER_ID)
            VarPlayerIntMapSetter.set(player, "varp.slayer_target", category.baseTaskId)
            VarPlayerIntMapSetter.set(player, "varp.slayer_count", amount)
            VarPlayerIntMapSetter.set(player, "varp.slayer_count_original", amount)
            VarPlayerIntMapSetter.set(player, "varp.slayer_area", 0)
            VarPlayerIntMapSetter.set(player, CanonicalSlayerRegistry.CATEGORY_VARP, category.id)
            VarPlayerIntMapSetter.set(player, "varp.slayer_last_target", category.baseTaskId)
            VarPlayerIntMapSetter.set(player, "varp.slayer_last_master", CanonicalSlayerRegistry.PRIMARY_MASTER_ID)
            VarPlayerIntMapSetter.set(player, "varp.slayer_last_area", 0)
            VarPlayerIntMapSetter.set(player, "varp.slayer_last_count", amount)
            VarPlayerIntMapSetter.set(player, CanonicalSlayerRegistry.LAST_CATEGORY_VARP, category.id)
            player.mes("Assigned $amount ${category.displayName} from the canonical Wilderness roster.")
        }

    private fun listNpcs(cheat: Cheat) =
        with(cheat) {
            val requested = args.firstOrNull()
            val task = requested?.let(::resolveTask)
            if (task == null) {
                player.mes("Unknown Slayer task key '${requested.orEmpty()}'.")
                return@with
            }
            val types = npcTypesForTask(task.id).sortedBy(NpcServerType::id)
            if (types.isEmpty()) {
                player.mes(
                    "${task.nameUppercase}: no mapped revision-240 NPC definitions (BLOCKED)."
                )
                return@with
            }
            player.mes("${task.nameUppercase}: ${types.size} mapped NPC definition(s).")
            types.chunked(4).forEach { chunk ->
                player.mes(chunk.joinToString("; ") { "${it.id} ${it.name} [${it.internalName}]" })
            }
        }

    private fun inspectNpc(cheat: Cheat) =
        with(cheat) {
            val npcId = args.firstOrNull()?.toIntOrNull()
            val type = npcId?.let(ServerCacheManager::getNpc)
            if (type == null) {
                player.mes("Unknown revision-240 NPC ID '${args.firstOrNull().orEmpty()}'.")
                return@with
            }
            val memberships =
                taskIds(type)
                    .asSequence()
                    .mapNotNull { id ->
                        SlayerTaskManager.slayerTargets
                            .find { it.id == id }
                            ?.let { "$id:${it.nameLowercase}" }
                    }
                    .toList()
            player.mes("NPC ${type.id} ${type.name} [${type.internalName}]")
            player.mes(
                "Slayer memberships: ${memberships.ifEmpty { listOf("none") }.joinToString()}"
            )
            player.mes(
                "Combat: level=${type.combatLevel}, hp=${type.hitpoints}, atk=${type.attack}, " +
                    "str=${type.strength}, def=${type.defence}, range=${type.ranged}, magic=${type.magic}."
            )
            player.mes(
                "Timing/range: speed=${type.paramOrNull(BaseParams.attackrate) ?: 4}, " +
                    "attackRange=${type.attackRange}, huntRange=${type.huntRange}, maxRange=${type.maxRange}."
            )
            player.mes(
                "Animations: attack=${paramId(type, BaseParams.attack_anim)}, " +
                    "block=${paramId(type, BaseParams.defend_anim)}, death=${paramId(type, BaseParams.death_anim)}, " +
                    "idle=${type.standAnim}, walk=${type.walkAnim}."
            )
            player.mes(
                "Projectile: travel=${paramId(type, BaseParams.proj_travel)}, " +
                    "type=${paramId(type, BaseParams.proj_type)}; superior=${paramId(type, BaseParams.slayer_superior)}."
            )
        }

    private fun resolveTask(input: String): SlayerTaskRow? {
        input.toIntOrNull()?.let { id ->
            SlayerTaskManager.slayerTargets
                .find { it.id == id }
                ?.let {
                    return it
                }
        }
        val key = normalize(input)
        return SlayerTaskManager.slayerTargets.find {
            normalize(it.nameLowercase) == key || normalize(it.nameUppercase) == key
        }
    }

    private fun resolveMaster(input: String?, task: SlayerTaskRow): SlayerMastersRow? {
        val candidates =
            SlayerTaskManager.tasks.filterValues { rows -> rows.any { it.task.id == task.id } }.keys
        if (input == null) return candidates.firstOrNull()
        input.toIntOrNull()?.let { id ->
            candidates
                .find { it.masterId == id }
                ?.let {
                    return it
                }
        }
        val key = normalize(input)
        return candidates.find { master ->
            normalize(masterLabel(master)) == key ||
                master.npcIds.any { normalize(it.name) == key || normalize(it.internalName) == key }
        }
    }

    private fun Cheat.resolveArea(
        master: SlayerMastersRow,
        masterTask: SlayerMasterTaskRow,
        input: String?,
    ): Int? {
        if (master.masterId != KONAR_MASTER_ID) return 0
        if (input == null) {
            player.mes("Konar tasks require a valid location-id-or-key.")
            return null
        }
        val key = normalize(input)
        val area =
            input.toIntOrNull()?.let { id -> masterTask.areas.find { it.areaId == id } }
                ?: masterTask.areas.find {
                    normalize(it.areaText) == key ||
                        normalize(it.areaNameInHelper) == key ||
                        normalize(teleportRegistry.areaKey(it)) == key
                }
        if (area == null) {
            player.mes("That location is not valid for '${masterTask.task.nameLowercase}'.")
            return null
        }
        return area.areaId
    }

    private fun npcTypesForTask(taskId: Int): List<NpcServerType> =
        ServerCacheManager.getNpcs().values.filter { taskId in taskIds(it) }

    private fun taskIds(type: NpcServerType): IntArray =
        type.slayerTaskIds.takeIf { it.isNotEmpty() }
            ?: type
                .paramOrNull<Int>(BaseParams.slayer_task_id)
                ?.takeIf { it > 0 }
                ?.let { intArrayOf(it) }
            ?: intArrayOf()

    private fun masterLabel(master: SlayerMastersRow): String =
        master.npcIds.firstOrNull()?.name?.takeIf(String::isNotBlank) ?: "master-${master.masterId}"

    private fun paramId(type: NpcServerType, param: dev.openrune.TypedParamType<*>): String =
        type.paramMap?.getOrNull(param)?.let { value ->
            (value as? Definition)?.id?.toString() ?: value.toString()
        } ?: "missing"

    private fun normalize(value: String): String =
        value.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()

    private companion object {
        private const val KONAR_MASTER_ID = 8
    }
}

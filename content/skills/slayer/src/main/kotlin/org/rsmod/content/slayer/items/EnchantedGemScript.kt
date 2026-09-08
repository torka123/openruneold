package org.rsmod.content.slayer.items

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.output.clearMapFlag
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeld2
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.content.slayer.core.CanonicalSlayerRegistry
import org.rsmod.content.slayer.core.SlayerTaskManager
import org.rsmod.content.slayer.dialogue.SlayerContact.contactSlayerMaster
import org.rsmod.content.slayer.dialogue.SlayerContact.checkSlayerTask
import org.rsmod.content.slayer.teleport.SlayerAssignmentSnapshot
import org.rsmod.content.slayer.teleport.SlayerGemTeleportPolicy
import org.rsmod.content.slayer.teleport.SlayerTaskTeleport
import org.rsmod.content.slayer.teleport.SlayerTaskTeleportRegistry
import org.rsmod.content.slayer.teleport.SlayerTeleportResolution
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class EnchantedGemScript
@Inject
constructor(
    private val npcRepo: NpcRepository,
    private val registry: SlayerTaskTeleportRegistry,
    private val teleportValidator: PlayerTeleportValidator,
    private val areaChecker: AreaChecker,
) : PluginScript() {
    private val logger = InlineLogger()
    private val missingLogCycles = mutableMapOf<String, Int>()

    override fun ScriptContext.startup() {
        onPlayerQueueWithArgs<PendingSlayerGemTeleport>(TELEPORT_QUEUE) {
            finishTeleport(it.args)
        }
        for (gem in SlayerGemTeleportPolicy.SUPPORTED_GEM_KEYS) {
            onOpHeld1(gem) { interact() }
            onOpHeld2(gem) { checkSlayerTask() }
            onOpHeld3(gem) { contactSlayerMaster(npcRepo) }
        }
    }

    private suspend fun ProtectedAccess.interact() {
        val cooldownUntil = player.attr[INTERACTION_COOLDOWN] ?: 0
        if (
            player.attr[TELEPORT_PENDING] == true ||
                TELEPORT_QUEUE in player.queueList ||
                cooldownUntil > mapClock
        ) {
            return
        }

        player.attr[INTERACTION_COOLDOWN] = mapClock + INTERACTION_COOLDOWN_CYCLES
        player.attr[TELEPORT_PENDING] = true
        try {
            val task = SlayerTaskManager.getCurrentSlayerTask(player)
            val remaining = vars[SLAYER_COUNT]
            if (task == null || remaining <= 0) {
                mes(SlayerGemTeleportPolicy.NO_ASSIGNMENT_MESSAGE)
                if (vars[SLAYER_TARGET] != 0) {
                    logMissing(
                        key = "legacy:${vars[SLAYER_TARGET]}",
                        taskName = "unknown saved task ${vars[SLAYER_TARGET]}",
                        masterId = vars[SLAYER_MASTER],
                        locationId = vars[SLAYER_AREA],
                        remaining = remaining,
                    )
                }
                return
            }
            val category = SlayerTaskManager.currentCategory(player)
            if (category == null) {
                mes("Your saved Slayer assignment is invalid and cannot be teleported to.")
                return
            }


            if (player.hitpoints <= 0 || "queue.death" in player.queueList) {
                mes("You cannot use the Slayer gem while dead.")
                return
            }
            if (RegionRegistry.inWorkingArea(player.coords)) {
                mes("You cannot use the Slayer gem from this instance or minigame.")
                return
            }
            if (actionDelay > mapClock) {
                mes("You are too busy to use the Slayer gem.")
                return
            }

            val master = SlayerTaskManager.getCurrentAssignedMaster(player)
            val locationId = vars[SLAYER_AREA]
            val initialSnapshot = snapshot()
            val resolution = registry.resolveCanonical(task, category)
            val teleport = when (resolution) {
                is SlayerTeleportResolution.Success -> resolution.teleport
                is SlayerTeleportResolution.Missing -> {
                    reportMissing(resolution, master?.masterId ?: 0, remaining)
                    return
                }
                is SlayerTeleportResolution.Unsafe -> {
                    mes("There is currently no safe Slayer teleport destination for your task: ${category.displayName}.")
                    logMissing(
                        key = "unsafe:${task.id}:$locationId",
                        taskName = task.nameLowercase,
                        masterId = master?.masterId ?: 0,
                        locationId = locationId,
                        remaining = remaining,
                    )
                    return
                }
            }

            val locationName =
                if (locationId > 0 || teleport.entranceOnly) teleport.description else null
            mes(
                SlayerGemTeleportPolicy.progressMessage(
                    taskName = category.displayName,
                    remaining = remaining,
                    locationName = locationName,
                    boss = category.boss,
                )
            )

            if (
                SlayerGemTeleportPolicy.requiresWildernessConfirmation(teleport.wildernessLevel) &&
                    !confirmWilderness(teleport)
            ) {
                return
            }

            val afterConfirmation = snapshot()
            if (!SlayerGemTeleportPolicy.assignmentStillMatches(initialSnapshot, afterConfirmation)) {
                mes("Your Slayer assignment changed, so the teleport was cancelled.")
                return
            }
            val currentTask = SlayerTaskManager.getCurrentSlayerTask(player)
            val currentCategory = SlayerTaskManager.currentCategory(player)
            if (currentTask == null || currentCategory == null) {
                mes("Your Slayer assignment changed, so the teleport was cancelled.")
                return
            }
            val reResolved = registry.resolveCanonical(currentTask, currentCategory)
            val currentTeleport = (reResolved as? SlayerTeleportResolution.Success)?.teleport
            if (currentTeleport?.destination != teleport.destination || !registry.isSafeDestination(teleport.destination)) {
                mes("The Slayer teleport destination is no longer safe.")
                return
            }
            if (!canUseStandardTeleport()) {
                return
            }

            actionDelay = mapClock + TELEPORT_ACTION_DELAY
            anim(TELEPORT_START_ANIM)
            spotanim(TELEPORT_SPOTANIM, height = TELEPORT_SPOTANIM_HEIGHT)
            soundSynth(TELEPORT_SOUND)
            queue(
                TELEPORT_QUEUE,
                TELEPORT_DELAY,
                PendingSlayerGemTeleport(initialSnapshot, teleport.destination),
            )
        } finally {
            player.attr.remove(TELEPORT_PENDING)
        }
    }

    private fun ProtectedAccess.finishTeleport(pending: PendingSlayerGemTeleport) {
        if (!SlayerGemTeleportPolicy.assignmentStillMatches(pending.assignment, snapshot())) {
            mes("Your Slayer assignment changed, so the teleport was cancelled.")
            return
        }
        if (player.hitpoints <= 0 || "queue.death" in player.queueList) {
            mes("You cannot use the Slayer gem while dead.")
            return
        }
        if (RegionRegistry.inWorkingArea(player.coords)) {
            mes("You cannot use the Slayer gem from this instance or minigame.")
            return
        }

        val currentTask = SlayerTaskManager.getCurrentSlayerTask(player)
        val currentCategory = SlayerTaskManager.currentCategory(player)
        if (currentTask == null || currentCategory == null) {
            mes("Your Slayer assignment changed, so the teleport was cancelled.")
            return
        }
        val resolution = registry.resolveCanonical(currentTask, currentCategory)
        val currentTeleport = (resolution as? SlayerTeleportResolution.Success)?.teleport
        if (
            currentTeleport?.destination != pending.destination ||
                !registry.isSafeDestination(pending.destination)
        ) {
            if (!SlayerGemTeleportPolicy.assignmentStillMatches(pending.assignment, snapshot())) {
                mes("Your Slayer assignment changed, so the teleport was cancelled.")
            } else {
                mes("The Slayer teleport destination is no longer safe.")
            }
            return
        }
        if (!canUseStandardTeleport()) return

        telejump(pending.destination, TeleportType.Standard)
        anim(TELEPORT_END_ANIM)
    }

    private fun ProtectedAccess.snapshot(): SlayerAssignmentSnapshot =
        SlayerAssignmentSnapshot(
            taskId = vars[SLAYER_TARGET],
            remaining = vars[SLAYER_COUNT],
            masterId = vars[SLAYER_MASTER],
            locationId = vars[SLAYER_AREA],
            categoryId = vars[SLAYER_CATEGORY],
        )

    private suspend fun ProtectedAccess.confirmWilderness(teleport: SlayerTaskTeleport): Boolean {
        mesbox(
            "<col=7f0000>Warning!</col> This teleport will take you into level " +
                "${teleport.wildernessLevel} <col=7f0000>Wilderness</col>. " +
                "Other players may attack you there.",
        )
        return choice2(
            "Teleport me.",
            true,
            "Cancel.",
            false,
            title = "Teleport into the Wilderness?",
        )
    }

    private fun ProtectedAccess.canUseStandardTeleport(): Boolean {
        val denial = teleportValidator.validate(player, TeleportType.Standard, areaChecker)
        if (denial == null) return true
        player.clearMapFlag()
        mes(denial, ChatType.Engine)
        return false
    }

    private fun ProtectedAccess.reportMissing(
        missing: SlayerTeleportResolution.Missing,
        masterId: Int,
        remaining: Int,
    ) {
        val locationName = missing.locationName
        if (missing.locationId > 0) {
            mes(
                "A teleport has not yet been configured for your assigned Slayer location: " +
                    "${locationName ?: missing.locationId}."
            )
        } else {
            mes("There is currently no Slayer teleport configured for your task: ${missing.task.nameUppercase}.")
        }
        logMissing(
            key = "missing:${missing.task.id}:${missing.locationId}",
            taskName = missing.task.nameLowercase,
            masterId = masterId,
            locationId = missing.locationId,
            remaining = remaining,
        )
    }

    private fun ProtectedAccess.logMissing(
        key: String,
        taskName: String,
        masterId: Int,
        locationId: Int,
        remaining: Int,
    ) {
        val last = missingLogCycles[key]
        if (last != null && mapClock - last < MISSING_LOG_RATE_LIMIT_CYCLES) return
        missingLogCycles[key] = mapClock
        logger.warn {
            "Missing Slayer gem teleport: player=${player.displayName} taskKey=$key " +
                "taskName=$taskName master=$masterId location=$locationId remaining=$remaining"
        }
    }

    private data class PendingSlayerGemTeleport(
        val assignment: SlayerAssignmentSnapshot,
        val destination: org.rsmod.map.CoordGrid,
    )

    private companion object {
        private val TELEPORT_PENDING = AttributeKey<Boolean>(temp = true)
        private val INTERACTION_COOLDOWN = AttributeKey<Int>(temp = true)

        private val TELEPORT_START_ANIM = RSCM.getReverseMapping(RSCMType.SEQ, 714)
        private val TELEPORT_END_ANIM = RSCM.getReverseMapping(RSCMType.SEQ, 715)
        private val TELEPORT_SPOTANIM = RSCM.getReverseMapping(RSCMType.SPOTANIM, 111)

        private const val SLAYER_TARGET = "varp.slayer_target"
        private const val SLAYER_COUNT = "varp.slayer_count"
        private const val SLAYER_MASTER = "varbit.slayer_master"
        private const val SLAYER_AREA = "varp.slayer_area"
        private const val SLAYER_CATEGORY = CanonicalSlayerRegistry.CATEGORY_VARP
        private const val TELEPORT_SOUND = "synth.teleport_all"
        private const val TELEPORT_QUEUE = "queue.slayer_gem_teleport"
        private const val TELEPORT_SPOTANIM_HEIGHT = 92
        private const val TELEPORT_DELAY = 4
        private const val TELEPORT_ACTION_DELAY = 5
        private const val INTERACTION_COOLDOWN_CYCLES = 2
        private const val MISSING_LOG_RATE_LIMIT_CYCLES = 100
    }
}

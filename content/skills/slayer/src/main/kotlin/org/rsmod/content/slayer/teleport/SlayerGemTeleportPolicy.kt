package org.rsmod.content.slayer.teleport

internal object SlayerGemTeleportPolicy {
    const val NO_ASSIGNMENT_MESSAGE = "You do not currently have a Slayer assignment."
    val SUPPORTED_GEM_KEYS = setOf(
        "obj.slayer_gem",
        "obj.slayer_eternal_gem",
    )

    fun remainingText(remaining: Int): String =
        "You have $remaining kill${if (remaining == 1) "" else "s"} remaining."

    fun progressMessage(
        taskName: String,
        remaining: Int,
        locationName: String? = null,
        boss: Boolean = false,
    ): String {
        val verb = if (boss) "defeat" else "kill"
        val location = locationName?.takeIf(String::isNotBlank)?.let { " in $it" }.orEmpty()
        return "Your current task is to $verb $taskName$location. ${remainingText(remaining)}"
    }

    fun canStart(
        hasTask: Boolean,
        remaining: Int,
        dead: Boolean,
        inUnsupportedInstance: Boolean,
        pending: Boolean,
        cooldownUntil: Int,
        currentCycle: Int,
    ): Boolean =
        hasTask &&
            remaining > 0 &&
            !dead &&
            !inUnsupportedInstance &&
            !pending &&
            cooldownUntil <= currentCycle

    fun destinationIsSafe(
        plane: Int,
        loaded: Boolean,
        walkable: Boolean,
        blockingObject: Boolean,
        occupiedByNpc: Boolean,
        inUnsupportedInstance: Boolean,
    ): Boolean =
        plane in 0..3 &&
            loaded &&
            walkable &&
            !blockingObject &&
            !occupiedByNpc &&
            !inUnsupportedInstance

    fun requiresWildernessConfirmation(wildernessLevel: Int): Boolean = wildernessLevel > 0

    fun readyToTeleport(
        assignmentMatches: Boolean,
        destinationSafe: Boolean,
        standardTeleportAllowed: Boolean,
    ): Boolean = assignmentMatches && destinationSafe && standardTeleportAllowed

    fun assignmentStillMatches(
        expected: SlayerAssignmentSnapshot,
        actual: SlayerAssignmentSnapshot,
    ): Boolean =
        actual.remaining > 0 &&
            actual.taskId == expected.taskId &&
            actual.masterId == expected.masterId &&
            actual.categoryId == expected.categoryId &&
            actual.locationId == expected.locationId
}

internal data class SlayerAssignmentSnapshot(
    val taskId: Int,
    val remaining: Int,
    val masterId: Int,
    val locationId: Int,
    val categoryId: Int = 0,
)

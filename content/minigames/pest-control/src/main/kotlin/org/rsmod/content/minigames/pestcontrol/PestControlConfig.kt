package org.rsmod.content.minigames.pestcontrol

import org.rsmod.api.attr.AttributeKey
import org.rsmod.map.CoordGrid

internal const val PC_SOURCE_REGION = 10536
internal const val PC_MATCH_TICKS = 2_000
internal const val PC_POINT_CAP = 4_000
internal const val PC_REWARD_POINTS = 5
internal const val PC_ADMIN_TEST_POINTS = 1_500
internal const val PC_LANDER_DEPARTURE_TICKS = 25
internal const val PC_MAX_PLAYERS = 25

internal val PEST_CONTROL_POINTS =
    AttributeKey<Int>(persistenceKey = "pest_control_commendation_points")
internal val PEST_CONTROL_REWARD_PURCHASES =
    AttributeKey<Int>(persistenceKey = "pest_control_reward_purchases")

internal object PestControlLander {
    const val minimumCombat = 40
    const val portalHitpoints = 200
    const val squire = "npc.pest_squire_lander"
    const val gangplank = "loc.pest_lander_gangplank"
    const val ladder = "loc.pest_lander_ladder"
    val queueCenter = CoordGrid(2661, 2641, 0)
    val exit = CoordGrid(2657, 2639, 0)
}

internal object PestControlRewardNpcs {
    val voidKnights =
        setOf(
            "npc.pest_voidknight_1",
            "npc.pest_voidknight_2",
            "npc.pest_voidknight_3",
            "npc.pest_voidknight_4",
        )
}

internal object DisabledPestControlLanders {
    val gangplanks = setOf("loc.pest_lander_gangplank_2", "loc.pest_lander_gangplank_3")
    val ladders = setOf("loc.pest_lander_ladder_2", "loc.pest_lander_ladder_3")
    val squires = setOf("npc.pest_squire_lander_2", "npc.pest_squire_lander_3")
}

internal enum class PestControlState {
    STARTING,
    RUNNING,
    WON,
    LOST,
    REWARDING,
    CLEANING_UP,
    CLOSED,
}

internal enum class PestControlResult {
    Won,
    Lost,
}

internal enum class PortalColor(
    val sourceCoord: CoordGrid,
    val activeNpc: String,
    val healthComponent: String,
    val doneComponent: String,
    val shieldComponent: String,
) {
    Purple(
        CoordGrid(2628, 2591, 0),
        "npc.pest_portal_1_active",
        "component.pest_status_overlay:pest_status_porttxt1",
        "component.pest_status_overlay:pest_portal_1_done",
        "component.pest_status_overlay:purple_shield",
    ),
    Blue(
        CoordGrid(2680, 2588, 0),
        "npc.pest_portal_2_active",
        "component.pest_status_overlay:pest_status_porttxt2",
        "component.pest_status_overlay:pest_portal_2_done",
        "component.pest_status_overlay:blue_shield",
    ),
    Yellow(
        CoordGrid(2669, 2570, 0),
        "npc.pest_portal_3_active",
        "component.pest_status_overlay:pest_status_porttxt3",
        "component.pest_status_overlay:pest_portal_3_done",
        "component.pest_status_overlay:yellow_shield",
    ),
    Red(
        CoordGrid(2645, 2569, 0),
        "npc.pest_portal_4_active",
        "component.pest_status_overlay:pest_status_porttxt4",
        "component.pest_status_overlay:pest_portal_4_done",
        "component.pest_status_overlay:red_shield",
    ),
}

internal object PestControlOverlay {
    val portalIcons =
        listOf(
            "component.pest_status_overlay:pest_status_port1",
            "component.pest_status_overlay:pest_status_port2",
            "component.pest_status_overlay:pest_status_port3",
            "component.pest_status_overlay:pest_status_port4",
        )

    val hiddenComponents =
        listOf(
            "component.pest_status_overlay:content_left",
            "component.pest_status_overlay:activity_container",
            "component.pest_status_overlay:pest_portal_1_done",
            "component.pest_status_overlay:pest_portal_2_done",
            "component.pest_status_overlay:pest_portal_3_done",
            "component.pest_status_overlay:pest_portal_4_done",
            "component.pest_status_overlay:purple_shield",
            "component.pest_status_overlay:blue_shield",
            "component.pest_status_overlay:yellow_shield",
            "component.pest_status_overlay:red_shield",
            "component.pest_status_overlay:com_33",
            "component.pest_status_overlay:com_34",
            "component.pest_status_overlay:com_35",
            "component.pest_status_overlay:com_36",
        )
}

internal object PestControlRewardShop {
    const val pointsVar = "varp.if1"
    const val confirmComponent = "component.pest_rewardshop:confirm"
}

internal object PestControlTuning {
    const val minimumPlayers = 1
    const val queueWaitTicks = PC_LANDER_DEPARTURE_TICKS
}

internal class PestControlQueue<T>(
    private val minimumPlayers: Int = PestControlTuning.minimumPlayers,
    private val maximumPlayers: Int = PC_MAX_PLAYERS,
    private val waitTicks: Int = PestControlTuning.queueWaitTicks,
) {
    private val entries = linkedSetOf<T>()
    private var departureTick: Int? = null

    val size: Int
        get() = entries.size

    val isFull: Boolean
        get() = entries.size >= maximumPlayers

    fun contains(value: T): Boolean = value in entries

    fun join(value: T, currentTick: Int): QueueJoinResult {
        if (value in entries) return QueueJoinResult.Duplicate
        if (isFull) return QueueJoinResult.Full
        entries += value
        if (departureTick == null) departureTick = currentTick + waitTicks
        return QueueJoinResult.Joined
    }

    fun leave(value: T): Boolean {
        val removed = entries.remove(value)
        if (entries.isEmpty()) departureTick = null
        return removed
    }

    fun secondsRemaining(currentTick: Int): Int {
        val ticks = (departureTick ?: (currentTick + waitTicks)) - currentTick
        return (ticks.coerceAtLeast(0) * 3 + 4) / 5
    }

    fun ready(currentTick: Int): Boolean {
        val scheduledTick = departureTick ?: return false
        if (currentTick < scheduledTick) return false
        departureTick = currentTick + waitTicks
        return entries.size >= minimumPlayers
    }

    fun snapshot(): List<T> = entries.toList()
}

internal enum class QueueJoinResult {
    Joined,
    Duplicate,
    Full,
}

internal data class PortalProgress(
    val newlyDestroyed: Boolean,
    val destroyedCount: Int,
    val complete: Boolean,
)

internal class PestControlPortalTracker {
    private val destroyed = linkedSetOf<PortalColor>()

    val destroyedCount: Int
        get() = destroyed.size

    fun isDestroyed(color: PortalColor): Boolean = color in destroyed

    fun markDestroyed(color: PortalColor): PortalProgress {
        val added = destroyed.add(color)
        return PortalProgress(
            newlyDestroyed = added,
            destroyedCount = destroyed.size,
            complete = destroyed.size == PortalColor.entries.size,
        )
    }
}

internal object PestControlResultArbiter {
    fun decide(destroyedPortals: Int, timerExpired: Boolean): PestControlResult? =
        when {
            destroyedPortals >= PortalColor.entries.size -> PestControlResult.Won
            timerExpired -> PestControlResult.Lost
            else -> null
        }
}

internal object PestControlRules {
    fun portalCanTakeDamage(destroyed: Boolean, running: Boolean): Boolean = running && !destroyed

    fun cappedPoints(current: Int, reward: Int = PC_REWARD_POINTS): Int =
        (current.coerceIn(0, PC_POINT_CAP).toLong() + reward.coerceAtLeast(0))
            .coerceAtMost(PC_POINT_CAP.toLong())
            .toInt()

    fun normalizePoints(points: Int): Int = points.coerceIn(0, PC_POINT_CAP)

    fun isArenaGate(displayName: String, internalName: String, actions: List<String?>): Boolean =
        (displayName.contains("gate", ignoreCase = true) ||
            internalName.contains("gate", ignoreCase = true)) &&
            actions.any { it.equals("Open", ignoreCase = true) }

    fun experienceReward(level: Int, multiplier: Int, pointsSpent: Int): Int =
        ((level.coerceAtLeast(1) * level.coerceAtLeast(1)) / 600) *
            multiplier *
            pointsSpent.coerceAtLeast(0)
}

internal object PestControlCoords {
    val playerEntry = CoordGrid(2657, 2611, 0)
    val outpostExit = PestControlLander.exit
}

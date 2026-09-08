package org.rsmod.content.other.consumables

import jakarta.inject.Singleton
import org.rsmod.game.entity.Player

public enum class ConsumableActivityKind {
    MINIGAME,
    RAID,
}

/**
 * Identifies the activity which issued a temporary consumable.
 *
 * [ownershipVars] is copied into [org.rsmod.game.inv.InvObj.vars]. A temporary consumable is valid
 * only while the player is registered in the same activity with the same non-zero token.
 */
public data class ConsumableActivityToken(
    val kind: ConsumableActivityKind,
    val activity: String,
    val ownershipVars: Int,
) {
    init {
        require(activity.isNotBlank()) { "Activity key must not be blank." }
        require(ownershipVars != 0) { "Activity ownership token must be non-zero." }
    }
}

/** Pure policy kept separate from the player registry so ownership rules can be tested directly. */
public object ConsumableActivityPolicy {
    public fun canConsume(
        active: ConsumableActivityToken?,
        minigameOnly: String,
        raidOnly: String,
        ownershipVars: Int,
    ): Boolean {
        if (minigameOnly.isBlank() && raidOnly.isBlank()) return true
        if (minigameOnly.isNotBlank() && raidOnly.isNotBlank()) return false
        if (ownershipVars == 0 || active == null || active.ownershipVars != ownershipVars) {
            return false
        }
        return when {
            raidOnly.isNotBlank() ->
                active.kind == ConsumableActivityKind.RAID && active.activity == raidOnly
            else ->
                active.kind == ConsumableActivityKind.MINIGAME && active.activity == minigameOnly
        }
    }
}

@Singleton
public class ConsumableActivityAccess {
    private val active = mutableMapOf<Long, ConsumableActivityToken>()

    public fun enterRaid(player: Player, raidOnly: String, ownershipVars: Int) {
        active[player.accountHash] =
            ConsumableActivityToken(ConsumableActivityKind.RAID, raidOnly, ownershipVars)
    }

    public fun enterMinigame(player: Player, minigameOnly: String, ownershipVars: Int) {
        active[player.accountHash] =
            ConsumableActivityToken(ConsumableActivityKind.MINIGAME, minigameOnly, ownershipVars)
    }

    public fun leaveRaid(player: Player, raidOnly: String, ownershipVars: Int? = null): Boolean =
        leave(player, ConsumableActivityKind.RAID, raidOnly, ownershipVars)

    public fun leaveMinigame(
        player: Player,
        minigameOnly: String,
        ownershipVars: Int? = null,
    ): Boolean = leave(player, ConsumableActivityKind.MINIGAME, minigameOnly, ownershipVars)

    public fun clear(player: Player): ConsumableActivityToken? = active.remove(player.accountHash)

    public fun activeToken(player: Player): ConsumableActivityToken? = active[player.accountHash]

    public fun canConsume(
        player: Player,
        minigameOnly: String,
        raidOnly: String,
        ownershipVars: Int,
    ): Boolean =
        ConsumableActivityPolicy.canConsume(
            active = activeToken(player),
            minigameOnly = minigameOnly,
            raidOnly = raidOnly,
            ownershipVars = ownershipVars,
        )

    private fun leave(
        player: Player,
        kind: ConsumableActivityKind,
        activity: String,
        ownershipVars: Int?,
    ): Boolean {
        val current = active[player.accountHash] ?: return false
        if (current.kind != kind || current.activity != activity) return false
        if (ownershipVars != null && current.ownershipVars != ownershipVars) return false
        active.remove(player.accountHash)
        return true
    }
}

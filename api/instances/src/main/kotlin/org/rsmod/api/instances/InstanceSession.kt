package org.rsmod.api.instances

import org.rsmod.api.instances.region.InstancePlacement
import org.rsmod.game.damage.DamageContributions
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.PlayerUid

/**
 * Identity-based typed key: two scripts cannot collide merely by choosing the same display name.
 */
public class InstanceStateKey<T : Any>(public val name: String)

/** The object itself is the token for one admission, including same-player re-entry. */
internal class FixedInstanceOccupant(val player: Player) {
    val uuid: Long = requireNotNull(player.uuid)
    val uid: PlayerUid = player.uid

    fun matches(candidate: Player): Boolean =
        candidate === player && candidate.uuid == uuid && candidate.uid == uid

    fun resolve(players: PlayerList): Player? = uid.resolve(players)?.takeIf { matches(it) }
}

class InstanceSession(
    val id: InstanceId,
    val regionIds: MutableSet<Int>,
    val owner: Long,
    val key: String,
    val spec: InstanceSpec,
    val placement: InstancePlacement,
    var access: InstanceAccess,
    val isServerOwned: Boolean = false,
) {
    private val contentState = mutableMapOf<InstanceStateKey<*>, Any>()
    private val cleanupActions = mutableListOf<() -> Unit>()
    var isEnded: Boolean = false
        private set

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> stateOrNull(key: InstanceStateKey<T>): T? = contentState[key] as T?

    fun <T : Any> state(key: InstanceStateKey<T>, create: () -> T): T {
        check(!isEnded) { "Instance session has ended: $id" }
        return stateOrNull(key) ?: create().also { contentState[key] = it }
    }

    /** Owns specialized resources across NPC forms until the native session resets or ends. */
    fun onCleanup(action: () -> Unit): () -> Unit {
        check(!isEnded) { "Instance session has ended: $id" }
        cleanupActions += action
        return {
            cleanupActions.remove(action)
            Unit
        }
    }

    internal fun markEnded() {
        if (isEnded) return
        isEnded = true
        damageContributions.clear()
        clearContentState()
    }

    private fun clearContentState() {
        val actions = cleanupActions.toList()
        cleanupActions.clear()
        contentState.clear()
        var failure: Throwable? = null
        for (action in actions) {
            try {
                action()
            } catch (error: Throwable) {
                if (failure == null) failure = error else failure.addSuppressed(error)
            }
        }
        failure?.let { throw it }
    }

    val damageContributions = DamageContributions()

    val occupants = mutableSetOf<Long>()
    internal val fixedOccupants = mutableMapOf<Long, FixedInstanceOccupant>()
    internal var fixedInitialized = false

    internal fun livePlayers(players: PlayerList): List<Player> =
        if (placement is InstancePlacement.Fixed)
            fixedOccupants.values.mapNotNull { it.resolve(players) }
        else occupants.mapNotNull { uuid -> players.firstOrNull { it.uuid == uuid } }

    val timeWarningsSent = mutableSetOf<Int>()
    val graceWarningsSent = mutableSetOf<Int>()

    var gracePending = false
        private set

    var arenaExpired = false
        private set

    var bossFightStartTick: Int? = null

    var state: SessionState = SessionState.Active
        private set

    var startedAtTick = 0
        private set

    fun markStarted(currentTick: Int) {
        startedAtTick = currentTick
        timeWarningsSent.clear()
        graceWarningsSent.clear()
        gracePending = false
    }

    fun elapsedTicks(currentTick: Int): Int = currentTick - startedAtTick

    fun remainingTicks(currentTick: Int): Int? =
        spec.timeLimitTicks?.minus(elapsedTicks(currentTick))

    fun graceRemainingTicks(currentTick: Int): Int? =
        (state as? SessionState.Grace)?.deadlineTick?.minus(currentTick)

    fun addOccupant(player: Long) {
        occupants += player
        if (state is SessionState.Reclaim) {
            state = SessionState.Active
        }
    }

    fun removeOccupant(player: Long, currentTick: Int) {
        occupants -= player
        if (
            !isServerOwned &&
                occupants.isEmpty() &&
                state !is SessionState.Grace &&
                !spec.destroyWhenEmpty
        ) {
            state = SessionState.Reclaim(currentTick + spec.reclaimTicks)
        }
    }

    fun enterGrace(currentTick: Int) {
        state = SessionState.Grace(currentTick + spec.graceTicks)
        graceWarningsSent.clear()
        gracePending = false
    }

    fun markGracePending(): Boolean =
        if (gracePending) {
            false
        } else {
            gracePending = true
            true
        }

    fun markArenaExpired(): Boolean =
        if (arenaExpired) {
            false
        } else {
            arenaExpired = true
            true
        }

    fun resetState() {
        check(!isEnded) { "Cannot reset a destroyed instance: $id" }
        state = SessionState.Active
        arenaExpired = false
        gracePending = false
        bossFightStartTick = null
        timeWarningsSent.clear()
        graceWarningsSent.clear()
        damageContributions.clear()
        clearContentState()
    }

    fun isExpired(currentTick: Int): Boolean =
        !isServerOwned &&
            state is SessionState.Reclaim &&
            occupants.isEmpty() &&
            currentTick >= (state as SessionState.Reclaim).deadlineTick

    fun isFull(): Boolean = occupants.size >= spec.maxPlayers

    fun registerRegion(regionId: Int) {
        regionIds += regionId
    }
}

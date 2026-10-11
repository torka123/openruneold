package org.rsmod.game.hit

import java.util.concurrent.atomic.AtomicBoolean
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.map.util.Bounds

/** How damage was produced, independently of its melee/ranged/magic presentation. */
public enum class HitOrigin {
    Direct,
    Recoil,
    Poison,
    Venom,
    Burn,
    Thrall,
    Environment,
}

/** [ownerUuid] uses the native player UUID, never an entity slot or display name. */
public data class HitAttribution(
    public val origin: HitOrigin = HitOrigin.Direct,
    public val ownerUuid: Long? = null,
    public val mechanic: String? = null,
)

/**
 * A game-thread-owned lifecycle. Invalidating it permanently retires all previously issued tokens;
 * later tokens belong to a new generation. Tokens contain no content callbacks or entity
 * references.
 */
public class HitScope(
    private val bounds: Bounds? = null,
    private val cancellationSignal: AtomicBoolean? = null,
) {
    @Volatile private var generation: Long = 0
    @Volatile private var closed: Boolean = false

    public fun invalidate() {
        generation = Math.incrementExact(generation)
    }

    /** Terminal retirement: newly captured tokens cannot revive an ended membership/effect. */
    public fun close() {
        closed = true
    }

    /** Called at coordinate mutation, before queues can observe an external teleport. */
    public fun observePosition(coords: CoordGrid) {
        val area = bounds ?: return
        if (
            coords.level != area.level ||
                coords.x !in area.x until area.x + area.width ||
                coords.z !in area.z until area.z + area.length
        )
            close()
    }

    private fun isCurrent(expected: Long): Boolean {
        if (cancellationSignal?.get() == true) close()
        return !closed && generation == expected
    }

    public fun token(): Token = Token(this, generation)

    public class Token
    internal constructor(private val scope: HitScope, private val generation: Long) {
        public fun isValid(): Boolean = scope.isCurrent(generation)
    }
}

/** Immutable attribution and composed scope snapshots carried unchanged through delayed damage. */
public class HitContext
private constructor(
    public val attribution: HitAttribution,
    private val tokens: List<HitScope.Token>,
) {
    public val isScoped: Boolean
        get() = tokens.isNotEmpty()

    public fun isValid(): Boolean = tokens.all { it.isValid() }

    public fun withAttribution(attribution: HitAttribution): HitContext =
        HitContext(attribution, tokens)

    public companion object {
        public val EMPTY: HitContext = HitContext(HitAttribution(), emptyList())

        public fun of(attribution: HitAttribution, vararg scopes: HitScope): HitContext =
            HitContext(attribution, scopes.map { it.token() })

        /** Keep every previously captured lifecycle while using the first context's attribution. */
        public fun compose(first: HitContext, vararg others: HitContext): HitContext =
            HitContext(first.attribution, first.tokens + others.flatMap { it.tokens })

        /**
         * Capture both sides when a hit/effect is created. An inherited effect retains its original
         * snapshots: re-queuing a burn or echo after departure can never renew an expired token.
         */
        public fun capture(
            source: PathingEntity?,
            target: PathingEntity,
            inherited: HitContext? = null,
        ): HitContext {
            val attribution =
                inherited?.attribution
                    ?: HitAttribution(
                        origin = if (source == null) HitOrigin.Environment else HitOrigin.Direct,
                        ownerUuid = (source as? Player)?.uuid,
                    )
            val tokens = buildList {
                if (inherited != null) addAll(inherited.tokens)
                source?.combatHitScope?.let { add(it.token()) }
                target.combatHitScope?.let { add(it.token()) }
            }
            return HitContext(attribution, tokens)
        }
    }
}

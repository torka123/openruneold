package org.rsmod.api.player.hook

import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.obj.Obj
import org.rsmod.map.CoordGrid
import org.rsmod.objtx.TransactionResultList

public enum class GroundItemDropSource {
    Manual,
    Death,
    Overflow,
}

public data class GroundItemDropContext(
    val player: Player,
    val type: ItemServerType,
    val coords: CoordGrid,
    val source: GroundItemDropSource,
    val receiver: Player? = null,
)

public data class GroundItemDropParams(
    val duration: Int,
    val reveal: Int,
    val ownerOnly: Boolean = false,
)

public fun interface PlayerGroundItemDropHook {
    /** @return Adjusted drop parameters when this hook applies, or `null` to use defaults. */
    public fun adjustDrop(
        context: GroundItemDropContext,
        duration: Int,
        reveal: Int,
    ): GroundItemDropParams?
}

public fun interface PlayerObjTakeValidateHook {
    /** @return A denial message if the pickup should be blocked, or `null` if allowed. */
    public fun validateTake(player: Player, obj: Obj, objType: ItemServerType): String?
}

public class GroundItemDropResolver
@Inject
constructor(private val hooks: Set<@JvmSuppressWildcards PlayerGroundItemDropHook>) {
    public fun resolve(
        context: GroundItemDropContext,
        duration: Int,
        reveal: Int,
    ): GroundItemDropParams {
        for (hook in hooks) {
            val adjusted = hook.adjustDrop(context, duration, reveal)
            if (adjusted != null) {
                return adjusted
            }
        }
        return GroundItemDropParams(duration, reveal)
    }
}

public class PlayerObjTakeValidator
@Inject
constructor(private val hooks: Set<@JvmSuppressWildcards PlayerObjTakeValidateHook>) {
    public fun validate(player: Player, obj: Obj, objType: ItemServerType): String? {
        for (hook in hooks) {
            val denial = hook.validateTake(player, obj, objType)
            if (denial != null) {
                return denial
            }
        }
        return null
    }
}

/**
 * Routes pickups into an alternate inventory, using the same prepare/commit boundary as normal
 * pickups. Preparation must not mutate storage. The caller commits only after removing the ground
 * obj successfully, with no suspension between preparation, removal and commit.
 */
public interface PlayerObjTakeRedirectHook {
    public fun prepare(
        player: Player,
        obj: Obj,
        objType: ItemServerType,
    ): TransactionResultList<InvObj>?

    /** Called after a successful commit, for persistence mirrors or interface updates. */
    public fun onTake(player: Player) {}
}

public class PlayerObjTakeRedirector
@Inject
constructor(private val hooks: Set<@JvmSuppressWildcards PlayerObjTakeRedirectHook>) {
    public fun prepare(player: Player, obj: Obj, objType: ItemServerType): Prepared? {
        for (hook in hooks) {
            val transaction = hook.prepare(player, obj, objType) ?: continue
            if (transaction.success) return Prepared(hook, transaction)
        }
        return null
    }

    public data class Prepared(
        val hook: PlayerObjTakeRedirectHook,
        val transaction: TransactionResultList<InvObj>,
    )
}

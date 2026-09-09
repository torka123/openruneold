package org.rsmod.game.hit

import dev.openrune.ServerCacheManager
import dev.openrune.types.ItemServerType
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.npc.NpcUid
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.queue.QueueArgumentValidity

public data class Hit(
    public val type: HitType,
    public val hitmark: Hitmark,
    private val sourceUid: Int?,
    private val righthandObj: Int?,
    private val secondaryObj: Int?,
    private val impactHandler: HitImpactHandler? = null,
    private val validity: HitValidity? = null,
    public val context: HitContext = HitContext.EMPTY,
) : QueueArgumentValidity {
    /** Optional encounter/lifecycle guard evaluated before any impact side effects. */
    public fun isValidNow(): Boolean = context.isValid() && (validity?.isValid() ?: true)

    override fun isQueueArgumentValid(): Boolean = context.isValid()

    public val damage: Int
        get() = hitmark.damage

    public val isFromNpc: Boolean
        get() = hitmark.isNpcSource

    public val isFromPlayer: Boolean
        get() = hitmark.isPlayerSource

    public fun isRighthandObj(type: ItemServerType): Boolean = type.id == righthandObj

    public fun righthandType(): ItemServerType? =
        righthandObj?.let { ServerCacheManager.getItem(it) }

    public fun isSecondaryObj(type: ItemServerType): Boolean = type.id == secondaryObj

    public fun secondaryType(): ItemServerType? =
        secondaryObj?.let { ServerCacheManager.getItem(it) }

    public fun resolveNpcSource(npcList: NpcList): Npc? {
        val uid = checkNotNull(sourceUid) { "Hit did not originate from a source: $this" }
        check(isFromNpc) { "Hit did not originate from an npc: $this" }

        val npcUid = NpcUid(uid)
        return npcUid.resolve(npcList)
    }

    public fun resolvePlayerSource(playerList: PlayerList): Player? {
        val uid = checkNotNull(sourceUid) { "Hit did not originate from a source: $this" }
        check(isFromPlayer) { "Hit did not originate from a player: $this" }

        val playerUid = PlayerUid(uid)
        return playerUid.resolve(playerList)
    }

    /** Invokes this hit's optional [HitImpactHandler] with its final processed damage. */
    public fun dispatchImpact(target: PathingEntity, damage: Int) {
        impactHandler?.onImpact(HitImpact(target = target, hit = this, damage = damage))
    }

    override fun toString(): String =
        "Hit(type=$type, righthandObj=$righthandObj, secondaryObj=$secondaryObj, hitmark=$hitmark)"
}

public fun interface HitValidity {
    public fun isValid(): Boolean
}

package org.rsmod.game.hit

import dev.openrune.types.ItemServerType
import dev.openrune.types.NpcServerType
import org.rsmod.game.entity.npc.NpcUid

public class HitBuilder(
    public val type: HitType,
    public var damage: Int,
    public val sourceUid: Int?,
    public val sourceSlot: Int?,
    public val isFromNpc: Boolean,
    public val isFromPlayer: Boolean,
    public var clientDelay: Int?,
    private var righthandType: Int?,
    private var secondaryType: Int?,
    private val targetHitmark: Int,
    private val sourceHitmark: Int,
    private val publicHitmark: Int?,
    private val zeroDamageHitmarkLit: Int?,
    private val zeroDamageHitmarkTint: Int?,
    private val maxDamageHitmarkLit: Int?,
    private val targetMaxDamageThreshold: Int,
    private val sourceMaxDamageThreshold: Int,
) {
    /** Optional callback preserved on the built [Hit] and invoked after it is processed. */
    public var impactHandler: HitImpactHandler? = null
    public var validity: HitValidity? = null

    public fun isRighthandObj(type: ItemServerType): Boolean = type.id == righthandType

    public fun isSecondaryObj(type: ItemServerType): Boolean = type.id == secondaryType

    /** Mirrors [Hit.righthandType] so target-side mechanics can classify an incoming attack. */
    public fun righthandType(): ItemServerType? =
        righthandType?.let(dev.openrune.ServerCacheManager::getItem)

    /** Mirrors [Hit.secondaryType] for spell/ammunition-aware target mechanics. */
    public fun secondaryType(): ItemServerType? =
        secondaryType?.let(dev.openrune.ServerCacheManager::getItem)

    /** Resolves the source NPC type without requiring the live NPC to remain in the scene. */
    public fun sourceNpcType(): NpcServerType? {
        val uid = sourceUid ?: return null
        if (!isFromNpc) return null
        return dev.openrune.ServerCacheManager.getNpc(NpcUid(uid).type)
    }

    public fun build(): Hit {
        val hitmark = buildHitmark()
        return Hit(
            type = type,
            hitmark = hitmark,
            sourceUid = sourceUid,
            righthandObj = righthandType,
            secondaryObj = secondaryType,
            impactHandler = impactHandler,
            validity = validity,
        )
    }

    private fun buildHitmark(): Hitmark {
        val clientDelay = checkNotNull(clientDelay) { "`clientDelay` must be set." }

        var target = targetHitmark
        var source = sourceHitmark
        var public = publicHitmark

        if (damage == 0) {
            target = zeroDamageHitmarkLit ?: targetHitmark
            source = zeroDamageHitmarkLit ?: sourceHitmark
            if (publicHitmark != null) {
                public = zeroDamageHitmarkTint ?: publicHitmark
            }
        } else {
            if (damage >= targetMaxDamageThreshold) {
                target = maxDamageHitmarkLit ?: targetHitmark
            }
            if (damage >= sourceMaxDamageThreshold) {
                source = maxDamageHitmarkLit ?: sourceHitmark
            }
        }

        return when {
            sourceSlot == null ->
                Hitmark.fromNoSource(
                    self = target,
                    source = source,
                    public = public,
                    damage = damage,
                    delay = clientDelay,
                )

            isFromNpc ->
                Hitmark.fromNpcSource(
                    self = target,
                    source = source,
                    public = public,
                    damage = damage,
                    delay = clientDelay,
                    slotId = sourceSlot,
                )

            isFromPlayer ->
                Hitmark.fromPlayerSource(
                    self = target,
                    source = source,
                    public = public,
                    damage = damage,
                    delay = clientDelay,
                    slotId = sourceSlot,
                )

            else -> throw IllegalStateException() // Should be unreachable.
        }
    }
}

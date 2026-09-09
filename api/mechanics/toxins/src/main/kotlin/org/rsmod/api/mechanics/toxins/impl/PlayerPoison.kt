package org.rsmod.api.mechanics.toxins.impl

import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.config.refs.done.hitmark_groups
import org.rsmod.api.config.refs.params
import org.rsmod.api.mechanics.toxins.Toxin
import org.rsmod.api.mechanics.toxins.ToxinImmunity
import org.rsmod.api.player.hit.modifier.NoopPlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitAttribution
import org.rsmod.game.hit.HitContext
import org.rsmod.game.hit.HitOrigin
import org.rsmod.game.hit.HitType
import org.rsmod.game.type.getInvObj

public object PlayerPoison {
    private val CONTEXT = AttributeKey<HitContext>(resetOnDeath = true, temp = true)
    private val DISCARD_ON_RECONNECT = AttributeKey<Boolean>("scoped_poison")

    public const val TICK_INTERVAL: Int = 30

    public fun isPoisoned(player: Player): Boolean {
        prune(player)
        return player.vars["varp.poison_severity"] > 0
    }

    private fun prune(player: Player) {
        if (player.attr[CONTEXT]?.isValid() == false) clear(player)
    }

    public fun damageForSeverity(severity: Int): Int = (severity + 4) / 5

    public fun severityForInitialDamage(initialDamage: Int): Int =
        if (initialDamage <= 0) 0 else 5 * initialDamage - 4

    public fun reduceSeverity(player: Player, amount: Int): Boolean {
        prune(player)
        if (amount <= 0) {
            return false
        }

        val current = player.vars["varp.poison_severity"]

        if (current <= 0) {
            return false
        }

        val reduced = (current - amount).coerceAtLeast(0)

        if (reduced == 0) {
            clear(player)
            return true
        }

        VarPlayerIntMapSetter.set(player, "varp.poison_severity", reduced)

        player.timer("timer.player_poison", TICK_INTERVAL)

        Toxin.syncStatusOrbs(player)
        return true
    }

    internal fun startWithoutInitialHit(
        player: Player,
        initialDamage: Int,
        context: HitContext? = null,
    ) {
        val captured = poisonContext(player, context)
        if (!captured.isValid()) return
        require(initialDamage > 0) { "`initialDamage` must be greater than 0." }

        val severity = severityForInitialDamage(initialDamage)
        storeContext(player, captured)

        VarPlayerIntMapSetter.set(player, "varp.poison_severity", severity)

        player.timer("timer.player_poison", TICK_INTERVAL)

        Toxin.syncStatusOrbs(player)
    }

    public fun tryPoison(player: Player, source: Npc): Boolean =
        tryPoison(player, initialDamage = 0, source = source)

    public fun tryPoison(player: Player, initialDamage: Int = 0, source: Npc): Boolean {
        val fromParam = source.visType.paramOrNull(params.npc_poison_severity) ?: 0
        return applyPoison(
            player,
            initialDamage,
            fromParam,
            context = HitContext.capture(source, player),
        )
    }

    public fun tryPoison(
        player: Player,
        initialDamage: Int = 0,
        severity: Int = 0,
        context: HitContext? = null,
    ): Boolean = applyPoison(player, initialDamage, paramSeverity = severity, context = context)

    /**
     * Applies poison status now and schedules its first damage for the standard 30-tick interval.
     */
    public fun tryPoisonAfterInterval(
        player: Player,
        initialDamage: Int = 0,
        severity: Int = 0,
        context: HitContext? = null,
    ): Boolean =
        applyPoison(
            player = player,
            initialDamage = initialDamage,
            paramSeverity = severity,
            immediateFirstHit = false,
            context = context,
        )

    /**
     * A single poison-type hitsplat using [initialDamage] as literal damage. Does not apply ongoing
     * poison (no severity varp, no timer, no game message). For scenery, traps, nettles, etc.
     */
    public fun incidentalPoisonHit(player: Player, initialDamage: Int): Boolean {
        if (initialDamage <= 0) return false
        val context = poisonContext(player, null)
        if (!context.isValid()) return false
        queuePoisonHit(player, initialDamage, context)
        return true
    }

    public fun hasWornPoisonEnvenomImmunity(player: Player): Boolean {
        for (obj in player.worn) {
            if (obj == null) continue
            val type = getInvObj(obj)
            if ((type.paramOrNull(params.worn_poison_envenom_immunity) ?: 0) > 0) {
                return true
            }
        }
        return false
    }

    private fun applyPoison(
        player: Player,
        initialDamage: Int,
        paramSeverity: Int,
        immediateFirstHit: Boolean = true,
        context: HitContext? = null,
    ): Boolean {
        val captured = poisonContext(player, context)
        if (!captured.isValid()) return false
        prune(player)
        if (PlayerVenom.isEnvenomed(player)) {
            return false
        }
        if (ToxinImmunity.hasPoisonImmunity(player)) {
            return false
        }
        if (hasWornPoisonEnvenomImmunity(player)) {
            return false
        }
        val storedSeverity =
            when {
                paramSeverity > 0 -> paramSeverity
                initialDamage > 0 -> severityForInitialDamage(initialDamage)
                else -> 0
            }
        if (storedSeverity <= 0) return false

        val incomingDamage = damageForSeverity(storedSeverity)
        val current = player.vars["varp.poison_severity"]
        if (current > 0) {
            val currentDamage = damageForSeverity(current)
            if (incomingDamage < currentDamage) return false
            if (incomingDamage == currentDamage && storedSeverity <= current) return false
        }

        storeContext(player, captured)
        VarPlayerIntMapSetter.set(player, "varp.poison_severity", storedSeverity)
        if (immediateFirstHit) {
            val firstHitDamage =
                if (initialDamage > 0) initialDamage else damageForSeverity(storedSeverity)
            queuePoisonHit(player, firstHitDamage)

            val severity = storedSeverity - 1
            if (severity <= 0) {
                clear(player)
            } else {
                VarPlayerIntMapSetter.set(player, "varp.poison_severity", severity)
                player.timer("timer.player_poison", TICK_INTERVAL)
            }
        } else {
            player.timer("timer.player_poison", TICK_INTERVAL)
        }
        player.mes("You have been poisoned!", ChatType.Spam)
        Toxin.syncStatusOrbs(player)
        return true
    }

    private fun poisonContext(player: Player, inherited: HitContext?): HitContext =
        HitContext.capture(null, player, inherited)
            .withAttribution(
                (inherited?.attribution ?: HitAttribution()).copy(origin = HitOrigin.Poison)
            )

    private fun storeContext(player: Player, context: HitContext) {
        player.attr[CONTEXT] = context
        if (context.isScoped) player.attr[DISCARD_ON_RECONNECT] = true
        else player.attr.remove(DISCARD_ON_RECONNECT)
    }

    /** Tokens are transient. Persist only the fact that this status cannot survive its activity. */
    public fun discardScopedStatus(player: Player) {
        if (player.attr[DISCARD_ON_RECONNECT] == true) clear(player)
    }

    private fun queuePoisonHit(
        player: Player,
        damage: Int,
        context: HitContext? = player.attr[CONTEXT],
    ) {
        player.queueHit(
            delay = 1,
            type = HitType.Typeless,
            damage = damage,
            hitmark = hitmark_groups.poison_damage,
            modifier = NoopPlayerHitModifier,
            context = poisonContext(player, context),
        )
    }

    public fun clear(player: Player) {
        player.attr.remove(CONTEXT)
        player.attr.remove(DISCARD_ON_RECONNECT)
        VarPlayerIntMapSetter.set(player, "varp.poison_severity", 0)
        player.clearTimer("timer.player_poison")
        Toxin.syncStatusOrbs(player)
    }

    public fun onPoisonTimerTick(player: Player) {
        if (player.attr[CONTEXT]?.isValid() == false) {
            clear(player)
            return
        }
        var severity = player.vars["varp.poison_severity"]
        if (severity <= 0) {
            clear(player)
            return
        }

        queuePoisonHit(player, damageForSeverity(severity))

        severity--
        if (severity <= 0) {
            clear(player)
            return
        }
        VarPlayerIntMapSetter.set(player, "varp.poison_severity", severity)

        player.timer("timer.player_poison", TICK_INTERVAL)

        Toxin.syncStatusOrbs(player)
    }
}

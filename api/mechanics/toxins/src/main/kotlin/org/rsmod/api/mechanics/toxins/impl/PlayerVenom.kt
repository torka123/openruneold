package org.rsmod.api.mechanics.toxins.impl

import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.config.refs.done.hitmark_groups
import org.rsmod.api.mechanics.toxins.Toxin
import org.rsmod.api.mechanics.toxins.ToxinImmunity
import org.rsmod.api.player.hit.modifier.NoopPlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitAttribution
import org.rsmod.game.hit.HitContext
import org.rsmod.game.hit.HitOrigin
import org.rsmod.game.hit.HitType

public object PlayerVenom {
    private val CONTEXT = AttributeKey<HitContext>(resetOnDeath = true, temp = true)
    private val DISCARD_ON_RECONNECT = AttributeKey<Boolean>("scoped_venom")

    public const val TICK_INTERVAL: Int = PlayerPoison.TICK_INTERVAL

    public const val NOT_ENVENOMED: Int = 0

    public fun isEnvenomed(player: Player): Boolean {
        if (player.attr[CONTEXT]?.isValid() == false) clear(player)
        return player.vars["varp.venom_strikes"] > NOT_ENVENOMED
    }

    public fun damageForStrikeIndex(strikesCompletedSoFar: Int): Int =
        minOf(20, 6 + 2 * strikesCompletedSoFar.coerceAtLeast(0))

    public fun tryVenom(player: Player, context: HitContext? = null): Boolean =
        applyVenom(player, context)

    private fun applyVenom(player: Player, context: HitContext?): Boolean {
        val captured = venomContext(player, context)
        if (!captured.isValid()) return false
        if (ToxinImmunity.hasVenomImmunity(player)) {
            return false
        }
        if (PlayerPoison.hasWornPoisonEnvenomImmunity(player)) {
            return false
        }
        if (isEnvenomed(player)) {
            return false
        }

        if (PlayerPoison.isPoisoned(player)) {
            PlayerPoison.clear(player)
        }

        player.attr[CONTEXT] = captured
        if (captured.isScoped) player.attr[DISCARD_ON_RECONNECT] = true
        else player.attr.remove(DISCARD_ON_RECONNECT)
        VarPlayerIntMapSetter.set(player, "varp.venom_strikes", 1)
        player.timer("timer.player_venom", TICK_INTERVAL)
        player.mes("You have been envenomed!", ChatType.Spam)
        Toxin.syncStatusOrbs(player)
        return true
    }

    public fun reduceToPoison(player: Player, initialPoisonDamage: Int = 6): Boolean {
        if (!isEnvenomed(player)) {
            return false
        }

        val context = player.attr[CONTEXT]
        clear(player)

        PlayerPoison.startWithoutInitialHit(
            player = player,
            initialDamage = initialPoisonDamage,
            context = context,
        )

        return true
    }

    public fun clear(player: Player) {
        player.attr.remove(CONTEXT)
        player.attr.remove(DISCARD_ON_RECONNECT)
        VarPlayerIntMapSetter.set(player, "varp.venom_strikes", NOT_ENVENOMED)
        player.clearTimer("timer.player_venom")
        Toxin.syncStatusOrbs(player)
    }

    public fun onVenomTimerTick(player: Player) {
        if (player.attr[CONTEXT]?.isValid() == false) {
            clear(player)
            return
        }
        var strikes = player.vars["varp.venom_strikes"]
        if (strikes <= NOT_ENVENOMED) {
            clear(player)
            return
        }
        val strikeIndex = strikes - 1
        val damage = damageForStrikeIndex(strikeIndex)
        queueVenomHit(player, damage)
        strikes++
        VarPlayerIntMapSetter.set(player, "varp.venom_strikes", strikes)
        player.timer("timer.player_venom", TICK_INTERVAL)
        Toxin.syncStatusOrbs(player)
    }

    public fun discardScopedStatus(player: Player) {
        if (player.attr[DISCARD_ON_RECONNECT] == true) clear(player)
    }

    private fun venomContext(player: Player, inherited: HitContext?): HitContext =
        HitContext.capture(null, player, inherited)
            .withAttribution(
                (inherited?.attribution ?: HitAttribution()).copy(origin = HitOrigin.Venom)
            )

    private fun queueVenomHit(player: Player, damage: Int) {
        player.queueHit(
            delay = 1,
            type = HitType.Typeless,
            damage = damage,
            hitmark = hitmark_groups.venom,
            modifier = NoopPlayerHitModifier,
            context = venomContext(player, player.attr[CONTEXT]),
        )
    }
}

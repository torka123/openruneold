package org.rsmod.api.player.hit.processor

import jakarta.inject.Inject
import kotlin.math.min
import org.rsmod.api.player.cheat.ADMIN_GOD_HITPOINTS_MAX
import org.rsmod.api.player.cheat.adminGodHitpoints
import org.rsmod.api.player.cheat.adminGodMode
import org.rsmod.api.player.death.recordDeathCause
import org.rsmod.api.player.death.resolveDeathCause
import org.rsmod.api.player.events.PlayerHitpointsChangedEvent
import org.rsmod.api.player.headbar.InternalPlayerHeadbars
import org.rsmod.api.player.queueDeath
import org.rsmod.api.player.stat.baseHitpointsLvl
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.statSub
import org.rsmod.events.EventBus
import org.rsmod.game.damage.recordDamageOn
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.headbar.Headbar
import org.rsmod.game.hit.Hit

public class DamageOnlyPlayerHitProcessor
@Inject
constructor(
    private val eventBus: EventBus,
    private val npcList: NpcList,
    private val playerList: PlayerList,
) : InstantPlayerHitProcessor {
    override fun Player.process(hit: Hit) {
        if (adminGodMode) {
            return
        }

        val godHitpoints = adminGodHitpoints
        val oldHitpoints = godHitpoints ?: hitpoints
        val damage = min(oldHitpoints, hit.damage)
        var currentHitpoints = oldHitpoints
        if (damage > 0) {
            currentHitpoints = oldHitpoints - damage
            if (godHitpoints != null) {
                adminGodHitpoints = currentHitpoints.takeIf { it > 0 }
                if (currentHitpoints == 0 && hitpoints > 0) {
                    statSub("stat.hitpoints", constant = hitpoints, percent = 0)
                }
            } else {
                statSub("stat.hitpoints", constant = damage, percent = 0)
                currentHitpoints = hitpoints
            }
            hit.recordDamageOn(this, damage, playerList, npcList)
            val event =
                PlayerHitpointsChangedEvent(
                    player = this,
                    oldHitpoints = oldHitpoints,
                    newHitpoints = currentHitpoints,
                    maxHitpoints =
                        if (godHitpoints != null) ADMIN_GOD_HITPOINTS_MAX else baseHitpointsLvl,
                    hit = hit,
                )
            eventBus.publish(event)
        }

        val queueDeath = currentHitpoints == 0 && "queue.death" !in queueList
        if (queueDeath) {
            val npcSource = if (hit.isFromNpc) hit.resolveNpcSource(npcList) else null
            val playerSource = if (hit.isFromPlayer) hit.resolvePlayerSource(playerList) else null
            recordDeathCause(hit.resolveDeathCause(npcSource, playerSource))
            queueDeath()
        }

        showHitmark(hit.hitmark)

        val maximumHitpoints =
            if (godHitpoints != null) ADMIN_GOD_HITPOINTS_MAX else baseHitpointsLvl
        val headbar = hit.createHeadbar(currentHitpoints, maximumHitpoints)
        showHeadbar(headbar)
        hit.dispatchImpact(this, damage)
    }

    private fun Hit.createHeadbar(currHp: Int, maxHp: Int): Headbar =
        InternalPlayerHeadbars.createFromHitmark(hitmark, currHp, maxHp, "headbar.health_30")
}

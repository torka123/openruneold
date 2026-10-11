package org.rsmod.api.npc.hit.modifier

import jakarta.inject.Inject
import kotlin.math.absoluteValue
import kotlin.math.max
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Npc
import org.rsmod.game.hit.HitBuilder

public class StandardNpcHitModifier @Inject constructor(private val eventBus: EventBus) :
    NpcHitModifier {
    override fun HitBuilder.modify(target: Npc) {
        target.publishEvent(this)
        target.applyFlatArmour(this)
    }

    private fun Npc.publishEvent(hit: HitBuilder) {
        val event = NpcHitEvents.Modify(this, hit)
        eventBus.publish(event)
    }

    private fun Npc.applyFlatArmour(hit: HitBuilder) {
        val armour = vars["varn.flat_armour"]

        // Flat armour is a stat, not an absorb pool. It applies independently after every
        // successful damage roll and is never consumed by a hit. A successful hit retains the
        // OSRS minimum of one damage after positive armour; a miss remains zero.
        if (hit.damage > 0) {
            hit.damage =
                if (armour >= 0) {
                    max(1, hit.damage - armour)
                } else {
                    hit.damage + armour.absoluteValue
                }
        }
    }
}

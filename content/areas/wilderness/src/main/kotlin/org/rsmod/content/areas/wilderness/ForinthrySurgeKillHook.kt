package org.rsmod.content.areas.wilderness

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.player.output.mes
import org.rsmod.api.realm.Realm
import org.rsmod.game.entity.PlayerList

/** The single award path used by the authoritative Maledictus kill hook. */
@Singleton
public class ForinthrySurgeAwardService
@Inject
constructor(private val players: PlayerList, private val realm: Realm) {
    private val logger = InlineLogger()

    public fun award(context: NpcDeathKillContext): Boolean {
        val mappedName = RSCM.getReverseMapping(RSCMType.NPC, context.npc.visType.id)
        if (!isMaledictusSurgeSource(context.npc.visType.internalName, mappedName)) return false
        val topDamager = context.npc.damageContributions.topPlayer(players) ?: context.hero
        if (!topDamager.isWearingAvarice()) {
            topDamager.mes(
                "You were the top damager, but you needed to be wearing an Amulet of avarice " +
                    "to receive Forinthry Surge."
            )
            logger.info {
                "Forinthry Surge not awarded: player=${topDamager.displayName}, " +
                    "npc=${context.npc.visType.id}, reason=amulet-not-worn"
            }
            return false
        }
        topDamager.applyForinthrySurge(realm.config.id)
        topDamager.mes("The Forinthry Surge flows through your amulet for 30 minutes.")
        logger.info {
            "Forinthry Surge awarded: player=${topDamager.displayName}, " +
                "npc=${context.npc.visType.id}, skull=${topDamager.skullIcon}"
        }
        return true
    }
}

internal fun isMaledictusSurgeSource(internalName: String, mappedName: String?): Boolean =
    internalName == "wild_cave_superior" ||
        mappedName == "npc.wild_cave_superior" ||
        mappedName == "npc.revenant_maledictus"

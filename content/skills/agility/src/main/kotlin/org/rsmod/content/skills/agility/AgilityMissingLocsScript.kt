package org.rsmod.content.skills.agility

import dev.openrune.ServerCacheManager
import jakarta.inject.Inject
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.slf4j.LoggerFactory

/** Restores the two course locs omitted by the revision-240 server map archive. */
public class AgilityMissingLocsScript @Inject constructor(private val locs: LocRepository) :
    PluginScript() {
    override fun ScriptContext.startup() {
        for (placement in PRIFDDINAS_MISSING_LOCS) {
            val type = ServerCacheManager.getObject(placement.id)
            if (type == null) {
                log.error(
                    "Cannot restore missing Prifddinas Agility loc {}: definition absent",
                    placement.id,
                )
                continue
            }
            if (locs.findExact(placement.coords, type) != null) continue
            val added =
                locs.add(
                    placement.coords,
                    type,
                    Int.MAX_VALUE,
                    placement.angle,
                    LocShape.CentrepieceStraight,
                )
            log.info(
                "Restored revision-240 Prifddinas Agility loc {} at {} (angle={})",
                placement.id,
                placement.coords,
                added.angle,
            )
        }
    }

    private data class Placement(val id: Int, val coords: CoordGrid, val angle: LocAngle)

    private companion object {
        private val log = LoggerFactory.getLogger(AgilityMissingLocsScript::class.java)
        private val PRIFDDINAS_MISSING_LOCS =
            listOf(
                // Donor cache region 50_95 [54_29_0], attributes 40: shape 10, angle 0.
                Placement(36221, CoordGrid(3254, 6109, 0), LocAngle.West),
                // Donor cache region 51_95 [5_38_0], attributes 41: shape 10, angle 1.
                Placement(36229, CoordGrid(3269, 6118, 0), LocAngle.North),
            )
    }
}

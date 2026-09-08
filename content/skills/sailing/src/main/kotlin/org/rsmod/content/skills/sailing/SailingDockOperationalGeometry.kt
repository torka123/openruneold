package org.rsmod.content.skills.sailing

import org.rsmod.map.CoordGrid

enum class MissingSailingDockField {
    LandReturn,
    SeaSpawn,
    SeaAnchor,
    BoatRotation,
    InZoneLand,
    InZoneSea,
}

data class SailingDockCaptureEvidence(
    val evidenceId: String,
    val revision: Int,
    val cacheProvider: String,
    val cacheHash: String,
)

sealed interface SailingDockOperationalGeometry {
    data class Verified(
        val landReturn: CoordGrid,
        val seaSpawn: CoordGrid,
        val seaAnchor: CoordGrid,
        val boatRotation: Int,
        val inZoneLand: Int,
        val inZoneSea: Int,
        val evidence: SailingDockCaptureEvidence,
    ) : SailingDockOperationalGeometry

    data class BlockedByCapture(
        val missingFields: Set<MissingSailingDockField>,
        val reason: String,
    ) : SailingDockOperationalGeometry

    companion object {
        val UNCERTIFIED: SailingDockOperationalGeometry =
            BlockedByCapture(
                missingFields = MissingSailingDockField.entries.toSet(),
                reason = "Revision-240 static loc placement does not prove Sailing travel geometry.",
            )
    }
}

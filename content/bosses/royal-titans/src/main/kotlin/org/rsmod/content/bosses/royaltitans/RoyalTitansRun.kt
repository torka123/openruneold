package org.rsmod.content.bosses.royaltitans

import java.util.UUID
import org.rsmod.api.instances.InstanceSession
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.hit.HitScope
import org.rsmod.game.loc.LocInfo
import org.rsmod.map.CoordGrid

/** Pair-specific data stored on the authoritative native InstanceSession. */
internal data class RoyalTitansRun(
    val session: InstanceSession,
    var generation: Long = 1L,
    var retired: Boolean = false,
    val participants: MutableMap<Long, Participant> = mutableMapOf(),
    val hitScope: HitScope = HitScope(),
    var completionId: UUID = UUID.randomUUID(),
    var completion: RoyalTitansCompletion? = null,
    var completionCommitted: Boolean = false,
    var reinvigoration: RoyalTitansReinvigoration.State? = null,
    val spawnCycles: MutableMap<Npc, Int> = mutableMapOf(),
    var branda: Npc? = null,
    var eldric: Npc? = null,
    var stage: RoyalTitansStage = RoyalTitansStage.INTRO,
    var stageTick: Int = 0,
    var elementalsUnlocked: Boolean = false,
    var eldricSpawnCycle: Int = Int.MAX_VALUE,
    var areaAttack: AreaAttack? = null,
    var rangedGrace: Int = 0,
    val claimed: MutableSet<Long> = mutableSetOf(),
    val lastBossTarget: MutableMap<Long, Npc> = mutableMapOf(),
    val walls: MutableList<WallSegment> = mutableListOf(),
    val pendingWalls: MutableList<PendingWall> = mutableListOf(),
    val elementals: MutableMap<Npc, ElementalState> = mutableMapOf(),
    val pendingSummons: MutableList<PendingSummon> = mutableListOf(),
    val corpses: MutableMap<Npc, RoyalElement> = mutableMapOf(),
    val dyingTitans: MutableSet<Npc> = mutableSetOf(),
    val arenaFloors: MutableList<LocInfo> = mutableListOf(),
    val arenaBlockers: MutableList<LocInfo> = mutableListOf(),
    val dyingElementals: MutableSet<Npc> = mutableSetOf(),
    val removableElementals: MutableSet<Npc> = mutableSetOf(),
    val creditedElementalKills: MutableSet<Npc> = mutableSetOf(),
) {
    internal data class Participant(val hitScope: HitScope, var deathRecorded: Boolean = false)

    internal data class PendingWall(val formCycle: Int, val element: RoyalElement, val row: Int)

    internal data class WallSegment(
        val element: RoyalElement,
        val row: Int,
        val index: Int,
        val loc: LocInfo,
        val hitbox: Npc,
        val expireCycle: Int,
    )

    internal data class PendingSummon(
        val spawnCycle: Int,
        val element: RoyalElement,
        val titan: Npc,
    )

    internal data class ElementalState(
        val element: RoyalElement,
        val explodeCycle: Int,
        var target: PlayerUid? = null,
        var routeTarget: CoordGrid? = null,
        var exploding: Boolean = false,
    )

    internal data class AreaAttack(
        val safeX: Int,
        val safeZ: Int,
        val impactCycle: Int,
        val safeLoc: LocInfo,
        val timer: Npc,
        val survivor: RoyalElement?,
    )
}

package org.rsmod.content.minigames.clanmayhem

import java.util.UUID
import org.rsmod.api.instances.InstanceSession
import org.rsmod.game.entity.Npc
import org.rsmod.game.loc.LocInfo
import org.rsmod.map.CoordGrid

internal enum class ClanMayhemFogStage {
    OPEN,
    FIRST,
    SECOND,
    THIRD,
    FINAL,
    LETHAL,
}

internal data class MayhemRectMask(val minX: Int, val maxX: Int, val minZ: Int, val maxZ: Int) {
    init {
        require(minX <= maxX && minZ <= maxZ)
    }

    operator fun contains(tile: MayhemSourceTile): Boolean =
        tile.x in minX..maxX && tile.z in minZ..maxZ
}

internal data class ClanMayhemFogRoute(
    val id: String,
    val name: String,
    val destination: MayhemSourceTile,
    val masks: Map<ClanMayhemFogStage, MayhemRectMask>,
) {
    fun contains(stage: ClanMayhemFogStage, tile: MayhemSourceTile): Boolean =
        tile in checkNotNull(masks[stage]) { "Missing $stage mask for $id" }
}

internal object ClanMayhemFogRoutes {
    val all: List<ClanMayhemFogRoute> =
        (ClanMayhemCrater.bossNodes +
                MayhemNamedNode("central", "Central Combat Yard", MayhemSourceTile(3_427, 4_064)))
            .map { node -> route(node) }

    private fun route(node: MayhemNamedNode): ClanMayhemFogRoute {
        fun square(radius: Int) =
            MayhemRectMask(
                (node.tile.x - radius).coerceAtLeast(3_328),
                (node.tile.x + radius).coerceAtMost(3_519),
                (node.tile.z - radius).coerceAtLeast(3_968),
                (node.tile.z + radius).coerceAtMost(4_159),
            )
        return ClanMayhemFogRoute(
            node.id,
            node.name,
            node.tile,
            mapOf(
                ClanMayhemFogStage.OPEN to MayhemRectMask(3_328, 3_519, 3_968, 4_159),
                ClanMayhemFogStage.FIRST to square(72),
                ClanMayhemFogStage.SECOND to square(48),
                ClanMayhemFogStage.THIRD to square(32),
                ClanMayhemFogStage.FINAL to square(16),
                ClanMayhemFogStage.LETHAL to square(16),
            ),
        )
    }
}

internal data class MayhemSupplyRuntime(var npc: Npc, val encounter: MayhemSupplyNpcEncounter)

internal data class MayhemCrateRuntime(val loc: LocInfo, var claimed: Boolean = false)

internal class ClanMayhemMatchClock(private val startedAtTick: Int) {
    private var testOffsetTicks: Int = 0

    fun elapsed(currentTick: Int): Int = currentTick - startedAtTick + testOffsetTicks

    fun fastForwardTo(currentTick: Int, targetElapsedTicks: Int): Boolean {
        val currentElapsed = elapsed(currentTick)
        if (targetElapsedTicks < currentElapsed) return false
        testOffsetTicks += targetElapsedTicks - currentElapsed
        return true
    }
}

internal class ClanMayhemMatch(
    val id: UUID,
    val session: InstanceSession,
    val format: ClanMayhemFormat,
    val startedAtTick: Int,
    val instanceEntry: CoordGrid,
    val teams: LinkedHashMap<UUID, ClanMayhemTeam>,
    val fogRoute: ClanMayhemFogRoute,
    val routeRoll: Int,
) {
    val timeline = ClanMayhemTimeline(id)
    val resultGate = ClanMayhemResultGate()
    private val matchClock = ClanMayhemMatchClock(startedAtTick)
    var state: ClanMayhemState = ClanMayhemState.ACTIVE
    var fogStage: ClanMayhemFogStage = ClanMayhemFogStage.OPEN
    var bossRoll: ClanMayhemBossRoll? = null
    var bossNpc: Npc? = null
    var bossResolved: Boolean = false
    var finalStandings: List<ClanMayhemFinalStanding> = emptyList()
    var cleanupAtTick: Int? = null
    var rewardChest: LocInfo? = null

    val supplies = mutableListOf<MayhemSupplyRuntime>()
    val crates = mutableListOf<MayhemCrateRuntime>()
    val pendingCratePackages = mutableMapOf<Int, MutableList<MayhemEquipmentRoll>>()
    val lastEnemyAttacker = mutableMapOf<Int, Pair<Int, Int>>()
    val pvpDamageSnapshots = mutableMapOf<Pair<Int, Int>, Int>()

    fun participant(accountId: Int): ClanMayhemParticipant? =
        teams.values.firstNotNullOfOrNull { it.roster[accountId] }

    fun teamFor(accountId: Int): ClanMayhemTeam? =
        teams.values.firstOrNull { accountId in it.roster }

    fun elapsed(currentTick: Int): Int = matchClock.elapsed(currentTick)

    fun fastForwardTo(currentTick: Int, targetElapsedTicks: Int): Boolean =
        matchClock.fastForwardTo(currentTick, targetElapsedTicks)

    fun mapSourceCoord(source: MayhemSourceTile): CoordGrid =
        instanceEntry.translate(
            source.x - ClanMayhemCrater.waitingRoom.tile.x,
            source.z - ClanMayhemCrater.waitingRoom.tile.z,
            source.level - ClanMayhemCrater.waitingRoom.tile.level,
        )

    fun sourceCoord(instance: CoordGrid): MayhemSourceTile =
        MayhemSourceTile(
            ClanMayhemCrater.waitingRoom.tile.x + instance.x - instanceEntry.x,
            ClanMayhemCrater.waitingRoom.tile.z + instance.z - instanceEntry.z,
            ClanMayhemCrater.waitingRoom.tile.level + instance.level - instanceEntry.level,
        )

    fun safe(instance: CoordGrid): Boolean =
        runCatching { fogRoute.contains(fogStage, sourceCoord(instance)) }.getOrDefault(false)
}

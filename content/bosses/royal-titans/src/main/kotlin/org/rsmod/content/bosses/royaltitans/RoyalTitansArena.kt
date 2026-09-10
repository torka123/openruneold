package org.rsmod.content.bosses.royaltitans

import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import kotlin.math.abs
import kotlin.math.max
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.RegionLocal
import org.rsmod.api.player.hit.modifier.NpcBypassProtectionPrayerPlayerHitModifier
import org.rsmod.api.player.hit.modifier.StandardPlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.content.bosses.royaltitans.RoyalTitansDefinitions.requireNpc
import org.rsmod.content.bosses.royaltitans.RoyalTitansRun as Run
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.hit.HitAttribution
import org.rsmod.game.hit.HitContext
import org.rsmod.game.hit.HitOrigin
import org.rsmod.game.hit.HitType
import org.rsmod.map.CoordGrid

/**
 * Arena geometry and native actor/hazard operations. No timer, selector or membership authority.
 */
internal class RoyalTitansArena
@Inject
constructor(
    private val instances: InstanceManager,
    private val npcs: NpcRepository,
    private val players: PlayerList,
) {
    fun spawnNpc(
        run: Run,
        id: Int,
        tile: CoordGrid,
        hitpoints: Int,
        movementLocked: Boolean = false,
    ): Npc {
        val npc = Npc(requireNpc(id), tile)
        npc.combatHitScope = run.hitScope
        npc.mode = NpcMode.None
        npc.baseHitpointsLvl = hitpoints
        npc.hitpoints = hitpoints
        npc.movementLocked = movementLocked
        npcs.add(npc, ROYAL_TITANS_NPC_LIFETIME)
        npc.respawns = false
        instances.attachNpc(run.session.id, npc)
        return npc
    }

    fun removeNpc(run: Run, npc: Npc) {
        npc.resetBodyModels()
        run.elementals.remove(npc)
        run.corpses.remove(npc)
        if (npc.isSlotAssigned) npcs.del(npc, Int.MAX_VALUE)
    }

    fun hit(
        run: Run,
        player: Player,
        damage: Int,
        type: HitType,
        bypassPrayer: Boolean,
        mechanic: String,
        source: Npc? = null,
    ) {
        val attacker = source ?: run.branda ?: run.eldric ?: return
        player.queueHit(
            source = attacker,
            delay = 1,
            type = type,
            damage = damage.coerceAtLeast(0),
            context =
                HitContext.of(HitAttribution(HitOrigin.Direct, mechanic = mechanic), run.hitScope),
            modifier =
                if (bypassPrayer) NpcBypassProtectionPrayerPlayerHitModifier
                else StandardPlayerHitModifier,
        )
    }

    fun isPresent(run: Run, npc: Npc): Boolean =
        !run.retired && instances.instanceForNpc(npc) == run.session.id

    fun isPresent(run: Run, player: Player): Boolean =
        !run.retired &&
            player.isSlotAssigned &&
            !player.pendingLogout &&
            !player.loggingOut &&
            player.hitpoints > 0 &&
            instances.sessionForPlayer(player)?.id == run.session.id

    fun targetFor(run: Run, npc: Npc): Player? =
        online(run)
            .filter { isPresent(run, it) && it.hitpoints > 0 }
            .minByOrNull { npc.distanceTo(it) }

    fun online(run: Run): List<Player> =
        players
            .filter { it.uuid in run.session.occupants }
            .filter { it.isSlotAssigned && !it.pendingLogout && !it.loggingOut }

    fun inArena(run: Run, tile: CoordGrid): Boolean {
        val sw = local(run, 29, 28)
        val ne = local(run, 33, 34)
        return tile.level == sw.level && tile.x in sw.x..ne.x && tile.z in sw.z..ne.z
    }

    fun inTitanLanding(run: Run, tile: CoordGrid, element: RoyalElement): Boolean {
        val x = localX(run, tile)
        val z = localZ(run, tile)
        return x in 30..32 &&
            when (element) {
                RoyalElement.FIRE -> z in 35..37
                RoyalElement.ICE -> z in 25..27
            }
    }

    fun local(run: Run, x: Int, z: Int): CoordGrid =
        requireNotNull(instances.localCoord(run.session, RegionLocal(0, 45, 149, x, z)))

    fun localX(run: Run, tile: CoordGrid): Int = tile.x - local(run, 0, 0).x

    fun localZ(run: Run, tile: CoordGrid): Int = tile.z - local(run, 0, 0).z

    fun distance(a: CoordGrid, b: CoordGrid): Int = max(abs(a.x - b.x), abs(a.z - b.z))
}

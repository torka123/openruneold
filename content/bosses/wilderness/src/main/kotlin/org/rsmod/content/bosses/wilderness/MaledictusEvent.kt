package org.rsmod.content.bosses.wilderness

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.config.constants
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.death.PlayerDeathDropTransformHook
import org.rsmod.api.game.process.npc.NpcPlayerChaseValidateHook
import org.rsmod.api.game.process.npc.hunt.NpcPlayerHuntValidateHook
import org.rsmod.api.player.output.mes
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.content.areas.wilderness.ForinthrySurgeAwardService
import org.rsmod.content.bosses.wilderness.ethereum.EthereumBraceletDeathTransformHook
import org.rsmod.content.bosses.wilderness.ethereum.EthereumBraceletHuntValidateHook
import org.rsmod.content.bosses.wilderness.ethereum.RevenantEtherKillHook
import org.rsmod.game.damage.DamageContributor
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.module.PluginModule

public class WildernessBossModule : PluginModule() {
    override fun bind() {
        addSetBinding<NpcDeathKillHook>(MaledictusKillHook::class.java)
        addSetBinding<NpcDeathKillHook>(RevenantEtherKillHook::class.java)
        addSetBinding<NpcPlayerChaseValidateHook>(BearCaveChaseValidateHook::class.java)
        addSetBinding<NpcPlayerHuntValidateHook>(EthereumBraceletHuntValidateHook::class.java)
        addSetBinding<PlayerDeathDropTransformHook>(EthereumBraceletDeathTransformHook::class.java)
    }
}

internal class MaledictusKillHook
@Inject
constructor(
    private val events: MaledictusWorldEvent,
    private val surgeAwards: ForinthrySurgeAwardService,
) : NpcDeathKillHook {
    override fun onKill(context: NpcDeathKillContext) {
        surgeAwards.award(context)
        events.onKill(context)
    }
}

/**
 * World-scoped Revenant Maledictus lifecycle. It deliberately reuses NPC damage contributions, the
 * normal death hooks, ground-object ownership and the boss encounter generation rather than
 * maintaining a second combat or loot-ownership system.
 */
@Singleton
internal class MaledictusEventService
@Inject
constructor(
    private val deps: BossDeps,
    private val areaChecker: AreaChecker,
    private val objRepo: ObjRepository,
) {
    private val accumulator =
        MaledictusSpawnAccumulator(
            denominator = SPAWN_DENOMINATOR,
            initialProgress = deps.random.of(SPAWN_DENOMINATOR),
            lockoutTicks = SPAWN_LOCKOUT_TICKS,
        )
    private var activeNpc: Npc? = null
    private var firstProgressTick: Int = -1

    fun onKill(context: NpcDeathKillContext) {
        val npc = context.npc
        if (npc.visType.internalName == MALEDICTUS_INTERNAL_NAME) {
            completeEvent(context)
            return
        }
        if (!isEligibleRevenant(npc)) return

        val tick = deps.mapClock.cycle
        if (firstProgressTick < 0) firstProgressTick = tick
        val failsafe = tick - firstProgressTick >= SPAWN_FAILSAFE_TICKS
        val rolled =
            accumulator.recordKill(
                combatLevel = npc.visType.combatLevel.coerceAtLeast(1),
                roll = deps.random.of(SPAWN_DENOMINATOR),
                tick = tick,
            )
        if (rolled || (failsafe && accumulator.canSpawn(tick))) {
            spawnEvent(tick)
        }
    }

    private fun isEligibleRevenant(npc: Npc): Boolean =
        npc.visType.internalName.startsWith("wild_cave_") &&
            npc.visType.internalName != MALEDICTUS_INTERNAL_NAME &&
            areaChecker.inArea(REVENANT_CAVES_AREA, npc.coords)

    private fun spawnEvent(tick: Int) {
        if (activeNpc?.isSlotAssigned == true || !accumulator.markSpawned(tick)) return
        val type =
            ServerCacheManager.getNpc(MALEDICTUS_NPC.asRSCM(RSCMType.NPC))
                ?: error("Active cache is missing $MALEDICTUS_NPC")
        val coords = deps.random.pick(MALEDICTUS_SPAWN_TILES)
        val npc =
            Npc(type, coords).also {
                it.mode = type.defaultMode
                deps.npcRepo.add(it, Int.MAX_VALUE)
                it.respawns = false
            }
        activeNpc = npc
        firstProgressTick = -1
        cavePlayers().forEach {
            it.mes("The Revenant Maledictus has appeared in the Revenant Caves!")
        }
        scheduleExpiry(npc)
    }

    private fun scheduleExpiry(npc: Npc) {
        deps.worldQueues.add(EVENT_LIFETIME_TICKS) {
            if (activeNpc !== npc || !npc.isSlotAssigned) return@add
            if (!npc.damageContributions.isEmpty) {
                // Current OSRS does not despawn Maledictus in the middle of a fight.
                scheduleEngagedExpiryCheck(npc)
                return@add
            }
            expire(npc)
        }
    }

    private fun scheduleEngagedExpiryCheck(npc: Npc) {
        deps.worldQueues.add(ENGAGED_RECHECK_TICKS) {
            if (activeNpc !== npc || !npc.isSlotAssigned) return@add
            if (npc.hitpoints <= 0) return@add
            // Contributions intentionally keep an engaged event alive until it is killed or reset.
            scheduleEngagedExpiryCheck(npc)
        }
    }

    private fun expire(npc: Npc) {
        if (npc.isSlotAssigned) deps.npcRepo.del(npc, Int.MAX_VALUE)
        activeNpc = null
        accumulator.markInactive()
        cavePlayers().forEach { it.mes("The Revenant Maledictus has faded away.") }
    }

    private fun completeEvent(context: NpcDeathKillContext) {
        val npc = context.npc
        if (activeNpc !== npc && activeNpc?.isSlotAssigned == true) return
        val contributors =
            npc.damageContributions
                .sortedByDamageDescending()
                .filterIsInstance<DamageContributor.ByPlayer>()
                .distinctBy { it.uuid }
                .mapNotNull { it.resolve(deps.playerList) }
        val top = npc.damageContributions.topPlayer(deps.playerList) ?: context.hero
        rewardTopDamager(top, npc)
        contributors.filter { it !== top }.forEach(::rewardParticipant)
        activeNpc = null
        accumulator.markInactive()
    }

    private fun rewardTopDamager(player: Player, npc: Npc) {
        val artifactId = if (deps.random.of(2) == 0) ANCIENT_EMBLEM_ID else ANCIENT_TOTEM_ID
        val artifact =
            ServerCacheManager.getItem(artifactId)
                ?: error("Active cache is missing Maledictus artefact item $artifactId")
        val duration = player.lootDropDuration ?: constants.lootdrop_duration
        objRepo.add(artifact, npc.coords, duration, player)
        player.mes("Your damage earned you a guaranteed ancient artefact from the Maledictus.")
    }

    private fun rewardParticipant(player: Player) {
        val duration = player.lootDropDuration ?: constants.lootdrop_duration
        objRepo.add("obj.blighted_4dose2restore", player.coords, duration, player)
        objRepo.add("obj.blighted_manta_ray", player.coords, duration, player)
        objRepo.add("obj.blighted_anglerfish", player.coords, duration, player)
        player.mes("Your contribution earned you blighted supplies from the Maledictus.")
    }

    private fun cavePlayers(): List<Player> =
        deps.playerList.filter { areaChecker.inArea(REVENANT_CAVES_AREA, it.coords) }

    internal fun snapshot(): MaledictusEventSnapshot =
        MaledictusEventSnapshot(
            progress = accumulator.progress,
            active = activeNpc?.isSlotAssigned == true,
            lockoutUntilTick = accumulator.lockoutUntilTick,
        )
}

internal data class MaledictusEventSnapshot(
    val progress: Int,
    val active: Boolean,
    val lockoutUntilTick: Int,
)

/** Deterministic, testable cumulative-spawn state; random rolls are supplied by the caller. */
internal class MaledictusSpawnAccumulator(
    private val denominator: Int,
    initialProgress: Int = 0,
    private val lockoutTicks: Int,
) {
    var progress: Int = initialProgress.coerceIn(0, denominator - 1)
        private set

    var active: Boolean = false
        private set

    var lockoutUntilTick: Int = 0
        private set

    init {
        require(denominator > 0)
        require(lockoutTicks >= 0)
    }

    fun recordKill(combatLevel: Int, roll: Int, tick: Int): Boolean {
        if (combatLevel <= 0 || !canSpawn(tick)) return false
        progress = (progress + combatLevel).coerceAtMost(denominator)
        return roll.coerceIn(0, denominator - 1) < progress
    }

    fun canSpawn(tick: Int): Boolean = !active && tick >= lockoutUntilTick

    fun markSpawned(tick: Int): Boolean {
        if (!canSpawn(tick)) return false
        active = true
        progress = 0
        lockoutUntilTick = tick + lockoutTicks
        return true
    }

    fun markInactive() {
        active = false
    }
}

private const val REVENANT_CAVES_AREA = "area.revenant_caves"
private const val MALEDICTUS_NPC = "npc.wild_cave_superior"
private const val MALEDICTUS_INTERNAL_NAME = "wild_cave_superior"
private const val SPAWN_DENOMINATOR = 10_000
private const val SPAWN_LOCKOUT_TICKS = 4_500
private const val SPAWN_FAILSAFE_TICKS = 6_000
private const val EVENT_LIFETIME_TICKS = 1_000
private const val ENGAGED_RECHECK_TICKS = 100
private const val ANCIENT_EMBLEM_ID = 21_807
private const val ANCIENT_TOTEM_ID = 21_810

private val MALEDICTUS_SPAWN_TILES =
    listOf(CoordGrid(3197, 10070), CoordGrid(3230, 10201), CoordGrid(3209, 10216))

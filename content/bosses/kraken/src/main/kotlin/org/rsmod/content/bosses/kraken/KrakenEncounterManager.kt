package org.rsmod.content.bosses.kraken

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ProjAnimType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.combat.NpcAttackOverride
import org.rsmod.api.combat.NpcProjectile
import org.rsmod.api.combat.NvPCombat
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.types.RangedAttackType
import org.rsmod.api.instances.InstanceAccess
import org.rsmod.api.instances.InstanceId
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.instances.RaidLeaveResult
import org.rsmod.api.instances.RegionLocal
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.owner.assignSpawnOwner
import org.rsmod.api.npc.owner.isSpawnOwnedBy
import org.rsmod.api.player.combatClearQueue
import org.rsmod.api.player.hit.modifier.NpcBypassProtectionPrayerPlayerHitModifier
import org.rsmod.api.player.output.mes
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

@Singleton
internal class KrakenEncounterManager
@Inject
constructor(
    private val clock: MapClock,
    private val instances: InstanceManager,
    private val npcs: NpcRepository,
    private val interactions: AiPlayerInteractions,
    private val collision: CollisionFlagMap,
    private val combat: NvPCombat,
) {
    private val runs = mutableMapOf<PlayerUid, Run>()

    fun validateAssets() {
        listOf(
                KrakenAssets.BOSS,
                KrakenAssets.BOSS_WHIRLPOOL,
                KrakenAssets.TENTACLE,
                KrakenAssets.TENTACLE_WHIRLPOOL,
            )
            .forEach(::requireNpc)
        listOf(KrakenAssets.PUBLIC_CREVICE, KrakenAssets.INSIDE_CREVICE).forEach { id ->
            requireNotNull(ServerCacheManager.getObject(id)) { "Missing Kraken location: $id" }
        }
        projectileType(KrakenAssets.MAGIC_PROJECTILE_CONTROLLER)
        listOf(
                KrakenAssets.BOSS_PROJECTILE,
                KrakenAssets.BOSS_IMPACT,
                KrakenAssets.TENTACLE_PROJECTILE,
                KrakenAssets.TENTACLE_IMPACT,
            )
            .forEach { it.asRSCM(RSCMType.SPOTANIM) }
    }

    fun enter(player: Player): Boolean {
        if (player.hitpoints <= 0 || player.isAccessProtected) {
            player.mes("Finish what you are doing before entering the Kraken's cove.")
            return false
        }
        if (runs.containsKey(player.uid) || instances.sessionForPlayer(player) != null) {
            player.mes("You already have an active instance.")
            return false
        }
        val activeType = requireNpc(KrakenAssets.BOSS)
        val result =
            instances.create(
                owner = player,
                key = KRAKEN_INSTANCE_KEY,
                spec = krakenSpec(activeType),
                access = InstanceAccess.Private,
                currentTick = clock.cycle,
            )
        if (result !is InstanceManager.Result.Created) {
            player.mes(
                (result as? InstanceManager.Result.Failed)?.reason
                    ?: "The Kraken cove is unavailable."
            )
            return false
        }

        prepare(player)
        PathingEntityCommon.telejump(player, collision, result.enter)
        instances.finalizeEntry(player, result.session, clock.cycle)
        val boss = spawnDormant(player, result.session, KrakenAssets.BOSS_WHIRLPOOL, 38, 50)
        val tentacles =
            KRAKEN_TENTACLE_SPAWNS.map { (localX, localZ) ->
                    spawnDormant(
                        player,
                        result.session,
                        KrakenAssets.TENTACLE_WHIRLPOOL,
                        localX,
                        localZ,
                    )
                }
                .toMutableList()
        runs[player.uid] = Run(player, result.session, boss, tentacles)
        player.mes("The water is still. Disturb the large whirlpool to awaken the Kraken.")
        return true
    }

    fun disturb(player: Player, npc: Npc): Boolean {
        val run = runs[player.uid] ?: return false
        if (!run.contains(npc) || !npc.isSpawnOwnedBy(player)) return false
        return when {
            npc === run.boss && KrakenRules.canAwaken(run.phase) -> {
                activateNpc(run.boss, KrakenAssets.BOSS)
                run.tentacles.filter(Npc::isSlotAssigned).forEach {
                    if (it.visType.internalName == KrakenAssets.TENTACLE_WHIRLPOOL) {
                        activateNpc(it, KrakenAssets.TENTACLE)
                    }
                }
                run.phase = KrakenPhase.ACTIVE
                keepAggressive(run)
                player.mes("The Kraken and its tentacles rise from the water!")
                true
            }
            npc in run.tentacles && npc.visType.internalName == KrakenAssets.TENTACLE_WHIRLPOOL -> {
                activateNpc(npc, KrakenAssets.TENTACLE)
                keepAggressive(run)
                player.mes("An enormous tentacle rises from the water.")
                true
            }
            else -> false
        }
    }

    fun attack(access: StandardNpcAccess, target: Player) {
        val run = runs[target.uid] ?: return
        val npc = access.npc
        if (!run.contains(npc) || !npc.isSpawnOwnedBy(target)) return
        when {
            npc === run.boss && run.phase == KrakenPhase.ACTIVE ->
                magicAttack(
                    access,
                    target,
                    KrakenRules.BOSS_MAX_HIT,
                    KrakenAssets.BOSS_PROJECTILE,
                    KrakenAssets.BOSS_IMPACT,
                    boss = true,
                )
            npc in run.tentacles && npc.visType.internalName == KrakenAssets.TENTACLE ->
                magicAttack(
                    access,
                    target,
                    KrakenRules.TENTACLE_MAX_HIT,
                    KrakenAssets.TENTACLE_PROJECTILE,
                    KrakenAssets.TENTACLE_IMPACT,
                    boss = false,
                )
        }
    }

    private fun magicAttack(
        access: StandardNpcAccess,
        target: Player,
        maxHit: Int,
        projectile: String,
        impact: String,
        boss: Boolean,
    ) {
        combat.attack(
            access,
            target,
            if (boss) CombatAttack.NpcMagic(maxHit)
            else CombatAttack.NpcRanged(RangedAttackType.Standard),
            NpcAttackOverride(
                maxHit = maxHit,
                projectile =
                    NpcProjectile(
                        projectile,
                        projectileType(KrakenAssets.MAGIC_PROJECTILE_CONTROLLER),
                    ),
                attackRate = KrakenRules.ATTACK_RATE_TICKS,
                hitModifier = NpcBypassProtectionPrayerPlayerHitModifier,
                onImpact = { _, _ -> target.spotanim(impact) },
            ),
        )
    }

    fun beginBossDeath(npc: Npc): CoordGrid? {
        val run = runs.values.firstOrNull { it.boss === npc } ?: return null
        if (run.phase != KrakenPhase.ACTIVE) return null
        run.phase = KrakenPhase.COMPLETE
        run.tentacles.toList().forEach { tentacle ->
            tentacle.clearInteraction()
            if (tentacle.isSlotAssigned) npcs.del(tentacle, Int.MAX_VALUE)
        }
        run.tentacles.clear()
        return run.player.coords
    }

    fun finishBossDeath(npc: Npc) {
        val run = runs.values.firstOrNull { it.boss === npc } ?: return
        run.player.mes("The Kraken sinks beneath the water. Its loot washes up at your feet.")
    }

    fun finishTentacleDeath(npc: Npc) {
        runs.values.firstOrNull { npc in it.tentacles }?.tentacles?.remove(npc)
    }

    fun tickAll() {
        runs.values.toList().forEach { run ->
            val actual = instances.sessionForPlayer(run.player)?.id?.value
            if (KrakenRules.shouldDiscardRun(run.session.id.value, actual)) {
                membershipEnded(run.player, run.session.id)
            } else {
                keepAggressive(run)
            }
        }
    }

    fun isEncounterNpc(npc: Npc): Boolean = runs.values.any { it.contains(npc) }

    fun isParticipant(player: Player, npc: Npc): Boolean =
        runs[player.uid]?.let { it.contains(npc) && npc.isSpawnOwnedBy(player) } == true

    fun attackDenial(player: Player, npc: Npc): String? {
        val run = runs[player.uid] ?: return "You do not have an active Kraken encounter."
        if (!run.contains(npc)) return "That creature belongs to another encounter."
        if (npc === run.boss && run.phase != KrakenPhase.ACTIVE) {
            return "Disturb the whirlpool before attacking the Kraken."
        }
        if (run.phase == KrakenPhase.COMPLETE || run.phase == KrakenPhase.INVALIDATED) {
            return "The encounter is over."
        }
        return null
    }

    fun leave(player: Player): Boolean {
        val run = runs.remove(player.uid) ?: return false
        run.phase = KrakenPhase.INVALIDATED
        clearInteractions(run)
        val exit = instances.leave(player, run.session, clock.cycle)
        if (player.isSlotAssigned) PathingEntityCommon.telejump(player, collision, exit)
        prepare(player)
        player.mes("You leave the Kraken's private cove.")
        return true
    }

    fun leaveFromLogoutTab(player: Player, expected: InstanceId): RaidLeaveResult {
        val run = runs[player.uid] ?: return RaidLeaveResult.Unhandled
        if (run.session.id != expected) return RaidLeaveResult.Unhandled
        return if (leave(player)) RaidLeaveResult.Left else RaidLeaveResult.Failed
    }

    fun membershipEnded(player: Player, instanceId: InstanceId) {
        val run = runs[player.uid] ?: return
        if (run.session.id != instanceId) return
        runs.remove(player.uid)
        run.phase = KrakenPhase.INVALIDATED
        clearInteractions(run)
    }

    fun externalTeleport(player: Player, instanceId: InstanceId) {
        membershipEnded(player, instanceId)
    }

    fun logout(player: Player) {
        val run = runs[player.uid] ?: return
        if (instances.sessionForPlayer(player)?.id == run.session.id) {
            instances.handleLogout(player, clock.cycle)
        } else {
            membershipEnded(player, run.session.id)
        }
    }

    private fun keepAggressive(run: Run) {
        if (run.phase == KrakenPhase.ACTIVE) engage(run.boss, run.player)
        run.tentacles
            .filter { it.visType.internalName == KrakenAssets.TENTACLE }
            .forEach { engage(it, run.player) }
    }

    private fun engage(npc: Npc, player: Player) {
        if (npc.isSlotAssigned && npc.hitpoints > 0 && npc.mode == null) {
            npc.apPlayer2(player, interactions)
        }
    }

    private fun activateNpc(npc: Npc, internal: String) {
        val type = requireNpc(internal)
        npc.transmog(type, Int.MAX_VALUE)
        npc.copyStats(type)
        npc.movementLocked = true
        npc.apRangeOverride = 10
        npc.maxOpRangeOverride = 64
        npc.apRequiresLineOfSight = true
        npc.actionDelay = clock.cycle + 1
    }

    private fun spawnDormant(
        player: Player,
        session: InstanceSession,
        internal: String,
        localX: Int,
        localZ: Int,
    ): Npc {
        val npc = Npc(requireNpc(internal), requireLocal(session, localX, localZ))
        npc.mode = null
        npc.respawns = false
        npc.movementLocked = true
        npc.apRangeOverride = 0
        npc.assignSpawnOwner(player, clock.cycle)
        npcs.add(npc, Int.MAX_VALUE)
        instances.attachNpc(session.id, npc)
        return npc
    }

    private fun requireLocal(session: InstanceSession, localX: Int, localZ: Int): CoordGrid =
        requireNotNull(
            instances.localCoord(
                session,
                RegionLocal(0, KRAKEN_SOURCE_MAP_X, KRAKEN_SOURCE_MAP_Z, localX, localZ),
            )
        )

    private fun projectileType(internal: String): ProjAnimType =
        requireNotNull(ServerCacheManager.getProjectile(internal.asRSCM(RSCMType.PROJANIM))) {
            "Missing Kraken projectile controller: $internal"
        }

    private fun requireNpc(internal: String) =
        requireNotNull(ServerCacheManager.getNpc(internal.asRSCM(RSCMType.NPC))) {
            "Missing Kraken NPC definition: $internal"
        }

    private fun clearInteractions(run: Run) {
        run.boss.clearInteraction()
        run.tentacles.forEach(Npc::clearInteraction)
    }

    private fun prepare(player: Player) {
        player.combatClearQueue()
        player.clearInteraction()
        player.resetAnim()
    }

    private data class Run(
        val player: Player,
        val session: InstanceSession,
        val boss: Npc,
        val tentacles: MutableList<Npc>,
        var phase: KrakenPhase = KrakenPhase.DORMANT,
    ) {
        fun contains(npc: Npc): Boolean = boss === npc || npc in tentacles
    }
}

package org.rsmod.content.bosses.kraken

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.instances.InstanceStateKey
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.owner.assignSpawnOwner
import org.rsmod.api.npc.owner.isSpawnOwnedBy
import org.rsmod.api.player.output.mes
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.map.CoordGrid

/**
 * Whirlpool awakening and completion only. Membership, spawning, combat and teardown are native.
 */
@Singleton
internal class KrakenWhirlpools
@Inject
constructor(
    private val clock: MapClock,
    private val instances: InstanceManager,
    private val npcs: NpcRepository,
    private val interactions: AiPlayerInteractions,
    private val players: PlayerList,
) {
    fun validateAssets() {
        listOf(
                KrakenAssets.BOSS,
                KrakenAssets.BOSS_WHIRLPOOL,
                KrakenAssets.TENTACLE,
                KrakenAssets.TENTACLE_WHIRLPOOL,
            )
            .forEach(::requireKrakenNpc)
        listOf(KrakenAssets.PUBLIC_CREVICE, KrakenAssets.INSIDE_CREVICE).forEach {
            requireNotNull(ServerCacheManager.getObject(it))
        }
        requireNotNull(
            ServerCacheManager.getProjectile(
                KrakenAssets.MAGIC_PROJECTILE_CONTROLLER.asRSCM(RSCMType.PROJANIM)
            )
        )
        listOf(
                KrakenAssets.BOSS_PROJECTILE,
                KrakenAssets.BOSS_IMPACT,
                KrakenAssets.TENTACLE_PROJECTILE,
                KrakenAssets.TENTACLE_IMPACT,
            )
            .forEach { it.asRSCM(RSCMType.SPOTANIM) }
    }

    fun onJoin(player: Player, session: InstanceSession) {
        if (session.stateOrNull(STATE) != null) return
        session.state(STATE) { Awakening() }
        // InstanceManager has already created and indexed the declarative area NPCs.
        for (npc in instances.npcsForInstance(session.id)) {
            npc.mode = null
            npc.respawns = false
            npc.movementLocked = true
            npc.apRangeOverride = 0
            npc.assignSpawnOwner(player, clock.cycle)
        }
        player.mes("The water is still. Disturb the large whirlpool to awaken the Kraken.")
    }

    fun disturb(player: Player, npc: Npc): Boolean {
        val session = session(player) ?: return false
        if (instances.instanceForNpc(npc) != session.id || !npc.isSpawnOwnedBy(player)) return false
        val state = session.stateOrNull(STATE) ?: return false
        return when {
            npc.visType.internalName == KrakenAssets.BOSS_WHIRLPOOL &&
                KrakenRules.canAwaken(state.phase) -> {
                activate(npc, KrakenAssets.BOSS, player)
                instances
                    .npcsForInstance(session.id)
                    .filter { it.visType.internalName == KrakenAssets.TENTACLE_WHIRLPOOL }
                    .forEach { activate(it, KrakenAssets.TENTACLE, player) }
                state.phase = KrakenPhase.ACTIVE
                player.mes("The Kraken and its tentacles rise from the water!")
                true
            }
            npc.visType.internalName == KrakenAssets.TENTACLE_WHIRLPOOL &&
                state.phase != KrakenPhase.COMPLETE -> {
                activate(npc, KrakenAssets.TENTACLE, player)
                player.mes("An enormous tentacle rises from the water.")
                true
            }
            else -> false
        }
    }

    fun owner(npc: Npc): Player? = npc.spawnOwner.resolve(players)

    fun beginBossDeath(npc: Npc): CoordGrid? {
        val session = session(npc) ?: return null
        val state = session.stateOrNull(STATE) ?: return null
        if (state.phase != KrakenPhase.ACTIVE) return null
        state.phase = KrakenPhase.COMPLETE
        instances
            .npcsForInstance(session.id)
            .filter { it !== npc }
            .forEach { tentacle ->
                tentacle.clearInteraction()
                if (tentacle.isSlotAssigned) npcs.del(tentacle, Int.MAX_VALUE)
            }
        return owner(npc)?.coords
    }

    fun isEncounterNpc(npc: Npc): Boolean = session(npc) != null

    fun isParticipant(player: Player, npc: Npc): Boolean =
        session(player)?.id == instances.instanceForNpc(npc) && npc.isSpawnOwnedBy(player)

    fun attackDenial(player: Player, npc: Npc): String? {
        val session = session(player) ?: return "You do not have an active Kraken encounter."
        if (instances.instanceForNpc(npc) != session.id)
            return "That creature belongs to another encounter."
        val state =
            session.stateOrNull(STATE)
                ?: return "Disturb the whirlpool before attacking the Kraken."
        if (
            npc.visType.internalName == KrakenAssets.BOSS_WHIRLPOOL ||
                (npc.visType.internalName == KrakenAssets.BOSS && state.phase != KrakenPhase.ACTIVE)
        ) {
            return "Disturb the whirlpool before attacking the Kraken."
        }
        if (state.phase == KrakenPhase.COMPLETE) return "The encounter is over."
        return null
    }

    private fun activate(npc: Npc, internal: String, player: Player) {
        val type = requireKrakenNpc(internal)
        npc.transmog(type, Int.MAX_VALUE)
        npc.copyStats(type)
        npc.movementLocked = true
        npc.apRangeOverride = 10
        npc.maxOpRangeOverride = 64
        npc.apRequiresLineOfSight = true
        npc.actionDelay = clock.cycle + 1
        // The native NPC interaction repeats until cancelled; no encounter polling loop.
        npc.apPlayer2(player, interactions)
    }

    private fun session(player: Player): InstanceSession? =
        instances.sessionForPlayer(player)?.takeIf { it.key == KRAKEN_INSTANCE_KEY }

    private fun session(npc: Npc): InstanceSession? =
        instances.instanceForNpc(npc)?.let(instances::sessionForId)?.takeIf {
            it.key == KRAKEN_INSTANCE_KEY
        }

    private class Awakening(var phase: KrakenPhase = KrakenPhase.DORMANT)

    private companion object {
        val STATE = InstanceStateKey<Awakening>("kraken-whirlpools")
    }
}

internal fun requireKrakenNpc(internal: String) =
    requireNotNull(ServerCacheManager.getNpc(internal.asRSCM(RSCMType.NPC))) {
        "Missing Kraken NPC definition: $internal"
    }

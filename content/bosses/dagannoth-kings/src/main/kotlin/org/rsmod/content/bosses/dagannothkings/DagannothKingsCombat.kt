package org.rsmod.content.bosses.dagannothkings

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcMode
import dev.openrune.types.NpcServerType
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import java.util.WeakHashMap
import org.rsmod.annotations.InternalApi
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.combat.NpcAttackOverride
import org.rsmod.api.combat.NpcProjectile
import org.rsmod.api.combat.NvPCombat
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.CombatEffects
import org.rsmod.api.combat.commons.types.RangedAttackType
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.lefthand
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onAiApPlayer2
import org.rsmod.api.script.onAiTimer
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onNpcQueue
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.hit.HitType
import org.rsmod.game.hit.HitValidity
import org.rsmod.game.inv.isType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Combat and lifecycle registration for the revision-240 public Dagannoth Kings lairs. */
internal class DagannothKingsCombat
@Inject
constructor(
    private val deps: BossDeps,
    private val combat: NvPCombat,
    private val accuracy: AccuracyFormulae,
    private val players: PlayerList,
    private val random: GameRandom,
    private val death: NpcDeath,
    private val world: WorldRepository,
) : PluginScript() {
    private val submergedTicks = WeakHashMap<Npc, Int>()
    private lateinit var spinolypCycles: Map<Int, SpinolypCycle>
    private lateinit var movementProfiles: Map<Int, DagannothKingsMovementProfile>

    override fun ScriptContext.startup() {
        val typesByInternal = DagannothKingsAssets.allNpcs.associateWith(::requireNpc)
        val kingTypes = DagannothKingsAssets.kings.map(typesByInternal::getValue)
        val activeSpinolyps = DagannothKingsAssets.activeSpinolyps.map(typesByInternal::getValue)
        val suspiciousWater = DagannothKingsAssets.suspiciousWater.map(typesByInternal::getValue)

        require(activeSpinolyps.size == suspiciousWater.size)
        val pairs = activeSpinolyps.zip(suspiciousWater)
        movementProfiles =
            typesByInternal
                .map { (internal, type) ->
                    val profile = DagannothKingsRules.movementProfile(internal)
                    type.wanderRange = profile.wanderRange
                    type.attackRange = profile.attackRange
                    type.huntRange = profile.huntRange
                    type.maxRange = profile.maxRange
                    type.huntMode = profile.huntMode
                    type.id to profile
                }
                .toMap()
        spinolypCycles = buildMap {
            pairs.forEach { (active, water) ->
                val cycle = SpinolypCycle(active, water)
                put(active.id, cycle)
                put(water.id, cycle)
            }
        }

        // Map-spawned permanent NPCs use the definition's respawn rate in the generic death flow.
        kingTypes.forEach { it.respawnRate = DagannothKingsRules.RESPAWN_TICKS }

        deps.extensionRegistry.registerAttackParameters(DKS_PRIME_PARAMETERS) { npc, target, _ ->
            primeParameters(npc, target)
        }
        for (type in DagannothKingsAssets.kings) {
            BossCombat.register(
                this,
                dagannothKingSpec(type),
                deps,
                ownerSlug = type.removePrefix("npc."),
            )
        }
        activeSpinolyps.forEach { type -> onAiApPlayer2(type) { attackSpinolyp(this, it.target) } }
        kingTypes.forEach { type ->
            onNpcQueue(type, "queue.death") {
                death.deathWithDrops(
                    this,
                    deathAnimationId =
                        DagannothKingsAssets.KING_DEATH_ANIMATION.asRSCM(RSCMType.SEQ),
                )
            }
        }
        activeSpinolyps.plus(suspiciousWater).forEach { type ->
            onNpcQueue(type, "queue.death") {
                death.deathWithDrops(
                    this,
                    deathAnimationId =
                        DagannothKingsAssets.SPINOLYP_DEATH_ANIMATION.asRSCM(RSCMType.SEQ),
                )
            }
        }

        pairs.forEach { (active, water) ->
            onAiTimer(active) { submerge(npc, water) }
            onAiTimer(water) { tickSuspiciousWater(npc, active) }
        }
        onEvent<NpcStateEvents.Create> { initializeNpc(npc) }
        onEvent<NpcStateEvents.Respawn> { initializeNpc(npc) }
        onEvent<NpcStateEvents.Delete> { submergedTicks.remove(npc) }
    }

    private fun primeParameters(npc: Npc, target: Player): NpcAttackOverride {
        val center = target.coords
        val splashTargets =
            players.filter { other ->
                other !== target &&
                    other.isSlotAssigned &&
                    other.hitpoints > 0 &&
                    other.coords.level == center.level &&
                    DagannothKingsRules.isPrimeSplash(
                        center.x,
                        center.z,
                        other.coords.x,
                        other.coords.z,
                    )
            }
        val encounter = deps.encounterRegistry.of(npc)
        return NpcAttackOverride(
            onAttack = {
                splashTargets.forEach { other ->
                    val uid = other.uid
                    val accurate = accuracy.rollMagicAccuracy(npc, other, random)
                    val damage =
                        if (accurate) random.of(DagannothKingsRules.PRIME_MAX_HIT + 1) else 0
                    other.queueHit(
                        source = npc,
                        delay = PRIME_SPLASH_DELAY,
                        type = HitType.Magic,
                        damage = damage,
                        validity =
                            HitValidity {
                                deps.encounterRegistry.isAttached(npc, encounter) &&
                                    uid.resolve(players) === other
                            },
                    )
                }
            }
        )
    }

    private fun attackSpinolyp(access: StandardNpcAccess, target: Player) {
        when (DagannothKingsRules.spinolypStyle(access.random.of(2))) {
            SpinolypStyle.RangedPoison ->
                combat.attack(
                    access,
                    target,
                    CombatAttack.NpcRanged(RangedAttackType.Standard),
                    NpcAttackOverride(
                        attackAnimation = DagannothKingsAssets.SPINOLYP_ATTACK_ANIMATION,
                        maxHit = DagannothKingsRules.SPINOLYP_MAX_HIT,
                        attackRate = DagannothKingsRules.SPINOLYP_ATTACK_RATE_TICKS,
                        projectile =
                            NpcProjectile(
                                DagannothKingsAssets.SPINOLYP_PROJECTILE,
                                DagannothKingsAssets.spinolypProjectileType,
                            ),
                        onImpact = { _, impact ->
                            if (impact.damage > 0) {
                                CombatEffects.poison(
                                    target,
                                    DagannothKingsRules.SPINOLYP_POISON_DAMAGE,
                                )
                            }
                        },
                    ),
                )

            SpinolypStyle.MagicPrayerDrain -> {
                // Mod Ash confirmed both Spinolyp attacks roll Ranged accuracy/defence. This hit
                // remains Magic-classified so Protect from Magic and its magic hitsplat are used.
                val rangedAccurate = accuracy.rollRangedAccuracy(access.npc, target, access.random)
                combat.attack(
                    access,
                    target,
                    CombatAttack.NpcMagic(DagannothKingsRules.SPINOLYP_MAX_HIT),
                    NpcAttackOverride(
                        attackAnimation = DagannothKingsAssets.SPINOLYP_ATTACK_ANIMATION,
                        maxHit = if (rangedAccurate) DagannothKingsRules.SPINOLYP_MAX_HIT else 0,
                        forceAccurate = true,
                        attackRate = DagannothKingsRules.SPINOLYP_ATTACK_RATE_TICKS,
                        projectile =
                            NpcProjectile(
                                DagannothKingsAssets.SPINOLYP_PROJECTILE,
                                DagannothKingsAssets.spinolypProjectileType,
                            ),
                        onImpact = { _, _ ->
                            if (rangedAccurate) {
                                val drain =
                                    DagannothKingsRules.prayerDrain(
                                        hasSpectral =
                                            target.lefthand.isType(
                                                DagannothKingsAssets.SPECTRAL_SHIELD
                                            ),
                                        spectralRound = access.random.of(2),
                                    )
                                if (drain > 0) {
                                    target.statSub("stat.prayer", constant = drain, percent = 0)
                                }
                            }
                        },
                    ),
                )
            }

            SpinolypStyle.SuspiciousWaterMelee -> error("Only submerged forms use melee")
        }
    }

    private fun initializeNpc(npc: Npc) {
        val profile = movementProfiles[npc.type.id] ?: return
        val hunt =
            requireNotNull(ServerCacheManager.getHunt(profile.huntMode)) {
                "Missing Dagannoth Kings hunt mode ${profile.huntMode}"
            }
        npc.setHuntMode(hunt)
        if (npc.type.id in spinolypCycles) {
            initializeSpinolyp(npc, profile)
        } else {
            npc.setHunt(profile.huntRange)
        }
    }

    private fun initializeSpinolyp(npc: Npc, profile: DagannothKingsMovementProfile) {
        val cycle = spinolypCycles[npc.type.id] ?: return
        submergedTicks.remove(npc)
        if (npc.visType.id == cycle.active.id) {
            npc.setHunt(profile.huntRange)
            npc.aiTimer(DagannothKingsRules.initialActiveTicks(npc.coords.x, npc.coords.z))
        } else {
            npc.setHunt(0)
            npc.mode = NpcMode.Wander
            submergedTicks[npc] =
                DagannothKingsRules.initialResurfaceCheckTicks(npc.coords.x, npc.coords.z)
            npc.aiTimer(DagannothKingsRules.SUSPICIOUS_WATER_ATTACK_RATE_TICKS)
        }
    }

    private fun submerge(npc: Npc, water: NpcServerType) {
        submergedTicks[npc] = DagannothKingsRules.SPINOLYP_RESURFACE_CHECK_TICKS
        changeSpinolypType(npc, water, aggressive = false)
        npc.aiTimer(DagannothKingsRules.SUSPICIOUS_WATER_ATTACK_RATE_TICKS)
    }

    private fun tickSuspiciousWater(npc: Npc, active: NpcServerType) {
        attackAdjacentFromWater(npc)
        val remaining =
            (submergedTicks[npc] ?: DagannothKingsRules.SPINOLYP_RESURFACE_CHECK_TICKS) -
                DagannothKingsRules.SUSPICIOUS_WATER_ATTACK_RATE_TICKS
        if (remaining <= 0) {
            if (
                DagannothKingsRules.shouldResurface(
                    random.of(DagannothKingsRules.SPINOLYP_RESURFACE_ROLL_BOUND)
                )
            ) {
                submergedTicks.remove(npc)
                world.spotanimMap(
                    SpotanimType(
                        DagannothKingsAssets.SPINOLYP_SURFACE_SPOTANIM.asRSCM(RSCMType.SPOTANIM)
                    ),
                    npc.coords,
                    delay = SPINOLYP_SURFACE_CLIENT_DELAY,
                )
                changeSpinolypType(npc, active, aggressive = true)
                npc.aiTimer(DagannothKingsRules.SPINOLYP_ACTIVE_TICKS)
            } else {
                submergedTicks[npc] = DagannothKingsRules.SPINOLYP_RESURFACE_CHECK_TICKS
            }
        } else {
            submergedTicks[npc] = remaining
        }
    }

    private fun attackAdjacentFromWater(npc: Npc) {
        val target =
            players.firstOrNull { player ->
                player.isSlotAssigned &&
                    player.hitpoints > 0 &&
                    player.coords.level == npc.coords.level &&
                    npc.coords.chebyshevDistance(player.coords) == 1
            } ?: return
        val accurate = accuracy.rollMeleeAccuracy(npc, target, null, random)
        val damage = if (accurate) random.of(DagannothKingsRules.SPINOLYP_MAX_HIT + 1) else 0
        target.queueHit(source = npc, delay = 1, type = HitType.Melee, damage = damage)
    }

    @OptIn(InternalApi::class)
    private fun changeSpinolypType(npc: Npc, type: NpcServerType, aggressive: Boolean) {
        val profile =
            requireNotNull(movementProfiles[npc.type.id]) {
                "Missing Dagannoth Kings movement profile for NPC ${npc.type.id}"
            }
        npc.resetMode()
        npc.transmog(type, Int.MAX_VALUE)
        npc.assignUid()
        npc.setHunt(if (aggressive) profile.huntRange else 0)
        // Suspicious water still roams around the perimeter and performs only its explicit
        // adjacent splash attack; setting hunt to zero prevents it from pathing toward players.
        npc.mode = NpcMode.Wander
    }

    private fun requireNpc(internal: String): NpcServerType =
        requireNotNull(ServerCacheManager.getNpc(internal.asRSCM(RSCMType.NPC))) {
            "Missing Dagannoth Kings revision-240 NPC definition: $internal"
        }

    private data class SpinolypCycle(val active: NpcServerType, val suspiciousWater: NpcServerType)

    private companion object {
        const val PRIME_SPLASH_DELAY = 1
        const val SPINOLYP_SURFACE_CLIENT_DELAY = 3
    }
}

package org.rsmod.content.skills.magic.spell.attacks.arceuus

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.combat.commons.CombatCorruption
import org.rsmod.api.combat.commons.magic.ArceuusSpellState
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.hit.modifier.NpcHitModifier
import org.rsmod.api.npc.hit.queueHit
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.statAdvance
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.specials.energy.SpecialAttackEnergy
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcUid
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.hit.HitAttribution
import org.rsmod.game.hit.HitContext
import org.rsmod.game.hit.HitOrigin
import org.rsmod.game.hit.HitScope
import org.rsmod.game.hit.HitType
import org.rsmod.game.hit.HitValidity
import org.rsmod.game.proj.ProjAnim

/** Authoritative runtime state for timed Arceuus buffs, procs, and a player's active thrall. */
@Singleton
internal class ArceuusUtilityState
@Inject
constructor(
    private val clock: MapClock,
    private val corruption: CombatCorruption,
    private val specials: SpecialAttackEnergy,
    private val random: GameRandom,
    private val npcs: NpcRepository,
    private val world: WorldRepository,
    private val npcHitModifier: NpcHitModifier,
    private val interactions: AiPlayerInteractions,
) {
    private val cooldowns = mutableMapOf<Pair<PlayerUid, ArceuusCooldown>, Int>()
    private val armedCorruption = mutableMapOf<PlayerUid, Boolean>()
    private val deathCharges = mutableMapOf<PlayerUid, DeathCharge>()
    private val lastTargets = mutableMapOf<PlayerUid, TrackedTarget>()
    private val thralls = mutableMapOf<PlayerUid, ActiveThrall>()
    private val reanimated = mutableMapOf<PlayerUid, ActiveReanimation>()

    fun onCooldown(player: Player, type: ArceuusCooldown): Boolean =
        (cooldowns[player.uid to type] ?: 0) > clock.cycle

    fun setCooldown(player: Player, type: ArceuusCooldown, ticks: Int) {
        cooldowns[player.uid to type] = clock.cycle + ticks
        VarPlayerIntMapSetter.set(player, type.varbit, 1)
    }

    fun armCorruption(player: Player, greater: Boolean) {
        armedCorruption[player.uid] = greater
        VarPlayerIntMapSetter.set(player, "varbit.arceuus_corruption", if (greater) 2 else 1)
    }

    fun armDeathCharge(player: Player) {
        val charges = if (player.vars["varbit.death_charge_scroll_used"] != 0) 2 else 1
        deathCharges[player.uid] = DeathCharge(clock.cycle + DEATH_CHARGE_DURATION, charges)
        VarPlayerIntMapSetter.set(player, "varbit.arceuus_death_charge_active", charges)
    }

    fun hasDeathCharge(player: Player): Boolean =
        (deathCharges[player.uid]?.expiresAt ?: 0) > clock.cycle

    fun onNpcDamage(player: Player, npc: Npc, damage: Int) {
        if (damage > 0 && npc.hitpoints <= 0) onNpcKill(player, npc)
        val reanimation = reanimated[player.uid]
        if (damage > 0 && reanimation?.npc === npc && npc.hitpoints <= 0) {
            if (!reanimation.prayerAwarded) {
                reanimation.prayerAwarded = true
                player.statAdvance("stat.prayer", reanimation.definition.prayerXp)
            }
            reanimated.remove(player.uid)
            return
        }
    }

    fun recordTarget(player: Player, npc: Npc, damage: Int = 0) {
        if (npc.isSlotAssigned && npc.hitpoints > 0) {
            lastTargets[player.uid] = TrackedTarget(npc, npc.uid)
        }
    }

    fun hasActiveReanimation(player: Player): Boolean {
        val active = reanimated[player.uid] ?: return false
        if (active.npc.isSlotAssigned && active.npc.hitpoints > 0) return true
        reanimated.remove(player.uid)
        return false
    }

    fun spawnReanimated(
        player: Player,
        definition: ArceuusReanimationDefinition,
        coords: org.rsmod.map.CoordGrid = player.coords.translate(1, 0),
    ) {
        val type =
            requireNotNull(ServerCacheManager.getNpc(definition.npcId)) {
                "Missing revision-240 reanimated NPC ${definition.npcId}"
            }
        val npc = Npc(type, coords)
        npc.suppressDrops = true
        npcs.add(npc, REANIMATION_LIFETIME)
        npc.apPlayer2(player, interactions)
        reanimated[player.uid] = ActiveReanimation(player, npc, definition, prayerAwarded = false)
    }

    fun validateReanimatedAttack(player: Player, npc: Npc): NpcAttackValidateResult {
        if (thralls.values.any { it.npc === npc }) {
            return NpcAttackValidateResult.Deny("You cannot attack that.")
        }
        val active =
            reanimated.values.firstOrNull { it.npc === npc } ?: return NpcAttackValidateResult.Pass
        return if (active.owner === player) {
            NpcAttackValidateResult.Pass
        } else {
            NpcAttackValidateResult.Deny("You cannot attack that creature.")
        }
    }

    fun tryApplyCorruption(attacker: Player, target: Player, damage: Int) {
        if (damage <= 0) return
        val greater = armedCorruption[attacker.uid] ?: return
        if (ArceuusSpellState.hasWard(target, clock.cycle)) return
        armedCorruption.remove(attacker.uid)
        VarPlayerIntMapSetter.set(attacker, "varbit.arceuus_corruption", 0)
        if (!random.randomBoolean(CORRUPTION_PROC_DENOMINATOR)) return
        val applied =
            corruption.apply(
                target,
                strong = greater,
                accelerated = ArceuusSpellState.isMarked(attacker, clock.cycle),
            )
        if (applied) {
            attacker.mes("Your target has been corrupted!")
        }
    }

    fun onNpcKill(player: Player, npc: Npc? = null) {
        val charge = deathCharges[player.uid] ?: return
        if (charge.expiresAt <= clock.cycle) {
            deathCharges.remove(player.uid)
            VarPlayerIntMapSetter.set(player, "varbit.arceuus_death_charge_active", 0)
            return
        }
        if (
            npc != null &&
                (npc.coords.level != player.coords.level ||
                    player.distanceTo(npc) > 16 ||
                    !deathChargeEligible(npc))
        )
            return
        val lastPvp = player.vars["varp.lastcombat_pvp"]
        if (lastPvp > 0 && clock.cycle - lastPvp <= 10) return
        charge.remaining--
        if (charge.remaining == 0) deathCharges.remove(player.uid)
        VarPlayerIntMapSetter.set(player, "varbit.arceuus_death_charge_active", charge.remaining)
        specials.restoreSpecialEnergy(player, DEATH_CHARGE_RESTORE)
        player.spotanim(if (player.vars["varbit.death_charge_scroll_used"] != 0) 3289 else 1855)
        player.soundSynth(RSCM.getReverseMapping(RSCMType.SYNTH, 5055))
        player.mes("<col=a53fff>Some of your special attack energy has been restored.</col>")
    }

    private fun deathChargeEligible(npc: Npc): Boolean =
        npc.id !in DEATH_CHARGE_EXCLUDED_IDS &&
            npc.visType.name.lowercase() !in DEATH_CHARGE_EXCLUDED_NAMES

    fun summon(player: Player, definition: ArceuusThrallDefinition, duration: Int) {
        removeThrall(player, announce = false)
        val type =
            requireNotNull(ServerCacheManager.getNpc(definition.npcId)) {
                "Missing revision-240 thrall NPC ${definition.npcId}"
            }
        val npc = Npc(type, player.coords.translate(1, 0))
        npc.mode = NpcMode.None
        npc.ignoreCombatInteractions = true
        npc.setHunt(0)
        npc.suppressRewards = true
        // Player-owned RSProx 3153: raise at +5, NPC removed at Magic+3, buff at Magic+4.
        val lifetime = duration.coerceAtLeast(MIN_THRALL_DURATION)
        npcs.add(npc, duration = lifetime + THRALL_DESPAWN_OFFSET)
        npc.anim(definition.spawnAnimation)
        npc.spotanim(definition.spawnGraphic)
        world.soundArea(npc.coords, definition.spawnSound, radius = 12, size = npc.size)
        thralls[player.uid] =
            ActiveThrall(
                owner = player,
                npc = npc,
                definition = definition,
                startsAt = clock.cycle + THRALL_RAISE_DELAY,
                expiresAt = clock.cycle + lifetime + THRALL_DESPAWN_OFFSET + 1,
                nextAttackAt = clock.cycle + THRALL_INITIAL_ATTACK_DELAY,
                context =
                    HitContext.capture(player, npc)
                        .withAttribution(HitAttribution(HitOrigin.Thrall, player.uuid)),
            )
        VarPlayerIntMapSetter.set(
            player,
            "varbit.arceuus_resurrection_used",
            ArceuusThralls.definitions.indexOf(definition),
        )
        VarPlayerIntMapSetter.set(player, "varbit.arceuus_resurrection_active", 0)
    }

    fun tick(player: Player) {
        if (!player.isSlotAssigned || player.pendingLogout || player.hitpoints <= 0) {
            clear(player)
            return
        }
        ArceuusSpellState.tick(player, clock.cycle)
        corruption.tick(player)
        tickReanimated(player)
        cooldowns.entries.removeIf { (key, expiry) ->
            if (key.first == player.uid && expiry <= clock.cycle) {
                VarPlayerIntMapSetter.set(player, key.second.varbit, 0)
                true
            } else false
        }

        val chargeExpiry = deathCharges[player.uid]
        if (chargeExpiry != null && chargeExpiry.expiresAt <= clock.cycle) {
            deathCharges.remove(player.uid)
            VarPlayerIntMapSetter.set(player, "varbit.arceuus_death_charge_active", 0)
            player.mes("<col=a53fff>Your Death Charge has faded away.</col>")
            player.soundSynth(RSCM.getReverseMapping(RSCMType.SYNTH, 5056))
        }

        val active = thralls[player.uid] ?: return
        if (
            !active.context.isValid() ||
                active.npc.coords.level != player.coords.level ||
                active.npc.distanceTo(player) > THRALL_TARGET_LEASH
        ) {
            lastTargets.remove(player.uid)
            removeThrall(player, announce = false)
            return
        }
        if (!player.isSlotAssigned || player.pendingLogout || active.expiresAt <= clock.cycle) {
            removeThrall(player, announce = player.isSlotAssigned && !player.pendingLogout)
            return
        }
        if (clock.cycle >= active.expiresAt - 1) {
            active.hitScope.invalidate()
            if (active.npc.isSlotAssigned) npcs.del(active.npc, Int.MAX_VALUE)
            return
        }
        if (!active.npc.isSlotAssigned || active.npc.hitpoints <= 0) {
            active.hitScope.invalidate()
            thralls.remove(player.uid)
            VarPlayerIntMapSetter.set(player, "varbit.arceuus_resurrection_active", 0)
            return
        }
        if (clock.cycle < active.startsAt - 1) return
        if (!active.raised && clock.cycle >= active.startsAt) {
            active.raised = true
            VarPlayerIntMapSetter.set(player, "varbit.arceuus_resurrection_active", 1)
            player.mes("<col=ff289d>You resurrect a ${active.definition.displayName}.</col>")
        }
        tickThrall(active)
    }

    fun clear(player: Player) {
        ArceuusCooldown.entries.forEach { VarPlayerIntMapSetter.set(player, it.varbit, 0) }
        VarPlayerIntMapSetter.set(player, "varbit.arceuus_corruption", 0)
        VarPlayerIntMapSetter.set(player, "varbit.arceuus_death_charge_active", 0)
        VarPlayerIntMapSetter.set(player, "varbit.arceuus_resurrection_active", 0)
        cooldowns.keys.removeIf { it.first == player.uid }
        armedCorruption.remove(player.uid)
        deathCharges.remove(player.uid)
        lastTargets.remove(player.uid)
        corruption.clear(player)
        ArceuusSpellState.clear(player)
        removeThrall(player, announce = false)
        removeReanimated(player)
    }

    private fun tickReanimated(player: Player) {
        val active = reanimated[player.uid] ?: return
        val npc = active.npc
        if (npc.isSlotAssigned && npc.hitpoints <= 0 && !active.prayerAwarded) {
            active.prayerAwarded = true
            player.statAdvance("stat.prayer", active.definition.prayerXp)
            reanimated.remove(player.uid)
            return
        }
        if (!npc.isSlotAssigned) {
            reanimated.remove(player.uid)
            return
        }
        if (
            player.pendingLogout ||
                player.coords.level != npc.coords.level ||
                player.distanceTo(npc) > 25
        ) {
            removeReanimated(player)
        }
    }

    private fun removeReanimated(player: Player) {
        val active = reanimated.remove(player.uid) ?: return
        if (active.npc.isSlotAssigned) npcs.del(active.npc, Int.MAX_VALUE)
    }

    private fun tickThrall(active: ActiveThrall) {
        val npc = active.npc
        val owner = active.owner
        val tracked = lastTargets[owner.uid]
        val target = tracked?.npc?.takeIf { it.uid == tracked.uid }
        if (target == null || !target.isSlotAssigned || target.hitpoints <= 0) {
            if (npc.distanceTo(owner) > THRALL_FOLLOW_DISTANCE) {
                npc.walk(owner.coords)
            } else {
                npc.abortRoute()
            }
            return
        }
        if (
            owner.distanceTo(target) > THRALL_TARGET_LEASH ||
                owner.coords.level != target.coords.level
        ) {
            lastTargets.remove(owner.uid)
            npc.routeDestination.clear()
            return
        }

        npc.faceSquare(target.coords)
        if (npc.distanceTo(target) > active.definition.attackRange) {
            npc.walk(target.coords)
            return
        }
        npc.routeDestination.clear()
        if (clock.cycle < active.nextAttackAt) return
        active.nextAttackAt = clock.cycle + THRALL_ATTACK_RATE

        val definition = active.definition
        npc.anim(definition.attackAnimation)
        world.soundArea(npc.coords, definition.attackSound, radius = 12, size = npc.size)
        val delay =
            definition.projectile?.let {
                val base = ProjAnim.fromBoundsToNpc(npc.bounds(), target, it, MAGIC_PROJECTILE)
                // RSProx 4037: skeleton shots at distances 1/2/3 arrive at frames 72/77/82.
                val projectile =
                    if (definition.hitType == HitType.Ranged) {
                        base.copy(
                            startHeight = 100,
                            endHeight = 124,
                            startTime = 57,
                            endTime = 67 + 5 * npc.distanceTo(target),
                            angle = 15,
                            progress = 32,
                        )
                    } else base
                world.projAnim(projectile)
                projectile.durations.serverDelay
            } ?: 1
        if (definition.hitSound != null) {
            world.soundArea(
                target.coords,
                definition.hitSound,
                delay = delay * 30,
                radius = 12,
                size = target.size,
            )
        }
        val targetUid = target.uid
        target.queueHit(
            source = owner,
            delay = delay.coerceAtLeast(1),
            type = definition.hitType,
            damage = random.of(definition.maxHit + 1),
            context =
                HitContext.compose(
                    active.context,
                    HitContext.of(active.context.attribution, active.hitScope),
                ),
            modifier =
                NpcHitModifier { victim ->
                    with(npcHitModifier) { modify(victim) }
                    val previous = validity
                    validity = HitValidity {
                        previous?.isValid() != false &&
                            thralls[owner.uid] === active &&
                            owner.isSlotAssigned &&
                            !owner.pendingLogout &&
                            owner.hitpoints > 0 &&
                            npc.isSlotAssigned &&
                            target.isSlotAssigned &&
                            target.uid == targetUid &&
                            owner.coords.level == target.coords.level &&
                            owner.coords.level == npc.coords.level &&
                            owner.distanceTo(target) <= THRALL_TARGET_LEASH &&
                            owner.distanceTo(npc) <= THRALL_TARGET_LEASH
                    }
                },
        )
    }

    private fun removeThrall(player: Player, announce: Boolean) {
        val active = thralls.remove(player.uid) ?: return
        active.hitScope.invalidate()
        VarPlayerIntMapSetter.set(player, "varbit.arceuus_resurrection_active", 0)
        if (active.npc.isSlotAssigned) {
            npcs.del(active.npc, Int.MAX_VALUE)
        }
        if (announce) {
            player.mes(
                "<col=ff289d>Your ${active.definition.displayName} returns to the grave.</col>"
            )
            player.soundSynth(RSCM.getReverseMapping(RSCMType.SYNTH, 5054))
        }
    }

    private data class ActiveThrall(
        val owner: Player,
        val npc: Npc,
        val definition: ArceuusThrallDefinition,
        val startsAt: Int,
        val expiresAt: Int,
        var nextAttackAt: Int,
        val context: HitContext,
        val hitScope: HitScope = HitScope(),
        var raised: Boolean = false,
    )

    private data class TrackedTarget(val npc: Npc, val uid: NpcUid)

    private data class DeathCharge(val expiresAt: Int, var remaining: Int)

    private data class ActiveReanimation(
        val owner: Player,
        val npc: Npc,
        val definition: ArceuusReanimationDefinition,
        var prayerAwarded: Boolean,
    )

    private companion object {
        const val CORRUPTION_PROC_DENOMINATOR = 2
        const val DEATH_CHARGE_DURATION = 100
        const val DEATH_CHARGE_RESTORE = 150
        val DEATH_CHARGE_EXCLUDED_IDS = setOf(5914, 5915, 7550, 7552, 7553, 7555, 7556, 7557, 7568)
        val DEATH_CHARGE_EXCLUDED_NAMES =
            setOf(
                "unstable ice",
                "blood moon",
                "blue moon",
                "eclipse moon",
                "totem",
                "obelisk",
                "energy siphon",
            )
        const val MIN_THRALL_DURATION = 6
        const val THRALL_INITIAL_ATTACK_DELAY = 4
        const val THRALL_RAISE_DELAY = 5
        const val THRALL_DESPAWN_OFFSET = 3
        const val THRALL_ATTACK_RATE = 4
        const val THRALL_FOLLOW_DISTANCE = 2
        const val THRALL_TARGET_LEASH = 16
        const val MAGIC_PROJECTILE = "projanim.magic_spell"
        const val REANIMATION_LIFETIME = 500
    }
}

internal enum class ArceuusCooldown(val varbit: String) {
    Corruption("varbit.arceuus_corruption_cooldown"),
    DeathCharge("varbit.arceuus_death_charge_cooldown"),
    Offering("varbit.arceuus_offering_cooldown"),
    ShadowVeil("varbit.arceuus_shadow_veil_cooldown"),
    Thrall("varbit.arceuus_resurrection_cooldown"),
    VileVigour("varbit.arceuus_vile_vigour_cooldown"),
    Ward("varbit.arceuus_ward_cooldown"),
}

internal data class ArceuusThrallDefinition(
    val spellObj: String,
    val displayName: String,
    val npcId: Int,
    val prayerDrain: Int,
    val maxHit: Int,
    val attackRange: Int,
    val castGraphic: Int,
    val spawnAnimation: Int,
    val spawnGraphic: Int,
    val spawnSound: Int,
    val attackAnimation: Int,
    val attackSound: Int,
    val hitSound: Int?,
    val projectile: Int?,
    val hitType: HitType,
)

internal data class ArceuusReanimationDefinition(
    val tier: ArceuusReanimationTier,
    val headIds: Set<Int>,
    val npcId: Int,
    val prayerXp: Double,
)

internal enum class ArceuusReanimationTier(val spellObj: String) {
    Basic("obj.easter_helper"),
    Adept("obj.cert_chompy_bird_obj"),
    Expert("obj.evil_bob_amulet_of_manspeak"),
    Master("obj.magictraining_guardianstatue"),
}

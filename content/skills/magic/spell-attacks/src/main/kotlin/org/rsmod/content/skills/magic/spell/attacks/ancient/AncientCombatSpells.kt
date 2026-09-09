package org.rsmod.content.skills.magic.spell.attacks.ancient

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.CombatEffects
import org.rsmod.api.combat.manager.MagicRuneManager.Companion.isFailure
import org.rsmod.api.combat.player.PvPAreaAttackManager
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.mechanics.toxins.NpcPoisonEffectService
import org.rsmod.api.npc.isValidTarget
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.attackLvl
import org.rsmod.api.player.stat.baseAttackLvl
import org.rsmod.api.player.stat.statHeal
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.player.PlayerRepository
import org.rsmod.api.spells.attack.SpellAttack
import org.rsmod.api.spells.attack.SpellAttackManager
import org.rsmod.api.spells.attack.SpellAttackMap
import org.rsmod.api.spells.attack.SpellAttackRepository
import org.rsmod.content.skills.magic.spell.attacks.RoyalTitansWallSpells
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitImpact
import org.rsmod.game.hit.HitImpactHandler
import org.rsmod.game.interact.InteractionOp
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey

/** Complete revision-240 registration for the sixteen stock Ancient Magicks combat spells. */
class AncientCombatSpells
@Inject
constructor(
    private val targets: AncientAreaTargetSelector,
    private val poison: NpcPoisonEffectService,
    private val pvp: PvPAreaAttackManager,
    private val walls: RoyalTitansWallSpells,
) : SpellAttackMap {
    override fun SpellAttackRepository.register(manager: SpellAttackManager) {
        for (definition in AncientSpellRules.definitions) {
            register(definition.obj, AncientSpellAttack(manager, definition))
        }
    }

    private inner class AncientSpellAttack(
        private val manager: SpellAttackManager,
        private val definition: AncientSpellDefinition,
    ) : SpellAttack {
        override suspend fun ProtectedAccess.attack(target: Npc, attack: CombatAttack.Spell) {
            cast(target, attack)
        }

        override suspend fun ProtectedAccess.attack(target: Player, attack: CombatAttack.Spell) {
            cast(target, attack)
        }

        private fun ProtectedAccess.cast(primary: PathingEntity, attack: CombatAttack.Spell) {
            if (RoyalTitansWallSpells.isWall(primary)) {
                val wall = primary as Npc
                if (!walls.begin(this, wall, attack)) return
                player.anim(definition.castAnim, priority = CAST_ANIMATION_PRIORITY)
                val timing = projectileTiming(wall)
                walls.finish(
                    this,
                    wall,
                    attack,
                    timing.serverDelay,
                    timing.clientDelay,
                    definition.impact,
                    definition.impactHeight,
                    definition.castSound,
                    definition.hitSound,
                )
                return
            }
            val castResult = manager.attemptCast(this, attack)
            if (castResult.isFailure()) {
                return
            }

            player.anim(definition.castAnim, priority = CAST_ANIMATION_PRIORITY)
            val affected =
                if (definition.area && isMultiwayFor(primary)) {
                    targets.select(this, primary, AncientSpellRules.MAX_AREA_TARGETS)
                } else {
                    listOf(primary)
                }

            for ((index, target) in affected.withIndex()) {
                val timing = projectileTiming(target)
                val splash = manager.rollSplash(this, target, attack, castResult)
                if (splash) {
                    manager.playSplashFx(
                        source = this,
                        target = target,
                        clientDelay = timing.clientDelay,
                        castSound = definition.castSound.takeIf { index == 0 },
                        soundRadius = SOUND_RADIUS,
                    )
                    manager.queueSplashHit(
                        source = this,
                        target = target,
                        spell = attack.spell.obj,
                        clientDelay = timing.clientDelay,
                        hitDelay = timing.serverDelay,
                    )
                    registerSecondaryPvpTarget(primary, target)
                    continue
                }

                val damage =
                    manager.rollMaxHit(
                        source = this,
                        target = target,
                        attack = attack,
                        castResult = castResult,
                        baseMaxHit = definition.baseMaxHit,
                    )
                manager.playHitFx(
                    source = this,
                    target = target,
                    clientDelay = timing.clientDelay,
                    castSound = definition.castSound.takeIf { index == 0 },
                    soundRadius = SOUND_RADIUS,
                    hitSpot = definition.impact,
                    hitSpotHeight = definition.impactHeight,
                    hitSound = definition.hitSound,
                )
                manager.giveCombatXp(this, target, attack, damage)
                manager.queueMagicHit(
                    source = this,
                    target = target,
                    spell = attack.spell.obj,
                    damage = damage,
                    clientDelay = timing.clientDelay,
                    hitDelay = timing.serverDelay,
                    effectHandler = HitImpactHandler { impact -> applyElementEffect(impact) },
                )
                registerSecondaryPvpTarget(primary, target)
            }
            manager.continueCombatIfAutocast(this, primary)
        }

        private fun ProtectedAccess.projectileTiming(target: PathingEntity): ImpactTiming {
            val travel = definition.travel
            if (travel == null) {
                return ImpactTiming(DIRECT_SERVER_DELAY, DIRECT_CLIENT_DELAY)
            }
            val projectile = manager.spawnProjectile(this, target, travel, MAGIC_PROJECTILE_ANIM)
            val (serverDelay, clientDelay) = projectile.durations
            return ImpactTiming(serverDelay, clientDelay)
        }

        private fun ProtectedAccess.applyElementEffect(impact: HitImpact) {
            if (!AncientSpellRules.effectLands(impact.damage)) {
                return
            }
            when (definition.element) {
                AncientElement.Smoke -> applyPoison(impact.target, definition.effectStrength)
                AncientElement.Shadow -> applyShadowDrain(impact.target, definition.effectStrength)
                AncientElement.Blood -> {
                    val heal = AncientSpellRules.bloodHeal(impact.damage)
                    if (heal > 0) {
                        player.statHeal("stat.hitpoints", constant = heal, percent = 0)
                    }
                }
                AncientElement.Ice -> Unit // The shared spell manager owns freeze and immunity.
            }
        }

        private fun ProtectedAccess.applyPoison(target: PathingEntity, damage: Int) {
            when (target) {
                is Player -> CombatEffects.poison(target, damage)
                is Npc -> poison.apply(player, target, initialDamage = damage)
            }
        }

        private fun applyShadowDrain(target: PathingEntity, percent: Int) {
            when (target) {
                is Player -> {
                    val floor = AncientSpellRules.shadowAttackFloor(target.baseAttackLvl, percent)
                    val drain = (target.attackLvl - floor).coerceAtLeast(0)
                    if (drain > 0) {
                        target.statSub("stat.attack", constant = drain, percent = 0)
                    }
                }
                is Npc -> {
                    val floor = AncientSpellRules.shadowAttackFloor(target.baseAttackLvl, percent)
                    target.attackLvl = target.attackLvl.coerceAtMost(floor)
                }
            }
        }

        private fun ProtectedAccess.isMultiwayFor(primary: PathingEntity): Boolean =
            if (primary is Npc) mapNpcMultiway() else mapMultiway()

        private fun ProtectedAccess.registerSecondaryPvpTarget(
            primary: PathingEntity,
            target: PathingEntity,
        ) {
            if (target is Player && target !== primary) {
                pvp.applySecondaryAttack(this, target)
            }
        }
    }

    private data class ImpactTiming(val serverDelay: Int, val clientDelay: Int)

    private companion object {
        const val CAST_ANIMATION_PRIORITY: Int = 6
        const val SOUND_RADIUS: Int = 8
        const val DIRECT_SERVER_DELAY: Int = 2
        const val DIRECT_CLIENT_DELAY: Int = 30
        const val MAGIC_PROJECTILE_ANIM: String = "projanim.magic_spell"
    }
}

/** Resolves valid entities occupying the target-centred 3x3 Ancient area. */
class AncientAreaTargetSelector
@Inject
constructor(
    private val npcs: NpcRepository,
    private val players: PlayerRepository,
    private val npcAttackValidateHooks: Set<NpcAttackValidateHook>,
    private val pvp: PvPAreaAttackManager,
) {
    fun select(source: ProtectedAccess, primary: PathingEntity, limit: Int): List<PathingEntity> {
        require(limit > 0)
        val tiles = square(primary.coords)
        val selected = mutableListOf(primary)
        val zone = ZoneKey.from(primary.coords)

        for (npc in npcs.findAll(zone, zoneRadius = SEARCH_ZONE_RADIUS)) {
            if (selected.size >= limit) break
            if (npc === primary || !npc.occupies(tiles) || !canAttack(source.player, npc)) continue
            selected += npc
        }
        for (player in players.findAll(zone, zoneRadius = SEARCH_ZONE_RADIUS)) {
            if (selected.size >= limit) break
            if (
                player === source.player ||
                    player === primary ||
                    !player.occupies(tiles) ||
                    !pvp.canAttack(source.player, player)
            ) {
                continue
            }
            selected += player
        }
        return selected
    }

    private fun square(centre: CoordGrid): Set<CoordGrid> = buildSet {
        for (x in -AREA_RADIUS..AREA_RADIUS) {
            for (z in -AREA_RADIUS..AREA_RADIUS) {
                add(centre.translate(x, z))
            }
        }
    }

    private fun canAttack(source: Player, target: Npc): Boolean {
        if (!target.isValidTarget() || !target.visType.hasOp(InteractionOp.Op2.slot)) {
            return false
        }
        return npcAttackValidateHooks.all { hook ->
            hook.validate(source, target) !is NpcAttackValidateResult.Deny
        }
    }

    private fun PathingEntity.occupies(tiles: Set<CoordGrid>): Boolean =
        bounds().asSequence().any { it in tiles }

    private companion object {
        const val AREA_RADIUS: Int = 1
        const val SEARCH_ZONE_RADIUS: Int = 1
    }
}

internal enum class AncientElement {
    Smoke,
    Shadow,
    Blood,
    Ice,
}

internal data class AncientSpellDefinition(
    val obj: String,
    val element: AncientElement,
    val baseMaxHit: Int,
    val effectStrength: Int,
    val area: Boolean,
    val castAnim: String,
    val travel: String?,
    val impact: String,
    val impactHeight: Int,
    val castSound: String,
    val hitSound: String,
)

internal object AncientSpellRules {
    const val MAX_AREA_TARGETS: Int = 9

    private val RushAnim = reverse(RSCMType.SEQ, 1978)
    private val AreaAnim = reverse(RSCMType.SEQ, 1979)

    val definitions: List<AncientSpellDefinition> =
        listOf(
            spell(
                "obj.50_smoke_rush",
                AncientElement.Smoke,
                13,
                2,
                false,
                RushAnim,
                384,
                385,
                183,
                185,
            ),
            spell(
                "obj.52_shadow_rush",
                AncientElement.Shadow,
                14,
                10,
                false,
                RushAnim,
                378,
                379,
                178,
                179,
            ),
            spell(
                "obj.56_blood_rush",
                AncientElement.Blood,
                15,
                25,
                false,
                RushAnim,
                372,
                373,
                106,
                110,
            ),
            spell(
                "obj.58_ice_rush",
                AncientElement.Ice,
                16,
                8,
                false,
                RushAnim,
                360,
                361,
                171,
                173,
            ),
            spell(
                "obj.62_smoke_burst",
                AncientElement.Smoke,
                17,
                2,
                true,
                AreaAnim,
                388,
                389,
                183,
                182,
            ),
            spell(
                "obj.64_shadow_burst",
                AncientElement.Shadow,
                18,
                10,
                true,
                AreaAnim,
                null,
                382,
                178,
                177,
            ),
            spell(
                "obj.68_blood_burst",
                AncientElement.Blood,
                21,
                25,
                true,
                AreaAnim,
                null,
                376,
                106,
                105,
            ),
            spell(
                "obj.70_ice_burst",
                AncientElement.Ice,
                22,
                16,
                true,
                AreaAnim,
                366,
                363,
                171,
                170,
            ),
            spell(
                "obj.74_smoke_blitz",
                AncientElement.Smoke,
                23,
                4,
                false,
                RushAnim,
                386,
                387,
                183,
                181,
            ),
            spell(
                "obj.76_shadow_blitz",
                AncientElement.Shadow,
                24,
                15,
                false,
                RushAnim,
                380,
                381,
                178,
                176,
            ),
            spell(
                "obj.80_blood_blitz",
                AncientElement.Blood,
                25,
                25,
                false,
                RushAnim,
                374,
                375,
                106,
                104,
            ),
            spell(
                "obj.82_ice_blitz",
                AncientElement.Ice,
                26,
                24,
                false,
                RushAnim,
                362,
                367,
                171,
                169,
            ),
            spell(
                "obj.86_smoke_barrage",
                AncientElement.Smoke,
                27,
                4,
                true,
                AreaAnim,
                390,
                391,
                183,
                180,
            ),
            spell(
                "obj.88_shadow_barrage",
                AncientElement.Shadow,
                28,
                15,
                true,
                AreaAnim,
                null,
                383,
                178,
                175,
            ),
            spell(
                "obj.92_blood_barrage",
                AncientElement.Blood,
                29,
                25,
                true,
                AreaAnim,
                null,
                377,
                106,
                102,
            ),
            spell(
                "obj.94_ice_barrage",
                AncientElement.Ice,
                30,
                32,
                true,
                AreaAnim,
                368,
                369,
                171,
                168,
            ),
        )

    fun effectLands(actualDamage: Int): Boolean = actualDamage >= 1

    fun bloodHeal(actualDamage: Int): Int = actualDamage.coerceAtLeast(0) / 4

    fun shadowAttackFloor(baseAttack: Int, percent: Int): Int =
        baseAttack.coerceAtLeast(0) * (100 - percent.coerceIn(0, 100)) / 100

    private fun spell(
        obj: String,
        element: AncientElement,
        maxHit: Int,
        effectStrength: Int,
        area: Boolean,
        castAnim: String,
        travelId: Int?,
        impactId: Int,
        castSoundId: Int,
        hitSoundId: Int,
    ): AncientSpellDefinition =
        AncientSpellDefinition(
            obj = obj,
            element = element,
            baseMaxHit = maxHit,
            effectStrength = effectStrength,
            area = area,
            castAnim = castAnim,
            travel = travelId?.let { reverse(RSCMType.SPOTANIM, it) },
            impact = reverse(RSCMType.SPOTANIM, impactId),
            impactHeight = 0,
            castSound = reverse(RSCMType.SYNTH, castSoundId),
            hitSound = reverse(RSCMType.SYNTH, hitSoundId),
        )

    private fun reverse(type: RSCMType, id: Int): String = RSCM.getReverseMapping(type, id)
}

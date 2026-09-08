package org.rsmod.content.skills.magic.spell.attacks.standard

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.magic.MagicSpellChecks
import org.rsmod.api.combat.commons.magic.TomeOfFireState
import org.rsmod.api.combat.manager.MagicRuneManager.Companion.isFailure
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.params
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.magicLvl
import org.rsmod.api.player.worn.EquipmentChecks
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.api.spells.attack.SpellAttack
import org.rsmod.api.spells.attack.SpellAttackManager
import org.rsmod.api.spells.attack.SpellAttackMap
import org.rsmod.api.spells.attack.SpellAttackRepository
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitImpactHandler
import org.rsmod.game.type.getOrNull

class ElementalSpells @Inject constructor(private val spellRegistry: MagicSpellRegistry) :
    SpellAttackMap {
    private val registeredSpells by lazy {
        spellRegistry.allSpells().associateBy { RSCM.getReverseMapping(RSCMType.OBJ, it.obj.id) }
    }

    override fun SpellAttackRepository.register(manager: SpellAttackManager) {
        registerStrikes(manager)
        registerBolts(manager)
        registerBlasts(manager)
        registerWaves(manager)
        registerSurges(manager)
    }

    private fun SpellAttackRepository.registerStrikes(manager: SpellAttackManager) {
        fun getMaxHit(magicLvl: Int): Int =
            when {
                magicLvl >= 13 -> 8
                magicLvl >= 9 -> 6
                magicLvl >= 5 -> 4
                else -> 2
            }

        register(
            spell = "obj.01_wind_strike",
            attack =
                ElementalSpellAttack(
                    manager = manager,
                    staffAnim = "seq.human_caststrike_staff",
                    unarmedAnim = "seq.human_caststrike",
                    launch = "spotanim.windstrike_casting",
                    travel = "spotanim.windstrike_travel",
                    impact = "spotanim.windstrike_impact",
                    castSound = "synth.windstrike_cast_and_fire",
                    hitSound = "synth.windstrike_hit",
                    getMaxHit = ::getMaxHit,
                ),
        )

        register(
            spell = "obj.05_water_strike",
            attack =
                ElementalSpellAttack(
                    manager = manager,
                    staffAnim = "seq.human_caststrike_staff",
                    unarmedAnim = "seq.human_caststrike",
                    launch = "spotanim.waterstrike_casting",
                    travel = "spotanim.waterstrike_travel",
                    impact = "spotanim.waterstrike_impact",
                    castSound = "synth.waterstrike_cast_and_fire",
                    hitSound = "synth.waterstrike_hit",
                    getMaxHit = ::getMaxHit,
                ),
        )

        register(
            spell = "obj.09_earth_strike",
            attack =
                ElementalSpellAttack(
                    manager = manager,
                    staffAnim = "seq.human_caststrike_staff",
                    unarmedAnim = "seq.human_caststrike",
                    launch = "spotanim.earthstrike_casting",
                    travel = "spotanim.earthstrike_travel",
                    impact = "spotanim.earthstrike_impact",
                    castSound = "synth.earthstrike_cast_and_fire",
                    hitSound = "synth.earthstrike_hit",
                    getMaxHit = ::getMaxHit,
                ),
        )

        register(
            spell = "obj.13_fire_strike",
            attack =
                ElementalSpellAttack(
                    manager = manager,
                    staffAnim = "seq.human_caststrike_staff",
                    unarmedAnim = "seq.human_caststrike",
                    launch = "spotanim.firestrike_casting",
                    travel = "spotanim.firestrike_travel",
                    impact = "spotanim.firestrike_impact",
                    castSound = "synth.firestrike_cast_and_fire",
                    hitSound = "synth.firestrike_hit",
                    getMaxHit = ::getMaxHit,
                ),
        )
    }

    private fun SpellAttackRepository.registerBolts(manager: SpellAttackManager) {
        fun getMaxHit(magicLvl: Int): Int =
            when {
                magicLvl >= 35 -> 12
                magicLvl >= 29 -> 11
                magicLvl >= 23 -> 10
                else -> 9
            }

        register(
            spell = "obj.17_wind_bolt",
            attack =
                ElementalSpellAttack(
                    manager = manager,
                    staffAnim = "seq.human_caststrike_staff",
                    unarmedAnim = "seq.human_caststrike",
                    launch = "spotanim.windbolt_casting",
                    travel = "spotanim.windbolt_travel",
                    impact = "spotanim.windbolt_impact",
                    castSound = "synth.windbolt_cast_and_fire",
                    hitSound = "synth.windbolt_hit",
                    getMaxHit = ::getMaxHit,
                ),
        )

        register(
            spell = "obj.23_water_bolt",
            attack =
                ElementalSpellAttack(
                    manager = manager,
                    staffAnim = "seq.human_caststrike_staff",
                    unarmedAnim = "seq.human_caststrike",
                    launch = "spotanim.waterbolt_casting",
                    travel = "spotanim.waterbolt_travel",
                    impact = "spotanim.waterbolt_impact",
                    castSound = "synth.waterbolt_cast_and_fire",
                    hitSound = "synth.waterbolt_hit",
                    getMaxHit = ::getMaxHit,
                ),
        )

        register(
            spell = "obj.29_earth_bolt",
            attack =
                ElementalSpellAttack(
                    manager = manager,
                    staffAnim = "seq.human_caststrike_staff",
                    unarmedAnim = "seq.human_caststrike",
                    launch = "spotanim.earthbolt_casting",
                    travel = "spotanim.earthbolt_travel",
                    impact = "spotanim.earthbolt_impact",
                    castSound = "synth.earthbolt_cast_and_fire",
                    hitSound = "synth.earthbolt_hit",
                    getMaxHit = ::getMaxHit,
                ),
        )

        register(
            spell = "obj.35_fire_bolt",
            attack =
                ElementalSpellAttack(
                    manager = manager,
                    staffAnim = "seq.human_caststrike_staff",
                    unarmedAnim = "seq.human_caststrike",
                    launch = "spotanim.firebolt_casting",
                    travel = "spotanim.firebolt_travel",
                    impact = "spotanim.firebolt_impact",
                    castSound = "synth.firebolt_cast_and_fire",
                    hitSound = "synth.firebolt_hit",
                    getMaxHit = ::getMaxHit,
                ),
        )
    }

    private fun SpellAttackRepository.registerBlasts(manager: SpellAttackManager) {
        fun getMaxHit(magicLvl: Int): Int =
            when {
                magicLvl >= 59 -> 16
                magicLvl >= 53 -> 15
                magicLvl >= 47 -> 14
                else -> 13
            }

        register(
            spell = "obj.41_wind_blast",
            attack =
                ElementalSpellAttack(
                    manager = manager,
                    staffAnim = "seq.human_caststrike_staff",
                    unarmedAnim = "seq.human_caststrike",
                    launch = "spotanim.windblast_casting",
                    travel = "spotanim.windblast_travel",
                    impact = "spotanim.windblast_impact",
                    castSound = "synth.windblast_cast_and_fire",
                    hitSound = "synth.windblast_hit",
                    getMaxHit = ::getMaxHit,
                ),
        )

        register(
            spell = "obj.47_water_blast",
            attack =
                ElementalSpellAttack(
                    manager = manager,
                    staffAnim = "seq.human_caststrike_staff",
                    unarmedAnim = "seq.human_caststrike",
                    launch = "spotanim.waterblast_casting",
                    travel = "spotanim.waterblast_travel",
                    impact = "spotanim.waterblast_impact",
                    castSound = "synth.waterblast_cast_and_fire",
                    hitSound = "synth.waterblast_hit",
                    getMaxHit = ::getMaxHit,
                ),
        )

        register(
            spell = "obj.53_earth_blast",
            attack =
                ElementalSpellAttack(
                    manager = manager,
                    staffAnim = "seq.human_caststrike_staff",
                    unarmedAnim = "seq.human_caststrike",
                    launch = "spotanim.earthblast_casting",
                    travel = "spotanim.earthblast_travel",
                    impact = "spotanim.earthblast_impact",
                    castSound = "synth.earthblast_cast_and_fire",
                    hitSound = "synth.earthblast_hit",
                    getMaxHit = ::getMaxHit,
                ),
        )

        register(
            spell = "obj.59_fire_blast",
            attack =
                ElementalSpellAttack(
                    manager = manager,
                    staffAnim = "seq.human_caststrike_staff",
                    unarmedAnim = "seq.human_caststrike",
                    launch = "spotanim.fireblast_casting",
                    travel = "spotanim.fireblast_travel",
                    impact = "spotanim.fireblast_impact",
                    castSound = "synth.fireblast_cast_and_fire",
                    hitSound = "synth.fireblast_hit",
                    getMaxHit = ::getMaxHit,
                ),
        )
    }

    private fun SpellAttackRepository.registerWaves(manager: SpellAttackManager) {
        fun getMaxHit(magicLvl: Int): Int =
            when {
                magicLvl >= 75 -> 20
                magicLvl >= 70 -> 19
                magicLvl >= 65 -> 18
                else -> 17
            }

        register(
            spell = "obj.62_wind_wave",
            attack =
                ElementalSpellAttack(
                    manager = manager,
                    staffAnim = "seq.human_castwave_staff",
                    unarmedAnim = "seq.human_castwave",
                    launch = "spotanim.windwave_casting",
                    travel = "spotanim.windwave_travel",
                    impact = "spotanim.windwave_impact",
                    castSound = "synth.windwave_cast_and_fire",
                    hitSound = "synth.windwave_hit",
                    getMaxHit = ::getMaxHit,
                ),
        )

        register(
            spell = "obj.65_water_wave",
            attack =
                ElementalSpellAttack(
                    manager = manager,
                    staffAnim = "seq.human_castwave_staff",
                    unarmedAnim = "seq.human_castwave",
                    launch = "spotanim.waterwave_casting",
                    travel = "spotanim.waterwave_travel",
                    impact = "spotanim.waterwave_impact",
                    castSound = "synth.waterwave_cast_and_fire",
                    hitSound = "synth.waterwave_hit",
                    getMaxHit = ::getMaxHit,
                ),
        )

        register(
            spell = "obj.70_earth_wave",
            attack =
                ElementalSpellAttack(
                    manager = manager,
                    staffAnim = "seq.human_castwave_staff",
                    unarmedAnim = "seq.human_castwave",
                    launch = "spotanim.earthwave_casting",
                    travel = "spotanim.earthwave_travel",
                    impact = "spotanim.earthwave_impact",
                    castSound = "synth.earthwave_cast_and_fire",
                    hitSound = "synth.earthwave_hit",
                    getMaxHit = ::getMaxHit,
                ),
        )

        register(
            spell = "obj.75_fire_wave",
            attack =
                ElementalSpellAttack(
                    manager = manager,
                    staffAnim = "seq.human_castwave_staff",
                    unarmedAnim = "seq.human_castwave",
                    launch = "spotanim.firewave_casting",
                    travel = "spotanim.firewave_travel",
                    impact = "spotanim.firewave_impact",
                    castSound = "synth.firewave_cast_and_fire",
                    hitSound = "synth.firewave_hit",
                    getMaxHit = ::getMaxHit,
                ),
        )
    }

    private fun SpellAttackRepository.registerSurges(manager: SpellAttackManager) {
        fun getMaxHit(magicLvl: Int): Int =
            when {
                magicLvl >= 95 -> 24
                magicLvl >= 90 -> 23
                magicLvl >= 85 -> 22
                else -> 21
            }

        register(
            spell = "obj.81_wind_surge",
            attack =
                ElementalSpellAttack(
                    manager = manager,
                    staffAnim = "seq.human_cast_surge",
                    unarmedAnim = "seq.human_cast_surge",
                    launch = "spotanim.windsurge_casting",
                    travel = "spotanim.windsurge_travel",
                    impact = "spotanim.windsurge_impact",
                    castSound = "synth.windsurge_cast_and_fire",
                    hitSound = "synth.windsurge_hit",
                    getMaxHit = ::getMaxHit,
                ),
        )

        register(
            spell = "obj.85_water_surge",
            attack =
                ElementalSpellAttack(
                    manager = manager,
                    staffAnim = "seq.human_cast_surge",
                    unarmedAnim = "seq.human_cast_surge",
                    launch = "spotanim.watersurge_casting",
                    travel = "spotanim.watersurge_travel",
                    impact = "spotanim.watersurge_impact",
                    castSound = "synth.watersurge_cast_and_fire",
                    hitSound = "synth.watersurge_hit",
                    getMaxHit = ::getMaxHit,
                ),
        )

        register(
            spell = "obj.90_earth_surge",
            attack =
                ElementalSpellAttack(
                    manager = manager,
                    staffAnim = "seq.human_cast_surge",
                    unarmedAnim = "seq.human_cast_surge",
                    launch = "spotanim.earthsurge_casting",
                    travel = "spotanim.earthsurge_travel",
                    impact = "spotanim.earthsurge_impact",
                    castSound = "synth.earthsurge_cast_and_fire",
                    hitSound = "synth.earthsurge_hit",
                    getMaxHit = ::getMaxHit,
                ),
        )

        register(
            spell = "obj.95_fire_surge",
            attack =
                ElementalSpellAttack(
                    manager = manager,
                    staffAnim = "seq.human_cast_surge",
                    unarmedAnim = "seq.human_cast_surge",
                    launch = "spotanim.firesurge_casting",
                    travel = "spotanim.firesurge_travel",
                    impact = "spotanim.firesurge_impact",
                    castSound = "synth.firesurge_cast_and_fire",
                    hitSound = "synth.firesurge_hit",
                    getMaxHit = ::getMaxHit,
                ),
        )
    }

    private inner class ElementalSpellAttack(
        private val manager: SpellAttackManager,
        private val staffAnim: String,
        private val unarmedAnim: String,
        private val launch: String,
        private val travel: String,
        private val impact: String,
        private val castSound: String,
        private val hitSound: String,
        private val getMaxHit: (Int) -> Int,
    ) : SpellAttack {
        override suspend fun ProtectedAccess.attack(target: Npc, attack: CombatAttack.Spell) {
            cast(target, attack)
        }

        override suspend fun ProtectedAccess.attack(target: Player, attack: CombatAttack.Spell) {
            cast(target, attack)
        }

        private fun ProtectedAccess.cast(
            target: PathingEntity,
            selectedAttack: CombatAttack.Spell,
        ) {
            val attack = resolveTwinflameSpell(target, selectedAttack)
            val castResult = manager.attemptCast(this, attack)
            if (castResult.isFailure()) {
                return
            }
            val weaponType = getOrNull(attack.weapon)
            val castAnim = weaponType.castStrikeAnim()
            val fx = spellFx(selectedAttack, attack)

            player.anim(castAnim, priority = 6)
            spotanim(fx.launch, height = 92)

            val proj = manager.spawnProjectile(this, target, fx.travel, "projanim.magic_spell")
            val (serverDelay, clientDelay) = proj.durations
            val spell = attack.spell.obj

            val splash = manager.rollSplash(this, target, attack, castResult)
            if (splash) {
                manager.playSplashFx(this, target, clientDelay, fx.castSound, soundRadius = 8)
                manager.queueSplashHit(this, target, spell, clientDelay, serverDelay)
                consumeTomeCharge(attack)
                manager.continueCombatIfAutocast(this, target)
                return
            }

            val baseMaxHit = getMaxHit(player.magicLvl)
            val attackRate =
                EquipmentChecks.spellAttackRateOverride(attack.weapon)
                    ?: constants.combat_spell_attackrate
            val damage =
                manager.rollMaxHit(this, target, attack, castResult, baseMaxHit, attackRate)
            consumeTomeCharge(attack)
            manager.playHitFx(
                source = this,
                target = target,
                clientDelay = clientDelay,
                castSound = fx.castSound,
                soundRadius = 8,
                hitSpot = fx.impact,
                hitSpotHeight = 124,
                hitSound = fx.hitSound,
            )
            val echoHandler =
                if (
                    EquipmentChecks.isTwinflameStaff(attack.weapon) &&
                        TwinflameStaffRules.echoes(attack.spell.obj)
                ) {
                    prepareTwinflameEcho(target, attack, fx)
                } else {
                    null
                }
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueMagicHit(
                this,
                target,
                spell,
                damage,
                clientDelay,
                serverDelay,
                effectHandler = echoHandler,
            )
            manager.continueCombatIfAutocast(this, target)
        }

        private fun ProtectedAccess.resolveTwinflameSpell(
            target: PathingEntity,
            selected: CombatAttack.Spell,
        ): CombatAttack.Spell {
            if (
                !EquipmentChecks.isTwinflameStaff(selected.weapon) ||
                    target !is Npc ||
                    !manager.isAutocasting(this)
            ) {
                return selected
            }
            val selectedInternal = RSCM.getReverseMapping(RSCMType.OBJ, selected.spell.obj.id)
            val replacementInternal =
                TwinflameStaffRules.royalTitansWallSpell(
                    selectedInternal = selectedInternal,
                    targetInternal = target.type.internalName,
                )
                    ?: run {
                        val weakness =
                            target.visType.paramOrNull(params.elemental_weakness_type)
                                ?: return selected
                        TwinflameStaffRules.weaknessSpell(selectedInternal, weakness)
                    }
                    ?: return selected
            val replacement = registeredSpells[replacementInternal] ?: return selected
            return if (manager.canCastSilently(this, replacement)) {
                selected.copy(spell = replacement)
            } else {
                mes(
                    "You lack the requirements to automate the superior elemental spell against your foe."
                )
                selected
            }
        }

        private fun ProtectedAccess.consumeTomeCharge(attack: CombatAttack.Spell) {
            // attemptCast has already succeeded. OSRS consumes one charge for every eligible
            // Standard fire cast, including splashes, but never for failed/invalid casts.
            if (MagicSpellChecks.isFireSpell(attack.spell.obj)) {
                TomeOfFireState.consumeEligibleCast(player)
            }
        }

        private fun spellFx(
            selected: CombatAttack.Spell,
            effective: CombatAttack.Spell,
        ): ElementalFx {
            if (selected.spell.obj.id == effective.spell.obj.id) {
                return ElementalFx(launch, travel, impact, castSound, hitSound)
            }
            val internal = RSCM.getReverseMapping(RSCMType.OBJ, effective.spell.obj.id)
            val stem = internal.removePrefix("obj.").substringAfter('_').replace("_", "")
            return ElementalFx(
                launch = "spotanim.${stem}_casting",
                travel = "spotanim.${stem}_travel",
                impact = "spotanim.${stem}_impact",
                castSound = "synth.${stem}_cast_and_fire",
                hitSound = "synth.${stem}_hit",
            )
        }

        private fun ProtectedAccess.prepareTwinflameEcho(
            target: PathingEntity,
            attack: CombatAttack.Spell,
            fx: ElementalFx,
        ): HitImpactHandler {
            // Live fires both spell projectiles from the one valid cast. The impact and hitsplat
            // of the echo are delayed by exactly one 0.6-second server tick.
            val echoProjectile =
                manager.spawnProjectileWithImpactOffset(
                    source = this,
                    target = target,
                    spotanim = fx.travel,
                    projanim = "projanim.magic_spell",
                    impactOffset =
                        TwinflameStaffRules.ECHO_VISIBLE_DELAY_TICKS * CLIENT_CYCLES_PER_SERVER_TICK,
                )
            manager.playHitFx(
                source = this,
                target = target,
                clientDelay = echoProjectile.durations.clientDelay,
                castSound = null,
                soundRadius = 8,
                hitSpot = fx.impact,
                hitSpotHeight = 124,
                hitSound = fx.hitSound,
            )
            val source = this
            return HitImpactHandler { impact ->
                manager.queueMagicEchoHit(
                    source = source,
                    target = impact.target,
                    damage = TwinflameStaffRules.echoDamage(impact.damage),
                    clientDelay =
                        TwinflameStaffRules.ECHO_VISIBLE_DELAY_TICKS *
                            CLIENT_CYCLES_PER_SERVER_TICK,
                    // This queue is created while the target's hit queues are already being
                    // processed. A value of one would therefore expire in the first hit's cycle.
                    hitDelay = TwinflameStaffRules.ECHO_IMPACT_QUEUE_DELAY_CYCLES,
                )
            }
        }

        private fun ItemServerType?.castStrikeAnim(): String =
            if (
                this != null && (isType("obj.twinflame_staff") || isCategoryType("category.staff"))
            ) {
                staffAnim
            } else {
                unarmedAnim
            }
    }

    private data class ElementalFx(
        val launch: String,
        val travel: String,
        val impact: String,
        val castSound: String,
        val hitSound: String,
    )

    private companion object {
        const val CLIENT_CYCLES_PER_SERVER_TICK: Int = 30
    }
}

internal object TwinflameStaffRules {
    private enum class Tier(val echo: Boolean, val autoWeakness: Boolean) {
        Strike(echo = false, autoWeakness = true),
        Bolt(echo = true, autoWeakness = true),
        Blast(echo = true, autoWeakness = true),
        Wave(echo = true, autoWeakness = true),
        Surge(echo = false, autoWeakness = false),
    }

    private data class ElementalSpell(val tier: Tier, val weakness: Int, val internalName: String)

    private val spells =
        listOf(
            ElementalSpell(Tier.Strike, constants.elemental_weakness_wind, "obj.01_wind_strike"),
            ElementalSpell(Tier.Strike, constants.elemental_weakness_water, "obj.05_water_strike"),
            ElementalSpell(Tier.Strike, constants.elemental_weakness_earth, "obj.09_earth_strike"),
            ElementalSpell(Tier.Strike, constants.elemental_weakness_fire, "obj.13_fire_strike"),
            ElementalSpell(Tier.Bolt, constants.elemental_weakness_wind, "obj.17_wind_bolt"),
            ElementalSpell(Tier.Bolt, constants.elemental_weakness_water, "obj.23_water_bolt"),
            ElementalSpell(Tier.Bolt, constants.elemental_weakness_earth, "obj.29_earth_bolt"),
            ElementalSpell(Tier.Bolt, constants.elemental_weakness_fire, "obj.35_fire_bolt"),
            ElementalSpell(Tier.Blast, constants.elemental_weakness_wind, "obj.41_wind_blast"),
            ElementalSpell(Tier.Blast, constants.elemental_weakness_water, "obj.47_water_blast"),
            ElementalSpell(Tier.Blast, constants.elemental_weakness_earth, "obj.53_earth_blast"),
            ElementalSpell(Tier.Blast, constants.elemental_weakness_fire, "obj.59_fire_blast"),
            ElementalSpell(Tier.Wave, constants.elemental_weakness_wind, "obj.62_wind_wave"),
            ElementalSpell(Tier.Wave, constants.elemental_weakness_water, "obj.65_water_wave"),
            ElementalSpell(Tier.Wave, constants.elemental_weakness_earth, "obj.70_earth_wave"),
            ElementalSpell(Tier.Wave, constants.elemental_weakness_fire, "obj.75_fire_wave"),
            ElementalSpell(Tier.Surge, constants.elemental_weakness_wind, "obj.81_wind_surge"),
            ElementalSpell(Tier.Surge, constants.elemental_weakness_water, "obj.85_water_surge"),
            ElementalSpell(Tier.Surge, constants.elemental_weakness_earth, "obj.90_earth_surge"),
            ElementalSpell(Tier.Surge, constants.elemental_weakness_fire, "obj.95_fire_surge"),
        )

    private val spellByInternal = spells.associateBy(ElementalSpell::internalName)
    private val spellsByTier = spells.groupBy(ElementalSpell::tier)

    fun weaknessSpell(selectedInternal: String, weakness: Int): String? {
        val selected = spellByInternal[selectedInternal] ?: return null
        if (!selected.tier.autoWeakness) {
            return null
        }
        return spellsByTier[selected.tier]?.firstOrNull { it.weakness == weakness }?.internalName
    }

    fun royalTitansWallSpell(selectedInternal: String, targetInternal: String): String? {
        val selected = spellByInternal[selectedInternal] ?: return null
        if (selected.tier != Tier.Wave) {
            return null
        }
        return when (targetInternal) {
            FIRE_WALL_INTERNAL -> "obj.47_water_blast"
            ICE_WALL_INTERNAL -> "obj.59_fire_blast"
            else -> null
        }
    }

    fun echoes(spell: ItemServerType): Boolean {
        val internal = RSCM.getReverseMapping(RSCMType.OBJ, spell.id)
        return spellByInternal[internal]?.tier?.echo == true
    }

    fun echoes(internalName: String): Boolean = spellByInternal[internalName]?.tier?.echo == true

    fun echoDamage(firstDamage: Int): Int = firstDamage * 2 / 5

    // Queuing occurs during the first hit's processing cycle, which immediately consumes one
    // count. Two queue cycles therefore produce one real game tick between visible impacts.
    const val ECHO_VISIBLE_DELAY_TICKS: Int = 1
    const val ECHO_IMPACT_QUEUE_DELAY_CYCLES: Int = ECHO_VISIBLE_DELAY_TICKS + 1

    private const val FIRE_WALL_INTERNAL = "npc.rs_loc_attack_fire_hitbox"
    private const val ICE_WALL_INTERNAL = "npc.rs_loc_attack_ice_hitbox"
}

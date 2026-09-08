package org.rsmod.content.bosses.wilderness

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import java.util.WeakHashMap
import org.rsmod.api.combat.NpcAttackOverride
import org.rsmod.api.combat.NpcProjectile
import org.rsmod.api.combat.NvPCombat
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.CombatEffects
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.combat.commons.types.RangedAttackType
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.heal
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.script.onAiApPlayer2
import org.rsmod.api.script.onAiOpPlayer2
import org.rsmod.api.script.onNpcHit
import org.rsmod.api.script.onNpcQueue
import org.rsmod.content.bosses.wilderness.ethereum.EthereumBraceletHitModifier
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Revision-240 combat for the eleven ordinary Revenant forms. */
internal class RevenantCombat
@Inject
constructor(
    private val combat: NvPCombat,
    private val braceletHitModifier: EthereumBraceletHitModifier,
    private val death: NpcDeath,
) : PluginScript() {
    private val healing = WeakHashMap<Npc, RevenantHealState>()

    override fun ScriptContext.startup() {
        validateAssets()
        REVENANT_COMBAT_DEFINITIONS.forEach { definition -> register(definition) }
    }

    private fun ScriptContext.register(definition: RevenantCombatDefinition) {
        val npcType =
            requireNotNull(ServerCacheManager.getNpc(definition.npcInternal.asRSCM(RSCMType.NPC))) {
                "Missing Revenant definition: ${definition.npcInternal}"
            }
        onAiOpPlayer2(npcType) { attackRevenant(this, it.target, definition) }
        onAiApPlayer2(npcType) { attackRevenant(this, it.target, definition) }
        onNpcHit(npcType) {
            if (npc.hitpoints > 0) {
                npc.anim(definition.blockAnimation)
            } else {
                // A heal graphic can still be active when the lethal hit lands. Clear its slot
                // before the death animation begins so a dying revenant never appears to heal.
                healing.remove(npc)
                npc.spotanim(-1)
            }
        }
        onNpcQueue(npcType, "queue.death") {
            healing.remove(npc)
            npc.spotanim(-1)
            death.deathWithDrops(this, deathAnimationId = definition.deathAnimation)
        }
    }

    private fun attackRevenant(
        access: StandardNpcAccess,
        target: Player,
        definition: RevenantCombatDefinition,
    ) =
        with(access) {
            if (tryHeal(definition)) return@with
            when (selectStyle(target)) {
                RevenantStyle.Melee ->
                    combat.attack(
                        this,
                        target,
                        CombatAttack.NpcMelee(MeleeAttackType.Crush),
                        attackOverride(definition, RevenantStyle.Melee, target),
                    )

                RevenantStyle.Ranged ->
                    combat.attack(
                        this,
                        target,
                        CombatAttack.NpcRanged(RangedAttackType.Standard),
                        attackOverride(definition, RevenantStyle.Ranged, target),
                    )

                RevenantStyle.Magic ->
                    combat.attack(
                        this,
                        target,
                        CombatAttack.NpcMagic(definition.maxHit),
                        attackOverride(definition, RevenantStyle.Magic, target),
                    )
            }
        }

    private fun StandardNpcAccess.tryHeal(definition: RevenantCombatDefinition): Boolean {
        val deathQueued = "queue.death" in npc.queueList
        if (!revenantCanHeal(npc.hitpoints, npc.baseHitpointsLvl, deathQueued)) return false
        val state =
            healing.getOrPut(npc) {
                RevenantHealState(random.of(REVENANT_HEAL_MIN_USES..REVENANT_HEAL_MAX_USES))
            }
        if (state.remaining <= 0 || mapClock < state.nextCycle) return false
        npc.anim(definition.attackAnimation)
        npc.spotanim(REVENANT_HEAL_SPOTANIM)
        npc.heal(REVENANT_HEAL_AMOUNT, showHitsplat = true)
        state.remaining--
        state.nextCycle = mapClock + REVENANT_HEAL_COOLDOWN_TICKS
        actionDelay = mapClock + REVENANT_ATTACK_RATE
        return true
    }

    private fun StandardNpcAccess.selectStyle(target: Player): RevenantStyle {
        val inMeleeRange = npc.coords.chebyshevDistance(target.coords) <= npc.visType.size
        return when {
            target.vars[PROTECT_MAGIC] == 1 -> RevenantStyle.Ranged
            target.vars[PROTECT_RANGED] == 1 -> RevenantStyle.Magic
            target.vars[PROTECT_MELEE] == 1 -> RevenantStyle.Magic
            inMeleeRange ->
                when (random.of(3)) {
                    0 -> RevenantStyle.Melee
                    1 -> RevenantStyle.Ranged
                    else -> RevenantStyle.Magic
                }
            random.of(2) == 0 -> RevenantStyle.Ranged
            else -> RevenantStyle.Magic
        }
    }

    private fun StandardNpcAccess.attackOverride(
        definition: RevenantCombatDefinition,
        style: RevenantStyle,
        target: Player,
    ): NpcAttackOverride =
        NpcAttackOverride(
            attackAnimation = definition.animationFor(style),
            maxHit = definition.maxHit,
            attackRate = REVENANT_ATTACK_RATE,
            projectile = if (style == RevenantStyle.Melee) null else revenantProjectile(),
            hitModifier = braceletHitModifier,
            onImpact =
                if (style == RevenantStyle.Melee) {
                    null
                } else {
                    { _, impact ->
                        if (target.isSlotAssigned && target.hitpoints > 0) {
                            target.spotanim(REVENANT_IMPACT_SPOTANIM)
                            if (style == RevenantStyle.Magic && impact.damage >= 0) {
                                CombatEffects.freeze(target, REVENANT_MAGIC_FREEZE_TICKS)
                            }
                        }
                    }
                },
        )

    private fun RevenantCombatDefinition.animationFor(style: RevenantStyle): String? =
        if (npcInternal == REVENANT_KNIGHT) {
            when (style) {
                RevenantStyle.Melee -> REVENANT_KNIGHT_MELEE_ANIMATION
                RevenantStyle.Ranged -> REVENANT_KNIGHT_RANGED_ANIMATION
                RevenantStyle.Magic -> REVENANT_KNIGHT_MAGIC_ANIMATION
            }
        } else {
            null
        }

    private fun revenantProjectile(): NpcProjectile =
        NpcProjectile(
            travelSpotanim = REVENANT_TRAVEL_SPOTANIM,
            type =
                requireNotNull(
                    ServerCacheManager.getProjectiles()[
                            REVENANT_PROJECTILE_TYPE.asRSCM(RSCMType.PROJANIM)]
                ),
        )

    private fun validateAssets() {
        REVENANT_COMBAT_DEFINITIONS.forEach { definition ->
            require(ServerCacheManager.getAnim(definition.attackAnimation) != null) {
                "Missing Revenant attack animation ${definition.attackAnimation}"
            }
            require(ServerCacheManager.getAnim(definition.blockAnimation) != null) {
                "Missing Revenant block animation ${definition.blockAnimation}"
            }
            require(ServerCacheManager.getAnim(definition.deathAnimation) != null) {
                "Missing Revenant death animation ${definition.deathAnimation}"
            }
        }
        listOf(
                REVENANT_KNIGHT_MELEE_ANIMATION,
                REVENANT_KNIGHT_RANGED_ANIMATION,
                REVENANT_KNIGHT_MAGIC_ANIMATION,
            )
            .forEach { animation ->
                require(ServerCacheManager.getAnim(animation.asRSCM(RSCMType.SEQ)) != null) {
                    "Missing Revenant knight animation $animation"
                }
            }
        requireNotNull(
            ServerCacheManager.getProjectiles()[REVENANT_PROJECTILE_TYPE.asRSCM(RSCMType.PROJANIM)]
        )
    }
}

internal enum class RevenantStyle {
    Melee,
    Ranged,
    Magic,
}

internal data class RevenantCombatDefinition(
    val npcInternal: String,
    val maxHit: Int,
    val attackAnimation: Int,
    val blockAnimation: Int,
    val deathAnimation: Int,
    val combatLevel: Int,
)

private data class RevenantHealState(var remaining: Int, var nextCycle: Int = 0)

internal fun revenantCanHeal(hitpoints: Int, baseHitpoints: Int, deathQueued: Boolean): Boolean =
    hitpoints > 0 && !deathQueued && hitpoints * 2 < baseHitpoints

internal val REVENANT_COMBAT_DEFINITIONS =
    listOf(
        RevenantCombatDefinition("npc.wild_cave_imp", 2, 169, 170, 172, 7),
        RevenantCombatDefinition("npc.wild_cave_goblin", 3, 6184, 6183, 6182, 15),
        RevenantCombatDefinition("npc.wild_cave_pyrefiend", 5, 1582, 1581, 1580, 52),
        RevenantCombatDefinition("npc.wild_cave_hobgoblin", 8, 163, 165, 167, 60),
        RevenantCombatDefinition("npc.wild_cave_cyclops", 16, 4652, 4651, 4653, 82),
        RevenantCombatDefinition("npc.wild_cave_hellhound", 14, 6581, 6578, 6576, 90),
        RevenantCombatDefinition("npc.wild_cave_demon", 18, 64, 65, 67, 98),
        RevenantCombatDefinition("npc.wild_cave_ork", 20, 4320, 4322, 4321, 105),
        RevenantCombatDefinition("npc.wild_cave_darkbeast", 23, 2731, 2732, 2733, 120),
        RevenantCombatDefinition(REVENANT_KNIGHT, 27, 3926, 424, 3280, 126),
        RevenantCombatDefinition("npc.wild_cave_dragon", 30, 80, 89, 92, 135),
    )

internal const val REVENANT_ATTACK_RATE = 5
internal const val REVENANT_HEAL_AMOUNT = 20
internal const val REVENANT_HEAL_COOLDOWN_TICKS = 15
internal const val REVENANT_HEAL_MIN_USES = 8
internal const val REVENANT_HEAL_MAX_USES = 25
internal const val REVENANT_MAGIC_FREEZE_TICKS = 5
private const val REVENANT_KNIGHT = "npc.wild_cave_knight"
private const val REVENANT_KNIGHT_MELEE_ANIMATION = "seq.pest_void_knight_casting"
private const val REVENANT_KNIGHT_RANGED_ANIMATION = "seq.ii_human_dart_throw_pvn"
private const val REVENANT_KNIGHT_MAGIC_ANIMATION = "seq.human_casting"
private const val REVENANT_TRAVEL_SPOTANIM = "spotanim.revenant_magic_travel"
private const val REVENANT_IMPACT_SPOTANIM = "spotanim.revenant_magic_impact"
private const val REVENANT_HEAL_SPOTANIM = "spotanim.godwars_saradomin_light_attk_spot"
private const val REVENANT_PROJECTILE_TYPE = "projanim.magic_spell"
private const val PROTECT_MELEE = "varbit.prayer_protectfrommelee"
private const val PROTECT_RANGED = "varbit.prayer_protectfrommissiles"
private const val PROTECT_MAGIC = "varbit.prayer_protectfrommagic"

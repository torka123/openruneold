package org.rsmod.content.bosses.doom

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.bosses.spec.*
import org.rsmod.api.combat.NpcProjectile
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.combat.commons.types.RangedAttackType

/**
 * Characterized three-stream decisions and ordinary launches use native selectors/effects. Spatial
 * rock volleys retain their established handler. This is not a new OSRS fidelity claim.
 */
internal fun doomBossSpec(): BossSpec {
    val abilities = linkedMapOf<String, Effect>()
    for (delve in 1..8) {
        val maxHit = DoomRules.standardMaxHit(delve)
        for (style in DoomAttackStyle.entries) {
            val attack =
                when (style) {
                    DoomAttackStyle.MELEE -> CombatAttack.NpcMelee(MeleeAttackType.Crush)
                    DoomAttackStyle.RANGED -> CombatAttack.NpcRanged(RangedAttackType.Standard)
                    DoomAttackStyle.MAGIC -> CombatAttack.NpcMagic(maxHit)
                }
            val gfx =
                when (style) {
                    DoomAttackStyle.MELEE -> DoomAssets.GFX_MELEE_PROJECTILE
                    DoomAttackStyle.RANGED -> DoomAssets.GFX_RANGED_PROJECTILE
                    DoomAttackStyle.MAGIC -> DoomAssets.GFX_MAGIC_PROJECTILE
                }
            abilities[doomAbility(delve, DoomPrimaryAttack.STANDARD, style)] =
                Effect.Sequence(
                    listOf(
                        Effect.SetAttackRate(DoomRules.attackRate(delve)),
                        Effect.NpcAttack(
                            attack = attack,
                            maxHit = maxHit,
                            animation = doomSequence(DoomAssets.ANIM_STANDARD),
                            projectile =
                                NpcProjectile(
                                    doomSpot(gfx),
                                    requireNotNull(
                                        ServerCacheManager.getProjectile(
                                            (if (style == DoomAttackStyle.RANGED) "projanim.arrow"
                                                else "projanim.magic_spell")
                                                .asRSCM()
                                        )
                                    ),
                                ),
                            impactDelay = DoomRules.standardProjectileTravelTicks(delve),
                            parameters = DOOM_ATTACK_PARAMETERS,
                            parameterData = DoomPrimaryAttack.STANDARD,
                            survivesSourceDeath = true,
                        ),
                    )
                )
        }
        abilities[doomAbility(delve, DoomPrimaryAttack.TONGUE)] =
            Effect.Sequence(
                listOf(
                    Effect.SetAttackRate(DoomRules.tongueAttackRate(delve)),
                    Effect.NpcAttack(
                        attack = CombatAttack.NpcMelee(MeleeAttackType.Crush),
                        animation = doomSequence(DoomAssets.ANIM_TONGUE),
                        maxHit = 40,
                        protectionPrayer = ProtectionPrayer.Reduce(50),
                        parameters = DOOM_ATTACK_PARAMETERS,
                        parameterData = DoomPrimaryAttack.TONGUE,
                        survivesSourceDeath = true,
                    ),
                )
            )
        abilities[doomAbility(delve, DoomPrimaryAttack.ROCKS)] =
            Effect.Sequence(
                listOf(
                    Effect.SetAttackRate(DoomRules.attackRate(delve)),
                    Effect.External(DOOM_ROCK_ATTACK),
                )
            )
        val rock = abilities.getValue(doomAbility(delve, DoomPrimaryAttack.ROCKS))
        val styleChoice =
            Effect.Choose(
                selector = doomStyleSelector(delve),
                branches =
                    DoomAttackStyle.entries.associate { style ->
                        style.name to
                            abilities.getValue(
                                doomAbility(delve, DoomPrimaryAttack.STANDARD, style)
                            )
                    },
            )
        val chargeChoice =
            Effect.Choose(
                selector =
                    Selector.SeededWeighted(
                        stream = DOOM_CHARGE_STREAM,
                        salt = 0x5A5A5A5AL,
                        entries = listOf(WeightedRef(DOOM_CHARGE), WeightedRef(DOOM_NO_CHARGE)),
                        preparedChoice =
                            PreparedChoice(DOOM_CHARGE_INPUT, drawBeforeOverride = false),
                    ),
                branches = mapOf(DOOM_CHARGE to rock, DOOM_NO_CHARGE to rock),
            )
        abilities[doomOrdinaryAbility(delve)] =
            Effect.Choose(
                selector =
                    Selector.SeededWeighted(
                        stream = DOOM_PRIMARY_STREAM,
                        salt = 0x13579BDFL,
                        entries = listOf(WeightedRef("ROCKS", 1), WeightedRef("STANDARD", 3)),
                        entriesAfterPrevious =
                            mapOf(
                                "ROCKS" to
                                    listOf(WeightedRef("ROCKS", 1), WeightedRef("STANDARD", 7))
                            ),
                    ),
                branches = mapOf("STANDARD" to styleChoice, "ROCKS" to chargeChoice),
            )
    }
    return BossSpec(
        npcTypes =
            listOf(DoomAssets.BOSS, DoomAssets.SHIELDED, DoomAssets.BURROWED).map {
                requireNotNull(RSCM.getReverseMapping(RSCMType.NPC, it))
            },
        stats = BossStats(attackRate = 1),
        abilities = abilities,
        phases =
            (1..8).associate { delve ->
                val name = doomPhase(delve)
                name to
                    PhaseSpec(
                        name,
                        lockMovement = true,
                        selector =
                            Selector.Conditional(
                                branches =
                                    listOf(
                                        Condition.WithinMeleeRange to
                                            doomAbility(delve, DoomPrimaryAttack.TONGUE)
                                    ),
                                fallback = doomOrdinaryAbility(delve),
                            ),
                    )
            },
        triggers = emptyList(),
    )
}

internal fun doomAbility(
    delve: Int,
    primary: DoomPrimaryAttack,
    style: DoomAttackStyle = DoomAttackStyle.RANGED,
): String =
    "${delve.coerceIn(1, 8)}.$primary" +
        if (primary == DoomPrimaryAttack.STANDARD) ".$style" else ""

internal const val DOOM_ATTACK_PARAMETERS = "doom.native-launch"
internal const val DOOM_ROCK_ATTACK = "doom.rock-volley"
internal const val DOOM_PRIMARY_STREAM = "doom.primary"
internal const val DOOM_STYLE_STREAM = "doom.style"
internal const val DOOM_CHARGE_STREAM = "doom.charge"
internal const val DOOM_STYLE_INPUT = "doom.coincident-style"
internal const val DOOM_CHARGE_INPUT = "doom.charge-suppression"
internal const val DOOM_CHARGE = "charge"
internal const val DOOM_NO_CHARGE = "plain"

internal fun doomPhase(delve: Int): String = "delve.${delve.coerceIn(1, 8)}"

private fun doomOrdinaryAbility(delve: Int): String = "${delve.coerceIn(1, 8)}.ordinary"

internal fun doomStyleSelector(delve: Int): Selector.SeededWeighted =
    Selector.SeededWeighted(
        stream = DOOM_STYLE_STREAM,
        salt = 0x2468ACE0L,
        entries =
            (if (delve == 1) listOf(DoomAttackStyle.RANGED, DoomAttackStyle.MAGIC)
                else listOf(DoomAttackStyle.MAGIC, DoomAttackStyle.RANGED, DoomAttackStyle.MELEE))
                .map { WeightedRef(it.name) },
        preparedChoice = PreparedChoice(DOOM_STYLE_INPUT, drawBeforeOverride = true),
    )

private fun doomSequence(id: Int) = requireNotNull(RSCM.getReverseMapping(RSCMType.SEQ, id))

private fun doomSpot(id: Int) = requireNotNull(RSCM.getReverseMapping(RSCMType.SPOTANIM, id))

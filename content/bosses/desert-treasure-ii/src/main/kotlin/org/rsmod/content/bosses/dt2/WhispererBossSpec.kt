package org.rsmod.content.bosses.dt2

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.bosses.spec.*
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.combat.commons.types.RangedAttackType

/** Native primary combat, special identities and the declarations consumed by owned followups. */
internal fun whispererBossSpec(): BossSpec {
    val abilities =
        Dt2AttackStyle.entries.associate { style ->
            style.name to
                Effect.NpcAttack(
                    attack =
                        when (style) {
                            Dt2AttackStyle.MELEE -> CombatAttack.NpcMelee(MeleeAttackType.Crush)
                            Dt2AttackStyle.MAGIC -> CombatAttack.NpcMagic(36)
                            Dt2AttackStyle.RANGED ->
                                CombatAttack.NpcRanged(RangedAttackType.Standard)
                        },
                    animation =
                        requireNotNull(
                            RSCM.getReverseMapping(
                                RSCMType.SEQ,
                                when (style) {
                                    Dt2AttackStyle.MELEE -> 10234
                                    Dt2AttackStyle.MAGIC -> 10243
                                    Dt2AttackStyle.RANGED -> 10237
                                },
                            )
                        ),
                    impactDelay = if (style == Dt2AttackStyle.MELEE) null else 2,
                    parameters = WHISPERER_ATTACK_PARAMETERS,
                    parameterData = style,
                    survivesSourceDeath = true,
                )
        }
    val phases =
        linkedMapOf(WHISPERER_INACTIVE to PhaseSpec(WHISPERER_INACTIVE, lockMovement = true))
    for (special in WhispererSpecial.entries) {
        val name = whispererSpecialPhase(special)
        phases[name] = PhaseSpec(name, lockMovement = true)
    }
    phases[WHISPERER_MELEE] =
        PhaseSpec(
            WHISPERER_MELEE,
            attackRate = 2,
            selector = Selector.Rotation(listOf(Dt2AttackStyle.MELEE.name)),
        )
    for (offset in 0..1) {
        val rotation = listOf(Dt2AttackStyle.MAGIC, Dt2AttackStyle.RANGED)
        val styles = List(2) { rotation[(offset + it) % rotation.size].name }
        val key = whispererDistancePhase(offset, false)
        phases[key] =
            PhaseSpec(
                key,
                lockMovement = true,
                attackRate = 10,
                selector = Selector.Rotation(styles),
            )
    }
    for (offset in 0..3) {
        val rotation =
            listOf(
                Dt2AttackStyle.RANGED,
                Dt2AttackStyle.RANGED,
                Dt2AttackStyle.MAGIC,
                Dt2AttackStyle.MAGIC,
            )
        val styles = List(4) { rotation[(offset + it) % rotation.size].name }
        val key = whispererDistancePhase(offset, true)
        phases[key] =
            PhaseSpec(
                key,
                lockMovement = true,
                attackRate = 2,
                selector = Selector.Rotation(styles),
            )
    }
    return BossSpec(
        npcTypes =
            listOf(12203, 12204, 12205).map {
                requireNotNull(RSCM.getReverseMapping(RSCMType.NPC, it))
            },
        stats = BossStats(attackRate = 10, aggressionRadius = 12),
        abilities = abilities,
        phases = phases,
        triggers = emptyList(),
    )
}

internal fun whispererDistancePhase(counter: Int, enraged: Boolean): String =
    if (enraged) "enraged.${Math.floorMod(counter, 4)}" else "distance.${Math.floorMod(counter, 2)}"

internal fun whispererSpecialPhase(special: WhispererSpecial): String =
    "special.${special.name.lowercase()}"

internal val WHISPERER_SPECIAL_DURATIONS =
    mapOf(
        WhispererSpecial.CORRUPTED_SEEDS to 22,
        WhispererSpecial.SCREECH to 30,
        WhispererSpecial.SOUL_SIPHON to 20,
    )

internal const val WHISPERER_SPECIAL_STREAM = "whisperer.special-order"

/** Singleton native choices preserve the scheduled slot even when an admin overrides its output. */
internal val WHISPERER_SPECIAL_CHOICES =
    listOf(WhispererSpecial.CORRUPTED_SEEDS, WhispererSpecial.SCREECH).map { first ->
        Selector.SeededWeighted(
            stream = WHISPERER_SPECIAL_STREAM,
            salt = 0x57484953504552L,
            entries = listOf(WeightedRef(first.name)),
            entriesAfterPrevious =
                mapOf(
                    WhispererSpecial.CORRUPTED_SEEDS.name to
                        listOf(WeightedRef(WhispererSpecial.SCREECH.name)),
                    WhispererSpecial.SCREECH.name to
                        listOf(WeightedRef(WhispererSpecial.SOUL_SIPHON.name)),
                    WhispererSpecial.SOUL_SIPHON.name to
                        listOf(WeightedRef(WhispererSpecial.CORRUPTED_SEEDS.name)),
                ),
        )
    }

internal data class WhispererFollowup(
    val style: Dt2AttackStyle,
    val afterTicks: Int,
    val projectile: Boolean = true,
)

/**
 * Primary style is already selected by BossSpec; this declares the rest of that accepted volley.
 */
internal fun whispererFollowups(
    primary: Dt2AttackStyle,
    startedSpecials: Int,
    enraged: Boolean,
): List<WhispererFollowup> {
    // Visual melee remains the first branch even when enrage begins before the form expires.
    if (primary == Dt2AttackStyle.MELEE)
        return listOf(WhispererFollowup(primary, 1, projectile = false))
    if (enraged) return emptyList()
    val opposite =
        if (primary == Dt2AttackStyle.MAGIC) Dt2AttackStyle.RANGED else Dt2AttackStyle.MAGIC
    val styles =
        when {
            startedSpecials >= 3 -> listOf(opposite, primary)
            startedSpecials >= 1 -> listOf(primary, opposite)
            else -> listOf(primary, primary)
        }
    return styles.mapIndexed { index, style -> WhispererFollowup(style, index + 1) }
}

internal const val WHISPERER_INACTIVE = "inactive"
internal const val WHISPERER_MELEE = "melee"
internal const val WHISPERER_ATTACK_PARAMETERS = "whisperer.primary-impact"

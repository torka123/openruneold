package org.rsmod.content.minigames.toa

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.bosses.spec.*
import org.rsmod.api.random.GameRandom

/** Ordinary selection only; the existing split launcher retains its fixed arena anchors. */
internal fun zebakBossSpec(): BossSpec =
    BossSpec(
        npcTypes =
            TOA_ZEBAK_BOSS_FORMS.map { id ->
                requireNotNull(RSCM.getReverseMapping(RSCMType.NPC, id))
            },
        stats = BossStats(attackRate = ZebakTuning.BASE_ATTACK_INTERVAL),
        abilities =
            mapOf(
                "melee" to Effect.External(TOA_ZEBAK_ORDINARY_LAUNCH, ZebakAttackStyle.MELEE),
                "distance" to
                    Effect.Choose(
                        Selector.WeightedRandom(
                            listOf(WeightedRef("ranged"), WeightedRef("magic")),
                            noRepeatBias = 1.0,
                        ),
                        mapOf(
                            "ranged" to
                                Effect.External(TOA_ZEBAK_ORDINARY_LAUNCH, ZebakAttackStyle.RANGED),
                            "magic" to
                                Effect.External(TOA_ZEBAK_ORDINARY_LAUNCH, ZebakAttackStyle.MAGIC),
                        ),
                    ),
            ),
        phases =
            mapOf(
                "ordinary" to
                    PhaseSpec(
                        "ordinary",
                        lockMovement = true,
                        selector =
                            Selector.Conditional(
                                listOf(Condition.WithinMeleeRange to "melee"),
                                fallback = "distance",
                            ),
                    )
            ),
        triggers = emptyList(),
    )

/** All Zebak rolls share the retained encounter RNG, including hazard and split-time rolls. */
internal class ZebakSelectionRandom(private val state: ZebakEncounter) : GameRandom {
    override fun of(maxExclusive: Int): Int = state.nextInt(maxExclusive)

    override fun of(minInclusive: Int, maxInclusive: Int): Int =
        minInclusive + state.nextInt(maxInclusive - minInclusive + 1)

    override fun randomDouble(): Double = state.nextInt(Int.MAX_VALUE).toDouble() / Int.MAX_VALUE
}

internal val TOA_ZEBAK_BOSS_FORMS = setOf(ZebakAssets.BODY, ZebakAssets.ENRAGED_BODY)
internal const val TOA_ZEBAK_ORDINARY_LAUNCH = "toa.zebak-ordinary-launch"
internal const val TOA_ZEBAK_DISPATCH = "toa.zebak-ordinary-dispatch"

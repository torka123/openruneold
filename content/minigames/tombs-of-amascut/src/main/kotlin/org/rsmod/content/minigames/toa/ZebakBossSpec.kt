package org.rsmod.content.minigames.toa

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.bosses.spec.*
import org.rsmod.api.random.GameRandom

/** Declared special edges retain the existing explicitly dispatched LateCycle boundaries. */
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
        phases = zebakPhases(),
        triggers = emptyList(),
    )

private fun zebakPhases(): Map<String, PhaseSpec> {
    val ordinary =
        Selector.Conditional(listOf(Condition.WithinMeleeRange to "melee"), fallback = "distance")
    fun phase(current: ZebakPhase, duration: Int? = null, next: ZebakPhase? = null): PhaseSpec =
        PhaseSpec(
            current.name,
            lockMovement = true,
            exitAfter = duration,
            nextPhase = next?.name,
            selector = if (current.admitsOrdinary) ordinary else Selector.WeightedRandom(),
        )
    return listOf(
            phase(ZebakPhase.EMERGING, ZebakTuning.EMERGE_TICKS, ZebakPhase.NORMAL),
            phase(ZebakPhase.NORMAL),
            phase(ZebakPhase.ENRAGED),
            phase(ZebakPhase.PREP_ROAR, ZebakTuning.ROAR_START_DELAY, ZebakPhase.ROAR_START),
            phase(
                ZebakPhase.ROAR_START,
                ZebakTuning.ROAR_FIRST_PULSE_DELAY,
                ZebakPhase.ROAR_PULSE_1,
            ),
            phase(
                ZebakPhase.ROAR_PULSE_1,
                ZebakTuning.ROAR_PULSE_INTERVAL,
                ZebakPhase.ROAR_PULSE_2,
            ),
            phase(
                ZebakPhase.ROAR_PULSE_2,
                ZebakTuning.ROAR_PULSE_INTERVAL,
                ZebakPhase.ROAR_PULSE_3,
            ),
            phase(ZebakPhase.ROAR_PULSE_3, ZebakTuning.ROAR_ROCK_CLEANUP_DELAY, ZebakPhase.NORMAL),
            phase(ZebakPhase.PREP_WAVES, ZebakTuning.WAVE_CALL_DELAY, ZebakPhase.WAVE_START),
            phase(
                ZebakPhase.WAVE_START,
                ZebakTuning.WAVE_FIRST_ROW_DELAY - ZebakTuning.WAVE_CALL_DELAY,
                ZebakPhase.WAVE_ROW_1,
            ),
            phase(ZebakPhase.WAVE_ROW_1, ZebakTuning.WAVE_ROW_INTERVAL, ZebakPhase.WAVE_ROW_2),
            phase(ZebakPhase.WAVE_ROW_2, ZebakTuning.WAVE_ROW_INTERVAL, ZebakPhase.WAVE_ROW_3),
            phase(ZebakPhase.WAVE_ROW_3),
        )
        .associateBy(PhaseSpec::name)
}

/** Payload identity only: timing and edges are declared in [zebakBossSpec]. */
internal enum class ZebakPhase(
    val state: ZebakState,
    val admitsOrdinary: Boolean = false,
    val roarPulse: Int = 0,
    val waveStage: Int = 0,
) {
    EMERGING(ZebakState.EMERGING),
    NORMAL(ZebakState.NORMAL, true),
    ENRAGED(ZebakState.ENRAGED, true),
    PREP_ROAR(ZebakState.PREP_ROAR, true),
    ROAR_START(ZebakState.ROAR),
    ROAR_PULSE_1(ZebakState.ROAR, roarPulse = 1),
    ROAR_PULSE_2(ZebakState.ROAR, roarPulse = 2),
    ROAR_PULSE_3(ZebakState.ROAR, roarPulse = 3),
    PREP_WAVES(ZebakState.PREP_WAVES, true),
    WAVE_START(ZebakState.WAVES, true),
    WAVE_ROW_1(ZebakState.WAVES, true, waveStage = 1),
    WAVE_ROW_2(ZebakState.WAVES, true, waveStage = 2),
    WAVE_ROW_3(ZebakState.WAVES, true, waveStage = 3),
}

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

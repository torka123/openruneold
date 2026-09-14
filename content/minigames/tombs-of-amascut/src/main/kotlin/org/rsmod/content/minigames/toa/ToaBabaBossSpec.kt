package org.rsmod.content.minigames.toa

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.bosses.spec.*
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.types.MeleeAttackType

/** Native Ba-Ba phases and accepted ordinary launches; spatial payloads remain raid-owned. */
internal fun toaBabaBossSpec(): BossSpec =
    BossSpec(
        npcTypes =
            TOA_BABA_BOSS_FORMS.map { id ->
                requireNotNull(RSCM.getReverseMapping(RSCMType.NPC, id))
            },
        stats = BossStats(attackRate = BabaTuning.ATTACK_INTERVAL.value),
        abilities =
            mapOf(
                "melee" to
                    Effect.NpcAttack(
                        attack = CombatAttack.NpcMelee(MeleeAttackType.Crush),
                        animation =
                            requireNotNull(
                                RSCM.getReverseMapping(RSCMType.SEQ, BabaAssets.MELEE_ATTACK)
                            ),
                        parameters = TOA_BABA_ATTACK_PARAMETERS,
                    )
            ),
        phases =
            BABA_NATIVE_PHASES.entries.associate { (state, phase) ->
                phase to
                    PhaseSpec(
                        phase,
                        selector =
                            Selector.Rotation(
                                if (state == BabaState.NORMAL_COMBAT) listOf("melee")
                                else emptyList()
                            ),
                    )
            },
        triggers = emptyList(),
    )

internal val TOA_BABA_BOSS_FORMS =
    setOf(BabaAssets.BABA, BabaAssets.BABA_COFFIN, BabaAssets.BABA_DIGGING)

internal const val TOA_BABA_ATTACK_PARAMETERS = "toa.baba-ordinary"

package org.rsmod.content.bosses.dt2

import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.spec.*
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext

/** StartCycle dispatch uses the registered native owner, with specialized mechanics retained. */
internal fun registerDukeBossCombat(
    context: ScriptContext,
    deps: BossDeps,
    canTarget: (Npc, Player) -> Boolean,
    launch: (Npc, Player, DukeAction) -> Unit,
    modifyHit: NpcHitEvents.Modify.() -> Unit,
) {
    for (action in DukeAction.entries) {
        deps.extensionRegistry.register(dukeHandler(action)) { _, npc, target, _ ->
            launch(npc, target, action)
        }
    }
    BossCombat.register(
        context,
        dukeBossSpec(),
        deps,
        ownerSlug = "duke-sucellus",
        canTarget = canTarget,
        onModifyHit = modifyHit,
    )
}

/**
 * Each declarative interval latches its gas spacing until GAS selects the next interval. Native
 * rotation cursors retain gaze progress across HP changes; content holds no fight selector.
 */
internal fun dukeBossSpec(): BossSpec {
    val phases = linkedMapOf(DUKE_INACTIVE to PhaseSpec(DUKE_INACTIVE, lockMovement = true))
    val abilities = linkedMapOf<String, Effect>()
    for (awakened in listOf(false, true)) {
        val mode = if (awakened) "awakened" else "normal"
        val maximum = if (awakened) 1697 else 485
        val enrage = if (awakened) 440 else 121
        fun atMost(hp: Int): Condition =
            Condition.HpBelow(hp.toDouble() / maximum) or Condition.HpExact(hp)
        val rate =
            Effect.Choose(
                Selector.Conditional(listOf(atMost(enrage) to "enraged"), "normal"),
                mapOf("enraged" to Effect.SetAttackRate(4), "normal" to Effect.SetAttackRate(5)),
            )
        for (action in listOf(DukeAction.STANDARD, DukeAction.GAZE)) {
            abilities["$mode.${action.name}"] =
                Effect.Sequence(listOf(rate, Effect.External(dukeHandler(action))))
        }
        for (gazeProgress in 1..4) {
            abilities["$mode.GAS.$gazeProgress"] =
                Effect.Sequence(
                    listOf(
                        rate,
                        Effect.External(dukeHandler(DukeAction.GAS)),
                        Effect.Choose(
                            Selector.Conditional(
                                listOf(
                                    atMost(enrage) to "three",
                                    atMost(maximum * 3 / 4) to "four",
                                ),
                                "five",
                            ),
                            mapOf(
                                "three" to
                                    Effect.TransitionTo(
                                        dukeIntervalPhase(awakened, 3, gazeProgress)
                                    ),
                                "four" to
                                    Effect.TransitionTo(
                                        dukeIntervalPhase(awakened, 4, gazeProgress)
                                    ),
                                "five" to
                                    Effect.TransitionTo(
                                        dukeIntervalPhase(awakened, 5, gazeProgress)
                                    ),
                            ),
                        ),
                    )
                )
        }
        fun interval(spacing: Int, initialGazeProgress: Int) {
            val (sequence, nextGazeProgress) = dukeIntervalSequence(spacing, initialGazeProgress)
            val name = dukeIntervalPhase(awakened, spacing, initialGazeProgress)
            phases[name] =
                PhaseSpec(
                    name,
                    lockMovement = true,
                    attackRate = 5,
                    selector =
                        Selector.Rotation(
                            sequence.map { action ->
                                if (action == DukeAction.GAS) "$mode.GAS.$nextGazeProgress"
                                else "$mode.${action.name}"
                            }
                        ),
                )
        }
        interval(8, 0)
        for (spacing in 3..5) for (gazeProgress in 1..4) interval(spacing, gazeProgress)
    }
    return BossSpec(
        npcTypes = listOf(Dt2Boss.DUKE.normalNpc),
        stats = BossStats(attackRate = 5),
        abilities = abilities,
        phases = phases,
        triggers = emptyList(),
    )
}

/** Compiles a bounded static sequence once; no encounter state or RNG lives in this generator. */
private fun dukeIntervalSequence(
    spacing: Int,
    initialGazeProgress: Int,
): Pair<List<DukeAction>, Int> {
    val sequence = mutableListOf<DukeAction>()
    var gazeProgress = initialGazeProgress
    var standards = 0
    var afterGaze = false
    while (true) {
        when {
            gazeProgress == 5 -> {
                sequence += DukeAction.GAZE
                gazeProgress = 0
                afterGaze = true
            }
            standards >= spacing && !afterGaze -> {
                sequence += DukeAction.GAS
                check(gazeProgress in 1..4)
                return sequence to gazeProgress
            }
            else -> {
                sequence += DukeAction.STANDARD
                gazeProgress++
                standards++
                afterGaze = false
            }
        }
    }
}

private fun dukeHandler(action: DukeAction): String = "duke.${action.name.lowercase()}"

private fun dukeIntervalPhase(awakened: Boolean, spacing: Int, gazeProgress: Int): String =
    "${if (awakened) "awakened" else "normal"}.gas$spacing.gaze$gazeProgress"

internal fun dukeInitialPhase(awakened: Boolean): String = dukeIntervalPhase(awakened, 8, 0)

internal const val DUKE_INACTIVE = "inactive"

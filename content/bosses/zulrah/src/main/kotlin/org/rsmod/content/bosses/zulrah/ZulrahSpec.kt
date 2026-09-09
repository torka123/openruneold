package org.rsmod.content.bosses.zulrah

import org.rsmod.api.bosses.spec.*
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.combat.commons.types.RangedAttackType

/** Stable declarative phase identity; the original rotation data remains authoritative. */
internal data class ZulrahSurface(val rotation: Int, val phase: Int) {
    val data
        get() = ZulrahRotations.byNumber(rotation).phases[phase - 1]

    val key
        get() = "r$rotation-p$phase"
}

internal data class ZulrahTail(val left: Boolean)

/** Ordinary attacks and exact counted action order share the native BossEncounter selector. */
internal fun zulrahCombatSpec(): BossSpec {
    val abilities = linkedMapOf<String, Effect>()
    val phases = linkedMapOf("dormant" to PhaseSpec("dormant", lockMovement = true))
    for (rotation in ZulrahRotations.all) {
        for (phase in rotation.phases) {
            val surface = ZulrahSurface(rotation.number, phase.number)
            val key = surface.key
            val sequence = mutableListOf<String>()
            for ((index, action) in phase.actions.withIndex()) {
                val count =
                    when (action) {
                        is ZulrahAction.StandardAttack -> action.count
                        is ZulrahAction.JadAttack -> action.count
                        is ZulrahAction.TailSwipe -> action.count
                        else -> 1
                    }
                repeat(count) { component ->
                    val ability = "$key-action$index-$component"
                    val rate =
                        when {
                            action is ZulrahAction.TailSwipe ->
                                ZULRAH_TAIL_WINDUP_TICKS + ZULRAH_TAIL_RECOVERY_TICKS
                            action is ZulrahAction.Delay -> action.ticks
                            index == phase.actions.lastIndex && component == count - 1 ->
                                ZULRAH_PHASE_END_TICKS
                            else -> ZULRAH_ATTACK_RATE
                        }
                    val effect =
                        when (action) {
                            is ZulrahAction.Delay -> Effect.NoOp
                            is ZulrahAction.StandardAttack ->
                                when (phase.form) {
                                    ZulrahForm.SERPENTINE ->
                                        standardAttack(ZulrahAttackStyle.RANGED)
                                    ZulrahForm.TANZANITE ->
                                        Effect.Choose(
                                            Selector.WeightedRandom(
                                                WeightedRef("magic", ZulrahRules.BLUE_MAGIC_WEIGHT),
                                                WeightedRef(
                                                    "ranged",
                                                    ZulrahRules.BLUE_RANGED_WEIGHT,
                                                ),
                                                noRepeatBias = 0.0,
                                            ),
                                            mapOf(
                                                "magic" to standardAttack(ZulrahAttackStyle.MAGIC),
                                                "ranged" to standardAttack(ZulrahAttackStyle.RANGED),
                                            ),
                                        )
                                    ZulrahForm.MAGMA ->
                                        error("Magma has no ordinary projectile attack")
                                }
                            is ZulrahAction.JadAttack ->
                                standardAttack(
                                    ZulrahRules.jadStyles(action.count, action.startsWith)[
                                            component]
                                )
                            is ZulrahAction.TailSwipe ->
                                Effect.External("zulrah.tail", ZulrahTail(component % 2 == 0))
                            is ZulrahAction.VenomCloudVolley ->
                                Effect.External("zulrah.clouds", action)
                            is ZulrahAction.SnakelingVolley -> Effect.External("zulrah.egg", action)
                        }
                    abilities[ability] = Effect.Sequence(listOf(Effect.SetAttackRate(rate), effect))
                    sequence += ability
                }
            }
            val dive = "$key-dive"
            abilities[dive] =
                Effect.Sequence(
                    listOf(
                        Effect.SetAttackRate(ZULRAH_ATTACK_RATE),
                        Effect.Anim(ZulrahAssets.DIVE),
                        Effect.TransitionTo("$key-diving"),
                    )
                )
            sequence += dive
            phases[key] =
                PhaseSpec(key, lockMovement = true, selector = Selector.Rotation(sequence))
            val present = "$key-present"
            abilities[present] = Effect.External("zulrah.surface", surface)
            phases["$key-emerging"] =
                PhaseSpec(
                    "$key-emerging",
                    lockMovement = true,
                    exitAfter = ZULRAH_EMERGE_TICKS,
                    nextPhase = key,
                    entry = present,
                )
            phases["$key-spawning"] =
                PhaseSpec(
                    "$key-spawning",
                    lockMovement = true,
                    exitAfter = ZULRAH_INITIAL_EMERGE_TICKS,
                    nextPhase = key,
                )
            phases["$key-diving"] =
                PhaseSpec(
                    "$key-diving",
                    lockMovement = true,
                    exitAfter = ZULRAH_DIVE_TICKS,
                    nextPhase =
                        if (phase.number == rotation.phases.size) "next-family"
                        else "${ZulrahSurface(rotation.number, phase.number + 1).key}-emerging",
                )
        }
    }
    phases["next-family"] =
        PhaseSpec(
            "next-family",
            lockMovement = true,
            selector = Selector.Rotation(listOf("select-next-family")),
        )
    abilities["select-next-family"] =
        Effect.Choose(
            Selector.WeightedRandom(
                ZulrahRotations.all.map { WeightedRef(it.number.toString()) },
                noRepeatBias = 0.0,
            ),
            ZulrahRotations.all.associate { rotation ->
                val next = ZulrahSurface(rotation.number, 2)
                rotation.number.toString() to
                    Effect.Sequence(
                        listOf(
                            Effect.TransitionTo("${next.key}-emerging"),
                            Effect.External("zulrah.surface", next),
                        )
                    )
            },
        )
    phases["dying"] = PhaseSpec("dying", lockMovement = true)
    phases["respawning"] = PhaseSpec("respawning", lockMovement = true)
    phases["completed"] = PhaseSpec("completed", lockMovement = true)
    return BossSpec(
        ZulrahAssets.forms,
        BossStats(attackRate = ZULRAH_ATTACK_RATE, aggressionRadius = 32),
        abilities,
        phases,
        emptyList(),
    )
}

private fun standardAttack(style: ZulrahAttackStyle): Effect.NpcAttack =
    Effect.NpcAttack(
        attack =
            if (style == ZulrahAttackStyle.MAGIC) CombatAttack.NpcMagic(ZULRAH_MAX_HIT)
            else CombatAttack.NpcRanged(RangedAttackType.Standard),
        maxHit = ZULRAH_MAX_HIT,
        animation = ZulrahAssets.ATTACK,
        parameters = "zulrah.standard",
        parameterData = style,
        survivesSourceDeath = true,
    )

internal fun snakelingSpec(magic: Boolean) =
    BossSpec(
        npcTypes =
            listOf(if (magic) ZulrahAssets.SNAKELING_MAGIC else ZulrahAssets.SNAKELING_MELEE),
        stats = BossStats(attackRate = ZULRAH_SNAKELING_ATTACK_RATE),
        abilities =
            mapOf(
                "attack" to
                    Effect.NpcAttack(
                        attack =
                            if (magic) CombatAttack.NpcMagic(ZULRAH_SNAKELING_MAX_HIT)
                            else CombatAttack.NpcMelee(MeleeAttackType.Stab),
                        maxHit = ZULRAH_SNAKELING_MAX_HIT,
                        animation = ZulrahAssets.SNAKELING_ATTACK,
                        parameters = "zulrah.snakeling",
                        parameterData = magic,
                    )
            ),
        phases =
            mapOf("active" to PhaseSpec("active", selector = Selector.Rotation(listOf("attack")))),
        triggers = emptyList(),
    )

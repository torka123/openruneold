package org.rsmod.content.bosses.scorpia

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcMode
import dev.openrune.types.ProjAnimType
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.boss
import org.rsmod.api.bosses.dsl.external
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.bosses.spec.AccuracyMode
import org.rsmod.api.bosses.spec.BossSpec
import org.rsmod.api.bosses.spec.HitType.Melee
import org.rsmod.api.bosses.spec.HitType.Ranged
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.npc.heal
import org.rsmod.api.player.stat.statSub
import org.rsmod.game.entity.Npc
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.plugin.scripts.ScriptContext

public class Scorpia
@Inject
constructor(deps: BossDeps) : BossPluginScript(deps) {
    override fun ScriptContext.startup() {
        registerPrayerDrain()
        registerGuardianSummon()
        BossCombat.register(
            this,
            spec,
            deps,
            ownerSlug = SCORPIA_SLUG,
            onModifyHit = {
                val encounter = deps.encounterRegistry.of(npc)
                if (npc.hitpoints <= SCORPIA_GUARDIAN_TRIGGER_HP && encounter.fireOnce(SCORPIA_GUARDIAN_MARK)) {
                    summonGuardians(npc)
                }
            },
        )
        BossCombat.register(this, SCORPIA_OFFSPRING_SPEC, deps, ownerSlug = SCORPIA_OFFSPRING_SLUG)
    }

    private fun registerPrayerDrain() {
        deps.extensionRegistry.register(PRAYER_DRAIN_EXTENSION) { _, _, target, _ ->
            if (target.vars[PROTECT_FROM_MELEE_VARBIT] == 1) {
                target.statSub("stat.prayer", constant = SCORPIA_PRAYER_DRAIN, percent = 0)
            }
        }
    }

    private fun registerGuardianSummon() {
        val guardianType =
            ServerCacheManager.getNpc(SCORPIA_GUARDIAN.asRSCM(RSCMType.NPC))
                ?: error("Missing npc type: $SCORPIA_GUARDIAN")
        guardianNpcType = guardianType
        deps.extensionRegistry.register(GUARDIAN_SUMMON_EXTENSION) { _, boss, _, _ ->
            summonGuardians(boss)
        }
    }

    private var guardianNpcType: dev.openrune.types.NpcServerType? = null

    private fun summonGuardians(boss: Npc) {
        val guardianType = guardianNpcType ?: return
        boss.anim(SCORPIA_SUMMON_ANIM)
        val guardians = GUARDIAN_SPAWN_OFFSETS.map { (x, z) ->
            val preferred = boss.coords.translate(x, z)
            val spawn = if (!deps.collision.isWalkBlocked(preferred)) preferred else boss.coords.translate(0, -2)
            Npc(guardianType, spawn).also { guardian ->
                guardian.mode = NpcMode.None
                guardian.spawnOwner = boss.spawnOwner
                guardian.suppressRewards = boss.suppressRewards
                deps.npcRepo.add(guardian, Int.MAX_VALUE)
                guardian.respawns = false
                deps.encounterRegistry.of(boss).own(guardian)
            }
        }
        startGuardianHealing(boss, guardians)
    }

    private fun startGuardianHealing(boss: Npc, guardians: List<Npc>) {
        val missedHealTicks = guardians.associateWith { 0 }.toMutableMap()

        fun removeAliveGuardian(guardian: Npc) {
            if (guardian.isSlotAssigned && guardian.hitpoints > 0) {
                deps.npcRepo.del(guardian, Int.MAX_VALUE)
            }
        }

        fun tick() {
            deps.worldQueues.add(1) {
                if (!boss.isSlotAssigned || boss.hitpoints <= 0) {
                    guardians.forEach(::removeAliveGuardian)
                    return@add
                }

                var activeGuardians = 0
                for (guardian in guardians) {
                    if (!guardian.isSlotAssigned || guardian.hitpoints <= 0) continue
                    activeGuardians++

                    if (guardian.isWithinDistance(boss, SCORPIA_HEAL_RANGE)) {
                        missedHealTicks[guardian] = 0
                        boss.heal(SCORPIA_HEAL_PER_GUARDIAN)
                        if (deps.mapClock.cycle % SCORPIA_HEAL_VISUAL_INTERVAL == 0) {
                            guardian.spotanim(GUARDIAN_HEAL_CAST)
                            deps.worldRepo.projAnimSourced(
                                guardian,
                                boss,
                                GUARDIAN_HEAL_TRAVEL_TYPE,
                                GUARDIAN_PROJECTILE,
                            )
                            boss.spotanim(GUARDIAN_HEAL_IMPACT)
                        }
                    } else {
                        val missed = missedHealTicks.getValue(guardian) + 1
                        missedHealTicks[guardian] = missed
                        if (missed >= GUARDIAN_DESPAWN_TICKS) {
                            removeAliveGuardian(guardian)
                            activeGuardians--
                        } else if (deps.mapClock.cycle % 3 == 0) {
                            guardian.walk(boss.coords)
                        }
                    }
                }

                if (activeGuardians > 0) tick()
            }
        }

        tick()
    }

    override val spec: BossSpec = SCORPIA_SPEC
}

internal val SCORPIA_SPEC: BossSpec =
    boss(SCORPIA_NPC) {
        stats(attackRate = 4, aggressionRadius = 8)

        val melee =
            ability("melee") {
                npcAttackAnim()
                hit {
                    damage(0..SCORPIA_MAX_HIT).roll()
                    type(Melee)
                    meleeType = MeleeAttackType.Stab
                    poisonOnImpact(damage = SCORPIA_POISON_DAMAGE, chance = 1, outOf = 4)
                }
                include(external(PRAYER_DRAIN_EXTENSION))
            }
        ability("summon_guardians") { include(external(GUARDIAN_SUMMON_EXTENSION)) }

        phase("combat") {
            weightedSelectorRandom(noRepeatBias = 0.0) {
                +random(melee, weight = 1)
            }
        }
    }

internal val SCORPIA_OFFSPRING_SPEC: BossSpec =
    boss(SCORPIA_OFFSPRING_NPC) {
        stats(attackRate = SCORPIA_OFFSPRING_ATTACK_RATE, aggressionRadius = 8)
        val ranged =
            ability("ranged") {
                npcAttackAnim()
                hit {
                    damage(0..SCORPIA_OFFSPRING_MAX_HIT).roll()
                    type(Ranged)
                    accuracy = AccuracyMode.Always
                    poisonOnImpact(
                        damage = SCORPIA_OFFSPRING_POISON_DAMAGE,
                        chance = 1,
                        outOf = 4,
                    )
                }
            }
        phase("combat") {
            weightedSelectorRandom(noRepeatBias = 0.0) { +random(ranged, weight = 1) }
        }
    }

internal const val SCORPIA_SLUG: String = "scorpia"
internal const val SCORPIA_OFFSPRING_SLUG: String = "scorpia-offspring"
internal const val SCORPIA_NPC: String = "npc.scorpia"
internal const val SCORPIA_OFFSPRING_NPC: String = "npc.scorpia_minion"
internal const val SCORPIA_GUARDIAN: String = "npc.scorpia_guardian"
internal const val SCORPIA_MAX_HIT: Int = 16
internal const val SCORPIA_POISON_DAMAGE: Int = 20
internal const val SCORPIA_OFFSPRING_MAX_HIT: Int = 2
internal const val SCORPIA_OFFSPRING_POISON_DAMAGE: Int = 6
internal const val SCORPIA_OFFSPRING_ATTACK_RATE: Int = 6
internal const val SCORPIA_PRAYER_DRAIN: Int = 2
internal const val SCORPIA_GUARDIAN_TRIGGER_HP: Int = 99
private const val SCORPIA_GUARDIAN_MARK: String = "guardians-summoned"
internal const val SCORPIA_HEAL_RANGE: Int = 3
internal const val SCORPIA_HEAL_PER_GUARDIAN: Int = 1
internal const val GUARDIAN_DESPAWN_TICKS: Int = 25

private const val PRAYER_DRAIN_EXTENSION: String = "scorpia.prayer-drain"
private const val GUARDIAN_SUMMON_EXTENSION: String = "scorpia.summon-guardians"
private const val PROTECT_FROM_MELEE_VARBIT: String = "varbit.prayer_protectfrommelee"
private const val SCORPIA_SUMMON_ANIM: String = "seq.scorpia_talk"
private const val GUARDIAN_HEAL_CAST: String = "spotanim.telegrab_casting"
private const val GUARDIAN_HEAL_TRAVEL: String = "spotanim.telegrab_travel"
private const val GUARDIAN_HEAL_IMPACT: String = "spotanim.telegrab_impact"
private const val SCORPIA_HEAL_VISUAL_INTERVAL: Int = 4
private val GUARDIAN_SPAWN_OFFSETS = listOf(-2 to 2, 6 to 2)
private val GUARDIAN_HEAL_TRAVEL_TYPE by lazy {
    SpotanimType(GUARDIAN_HEAL_TRAVEL.asRSCM(RSCMType.SPOTANIM))
}
private val GUARDIAN_PROJECTILE =
    ProjAnimType(
        startHeight = 20,
        endHeight = 20,
        delay = 0,
        angle = 15,
        lengthAdjustment = 30,
        progress = 0,
        stepMultiplier = 5,
    )

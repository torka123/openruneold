package org.rsmod.content.bosses.bryophyta

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.boss
import org.rsmod.api.bosses.dsl.external
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.api.bosses.dsl.whenever
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossEncounter
import org.rsmod.api.bosses.runtime.EffectInterpreter
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.bosses.spec.BossSpec
import org.rsmod.api.bosses.spec.Condition
import org.rsmod.api.bosses.spec.HitType.Magic
import org.rsmod.api.bosses.spec.HitType.Melee
import org.rsmod.api.bosses.spec.ProtectionPrayer
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.script.onModifyNpcHit
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.game.map.translate
import org.rsmod.plugin.scripts.ScriptContext

public class Bryophyta
@Inject
constructor(
    deps: BossDeps,
    private val instances: InstanceManager,
) : BossPluginScript(deps) {

    override fun ScriptContext.startup() {
        BossCombat.register(
            this,
            spec,
            deps,
            ownerSlug = BRYOPHYTA_SLUG,
            onModifyHit = {
                if (!hit.isFromPlayer) return@register
                val encounter = deps.encounterRegistry.of(npc)
                if (aliveGrowthlings(encounter) > 0) hit.damage = 0
            },
        )
        registerGrowthlingSummon()
        registerGrowthlingDamageRule()
    }

    private fun ScriptContext.registerGrowthlingSummon() {
        val type =
            ServerCacheManager.getNpc(GROWTHLING_NPC.asRSCM(RSCMType.NPC))
                ?: error("Missing npc type: $GROWTHLING_NPC")
        deps.extensionRegistry.register(GROWTHLING_SUMMON_EXTENSION) { access, boss, target, _ ->
            val encounter = deps.encounterRegistry.of(boss)
            val mechanic =
                encounter.state(GROWTHLING_STATE_KEY) { GrowthlingMechanicState() }
            if (!canSummonGrowthlings(aliveGrowthlings(encounter), deps.mapClock.cycle, mechanic.nextSummonTick)) {
                val encounter = deps.encounterRegistry.of(boss)
                val normal = spec.abilities.getValue(NORMAL_ATTACK_ABILITY)
                EffectInterpreter(boss, target, spec, encounter, deps).run(access, normal)
                return@register
            }

            val candidates =
                GROWTHLING_OFFSETS
                    .map { (x, z) -> boss.coords.translate(x, z) }
                    .filter {
                        it.level == boss.spawnCoords.level && it.chebyshevDistance(boss.spawnCoords) <= GROWTHLING_ROOM_RADIUS
                    }
                    .filterNot { deps.collision.isWalkBlocked(it) }
                    .toMutableList()
            repeat(GROWTHLING_COUNT) {
                val coords =
                    if (candidates.isEmpty()) boss.coords
                    else candidates.removeAt(deps.random.of(candidates.size))
                val growthling = Npc(type, coords)
                growthling.mode = type.defaultMode
                growthling.spawnOwner = boss.spawnOwner
                growthling.suppressRewards = true
                deps.npcRepo.add(growthling, Int.MAX_VALUE)
                growthling.respawns = false
                encounter.own(growthling)
                instances.instanceForNpc(boss)?.let { instanceId ->
                    instances.attachNpc(instanceId, growthling)
                }
            }
            mechanic.nextSummonTick = deps.mapClock.cycle + GROWTHLING_COOLDOWN_TICKS
        }
    }

    private fun aliveGrowthlings(encounter: BossEncounter): Int =
        encounter.owned().count { it.isSlotAssigned && it.hitpoints > 0 && it.type.internalName == GROWTHLING_NPC }

    private fun ScriptContext.registerGrowthlingDamageRule() {
        val growthlingType =
            ServerCacheManager.getNpc(GROWTHLING_NPC.asRSCM(RSCMType.NPC))
                ?: error("Missing npc type: $GROWTHLING_NPC")

        onModifyNpcHit(growthlingType) {
            if (!hit.isFromPlayer) return@onModifyNpcHit
            val sourceUid = hit.sourceUid ?: return@onModifyNpcHit
            val attacker = PlayerUid(sourceUid).resolve(deps.playerList) ?: return@onModifyNpcHit
            if (attacker.hasGrowthlingFinisherEquipped()) {
                hit.damage = npc.hitpoints.coerceAtLeast(1)
            }
        }
    }

    private fun Player.hasGrowthlingFinisherEquipped(): Boolean =
        "content.woodcutting_axe" in worn || MAGIC_SECATEURS in worn

    override val spec: BossSpec = BRYOPHYTA_SPEC
}

internal data class GrowthlingMechanicState(var nextSummonTick: Int = 0)

internal fun canSummonGrowthlings(active: Int, currentTick: Int, nextSummonTick: Int): Boolean =
    active == 0 && currentTick >= nextSummonTick

internal val BRYOPHYTA_SPEC: BossSpec =
    boss(BRYOPHYTA_NPC) {
        stats(attackRate = 6, aggressionRadius = 8)

        val melee =
            ability("melee") {
                npcAttackAnim()
                hit {
                    damage(0..16).roll()
                    type(Melee)
                    protectionPrayer = ProtectionPrayer.Bypass
                }
            }
        val magic =
            ability("magic") {
                npcAttackAnim()
                hit {
                    damage(0..16).roll()
                    type(Magic)
                    delay = 1
                    protectionPrayer = ProtectionPrayer.Bypass
                }
            }
        val normal =
            ability(NORMAL_ATTACK_ABILITY) {
                include(
                    whenever(
                        Condition.WithinMeleeRange,
                        Effect.Run(melee.name),
                        Effect.Run(magic.name),
                    )
                )
            }
        val summon =
            ability("summon_growthlings") {
                include(external(GROWTHLING_SUMMON_EXTENSION))
            }

        phase("combat") {
            weightedSelectorRandom(noRepeatBias = 0.0) {
                +random(normal, weight = 4)
                +random(summon, weight = 1)
            }
        }
    }

internal const val BRYOPHYTA_SLUG: String = "bryophyta"
internal const val BRYOPHYTA_NPC: String = "npc.gb_mossgiant"
internal const val GROWTHLING_NPC: String = "npc.gb_growthling"
internal const val BRYOPHYTA_KEY: String = "obj.mossy_key"
internal const val BRYOPHYTA_CHEST_CLOSED: String = "loc.mossykey_chest_closed"
internal const val BRYOPHYTA_CHEST_OPEN: String = "loc.mossykey_chest_open"
private const val GROWTHLING_SUMMON_EXTENSION: String = "bryophyta.summon_growthlings"
private const val GROWTHLING_COUNT: Int = 3
private const val GROWTHLING_COOLDOWN_TICKS: Int = 20
private const val GROWTHLING_ROOM_RADIUS: Int = 10
private const val GROWTHLING_STATE_KEY: String = "bryophyta.growthlings"
private const val NORMAL_ATTACK_ABILITY: String = "normal"
private const val MAGIC_SECATEURS: String = "obj.fairy_enchanted_secateurs"
private val GROWTHLING_OFFSETS =
    listOf(-3 to -2, -3 to 2, 0 to -3, 0 to 3, 3 to -2, 3 to 2)

package org.rsmod.content.bosses.kraken

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.boss
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.spec.BossSpec
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.api.bosses.spec.ProtectionPrayer
import org.rsmod.api.bosses.spec.Selector
import org.rsmod.api.combat.NpcProjectile
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.types.RangedAttackType
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.player.output.mes
import org.rsmod.api.script.onNpcQueue
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class KrakenCombat
@Inject
constructor(
    private val whirlpools: KrakenWhirlpools,
    private val death: NpcDeath,
    private val deps: BossDeps,
) : PluginScript() {
    override fun ScriptContext.startup() {
        BossCombat.register(this, krakenBossCombatSpec(), deps, ownerSlug = "kraken")
        BossCombat.register(this, krakenTentacleCombatSpec(), deps, ownerSlug = "kraken-tentacle")
        for (type in listOf(KrakenAssets.BOSS, KrakenAssets.BOSS_WHIRLPOOL)) {
            onNpcQueue(requireKrakenNpc(type), "queue.death") {
                val owner = whirlpools.owner(npc)
                val dropTile = whirlpools.beginBossDeath(npc)
                if (dropTile == null) {
                    death.deathNoDrops(this)
                } else {
                    death.deathWithDrops(this, dropTile)
                    owner?.mes(
                        "The Kraken sinks beneath the water. Its loot washes up at your feet."
                    )
                }
            }
        }
        for (type in listOf(KrakenAssets.TENTACLE, KrakenAssets.TENTACLE_WHIRLPOOL)) {
            onNpcQueue(requireKrakenNpc(type), "queue.death") { death.deathNoDrops(this) }
        }
    }
}

internal fun krakenBossCombatSpec(): BossSpec =
    krakenCombatSpec(
        KrakenAssets.BOSS,
        KrakenAssets.BOSS_WHIRLPOOL,
        CombatAttack.NpcMagic(KrakenRules.BOSS_MAX_HIT),
        KrakenRules.BOSS_MAX_HIT,
        KrakenAssets.BOSS_PROJECTILE,
        KrakenAssets.BOSS_IMPACT,
    )

internal fun krakenTentacleCombatSpec(): BossSpec =
    krakenCombatSpec(
        KrakenAssets.TENTACLE,
        KrakenAssets.TENTACLE_WHIRLPOOL,
        CombatAttack.NpcRanged(RangedAttackType.Standard),
        KrakenRules.TENTACLE_MAX_HIT,
        KrakenAssets.TENTACLE_PROJECTILE,
        KrakenAssets.TENTACLE_IMPACT,
    )

private fun krakenCombatSpec(
    npc: String,
    dormant: String,
    attack: CombatAttack.NpcAttack,
    maximum: Int,
    travel: String,
    impact: String,
): BossSpec =
    boss(npc, dormant) {
        stats(attackRate = KrakenRules.ATTACK_RATE_TICKS, aggressionRadius = 10)
        ability(
            "attack",
            Effect.NpcAttack(
                attack = attack,
                maxHit = maximum,
                projectile =
                    NpcProjectile(
                        travel,
                        requireNotNull(
                            ServerCacheManager.getProjectile(
                                KrakenAssets.MAGIC_PROJECTILE_CONTROLLER.asRSCM(RSCMType.PROJANIM)
                            )
                        ),
                    ),
                impact = impact,
                protectionPrayer = ProtectionPrayer.Bypass,
            ),
        )
        phase("active", lockMovement = true) { selector = Selector.Rotation(listOf("attack")) }
    }

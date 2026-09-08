package org.rsmod.content.bosses.kbd

import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.api.bosses.spec.ImpactProtection
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript

class KingBlackDragon @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val bossSlug: String = "king-black-dragon"

    override val spec = KING_BLACK_DRAGON_SPEC
}

internal val KING_BLACK_DRAGON_SPEC = boss("npc.king_dragon") {

        stats(attackRate = 4, aggressionRadius = 8)

        val melee = ability("melee") {
            anim("seq.dragon_attack")
            hit {
                damage(0..25).roll()
                type(Melee)
            }
        }

        val dragonFire = ability("dragonfire") {
            anim("seq.dragon_firebreath_all_attack")
            projectile(
                spotanim = "spotanim.dragon_ranged_fire_attack",
                travel = "projanim.dragonfire",
                hit = Effect.Hit(damage = Roll(0..65), type = KbdDragonfire),
            )
        }

        val poisonBreath = ability("poison_breath") {
            anim("seq.dragon_firebreath_all_attack")
            projectile {
                spotanim = "spotanim.dragon_ranged_toxic_attack"
                travel = "projanim.dragonfire"
                hit {
                    damage(0..50).roll()
                    type(KbdSpecialDragonfire)
                    poisonOnImpact(
                        damage = 8,
                        chance = 1,
                        outOf = 2,
                        protection = ImpactProtection.KingBlackDragon,
                    )
                }
            }
        }

        val freezeBreath = ability("freeze_breath") {
            anim("seq.dragon_firebreath_all_attack")
            projectile {
                spotanim = "spotanim.dragon_ranged_ice_attack"
                travel = "projanim.dragonfire"
                hit {
                    damage(0..50).roll()
                    type(KbdSpecialDragonfire)
                    freezeOnImpact(
                        ticks = 10,
                        chance = 1,
                        outOf = 2,
                        protection = ImpactProtection.KingBlackDragon,
                    )
                }
            }
        }

        val shockBreath = ability("shock_breath") {
            anim("seq.dragon_firebreath_all_attack")
            projectile {
                spotanim = "spotanim.dragon_ranged_lightning_attack"
                travel = "projanim.dragonfire"
                hit {
                    damage(0..50).roll()
                    type(KbdSpecialDragonfire)
                    statDrainOnImpact(
                        "stat.attack",
                        "stat.strength",
                        "stat.defence",
                        "stat.ranged",
                        "stat.magic",
                        amount = 2,
                        chance = 1,
                        outOf = 2,
                        protection = ImpactProtection.KingBlackDragon,
                    )
                }
            }
        }

        phase("combat") {
            weightedSelectorRandom {
                +random(melee, weight = 3, requires = WithinMeleeRange)
                +random(dragonFire, weight = 3)
                +random(poisonBreath, weight = 1)
                +random(freezeBreath, weight = 1)
                +random(shockBreath, weight = 1)
            }
        }
}

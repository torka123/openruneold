package org.rsmod.content.bosses.zulrah

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.combat.NpcAttackOverride
import org.rsmod.api.mechanics.toxins.impl.PlayerVenom
import org.rsmod.api.player.hit.modifier.NpcCalculatedProtectionPrayerPlayerHitModifier
import org.rsmod.api.player.output.soundSynth
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitValidity
import org.rsmod.game.proj.ProjAnim

/** Native launch snapshots only. The DSL selects attacks and NvPCombat launches every hit. */
internal class ZulrahAttackParameters
@Inject
constructor(private val encounters: ZulrahEncounterManager) {
    fun standard(npc: Npc, target: Player, style: ZulrahAttackStyle): NpcAttackOverride {
        val magic = style == ZulrahAttackStyle.MAGIC
        val protected =
            target.vars[
                    if (magic) "varbit.prayer_protectfrommagic"
                    else "varbit.prayer_protectfrommissiles"] == 1
        val projectile =
            ProjAnim.fromBoundsToPlayer(
                npc.bounds(),
                target,
                (if (magic) ZulrahAssets.MAGIC_PROJECTILE else ZulrahAssets.RANGED_PROJECTILE)
                    .asRSCM(RSCMType.SPOTANIM),
                ZulrahAssets.ATTACK_PROJECTILE_CONTROLLER,
            )
        var accurate = false
        return NpcAttackOverride(
            projectileFactory = { _, _ -> projectile },
            hitValidity = HitValidity { encounters.validHit(npc, target) },
            hitModifier = NpcCalculatedProtectionPrayerPlayerHitModifier(protected, 100),
            onAttack = {
                target.soundSynth(if (magic) ZULRAH_MAGIC_SEND_SOUND else ZULRAH_RANGED_SEND_SOUND)
            },
            onSuccessfulAttack = { _, _ -> accurate = true },
            onImpact = { _, _ ->
                target.soundSynth(
                    if (magic) ZULRAH_MAGIC_IMPACT_SOUND else ZULRAH_RANGED_IMPACT_SOUND
                )
                if (accurate) PlayerVenom.tryVenom(target)
            },
        )
    }

    fun snakeling(npc: Npc, target: Player, magic: Boolean): NpcAttackOverride {
        val projectile =
            if (magic)
                ProjAnim.fromNpcToPlayer(
                    npc,
                    target,
                    ZulrahAssets.SNAKELING_MAGIC_PROJECTILE.asRSCM(RSCMType.SPOTANIM),
                    requireNotNull(
                        ServerCacheManager.getProjectile(
                            ZulrahAssets.SNAKELING_MAGIC_PROJECTILE_CONTROLLER.asRSCM(
                                RSCMType.PROJANIM
                            )
                        )
                    ),
                )
            else null
        var accurate = false
        return NpcAttackOverride(
            projectileFactory = if (projectile == null) null else { _, _ -> projectile },
            hitValidity = HitValidity { encounters.validHit(npc, target) },
            onAttack = {
                target.soundSynth(
                    if (magic) ZULRAH_RANGED_IMPACT_SOUND else ZULRAH_SNAKELING_IMPACT_SOUND
                )
            },
            onSuccessfulAttack = { _, _ -> accurate = true },
            onImpact = { _, _ ->
                if (magic) target.soundSynth(ZULRAH_SNAKELING_IMPACT_SOUND)
                if (accurate) PlayerVenom.tryVenom(target)
            },
        )
    }
}

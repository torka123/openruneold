package org.rsmod.api.combat.scripts.armour

import jakarta.inject.Inject
import org.rsmod.api.npc.hit.modifier.StandardNpcHitModifier
import org.rsmod.api.npc.hit.queueHit
import org.rsmod.api.player.front
import org.rsmod.api.player.hat
import org.rsmod.api.player.hit.modifier.BypassProtectionPrayerPlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.legs
import org.rsmod.api.player.righthand
import org.rsmod.api.player.torso
import org.rsmod.api.player.worn.EquipmentChecks
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onPlayerHitpointsChanged
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitAttribution
import org.rsmod.game.hit.HitOrigin
import org.rsmod.game.hit.HitType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Implements the Amulet of the damned recoil for a complete Dharok set. */
internal class ArmourRecoilScript
@Inject
constructor(private val random: GameRandom, private val npcHitModifier: StandardNpcHitModifier) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onPlayerHitpointsChanged {
            val damageTaken = oldHitpoints - newHitpoints
            val attacker = source
            if (
                damageTaken <= 0 ||
                    attacker == null ||
                    attacker === player ||
                    (attacker is Npc && attacker.type.internalName in RECOIL_IMMUNE_NPCS) ||
                    hit.type == HitType.Typeless ||
                    !EquipmentChecks.isAmuletOfTheDamned(player.front) ||
                    !EquipmentChecks.isDharokSet(
                        player.hat,
                        player.torso,
                        player.legs,
                        player.righthand,
                    ) ||
                    !random.randomBoolean(PROC_DENOMINATOR)
            ) {
                return@onPlayerHitpointsChanged
            }

            // Each recoil contribution is independently rounded down in the live game.
            val recoil = (damageTaken * RECOIL_PERCENT) / 100
            if (recoil <= 0) {
                return@onPlayerHitpointsChanged
            }

            val context = hit.context.withAttribution(HitAttribution(HitOrigin.Recoil, player.uuid))
            when (attacker) {
                is Npc ->
                    attacker.queueHit(
                        source = player,
                        delay = 1,
                        type = HitType.Typeless,
                        damage = recoil,
                        modifier = npcHitModifier,
                        context = context,
                    )
                is Player ->
                    attacker.queueHit(
                        source = player,
                        delay = 1,
                        type = HitType.Typeless,
                        damage = recoil,
                        modifier = BypassProtectionPrayerPlayerHitModifier,
                        context = context,
                    )
            }
        }
    }

    private companion object {
        const val PROC_DENOMINATOR: Int = 4
        val RECOIL_IMMUNE_NPCS: Set<String> =
            setOf(
                "venenatis",
                "venenatis_singles",
                "callisto",
                "callisto_singles",
                "vetion",
                "vetion_2",
                "vetion_single",
                "vetion_2_single",
            )

        const val RECOIL_PERCENT: Int = 15
    }
}

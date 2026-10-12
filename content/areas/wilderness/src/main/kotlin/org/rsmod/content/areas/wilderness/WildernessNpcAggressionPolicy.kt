package org.rsmod.content.areas.wilderness

import jakarta.inject.Inject
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.area.checker.isInWilderness
import org.rsmod.api.config.refs.CanonicalSlayerCategoryMembership
import org.rsmod.api.game.process.npc.hunt.NpcPlayerHuntProfile
import org.rsmod.api.game.process.npc.hunt.NpcPlayerHuntProfileOverride
import org.rsmod.game.entity.Npc

/** Explicit behavior categories used by the Wilderness NPC aggression audit. */
public enum class WildernessNpcBehavior {
    AGGRESSIVE,
    PASSIVE_RETALIATE_ONLY,
    SCRIPTED_BOSS,
    CONDITIONAL_OR_SUMMON,
    NON_COMBAT,
    DORMANT_OR_TRANSFORMATION,
}

/**
 * Location-specific aggression policy. NPC IDs reused outside the Wilderness retain their cache
 * behavior because this provider returns `null` outside the authoritative Wilderness area.
 */
public class WildernessNpcAggressionPolicy
@Inject
constructor(private val areaChecker: AreaChecker) : NpcPlayerHuntProfileOverride {
    override fun resolve(npc: Npc): NpcPlayerHuntProfile? {
        val behavior = classify(npc)
        val inWilderness = npc.coords.isInWilderness(areaChecker)
        if (!shouldApplyProfile(inWilderness, behavior)) {
            return null
        }
        val range = boundedHuntRange(npc.type.huntRange, npc.type.attackRange)
        val mode = if (npc.type.attackRange > 1) AGGRESSIVE_RANGED_MODE else AGGRESSIVE_MELEE_MODE
        return NpcPlayerHuntProfile(modeId = mode, range = range, source = PROFILE_SOURCE)
    }

    public companion object {
        public const val AGGRESSIVE_MELEE_MODE: Int = 6
        public const val AGGRESSIVE_RANGED_MODE: Int = 8
        public const val PROFILE_SOURCE: String = "wilderness-npc-aggression-audit"
        public const val DEFAULT_HUNT_RANGE: Int = 8
        public const val MAX_HUNT_RANGE: Int = 32

        private val slayerNpcIds: Set<Int> =
            CanonicalSlayerCategoryMembership.categoryIds()
                .flatMap { CanonicalSlayerCategoryMembership.npcIds(it).asIterable() }
                .toSet()

        /**
         * Active bosses retain their custom combat scripts; this policy only starts acquisition.
         */
        private val scriptedBossIds: Set<Int> =
            setOf(
                239, // King Black Dragon (only when an authoritative Wilderness area includes it)
                319, // Corporeal Beast
                2054, // Chaos Elemental
                4987, // Chronozon
                6609, // Callisto
                6610, // Venenatis
                6611, // Vet'ion first form
                6615, // Scorpia
                6618, // Crazy Archaeologist
                6619, // Chaos Fanatic
                7221, // Scurrius
                7416, // Obor
                8195, // Bryophyta
                11246, // Revenant Maledictus
                11992, // Artio
                11993, // Calvar'ion first form
                11998, // Spindel
                14704, // Elder Custodian Stalker
                16305, // Mad Angel
            )

        private val scriptedBossNames: Set<String> =
            setOf(
                "corp_beast",
                "chaos_elemental",
                "chaos_fanatic",
                "crazy_archaeologist",
                "scorpia",
                "callisto",
                "callisto_singles",
                "venenatis",
                "venenatis_singles",
                "vetion",
                "vetion_single",
                "wild_cave_superior",
                "rat_boss_normal",
            )

        private val conditionalNames: Set<String> =
            setOf(
                "scorpia_guardian",
                "scorpia_minion",
                "venenatis_spiderling",
                "spindel_spiderling",
                "vetion_hellhound_jnr",
                "vetion_hellhound_snr",
                "vetion_hellhound_jnr_singles",
                "vetion_hellhound_snr_singles",
            )

        private val transformationIds: Set<Int> = setOf(6612, 11994)
        private val transformationNames: Set<String> = setOf("vetion_2", "vetion_2_single")

        public fun classify(npc: Npc): WildernessNpcBehavior {
            val type = npc.type
            return classify(type.id, type.internalName, type.combatLevel, type.hitpoints)
        }

        public fun classify(
            npcId: Int,
            internalName: String,
            combatLevel: Int,
            hitpoints: Int,
        ): WildernessNpcBehavior {
            val name = normalize(internalName)
            return when {
                combatLevel <= 0 || hitpoints <= 0 -> WildernessNpcBehavior.NON_COMBAT
                npcId in transformationIds || name in transformationNames ->
                    WildernessNpcBehavior.DORMANT_OR_TRANSFORMATION
                name in conditionalNames -> WildernessNpcBehavior.CONDITIONAL_OR_SUMMON
                npcId in scriptedBossIds || name in scriptedBossNames ->
                    WildernessNpcBehavior.SCRIPTED_BOSS
                npcId in slayerNpcIds -> WildernessNpcBehavior.AGGRESSIVE
                else -> WildernessNpcBehavior.PASSIVE_RETALIATE_ONLY
            }
        }

        public fun isSlayerNpc(npcId: Int): Boolean = npcId in slayerNpcIds

        public fun shouldApplyProfile(
            inWilderness: Boolean,
            behavior: WildernessNpcBehavior,
        ): Boolean =
            inWilderness &&
                (behavior == WildernessNpcBehavior.AGGRESSIVE ||
                    behavior == WildernessNpcBehavior.SCRIPTED_BOSS)

        public fun boundedHuntRange(configured: Int, attackRange: Int): Int =
            maxOf(DEFAULT_HUNT_RANGE, configured, attackRange).coerceAtMost(MAX_HUNT_RANGE)

        private fun normalize(name: String): String = name.removePrefix("npc.")
    }
}

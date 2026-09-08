package dev.openrune.tools

import dev.openrune.definition.Definition
import dev.openrune.definition.type.NpcType
import dev.openrune.definition.type.SequenceType
import dev.openrune.filesystem.Cache
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcServerType

/**
 * Resolves revision-specific attack sequences for every combat NPC.
 *
 * Jagex's client NPC definitions contain movement sequences, while attack sequences are supplied by
 * server content. This project only configured a small number of them, causing the typed param
 * default (a human animation) to be played on unrelated models. Explicit server overrides remain
 * authoritative. Missing values are inferred only from sequences using the visual model's actual
 * animation skeleton, so even an imperfect name match cannot deform the model.
 */
internal object NpcAttackAnimationResolver {

    private enum class AnimationFormat {
        Legacy,
        Skeletal,
    }

    private data class AnimationSkeleton(val format: AnimationFormat, val id: Int)

    fun resolve(
        cache: Cache,
        npcs: Map<Int, NpcType>,
        customNpcs: Map<Int, NpcServerType>,
        sequences: Map<Int, SequenceType>,
        combatNpcIds: Set<Int>,
    ): Map<Int, Int> {
        val attackParamId = RSCM.getRSCM("param.attack_anim")
        val explicitByNpcId =
            npcs
                .mapNotNull { (npcId, npc) ->
                    val rawValue =
                        customNpcs[npcId]?.paramsRaw?.get(attackParamId)
                            ?: npc.params?.get(attackParamId)
                    val animation = rawValue.definitionIdOrNull()
                    if (animation != null && sequences.containsKey(animation)) {
                        npcId to animation
                    } else {
                        null
                    }
                }
                .toMap()

        val skeletonsBySequenceId = HashMap<Int, Set<AnimationSkeleton>>()
        fun skeletons(sequenceId: Int): Set<AnimationSkeleton> =
            skeletonsBySequenceId.getOrPut(sequenceId) {
                sequences[sequenceId]?.animationSkeletons(cache).orEmpty()
            }

        val explicitByStandAnimation =
            explicitByNpcId.entries
                .mapNotNull { (npcId, animation) ->
                    val visual = resolveVisualNpc(npcId, npcs, sequences) ?: return@mapNotNull null
                    visual.standAnim to animation
                }
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, values) -> values.toSet() }

        // A few official NPC families intentionally change animation rigs between movement and
        // attack. They cannot be proven by skeleton identity, so keep this small list restricted
        // to revision-240 sequence pairs verified directly from the named cache definitions.
        val reviewedAttackByStand =
            REVIEWED_ATTACK_FAMILIES.mapNotNull { (standName, attackName) ->
                    val stand = RSCM.getRSCM(standName)
                    val attack = RSCM.getRSCM(attackName)
                    if (sequences.containsKey(stand) && sequences.containsKey(attack)) {
                        stand to attack
                    } else {
                        null
                    }
                }
                .toMap()

        val attackSequences =
            sequences.values.filter { sequence -> isAttackName(sequenceName(sequence.id)) }
        val attacksBySkeleton = HashMap<AnimationSkeleton, MutableList<SequenceType>>()
        for (sequence in attackSequences) {
            for (skeleton in skeletons(sequence.id)) {
                attacksBySkeleton.getOrPut(skeleton) { mutableListOf() }.add(sequence)
            }
        }

        val visualCandidates =
            combatNpcIds.mapNotNull(npcs::get).filter { sequences.containsKey(it.standAnim) }
        val resolvedByVisual = HashMap<Pair<Int, String>, Int>()

        return buildMap {
            for (npcId in combatNpcIds.sorted()) {
                val explicit = explicitByNpcId[npcId]
                if (explicit != null) {
                    put(npcId, explicit)
                    continue
                }

                val npc = npcs[npcId] ?: continue
                val visual = resolveVisualNpc(npcId, npcs, sequences, visualCandidates) ?: continue
                val knownFamily = explicitByStandAnimation[visual.standAnim]
                val proven =
                    knownFamily?.singleOrNull()?.takeIf { animation ->
                        skeletons(visual.standAnim).intersect(skeletons(animation)).isNotEmpty()
                    }
                if (proven != null) {
                    put(npcId, proven)
                    continue
                }

                val reviewed = reviewedAttackByStand[visual.standAnim]
                if (reviewed != null) {
                    put(npcId, reviewed)
                    continue
                }

                val key = visual.standAnim to npc.name.lowercase()
                val animation =
                    resolvedByVisual.getOrPut(key) {
                        val compatible =
                            skeletons(visual.standAnim)
                                .asSequence()
                                .flatMap { skeleton ->
                                    attacksBySkeleton[skeleton].orEmpty().asSequence()
                                }
                                .distinctBy(SequenceType::id)
                                .map { candidate ->
                                    candidate to
                                        candidateScore(
                                            standName = sequenceName(visual.standAnim),
                                            npcName = npc.name,
                                            candidateName = sequenceName(candidate.id),
                                        )
                                }
                                .sortedWith(
                                    compareByDescending<Pair<SequenceType, Int>> { it.second }
                                        .thenBy { it.first.id }
                                )
                                .firstOrNull()
                                ?.first
                                ?.id
                        compatible ?: visual.standAnim
                    }
                put(npcId, animation)
            }
        }
    }

    private fun resolveVisualNpc(
        npcId: Int,
        npcs: Map<Int, NpcType>,
        sequences: Map<Int, SequenceType>,
        fallbackCandidates: List<NpcType> = emptyList(),
    ): NpcType? {
        val npc = npcs[npcId] ?: return null
        if (sequences.containsKey(npc.standAnim)) {
            return npc
        }
        val transformedIds = buildList {
            npc.transforms?.filterTo(this) { it >= 0 }
            if (npc.multiDefault >= 0) {
                add(npc.multiDefault)
            }
        }
        return transformedIds
            .asSequence()
            .mapNotNull { npcs[it] }
            .firstOrNull { sequences.containsKey(it.standAnim) }
            ?: fallbackCandidates
                .asSequence()
                .map { candidate -> candidate to visualFamilyScore(npc, candidate) }
                .filter { (_, score) -> score >= MIN_VISUAL_FAMILY_SCORE }
                .sortedWith(
                    compareByDescending<Pair<NpcType, Int>> { it.second }.thenBy { it.first.id }
                )
                .firstOrNull()
                ?.first
    }

    private fun visualFamilyScore(target: NpcType, candidate: NpcType): Int {
        val targetInternal = canonicalNpcName(target.id)
        val candidateInternal = canonicalNpcName(candidate.id)
        val prefix = commonPrefixLength(targetInternal, candidateInternal)
        val sameDisplayName = target.name.equals(candidate.name, ignoreCase = true)
        if (!sameDisplayName && prefix < MIN_INTERNAL_NAME_PREFIX) {
            return Int.MIN_VALUE
        }
        return prefix * NPC_INTERNAL_PREFIX_WEIGHT +
            if (sameDisplayName) SAME_DISPLAY_NAME_BONUS else 0
    }

    private fun canonicalNpcName(id: Int): String =
        RSCM.getReverseMapping(RSCMType.NPC, id)
            .lowercase()
            .removePrefix("npc.")
            .replace(NON_ALPHANUMERIC, "_")
            .trim('_')

    private fun SequenceType.animationSkeletons(cache: Cache): Set<AnimationSkeleton> {
        if (skeletalId >= 0) {
            val data =
                cache.data(
                    SKELETAL_ANIMATION_INDEX,
                    skeletalId ushr PACKED_FILE_BITS,
                    skeletalId and PACKED_FILE_MASK,
                )
            if (data == null || data.size < SKELETAL_HEADER_SIZE) {
                return emptySet()
            }
            val skeletonId =
                ((data[1].toInt() and BYTE_MASK) shl Byte.SIZE_BITS) or
                    (data[2].toInt() and BYTE_MASK)
            return setOf(AnimationSkeleton(AnimationFormat.Skeletal, skeletonId))
        }

        val frames = frameIDs ?: return emptySet()
        return frames.mapNotNullTo(mutableSetOf()) { packedFrame ->
            val data =
                cache.data(
                    LEGACY_ANIMATION_INDEX,
                    packedFrame ushr PACKED_FILE_BITS,
                    packedFrame and PACKED_FILE_MASK,
                )
            if (data == null || data.size < LEGACY_HEADER_SIZE) {
                null
            } else {
                val skeletonId =
                    ((data[0].toInt() and BYTE_MASK) shl Byte.SIZE_BITS) or
                        (data[1].toInt() and BYTE_MASK)
                AnimationSkeleton(AnimationFormat.Legacy, skeletonId)
            }
        }
    }

    internal fun candidateScore(standName: String, npcName: String, candidateName: String): Int {
        val stand = canonicalName(standName)
        val candidate = canonicalName(candidateName)
        val standStem = stand.replace(STANCE_SUFFIX, "")
        val candidateStem = attackStem(candidate)
        val standTokens = tokens(standStem)
        val candidateTokens = tokens(candidate)
        val npcTokens = tokens(canonicalName(npcName)).filterNot(GENERIC_NPC_TOKENS::contains)

        var score = commonPrefixLength(standStem, candidateStem) * PREFIX_WEIGHT
        score += standTokens.intersect(candidateTokens).size * SHARED_TOKEN_WEIGHT
        if (candidateStem == standStem) {
            score += EXACT_FAMILY_BONUS
        }

        val specificNpcTokens =
            npcTokens.filter { token -> token !in standTokens && token in candidateTokens }
        score += specificNpcTokens.size * NPC_VARIANT_BONUS

        val acronym = npcTokens.mapNotNull(String::firstOrNull).joinToString("")
        if (acronym.length >= MIN_ACRONYM_LENGTH && candidateTokens.any { it == acronym }) {
            score += NPC_ACRONYM_BONUS
        }

        if ("_attack" in candidate) {
            score += ATTACK_NAME_BONUS
        }
        score -= VARIANT_MARKERS.count(candidate::contains) * VARIANT_PENALTY
        score -= candidate.length
        return score
    }

    private fun sequenceName(id: Int): String = RSCM.getReverseMapping(RSCMType.SEQ, id)

    private fun isAttackName(name: String): Boolean {
        val canonical = canonicalName(name)
        return ATTACK_MARKERS.any(canonical::contains) && REJECTED_MARKERS.none(canonical::contains)
    }

    private fun attackStem(name: String): String {
        val markerIndex =
            ATTACK_MARKERS.map(name::indexOf).filter { it >= 0 }.minOrNull() ?: return name
        return name.substring(0, markerIndex).trimEnd('_')
    }

    private fun canonicalName(name: String): String =
        name.lowercase().removePrefix("seq.").replace(NON_ALPHANUMERIC, "_").trim('_')

    private fun tokens(name: String): Set<String> =
        name.split('_').filter { it.length >= MIN_TOKEN_LENGTH }.toSet()

    private fun commonPrefixLength(left: String, right: String): Int =
        left.zip(right).takeWhile { (a, b) -> a == b }.size

    private fun Any?.definitionIdOrNull(): Int? =
        when (this) {
            is Definition -> id
            is Number -> toInt()
            else -> null
        }

    private val STANCE_SUFFIX = Regex("_(?:ready|idle|stand)(?:_?\\d+)?(?:_update)?$")
    private val NON_ALPHANUMERIC = Regex("[^a-z0-9]+")
    private val ATTACK_MARKERS =
        listOf(
            "_attack",
            "_melee",
            "_strike",
            "_stab",
            "_slash",
            "_hack",
            "_pound",
            "_pummel",
            "_bite",
            "_claw",
            "_kick",
            "_punch",
            "_peck",
            "_headbutt",
            "_swipe",
            "_lunge",
            "_sweep",
            "_crush",
            "_slam",
            "_cast",
            "_shoot",
            "_ranged",
            "_fireball",
            "_breath",
            "_spit",
            "_sonic",
        )
    private val REJECTED_MARKERS =
        listOf("_defend", "_defence", "_block", "_parry", "_death", "_die", "_dead")
    private val VARIANT_MARKERS =
        listOf(
            "_special",
            "_spec_",
            "_enrage",
            "_intro",
            "_outro",
            "_tele_",
            "_priority",
            "_walkmerge",
            "_transparent",
            "_thrall",
        )
    private val GENERIC_NPC_TOKENS = setOf("npc", "monster", "the")

    private val REVIEWED_ATTACK_FAMILIES =
        listOf(
            "seq.shadeshadow_ready" to "seq.shade_attack",
            "seq.wyrm_walk" to "seq.wyrm_attack_melee",
        )

    private const val LEGACY_ANIMATION_INDEX = 0
    private const val SKELETAL_ANIMATION_INDEX = 22
    private const val PACKED_FILE_BITS = 16
    private const val PACKED_FILE_MASK = 0xFFFF
    private const val BYTE_MASK = 0xFF
    private const val LEGACY_HEADER_SIZE = 2
    private const val SKELETAL_HEADER_SIZE = 3
    private const val MIN_TOKEN_LENGTH = 3
    private const val MIN_ACRONYM_LENGTH = 3
    private const val PREFIX_WEIGHT = 5
    private const val SHARED_TOKEN_WEIGHT = 100
    private const val EXACT_FAMILY_BONUS = 2_000
    private const val NPC_VARIANT_BONUS = 3_000
    private const val NPC_ACRONYM_BONUS = 5_000
    private const val ATTACK_NAME_BONUS = 800
    private const val VARIANT_PENALTY = 500
    private const val MIN_INTERNAL_NAME_PREFIX = 8
    private const val NPC_INTERNAL_PREFIX_WEIGHT = 100
    private const val SAME_DISPLAY_NAME_BONUS = 10_000
    private const val MIN_VISUAL_FAMILY_SCORE =
        MIN_INTERNAL_NAME_PREFIX * NPC_INTERNAL_PREFIX_WEIGHT
}

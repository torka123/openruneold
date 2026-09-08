package dev.openrune.tools

import dev.openrune.definition.Definition
import dev.openrune.definition.type.NpcType
import dev.openrune.definition.type.SequenceType
import dev.openrune.filesystem.Cache
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcServerType

/**
 * Supplies a valid revision-specific defend sequence for every NPC represented by a Slayer task.
 *
 * NPC client definitions contain movement sequences but Jagex keeps combat sequences server-side.
 * The raw server config only overrides a small subset of them. This resolver preserves every
 * explicit override, reuses proven movement families, then chooses a named defend/block sequence
 * only when it uses the same underlying animation skeleton. If that revision has no dedicated
 * reaction for the model, the model's own idle sequence is the safe final fallback.
 */
internal object SlayerNpcDefendAnimationResolver {

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
        slayerNpcIds: Set<Int>,
    ): Map<Int, Int> {
        val defendParamId = RSCM.getRSCM("param.defend_anim")
        val explicitByNpcId =
            npcs
                .mapNotNull { (npcId, npc) ->
                    val rawValue =
                        customNpcs[npcId]?.paramsRaw?.get(defendParamId)
                            ?: npc.params?.get(defendParamId)
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

        val reactionSequences =
            sequences.values.filter { sequence -> isReactionName(sequenceName(sequence.id)) }

        val slayerVisualCandidates =
            slayerNpcIds.mapNotNull(npcs::get).filter { sequences.containsKey(it.standAnim) }

        return buildMap {
            for (npcId in slayerNpcIds.sorted()) {
                val explicit = explicitByNpcId[npcId]
                if (explicit != null) {
                    put(npcId, explicit)
                    continue
                }

                val npc = npcs[npcId] ?: continue
                val visual =
                    resolveVisualNpc(npcId, npcs, sequences, slayerVisualCandidates) ?: continue
                val knownFamily = explicitByStandAnimation[visual.standAnim]
                if (knownFamily?.size == 1) {
                    put(npcId, knownFamily.single())
                    continue
                }

                val compatible =
                    reactionSequences
                        .asSequence()
                        .filter { candidate ->
                            skeletons(visual.standAnim)
                                .intersect(skeletons(candidate.id))
                                .isNotEmpty()
                        }
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

                val animation = compatible ?: visual.standAnim.takeIf(sequences::containsKey)
                if (animation != null) {
                    put(npcId, animation)
                }
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
        val candidateStem = reactionStem(candidate)
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

        score -= VARIANT_MARKERS.count(candidate::contains) * VARIANT_PENALTY
        score -= candidate.length
        return score
    }

    private fun sequenceName(id: Int): String = RSCM.getReverseMapping(RSCMType.SEQ, id)

    private fun isReactionName(name: String): Boolean {
        val canonical = canonicalName(name)
        return REACTION_MARKERS.any(canonical::contains)
    }

    private fun reactionStem(name: String): String {
        val markerIndex =
            REACTION_MARKERS.map(name::indexOf).filter { it >= 0 }.minOrNull() ?: return name
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
    private val REACTION_MARKERS =
        listOf("_hit_react", "_hitreact", "_defence", "_defend", "_block", "_parry", "unarmedblock")
    private val VARIANT_MARKERS =
        listOf("_transparent", "_thrall", "_priority", "_walkmerge", "_leather_hit", "_tele_")
    private val GENERIC_NPC_TOKENS = setOf("npc", "monster", "the")

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
    private const val VARIANT_PENALTY = 500
    private const val MIN_INTERNAL_NAME_PREFIX = 8
    private const val NPC_INTERNAL_PREFIX_WEIGHT = 100
    private const val SAME_DISPLAY_NAME_BONUS = 10_000
    private const val MIN_VISUAL_FAMILY_SCORE =
        MIN_INTERNAL_NAME_PREFIX * NPC_INTERNAL_PREFIX_WEIGHT
}

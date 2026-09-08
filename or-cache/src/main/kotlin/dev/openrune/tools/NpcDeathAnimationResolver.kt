package dev.openrune.tools

import dev.openrune.definition.Definition
import dev.openrune.definition.type.NpcType
import dev.openrune.definition.type.SequenceType
import dev.openrune.filesystem.Cache
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcServerType

/** Resolves revision-specific death sequences for every combat NPC. */
internal object NpcDeathAnimationResolver {

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
        val deathParamId = RSCM.getRSCM("param.death_anim")
        val explicitByNpcId =
            npcs
                .mapNotNull { (npcId, npc) ->
                    val rawValue =
                        customNpcs[npcId]?.paramsRaw?.get(deathParamId)
                            ?: npc.params?.get(deathParamId)
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

        val reviewedDeathByStand =
            REVIEWED_DEATH_FAMILIES.mapNotNull { (standName, deathName) ->
                    val stand =
                        runCatching { RSCM.getRSCM(standName) }.getOrNull()
                            ?: return@mapNotNull null
                    val death =
                        runCatching { RSCM.getRSCM(deathName) }.getOrNull()
                            ?: return@mapNotNull null
                    if (sequences.containsKey(stand) && sequences.containsKey(death)) {
                        stand to death
                    } else {
                        null
                    }
                }
                .toMap()

        val deathSequences =
            sequences.values.filter { sequence -> isDeathName(sequenceName(sequence.id)) }
        val deathsBySkeleton = HashMap<AnimationSkeleton, MutableList<SequenceType>>()
        for (sequence in deathSequences) {
            for (skeleton in skeletons(sequence.id)) {
                deathsBySkeleton.getOrPut(skeleton) { mutableListOf() }.add(sequence)
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
                val reviewedNpc = REVIEWED_DEATH_BY_NPC_ID[npcId]?.takeIf(sequences::containsKey)
                if (reviewedNpc != null) {
                    put(npcId, reviewedNpc)
                    continue
                }
                val knownFamily = explicitByStandAnimation[visual.standAnim]
                val proven =
                    knownFamily?.singleOrNull()?.takeIf { animation ->
                        skeletons(visual.standAnim).intersect(skeletons(animation)).isNotEmpty()
                    }
                if (proven != null) {
                    put(npcId, proven)
                    continue
                }

                val reviewed = reviewedDeathByStand[visual.standAnim]
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
                                    deathsBySkeleton[skeleton].orEmpty().asSequence()
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
        val candidateStem = deathStem(candidate)
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

        if (DEATH_MARKERS.any(candidate::contains)) {
            score += DEATH_NAME_BONUS
        }
        score -= VARIANT_MARKERS.count(candidate::contains) * VARIANT_PENALTY
        score -= candidate.length
        return score
    }

    private fun sequenceName(id: Int): String = RSCM.getReverseMapping(RSCMType.SEQ, id)

    private fun isDeathName(name: String): Boolean {
        val canonical = canonicalName(name)
        return DEATH_MARKERS.any(canonical::contains) && REJECTED_MARKERS.none(canonical::contains)
    }

    private fun deathStem(name: String): String {
        val markerIndex = DEATH_MARKERS.maxOf(name::lastIndexOf)
        return if (markerIndex >= 0) name.substring(0, markerIndex).trimEnd('_') else name
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
    private val DEATH_MARKERS = listOf("_death", "_dying", "_die", "_dead")
    private val REJECTED_MARKERS =
        listOf(
            "_attack",
            "_melee",
            "_ranged",
            "_defend",
            "_defence",
            "_block",
            "_parry",
            "_ready",
            "_idle",
            "_stand",
            "_walk",
            "_run",
            "_spawn",
            "_revive",
            "_resurrect",
        )
    private val VARIANT_MARKERS =
        listOf("_special", "_intro", "_outro", "_transparent", "_thrall", "_priority", "_tele_")
    private val GENERIC_NPC_TOKENS = setOf("npc", "monster", "the")

    private val REVIEWED_DEATH_FAMILIES =
        listOf(
            // These revision-240 families use a different skeleton for their official death
            // transition, so they cannot be inferred through stand/death skeleton identity.
            "seq.horror_crab_ready" to "seq.horror_crab_hide",
            "seq.shade_ready" to "seq.shade_sink",
            "seq.shadeshadow_ready" to "seq.shadeshadow_sink",
            "seq.scarab_ready" to "seq.scarab_spiral",
            "seq.hydra_stage_1_ready" to "seq.hydra_stage_1_death",
            "seq.hydra_stage_2_ready" to "seq.hydra_stage_2_death",
            "seq.hydra_stage_3_ready" to "seq.hydra_stage_3_death",
            "seq.hydra_stage_4_ready" to "seq.hydra_stage_4_death",
            "seq.wyrm_walk" to "seq.wyrm_death",
            // Theatre of Blood Nylocas. The ranged family was previously falling back to its
            // idle sequence and the melee family could select an unrelated compatible death.
            "seq.top_spider_magic_idle" to "seq.top_spider_magic_death",
            "seq.top_spider_ranged_idle" to "seq.top_spider_ranged_death",
            "seq.top_spider_melee_idle" to "seq.top_spider_melee_death",
        )

    private val REVIEWED_DEATH_BY_NPC_ID = buildMap {
        fun addStyles(ids: IntArray) {
            put(ids[0], 8006)
            put(ids[1], 8000)
            put(ids[2], 7992)
        }
        addStyles(intArrayOf(8345, 8346, 8347))
        addStyles(intArrayOf(8351, 8352, 8353))
        addStyles(intArrayOf(10777, 10778, 10779))
        addStyles(intArrayOf(10783, 10784, 10785))
        addStyles(intArrayOf(10794, 10795, 10796))
        addStyles(intArrayOf(10800, 10801, 10802))
    }

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
    private const val DEATH_NAME_BONUS = 800
    private const val VARIANT_PENALTY = 500
    private const val MIN_INTERNAL_NAME_PREFIX = 8
    private const val NPC_INTERNAL_PREFIX_WEIGHT = 100
    private const val SAME_DISPLAY_NAME_BONUS = 10_000
    private const val MIN_VISUAL_FAMILY_SCORE =
        MIN_INTERNAL_NAME_PREFIX * NPC_INTERNAL_PREFIX_WEIGHT
}

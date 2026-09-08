package dev.openrune.tools

import dev.openrune.definition.type.NpcType
import dev.openrune.definition.type.SequenceType
import dev.openrune.filesystem.Cache
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcServerType

data class NpcMovementAnimations(
    val stand: Int,
    val rotateLeft: Int,
    val rotateRight: Int,
    val walk: Int,
    val rotateBack: Int,
    val walkLeft: Int,
    val walkRight: Int,
    val run: Int,
    val runBack: Int,
    val runRight: Int,
    val runLeft: Int,
    val crawl: Int,
    val crawlBack: Int,
    val crawlRight: Int,
    val crawlLeft: Int,
)

/** Resolves the affected Slayer masters from their real revision-specific animation families. */
object SlayerMasterMovementAnimationResolver {
    private enum class AnimationFormat {
        Legacy,
        Skeletal,
    }

    private data class AnimationSkeleton(val format: AnimationFormat, val id: Int)

    internal val targetNames =
        setOf(
            "npc.slayer_master_7",
            "npc.slayer_master_mortimer_vis",
            "npc.slayer_master_9_active",
            "npc.porcine_spria",
        )

    fun resolve(
        cache: Cache,
        npcs: Map<Int, NpcType>,
        customNpcs: Map<Int, NpcServerType>,
        sequences: Map<Int, SequenceType>,
    ): Map<Int, NpcMovementAnimations> {
        val skeletonCache = HashMap<Int, Set<AnimationSkeleton>>()
        fun skeletons(sequenceId: Int): Set<AnimationSkeleton> =
            skeletonCache.getOrPut(sequenceId) {
                sequences[sequenceId]?.animationSkeletons(cache).orEmpty()
            }

        val resolved = buildMap {
            for (targetName in targetNames.sorted()) {
                val targetId = RSCM.getRSCM(targetName)
                val target = npcs[targetId] ?: continue
                val custom = customNpcs[targetId]
                val source =
                    resolveVisualSource(target, npcs, sequences)
                        ?: resolveHumanFallback(npcs, sequences)
                        ?: continue

                val stand = valid(custom?.standAnim, sequences) ?: source.standAnim
                val sourceWalk =
                    valid(custom?.walkAnim, sequences) ?: valid(source.walkAnim, sequences)
                val walk =
                    sourceWalk?.takeUnless { it == stand }
                        ?: resolveFamilyWalk(stand, target.name, sequences, ::skeletons)
                        ?: sourceWalk
                        ?: continue

                put(
                    targetId,
                    NpcMovementAnimations(
                        stand = stand,
                        rotateLeft =
                            valid(custom?.rotateLeftAnim, sequences)
                                ?: valid(source.rotateLeftAnim, sequences)
                                ?: walk,
                        rotateRight =
                            valid(custom?.rotateRightAnim, sequences)
                                ?: valid(source.rotateRightAnim, sequences)
                                ?: walk,
                        walk = walk,
                        rotateBack =
                            valid(custom?.rotateBackAnim, sequences)
                                ?: valid(source.rotateBackAnim, sequences)
                                ?: walk,
                        walkLeft =
                            valid(custom?.walkLeftAnim, sequences)
                                ?: valid(source.walkLeftAnim, sequences)
                                ?: walk,
                        walkRight =
                            valid(custom?.walkRightAnim, sequences)
                                ?: valid(source.walkRightAnim, sequences)
                                ?: walk,
                        run =
                            valid(custom?.runSequence, sequences)
                                ?: valid(source.runSequence, sequences)
                                ?: walk,
                        runBack =
                            valid(custom?.runBackSequence, sequences)
                                ?: valid(source.runBackSequence, sequences)
                                ?: walk,
                        runRight =
                            valid(custom?.runRightSequence, sequences)
                                ?: valid(source.runRightSequence, sequences)
                                ?: walk,
                        runLeft =
                            valid(custom?.runLeftSequence, sequences)
                                ?: valid(source.runLeftSequence, sequences)
                                ?: walk,
                        crawl =
                            valid(custom?.crawlSequence, sequences)
                                ?: valid(source.crawlSequence, sequences)
                                ?: walk,
                        crawlBack =
                            valid(custom?.crawlBackSequence, sequences)
                                ?: valid(source.crawlBackSequence, sequences)
                                ?: walk,
                        crawlRight =
                            valid(custom?.crawlRightSequence, sequences)
                                ?: valid(source.crawlRightSequence, sequences)
                                ?: walk,
                        crawlLeft =
                            valid(custom?.crawlLeftSequence, sequences)
                                ?: valid(source.crawlLeftSequence, sequences)
                                ?: walk,
                    ),
                )
            }
        }
        val missing =
            targetNames.filter { targetName ->
                val targetId = RSCM.getRSCM(targetName)
                targetId < 0 || !resolved.containsKey(targetId)
            }
        check(missing.isEmpty()) { "Unable to resolve Slayer-master movement for: $missing" }
        for (targetName in targetNames.sorted()) {
            val targetId = RSCM.getRSCM(targetName)
            val before = npcs.getValue(targetId)
            val after = resolved.getValue(targetId)
            println(
                "Slayer master movement $targetName ($targetId): " +
                    "stand ${before.standAnim}->${after.stand}, walk ${before.walkAnim}->${after.walk}, " +
                    "left ${before.rotateLeftAnim}->${after.rotateLeft}, right ${before.rotateRightAnim}->${after.rotateRight}, run ${before.runSequence}->${after.run}"
            )
        }
        return resolved
    }

    private fun resolveHumanFallback(
        npcs: Map<Int, NpcType>,
        sequences: Map<Int, SequenceType>,
    ): NpcType? {
        val stand = RSCM.getRSCM("seq.human_ready")
        val walk = RSCM.getRSCM("seq.human_walk_f")
        if (valid(stand, sequences) == null || valid(walk, sequences) == null) return null
        return npcs.values
            .asSequence()
            .filter { candidate -> candidate.standAnim == stand && candidate.walkAnim == walk }
            .minByOrNull { it.id }
    }

    private fun resolveVisualSource(
        target: NpcType,
        npcs: Map<Int, NpcType>,
        sequences: Map<Int, SequenceType>,
    ): NpcType? {
        if (valid(target.standAnim, sequences) != null) return target
        val transforms = buildList {
            target.transforms?.filterTo(this) { it >= 0 }
            if (target.multiDefault >= 0) add(target.multiDefault)
        }
        transforms
            .asSequence()
            .mapNotNull(npcs::get)
            .firstOrNull { valid(it.standAnim, sequences) != null }
            ?.let {
                return it
            }

        return npcs.values
            .asSequence()
            .filter { candidate ->
                candidate.id != target.id &&
                    candidate.name.equals(target.name, ignoreCase = true) &&
                    valid(candidate.standAnim, sequences) != null &&
                    valid(candidate.walkAnim, sequences) != null
            }
            .minByOrNull { it.id }
    }

    private fun resolveFamilyWalk(
        stand: Int,
        npcName: String,
        sequences: Map<Int, SequenceType>,
        skeletons: (Int) -> Set<AnimationSkeleton>,
    ): Int? {
        val standSkeletons = skeletons(stand)
        if (standSkeletons.isEmpty()) return null
        val standName = sequenceName(stand)
        return sequences.values
            .asSequence()
            .filter { sequence ->
                isWalkName(sequenceName(sequence.id)) &&
                    standSkeletons.intersect(skeletons(sequence.id)).isNotEmpty()
            }
            .map { sequence ->
                sequence.id to movementScore(standName, npcName, sequenceName(sequence.id))
            }
            .sortedWith(compareByDescending<Pair<Int, Int>> { it.second }.thenBy { it.first })
            .firstOrNull()
            ?.first
    }

    internal fun movementScore(standName: String, npcName: String, candidateName: String): Int {
        val stand = canonicalName(standName).replace(STANCE_SUFFIX, "")
        val candidate = canonicalName(candidateName)
        val candidateStem = candidate.replace(WALK_SUFFIX, "")
        val standTokens = tokens(stand)
        val candidateTokens = tokens(candidateStem)
        val npcTokens = tokens(canonicalName(npcName))
        var score = commonPrefixLength(stand, candidateStem) * PREFIX_WEIGHT
        score += standTokens.intersect(candidateTokens).size * SHARED_TOKEN_WEIGHT
        if (stand == candidateStem) score += EXACT_FAMILY_BONUS
        score += npcTokens.intersect(candidateTokens).size * NPC_TOKEN_WEIGHT
        if (candidate.matches(WALK_NAME)) score += WALK_NAME_BONUS
        score -= candidate.length
        return score
    }

    private fun valid(id: Int?, sequences: Map<Int, SequenceType>): Int? =
        id?.takeIf { it >= 0 && sequences.containsKey(it) }

    private fun SequenceType.animationSkeletons(cache: Cache): Set<AnimationSkeleton> {
        if (skeletalId >= 0) {
            val data =
                cache.data(
                    SKELETAL_ANIMATION_INDEX,
                    skeletalId ushr PACKED_FILE_BITS,
                    skeletalId and PACKED_FILE_MASK,
                )
            if (data == null || data.size < SKELETAL_HEADER_SIZE) return emptySet()
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

    private fun sequenceName(id: Int): String = RSCM.getReverseMapping(RSCMType.SEQ, id)

    private fun isWalkName(name: String): Boolean =
        canonicalName(name).let { it.matches(WALK_NAME) && !it.contains("walkmerge") }

    private fun canonicalName(name: String): String =
        name.lowercase().substringAfter('.').replace(NON_ALPHANUMERIC, "_").trim('_')

    private fun tokens(name: String): Set<String> =
        name.split('_').filter { it.length >= MIN_TOKEN_LENGTH }.toSet()

    private fun commonPrefixLength(left: String, right: String): Int =
        left.zip(right).takeWhile { (a, b) -> a == b }.size

    private val STANCE_SUFFIX = Regex("_?(?:ready|idle|stand)(?:_?\\d+)?(?:_update)?$")
    private val WALK_SUFFIX = Regex("_?walk(?:_?\\d+)?(?:_update)?$")
    private val WALK_NAME = Regex(".*walk(?:_?\\d+)?(?:_update)?$")
    private val NON_ALPHANUMERIC = Regex("[^a-z0-9]+")

    private const val LEGACY_ANIMATION_INDEX = 0
    private const val SKELETAL_ANIMATION_INDEX = 22
    private const val PACKED_FILE_BITS = 16
    private const val PACKED_FILE_MASK = 0xFFFF
    private const val BYTE_MASK = 0xFF
    private const val LEGACY_HEADER_SIZE = 2
    private const val SKELETAL_HEADER_SIZE = 3
    private const val MIN_TOKEN_LENGTH = 3
    private const val PREFIX_WEIGHT = 5
    private const val SHARED_TOKEN_WEIGHT = 100
    private const val EXACT_FAMILY_BONUS = 2_000
    private const val NPC_TOKEN_WEIGHT = 250
    private const val WALK_NAME_BONUS = 800
}

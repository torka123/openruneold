package org.rsmod.api.net.social

import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.ArrayDeque

@Singleton
public class SocialRuntimeService @Inject constructor() {
    private val states = HashMap<Int, State>()
    private val messageTimes = HashMap<Int, ArrayDeque<Long>>()

    public fun install(characterId: Int, snapshot: SocialRuntimeSnapshot) {
        val ranksById =
            snapshot.friends.associate { it.identity.characterId to it.rank }.toMutableMap()
        val ranksByName = HashMap<String, Int>()
        for (friend in snapshot.friends) {
            ranksByName[SocialNameNormalizer.normalize(friend.identity.displayName)] = friend.rank
            if (friend.identity.previousDisplayName.isNotBlank()) {
                ranksByName[SocialNameNormalizer.normalize(friend.identity.previousDisplayName)] =
                    friend.rank
            }
        }
        states[characterId] =
            State(ranksById, ranksByName, snapshot.ignoredCharacterIds.toMutableSet())
    }

    public fun clear(characterId: Int) {
        states.remove(characterId)
        messageTimes.remove(characterId)
    }

    public fun ranksByName(ownerCharacterId: Int): Map<String, Int> =
        states[ownerCharacterId]?.ranksByName?.toMap() ?: emptyMap()

    public fun friendRankByName(ownerCharacterId: Int, name: String): Int =
        states[ownerCharacterId]?.ranksByName?.get(SocialNameNormalizer.normalize(name)) ?: 0

    public fun friendRank(ownerCharacterId: Int, targetCharacterId: Int): Int =
        states[ownerCharacterId]?.ranksById?.get(targetCharacterId) ?: -1

    public fun isIgnoring(ownerCharacterId: Int, targetCharacterId: Int): Boolean =
        targetCharacterId in (states[ownerCharacterId]?.ignoredIds ?: emptySet())

    public fun addFriend(ownerCharacterId: Int, identity: SocialIdentity) {
        val state = states[ownerCharacterId] ?: return
        state.ranksById[identity.characterId] = 0
        state.ranksByName[SocialNameNormalizer.normalize(identity.displayName)] = 0
        if (identity.previousDisplayName.isNotBlank()) {
            state.ranksByName[SocialNameNormalizer.normalize(identity.previousDisplayName)] = 0
        }
    }

    public fun removeFriend(ownerCharacterId: Int, identity: SocialIdentity) {
        val state = states[ownerCharacterId] ?: return
        state.ranksById.remove(identity.characterId)
        state.ranksByName.remove(SocialNameNormalizer.normalize(identity.displayName))
        state.ranksByName.remove(SocialNameNormalizer.normalize(identity.previousDisplayName))
    }

    public fun updateFriendRank(ownerCharacterId: Int, identity: SocialIdentity, rank: Int) {
        val state = states[ownerCharacterId] ?: return
        if (identity.characterId !in state.ranksById) return
        state.ranksById[identity.characterId] = rank
        state.ranksByName[SocialNameNormalizer.normalize(identity.displayName)] = rank
        if (identity.previousDisplayName.isNotBlank()) {
            state.ranksByName[SocialNameNormalizer.normalize(identity.previousDisplayName)] = rank
        }
    }

    public fun addIgnore(ownerCharacterId: Int, targetCharacterId: Int) {
        states[ownerCharacterId]?.ignoredIds?.add(targetCharacterId)
    }

    public fun removeIgnore(ownerCharacterId: Int, targetCharacterId: Int) {
        states[ownerCharacterId]?.ignoredIds?.remove(targetCharacterId)
    }

    public fun allowMessage(
        characterId: Int,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        val queue = messageTimes.getOrPut(characterId) { ArrayDeque() }
        while (queue.isNotEmpty() && nowMillis - queue.first() >= MESSAGE_WINDOW_MILLIS) queue
            .removeFirst()
        if (queue.size >= MESSAGE_LIMIT) return false
        queue.addLast(nowMillis)
        return true
    }

    private data class State(
        val ranksById: MutableMap<Int, Int>,
        val ranksByName: MutableMap<String, Int>,
        val ignoredIds: MutableSet<Int>,
    )

    private companion object {
        private const val MESSAGE_LIMIT = 6
        private const val MESSAGE_WINDOW_MILLIS = 5_000L
    }
}

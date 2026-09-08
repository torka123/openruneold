package org.rsmod.api.net.central

import jakarta.inject.Inject
import org.rsmod.api.db.DatabaseConnection
import org.rsmod.api.db.gateway.model.GameDbResult
import org.rsmod.api.net.central.WorldLinkFrameSpecs.PRIVATE_MESSAGE_MAX_CHARS
import org.rsmod.api.net.social.FriendChatRepository
import org.rsmod.api.net.social.SocialIdentity

class CentralSocialService
@Inject
constructor(
    private val central: OpenRuneCentralWorldLink,
    private val repository: FriendChatRepository,
) {
    fun addFriend(characterId: Int, name: String): GameDbResult<CentralSocialResult> {
        val cleaned = name.trim()
        if (cleaned.isBlank()) {
            return GameDbResult.Ok(CentralSocialResult.Ignored)
        }

        if (characterId <= 0) {
            return GameDbResult.Ok(CentralSocialResult.Failed("Social is not available right now."))
        }

        return GameDbResult.Ok(central.addFriend(characterId = characterId, targetName = cleaned))
    }

    fun addFriend(
        connection: DatabaseConnection,
        characterId: Int,
        name: String,
    ): GameDbResult<ResolvedCentralSocialResult> =
        resolve(connection, name) { identity -> addFriend(characterId, identity.displayName) }

    fun deleteFriend(characterId: Int, name: String): GameDbResult<CentralSocialResult> {
        val cleaned = name.trim()
        if (cleaned.isBlank()) {
            return GameDbResult.Ok(CentralSocialResult.Ignored)
        }

        if (characterId <= 0) {
            return GameDbResult.Ok(CentralSocialResult.Failed("Social is not available right now."))
        }

        return GameDbResult.Ok(
            central.deleteFriend(characterId = characterId, targetName = cleaned)
        )
    }

    fun deleteFriend(
        connection: DatabaseConnection,
        characterId: Int,
        name: String,
    ): GameDbResult<ResolvedCentralSocialResult> {
        val identity =
            repository.resolveIdentity(connection, name)
                ?: return GameDbResult.Ok(
                    ResolvedCentralSocialResult(
                        CentralSocialResult.Failed("That player does not exist."),
                        null,
                    )
                )
        return when (val result = deleteFriend(characterId, identity.displayName)) {
            is GameDbResult.Ok -> {
                if (result.value == CentralSocialResult.Ok) {
                    repository.deleteRank(connection, characterId, identity.characterId)
                }
                GameDbResult.Ok(ResolvedCentralSocialResult(result.value, identity))
            }
            is GameDbResult.Err -> result
        }
    }

    fun socialSnapshot(
        characterId: Int
    ): GameDbResult<OpenRuneCentralWorldLink.CentralSocialSnapshotResult> {
        if (characterId <= 0) {
            return GameDbResult.Ok(
                OpenRuneCentralWorldLink.CentralSocialSnapshotResult.Failed(
                    "Social is not available right now."
                )
            )
        }

        return GameDbResult.Ok(central.socialSnapshot(characterId = characterId))
    }

    fun addIgnore(characterId: Int, name: String): GameDbResult<CentralSocialResult> {
        val cleaned = name.trim()
        if (cleaned.isBlank()) {
            return GameDbResult.Ok(CentralSocialResult.Ignored)
        }

        if (characterId <= 0) {
            return GameDbResult.Ok(CentralSocialResult.Failed("Social is not available right now."))
        }

        return GameDbResult.Ok(central.addIgnore(characterId = characterId, targetName = cleaned))
    }

    fun addIgnore(
        connection: DatabaseConnection,
        characterId: Int,
        name: String,
    ): GameDbResult<ResolvedCentralSocialResult> =
        resolve(connection, name) { identity -> addIgnore(characterId, identity.displayName) }

    fun deleteIgnore(characterId: Int, name: String): GameDbResult<CentralSocialResult> {
        val cleaned = name.trim()
        if (cleaned.isBlank()) {

            return GameDbResult.Ok(CentralSocialResult.Ignored)
        }

        if (characterId <= 0) {
            return GameDbResult.Ok(CentralSocialResult.Failed("Social is not available right now."))
        }

        return GameDbResult.Ok(
            central.deleteIgnore(characterId = characterId, targetName = cleaned)
        )
    }

    fun deleteIgnore(
        connection: DatabaseConnection,
        characterId: Int,
        name: String,
    ): GameDbResult<ResolvedCentralSocialResult> =
        resolve(connection, name) { identity -> deleteIgnore(characterId, identity.displayName) }

    fun sendPrivateMessage(
        fromCharacterId: Int,
        targetName: String,
        senderDisplayName: String,
        senderCrown: Int,
        message: String,
    ): GameDbResult<CentralSocialResult> {
        val cleanedTarget = targetName.trim()
        val cleanedMessage = message.trim().take(PRIVATE_MESSAGE_MAX_CHARS)

        if (cleanedTarget.isBlank() || cleanedMessage.isBlank()) {
            return GameDbResult.Ok(CentralSocialResult.Ignored)
        }

        if (fromCharacterId <= 0) {
            return GameDbResult.Ok(
                CentralSocialResult.Failed("Private messaging is not available right now.")
            )
        }

        return GameDbResult.Ok(
            central.sendPrivateMessage(
                fromCharacterId = fromCharacterId,
                targetName = cleanedTarget,
                senderDisplayName = senderDisplayName,
                senderCrown = senderCrown,
                message = cleanedMessage,
            )
        )
    }

    fun sendPrivateMessage(
        connection: DatabaseConnection,
        fromCharacterId: Int,
        targetName: String,
        senderDisplayName: String,
        senderCrown: Int,
        message: String,
    ): GameDbResult<CentralSocialResult> {
        val identity =
            repository.resolveIdentity(connection, targetName)
                ?: return GameDbResult.Ok(CentralSocialResult.Failed("That player does not exist."))
        return sendPrivateMessage(
            fromCharacterId = fromCharacterId,
            targetName = identity.displayName,
            senderDisplayName = senderDisplayName,
            senderCrown = senderCrown,
            message = message,
        )
    }

    fun setChatFilters(
        characterId: Int,
        publicChat: Int,
        privateChat: Int,
        tradeChat: Int,
    ): GameDbResult<CentralSocialResult> {
        if (characterId <= 0) {
            return GameDbResult.Ok(
                CentralSocialResult.Failed("Social settings are not available right now.")
            )
        }

        return GameDbResult.Ok(
            central.setChatFilters(
                characterId = characterId,
                publicChat = publicChat,
                privateChat = privateChat,
                tradeChat = tradeChat,
            )
        )
    }

    fun setPrivateChatFilter(
        characterId: Int,
        privateChatFilter: Int,
    ): GameDbResult<CentralSocialResult> {
        if (characterId <= 0) {
            return GameDbResult.Ok(
                CentralSocialResult.Failed("Social settings are not available right now.")
            )
        }

        return GameDbResult.Ok(
            central.setPrivateChatFilter(
                characterId = characterId,
                privateChatFilter = privateChatFilter,
            )
        )
    }

    private fun resolve(
        connection: DatabaseConnection,
        name: String,
        operation: (SocialIdentity) -> GameDbResult<CentralSocialResult>,
    ): GameDbResult<ResolvedCentralSocialResult> {
        val identity =
            repository.resolveIdentity(connection, name)
                ?: return GameDbResult.Ok(
                    ResolvedCentralSocialResult(
                        CentralSocialResult.Failed("That player does not exist."),
                        null,
                    )
                )
        return when (val result = operation(identity)) {
            is GameDbResult.Ok ->
                GameDbResult.Ok(ResolvedCentralSocialResult(result.value, identity))
            is GameDbResult.Err -> result
        }
    }
}

sealed class CentralSocialResult {
    data object Ok : CentralSocialResult()

    data object Ignored : CentralSocialResult()

    data class Failed(val message: String) : CentralSocialResult()
}

data class ResolvedCentralSocialResult(
    val result: CentralSocialResult,
    val identity: SocialIdentity?,
)

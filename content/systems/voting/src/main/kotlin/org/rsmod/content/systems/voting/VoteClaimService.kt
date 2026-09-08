@file:OptIn(dev.openrune.types.util.UncheckedType::class)

package org.rsmod.content.systems.voting

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.invtx.add
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.output.mes
import org.rsmod.game.entity.Player

internal val VOTE_POINTS: AttributeKey<Int> = AttributeKey(persistenceKey = "vote_points_v1")
internal val VOTE_TOTAL: AttributeKey<Int> = AttributeKey(persistenceKey = "vote_total_v1")
internal val VOTE_STREAK: AttributeKey<Int> = AttributeKey(persistenceKey = "vote_streak_v1")
internal val VOTE_LAST_DAY: AttributeKey<Int> = AttributeKey(persistenceKey = "vote_last_day_v1")
internal val VOTE_CLAIM_IDS: AttributeKey<MutableList<String>> =
    AttributeKey(persistenceKey = "vote_claim_ids_v1")
internal val VOTE_BOSS_POINTS: AttributeKey<Int> =
    AttributeKey(persistenceKey = "vote_boss_points_v1")
internal val VOTE_BOSS_KILLS: AttributeKey<Int> =
    AttributeKey(persistenceKey = "vote_boss_kills_v1")

internal data class VoteApiResult(
    val voteIds: List<String> = emptyList(),
    val error: String? = null,
)

internal class VoteClaimParser(private val mapper: ObjectMapper = ObjectMapper()) {
    fun parse(body: String): VoteApiResult {
        val root =
            runCatching { mapper.readTree(body) }
                .getOrElse {
                    return VoteApiResult(error = "Invalid JSON response.")
                }
        if (root.path("success").isBoolean && !root.path("success").asBoolean()) {
            return VoteApiResult(
                error = root.path("message").asText("The vote provider rejected the claim.")
            )
        }
        val arrays = listOf("votes", "claims", "voteIds").map(root::path).filter(JsonNode::isArray)
        val ids =
            arrays
                .flatMap { array ->
                    array.mapNotNull { node ->
                        when {
                            node.isTextual || node.isNumber -> node.asText()
                            node.isObject ->
                                listOf("id", "voteId", "claimId").firstNotNullOfOrNull { field ->
                                    node
                                        .path(field)
                                        .takeIf { !it.isMissingNode && !it.isNull }
                                        ?.asText()
                                }
                            else -> null
                        }
                    }
                }
                .filter(String::isNotBlank)
                .distinct()
        if (ids.isNotEmpty()) return VoteApiResult(ids)
        val count =
            root.path("count").asInt(root.path("votes").takeIf(JsonNode::isNumber)?.asInt() ?: 0)
        return if (count > 0) {
            VoteApiResult(
                error =
                    "Vote API returned a count without unique claim IDs; rewards were refused for idempotence."
            )
        } else {
            VoteApiResult()
        }
    }
}

@Singleton
internal class VoteClaimService
@Inject
constructor(private val config: VoteSystemConfig, private val bosses: VoteBossService) {
    private val log = InlineLogger()
    private val parser = VoteClaimParser()
    private val http = HttpClient.newBuilder().connectTimeout(config.requestTimeout).build()
    private val executor =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "openrune-vote-claims").apply { isDaemon = true }
        }
    private val cooldowns = ConcurrentHashMap<Int, Long>()
    private val inFlight = ConcurrentHashMap.newKeySet<Int>()
    private val completed = ConcurrentLinkedQueue<CompletedClaim>()

    fun voteUrl(player: Player): String =
        config.votePageUrl.replace(
            "{username}",
            URLEncoder.encode(
                player.username.ifBlank { player.displayName },
                StandardCharsets.UTF_8,
            ),
        )

    fun claim(player: Player) {
        if (claimPending(player)) return
        if (config.claimEndpoint.isBlank()) {
            player.mes("Vote claiming is not configured yet. Please contact an administrator.")
            return
        }
        val now = System.currentTimeMillis()
        val remaining = (cooldowns[player.accountId] ?: 0L) - now
        if (remaining > 0) {
            player.mes("Wait ${(remaining + 999) / 1_000} seconds before checking votes again.")
            return
        }
        if (!inFlight.add(player.accountId)) {
            player.mes("Your vote claim is already being checked.")
            return
        }
        cooldowns[player.accountId] = now + config.claimCooldownMillis
        val accountId = player.accountId
        val username = player.username.ifBlank { player.displayName }
        player.mes("Checking the vote provider...")
        executor.execute {
            val result = request(username)
            completed += CompletedClaim(accountId, username, result)
        }
    }

    fun tick() {
        while (true) {
            val result = completed.poll() ?: break
            inFlight.remove(result.accountId)
            val player = bosses.onlinePlayer(result.accountId)
            if (player == null) {
                if (result.result.voteIds.isNotEmpty())
                    bosses.storePendingClaim(
                        result.accountId,
                        result.username,
                        result.result.voteIds,
                    )
                continue
            }
            process(player, result.result)
        }
    }

    fun onLogin(player: Player) {
        claimPending(player)
        bosses.claimRafflePrize(player)
    }

    private fun process(player: Player, result: VoteApiResult) {
        if (result.error != null) {
            player.mes("Vote claim failed: ${result.error}")
            return
        }
        if (result.voteIds.isEmpty()) {
            player.mes("You do not have any valid unclaimed votes.")
            return
        }
        val accepted = bosses.filterUnclaimed(result.voteIds, player)
        if (accepted.isEmpty()) {
            player.mes("Those votes were already claimed.")
            return
        }
        bosses.storePendingClaim(player.accountId, player.username, accepted)
        claimPending(player)
    }

    private fun claimPending(player: Player): Boolean {
        val pending = bosses.pendingClaim(player.accountId) ?: return false
        val claimedIds =
            player.attr[VOTE_CLAIM_IDS]
                ?: mutableListOf<String>().also { player.attr[VOTE_CLAIM_IDS] = it }
        val deliverIds = pending.voteIds.filterNot(claimedIds::contains)
        if (deliverIds.isEmpty()) {
            bosses.completePendingClaim(player, pending)
            player.mes(
                "Your pending vote claim was already rewarded and has been safely finalized."
            )
            bosses.claimRafflePrize(player)
            return true
        }
        val rewardCount = Math.multiplyExact(deliverIds.size, config.rewardBoxesPerVote)
        val item = ServerCacheManager.getItem(config.rewardBoxItemId)
        if (item == null) {
            player.mes("Vote rewards are temporarily unavailable; your claim is safely pending.")
            log.error { "[Vote] Missing configured reward-box item ${config.rewardBoxItemId}" }
            return true
        }
        val result =
            player.invTransaction(player.inv) { add(select(player.inv), item.id, rewardCount) }
        if (!result.success) {
            player.mes(
                "Make enough inventory space for your Vote Reward Boxes, then use ::claimvote again."
            )
            return true
        }
        applyPlayerProgress(player, deliverIds)
        bosses.completePendingClaim(player, pending)
        player.mes("You claim ${deliverIds.size} vote(s) and $rewardCount Vote Reward Box(es).")
        showStreak(player)
        bosses.claimRafflePrize(player)
        return true
    }

    private fun applyPlayerProgress(player: Player, ids: List<String>) {
        val count = ids.size
        player.attr[VOTE_POINTS] =
            (player.attr[VOTE_POINTS] ?: 0) + count * config.votePointsPerVote
        player.attr[VOTE_TOTAL] = (player.attr[VOTE_TOTAL] ?: 0) + count
        val claimedIds =
            player.attr[VOTE_CLAIM_IDS]
                ?: mutableListOf<String>().also { player.attr[VOTE_CLAIM_IDS] = it }
        ids.filterNot(claimedIds::contains).forEach(claimedIds::add)
        val today = (System.currentTimeMillis() / 86_400_000L).toInt()
        val last = player.attr[VOTE_LAST_DAY]
        if (last != today) {
            player.attr[VOTE_STREAK] =
                if (last == today - 1) (player.attr[VOTE_STREAK] ?: 0) + 1 else 1
            player.attr[VOTE_LAST_DAY] = today
        }
    }

    private fun showStreak(player: Player) {
        player.mes(
            "Vote rewards: ${player.attr[VOTE_POINTS] ?: 0} points, ${player.attr[VOTE_TOTAL] ?: 0} total votes, " +
                "${player.attr[VOTE_STREAK] ?: 0}-day streak."
        )
    }

    private fun request(username: String): VoteApiResult {
        return try {
            val encoded = URLEncoder.encode(username, StandardCharsets.UTF_8)
            val uri = URI.create(config.claimEndpoint.replace("{username}", encoded))
            if (!config.allowInsecureApi && uri.scheme != "https")
                return VoteApiResult(error = "The configured endpoint is not HTTPS.")
            val builder =
                HttpRequest.newBuilder(uri)
                    .timeout(config.requestTimeout)
                    .GET()
                    .header("Accept", "application/json")
            if (config.apiToken.isNotBlank()) {
                val value =
                    if (config.apiTokenHeader.equals("Authorization", true))
                        "Bearer ${config.apiToken}"
                    else config.apiToken
                builder.header(config.apiTokenHeader, value)
            }
            val response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299)
                VoteApiResult(error = "Provider returned HTTP ${response.statusCode()}.")
            else parser.parse(response.body())
        } catch (error: Throwable) {
            log.error(error) { "[Vote] Claim API request failed for username=$username" }
            VoteApiResult(error = "The vote provider is unavailable; no rewards were issued.")
        }
    }

    private data class CompletedClaim(
        val accountId: Int,
        val username: String,
        val result: VoteApiResult,
    )
}

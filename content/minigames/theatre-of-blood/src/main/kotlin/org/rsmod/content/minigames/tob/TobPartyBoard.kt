package org.rsmod.content.minigames.tob

import dev.openrune.ServerCacheManager
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.ui.ifOpenMainModal
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player

internal data class TobPartyListEntry(
    val slot: Int,
    val leader: String,
    val members: Int,
    val preferredSize: Int,
    val preferredCombat: Int,
)

internal data class TobPartyDetails(
    val status: Int,
    val members: List<Player?>,
    val applicants: List<Player>,
    val preferredSize: Int,
    val preferredCombat: Int,
)

@Singleton
internal class TobPartyBoard @Inject constructor(private val eventBus: EventBus) {
    fun validateAssets() {
        requireNotNull(ServerCacheManager.getInterface(PARTY_DETAILS_ID)) {
            "Missing ToB party-details interface $PARTY_DETAILS_ID"
        }
        requireNotNull(ServerCacheManager.getInterface(PARTY_LIST_ID)) {
            "Missing ToB party-list interface $PARTY_LIST_ID"
        }
    }

    fun openList(player: Player, entries: List<TobPartyListEntry>) {
        player.ifOpenMainModal(PARTY_LIST_ID, eventBus)
        BOARD_SLOTS.forEach { slot ->
            val entry = entries.firstOrNull { it.slot == slot }
            val text =
                if (entry == null) {
                    ""
                } else {
                    "${entry.leader}|${entry.members}|${entry.preferredSize}|${entry.preferredCombat}|"
                }
            player.runClientScript(PARTY_LIST_ADD_LINE, slot, text)
        }
    }

    fun openDetails(player: Player, details: TobPartyDetails) {
        player.ifOpenMainModal(PARTY_DETAILS_ID, eventBus)
        repeat(5) { index ->
            val member = details.members.getOrNull(index)
            player.runClientScript(
                PARTY_DETAILS_ADD_MEMBER,
                details.status,
                member?.let(::memberDetails) ?: BLANK_MEMBER,
            )
        }
        details.applicants.take(5).forEach { applicant ->
            player.runClientScript(PARTY_DETAILS_ADD_APPLICANT, memberDetails(applicant))
        }
        player.runClientScript(
            PARTY_DETAILS_REFRESH,
            details.status,
            details.preferredSize,
            details.preferredCombat,
        )
    }

    fun isDetailsOpen(player: Player): Boolean =
        player.ui.containsModal(requireNotNull(ServerCacheManager.getInterface(PARTY_DETAILS_ID)))

    private fun memberDetails(player: Player): String = buildString {
        append(player.displayName)
        append('|')
        append(player.combatLevel)
        append('|')
        MEMBER_STATS.forEach { stat ->
            append(player.statBase(stat))
            append('|')
        }
        append(player.attr.getOrDefault(TOB_NORMAL_COMPLETIONS, 0))
        append('|')
    }

    companion object {
        const val PARTY_DETAILS_CONTROLS = 3_276_800
        const val PARTY_LIST_CONTROLS = 23_855_107
        const val PARTY_LIST_PARTIES = 23_855_121

        const val STATUS_IN_PARTY = 1
        const val STATUS_LEADER = 2
        const val STATUS_WITHDRAW_APPLICATION = 3
        const val STATUS_APPLY = 4

        val BOARD_SLOTS =
            listOf(
                9,
                8,
                7,
                6,
                5,
                44,
                43,
                42,
                41,
                40,
                4,
                39,
                38,
                37,
                36,
                35,
                34,
                33,
                32,
                31,
                30,
                3,
                29,
                28,
                27,
                26,
                25,
                24,
                23,
                22,
                21,
                20,
                2,
                19,
                18,
                17,
                16,
                15,
                14,
                13,
                12,
                11,
                10,
                1,
                0,
            )

        const val PARTY_DETAILS_ID = 50
        const val PARTY_LIST_ID = 364
        private const val PARTY_DETAILS_ADD_MEMBER = 2317
        private const val PARTY_DETAILS_ADD_APPLICANT = 2321
        private const val PARTY_DETAILS_REFRESH = 2323
        private const val PARTY_LIST_ADD_LINE = 2340
        private const val BLANK_MEMBER = "-||||||||||"
        private val MEMBER_STATS =
            listOf(
                "stat.attack",
                "stat.strength",
                "stat.ranged",
                "stat.magic",
                "stat.defence",
                "stat.hitpoints",
                "stat.prayer",
            )
    }
}

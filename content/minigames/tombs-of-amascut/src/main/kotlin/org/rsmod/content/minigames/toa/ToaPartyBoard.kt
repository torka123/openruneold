package org.rsmod.content.minigames.toa

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.widget.IfEvent
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.output.VarpSync
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.ui.ifOpenMainModal
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.PlayerUid

@Singleton
internal class ToaPartyBoard @Inject constructor(private val eventBus: EventBus) {
    fun validateAssets() {
        INTERFACES.forEach { id ->
            requireNotNull(ServerCacheManager.getInterface(id)) {
                "Missing native revision-240 ToA interface $id"
            }
        }
        INTERFACE_COMPONENTS.forEach { (interfaceId, components) ->
            val type = requireNotNull(ServerCacheManager.getInterface(interfaceId))
            components.forEach { component ->
                requireNotNull(type.components[component]) {
                    "Missing native revision-240 ToA component $interfaceId:$component"
                }
            }
        }
        INTERFACE_ON_LOAD_SCRIPTS.forEach(::validateOnLoadScript)
        requireNotNull(ServerCacheManager.getVarp(TOA_MY_CONTROLLER_VARP)) {
            "Missing native revision-240 ToA party controller varp $TOA_MY_CONTROLLER_VARP"
        }
        requireNotNull(ServerCacheManager.getVarbit(INVOCATION_PRESET_SELECT_VARBIT)) {
            "Missing native revision-240 ToA preset-selection varbit " +
                INVOCATION_PRESET_SELECT_VARBIT
        }
        validateEnumMap(PARTY_LIST_CONTROL_ENUM, PARTY_LIST_CACHE_CONTROLS)
        validateEnumMap(PARTY_DETAILS_CONTROL_ENUM, PARTY_DETAILS_CACHE_CONTROLS)
        validatePartyListRows()
        validateInvocationRows()
    }

    fun openList(
        player: Player,
        parties: List<ToaParty>,
        hasParty: Boolean,
        label: (ToaParty) -> String,
    ) {
        val controller = requireNotNull(ServerCacheManager.getVarp(TOA_MY_CONTROLLER_VARP))
        VarpSync.writeVarp(player, controller, partyController(hasParty))
        player.ifOpenMainModal(PARTY_LIST, eventBus)
        PARTY_LIST_ROOT_OP_COMPONENTS.forEach { packed ->
            player.ifSetEvents(
                ServerCacheManager.fromComponent(packed),
                BUTTON_OP_SLOTS,
                IfEvent.Op1,
            )
        }
        PARTY_LIST_ROW_COMPONENTS.forEach { packed ->
            player.ifSetEvents(
                ServerCacheManager.fromComponent(packed),
                PARTY_LIST_ROW_OP_SLOTS,
                IfEvent.Op1,
            )
        }
        repeat(BOARD_SLOTS) { slot ->
            val party = parties.getOrNull(slot)
            val value =
                if (party == null || party.activeSession != null) {
                    ""
                } else {
                    val leader =
                        if (player.uid in party.members) "<col=ffffff>${label(party)}"
                        else label(party)
                    buildString {
                        append(leader)
                        append('|')
                        append(party.members.size)
                        append('|')
                        append(0)
                        append('|')
                        append(party.invocations.all().size)
                        append('|')
                        append(party.invocations.raidLevel)
                        append('|')
                        append(party.invocations.raidLevel)
                        append('|')
                        append(0)
                        append('|')
                    }
                }
            player.runClientScript(PARTY_LIST_ADD_LINE, slot, value)
        }
        player.runClientScript(PARTY_LIST_SET_PARTY_BUTTON)
    }

    /** Opens the cache-native invocation reference panel without enabling raid-level selection. */
    fun openInvocationBoard(player: Player) {
        player.ifOpenMainModal(INVOCATIONS, eventBus)
    }

    fun openMembers(player: Player, party: ToaParty, resolve: (PlayerUid) -> Player?) =
        openDetails(player, party, resolve, TAB_MEMBERS)

    fun openApplicants(player: Player, party: ToaParty, resolve: (PlayerUid) -> Player?) =
        openDetails(player, party, resolve, TAB_APPLICANTS)

    fun openView(player: Player, party: ToaParty, resolve: (PlayerUid) -> Player?) =
        openDetails(player, party, resolve, TAB_MEMBERS)

    fun openInvocations(player: Player, party: ToaParty, resolve: (PlayerUid) -> Player?) =
        openDetails(player, party, resolve, TAB_INVOCATIONS)

    private fun openDetails(
        player: Player,
        party: ToaParty,
        resolve: (PlayerUid) -> Player?,
        tab: Int,
    ) {
        player.ifOpenMainModal(PARTY_DETAILS, eventBus)
        PARTY_DETAILS_ROOT_OP_COMPONENTS.forEach { packed ->
            player.ifSetEvents(
                ServerCacheManager.fromComponent(packed),
                BUTTON_OP_SLOTS,
                IfEvent.Op1,
            )
        }
        player.ifSetEvents(
            ServerCacheManager.fromComponent(PARTY_DETAILS_MEMBER_LIST),
            PARTY_DETAILS_MEMBER_OP_SLOTS,
            IfEvent.Op1,
        )
        player.ifSetEvents(
            ServerCacheManager.fromComponent(PARTY_DETAILS_APPLICANT_LIST),
            PARTY_DETAILS_APPLICANT_OP_SLOTS,
            IfEvent.Op1,
            IfEvent.Op2,
        )
        ToaInvocation.interfaceSubcomponents.forEach { subcomponent ->
            player.ifSetEvents(
                ServerCacheManager.fromComponent(PARTY_DETAILS_INVOCATION_CONTENT),
                subcomponent..subcomponent,
                IfEvent.Op1,
            )
        }
        player.ifSetEvents(
            ServerCacheManager.fromComponent(PARTY_DETAILS_PRESET_SLOTS),
            PARTY_DETAILS_PRESET_OP_SLOTS,
            IfEvent.Op1,
            IfEvent.Op2,
        )
        val status = status(player, party)
        repeat(TOA_MAX_PARTY_SIZE) { index ->
            val member = party.members.elementAtOrNull(index)?.let(resolve)
            player.runClientScript(
                PARTY_DETAILS_ADD_MEMBER,
                status,
                member?.let(::memberDetails) ?: BLANK_MEMBER,
            )
        }
        party.applicants.mapNotNull(resolve).take(TOA_MAX_PARTY_SIZE).forEach { applicant ->
            player.runClientScript(PARTY_DETAILS_ADD_APPLICANT, memberDetails(applicant))
        }
        val displayedInvocations =
            party.activeSession?.startSnapshot?.invocations ?: party.invocations.all()
        val displayedRaidLevel =
            party.activeSession?.startSnapshot?.raidLevel
                ?: ToaInvocationCatalog.raidLevel(displayedInvocations)
        val invocationBitmaps = ToaInvocationCatalog.clientBitmaps(displayedInvocations)
        player.runClientScript(
            PARTY_DETAILS_REFRESH,
            status,
            0,
            displayedInvocations.size,
            displayedRaidLevel,
            tab,
            invocationBitmaps[0],
            invocationBitmaps[1],
            invocationBitmaps[2],
        )
    }

    private fun validateOnLoadScript(interfaceId: Int, scriptId: Int) {
        val type = requireNotNull(ServerCacheManager.getInterface(interfaceId))
        val actual = type.components[0]?.onLoad?.firstOrNull()
        require(actual == scriptId) {
            "Native revision-240 ToA interface $interfaceId expected on-load script " +
                "$scriptId but found $actual"
        }
    }

    private fun validateEnumMap(enumId: Int, expected: Map<Int, Int>) {
        val type =
            requireNotNull(ServerCacheManager.getEnum(enumId)) {
                "Missing native revision-240 ToA enum $enumId"
            }
        expected.forEach { (key, value) ->
            require(type.values[key] == value) {
                "Native revision-240 ToA enum $enumId expected $key -> $value but found " +
                    type.values[key]
            }
        }
    }

    private fun validatePartyListRows() {
        val type =
            requireNotNull(ServerCacheManager.getEnum(PARTY_LIST_ROW_ENUM)) {
                "Missing native revision-240 ToA party-row enum $PARTY_LIST_ROW_ENUM"
            }
        PARTY_LIST_ROW_COMPONENTS.forEachIndexed { slot, packed ->
            require(type.values[slot] == packed) {
                "Native revision-240 ToA party slot $slot expected component $packed but found " +
                    type.values[slot]
            }
        }
    }

    private fun validateInvocationRows() {
        val type =
            requireNotNull(ServerCacheManager.getEnum(INVOCATION_ENUM)) {
                "Missing native revision-240 ToA invocation enum $INVOCATION_ENUM"
            }
        ToaInvocationCatalog.standard.forEach { definition ->
            val row = definition.ui.row
            val structId = definition.ui.structId
            require(type.values[row] == structId) {
                "Native revision-240 ToA invocation row $row expected struct $structId but found " +
                    type.values[row]
            }
            val struct =
                requireNotNull(ServerCacheManager.getStruct(structId)) {
                    "Missing native revision-240 ToA invocation struct $structId"
                }
            val params =
                requireNotNull(struct.params) {
                    "Missing native revision-240 ToA invocation params for struct $structId"
                }
            require(params[INVOCATION_ID_PARAM] == definition.ui.clientInvocationId)
            require(params[INVOCATION_NAME_PARAM] == definition.displayName)
            require(params[INVOCATION_CATEGORY_PARAM] == definition.category.cacheParamValue)
            require(params[INVOCATION_LEVEL_PARAM] == definition.raidLevel)
        }
    }

    private fun status(player: Player, party: ToaParty): Int =
        when {
            party.leader == player.uid -> STATUS_LEADER
            player.uid in party.members -> STATUS_IN_PARTY
            player.uid in party.applicants -> STATUS_WITHDRAW_APPLICATION
            else -> STATUS_APPLY
        }

    private fun memberDetails(player: Player): String = buildString {
        append(player.displayName)
        append('|')
        append(player.combatLevel)
        append('|')
        MEMBER_STATS.forEach { stat ->
            append(player.statBase(stat))
            append('|')
        }
        append(player.attr.getOrDefault(TOA_NORMAL_COMPLETIONS, 0))
        append('|')
    }

    companion object {
        internal fun partyController(hasParty: Boolean): Int =
            if (hasParty) PARTY_CONTROLLER else NO_PARTY_CONTROLLER

        const val PARTY_LIST = 772
        const val PARTY_DETAILS = 774
        const val INVOCATIONS = 776
        const val MIDRAID_LOOT = 777

        const val PARTY_LIST_CONTENT = (PARTY_LIST shl 16) or 62
        const val PARTY_LIST_REFRESH_BUTTON = (PARTY_LIST shl 16) or 63
        const val PARTY_LIST_PARTY_BUTTON = (PARTY_LIST shl 16) or 64
        const val PARTY_LIST_FILTER_BUTTON = (PARTY_LIST shl 16) or 65
        const val PARTY_DETAILS_CONTROLS = (PARTY_DETAILS shl 16) or 1
        const val PARTY_DETAILS_BACK = (PARTY_DETAILS shl 16) or 4
        const val PARTY_DETAILS_MEMBERS_TAB = (PARTY_DETAILS shl 16) or 6
        const val PARTY_DETAILS_APPLICANTS_TAB = (PARTY_DETAILS shl 16) or 7
        const val PARTY_DETAILS_INVOCATIONS_TAB = (PARTY_DETAILS shl 16) or 8
        const val PARTY_DETAILS_SUMMARY_TAB = (PARTY_DETAILS shl 16) or 9
        const val PARTY_DETAILS_MEMBER_LIST = (PARTY_DETAILS shl 16) or 32
        const val PARTY_DETAILS_APPLICANT_LIST = (PARTY_DETAILS shl 16) or 48
        const val PARTY_DETAILS_INVOCATION_CONTENT = (PARTY_DETAILS shl 16) or 52
        const val PARTY_DETAILS_REFRESH_BUTTON = (PARTY_DETAILS shl 16) or 62
        const val PARTY_DETAILS_ACTION_BUTTON = (PARTY_DETAILS shl 16) or 63
        const val PARTY_DETAILS_UNBLOCK_BUTTON = (PARTY_DETAILS shl 16) or 64
        const val PARTY_DETAILS_CLEAR_INVOCATIONS = (PARTY_DETAILS shl 16) or 69
        const val PARTY_DETAILS_REWARDS = (PARTY_DETAILS shl 16) or 70
        const val PARTY_DETAILS_PRESETS = (PARTY_DETAILS shl 16) or 71
        const val PARTY_DETAILS_COMPLETIONS = (PARTY_DETAILS shl 16) or 72
        const val PARTY_DETAILS_LOAD_PRESET = (PARTY_DETAILS shl 16) or 83
        const val PARTY_DETAILS_SAVE_PRESET = (PARTY_DETAILS shl 16) or 84
        const val PARTY_DETAILS_PRESET_SLOTS = (PARTY_DETAILS shl 16) or 98

        const val STATUS_IN_PARTY = 1
        const val STATUS_LEADER = 2
        const val STATUS_WITHDRAW_APPLICATION = 3
        const val STATUS_APPLY = 4

        const val BOARD_SLOTS = 45

        private const val TAB_MEMBERS = 0
        private const val TAB_APPLICANTS = 1
        private const val TAB_INVOCATIONS = 2
        private const val TOA_MY_CONTROLLER_VARP = 3603
        internal const val INVOCATION_PRESET_SELECT_VARBIT = 14541
        private const val NO_PARTY_CONTROLLER = -1
        private const val PARTY_CONTROLLER = 0
        // Native script 1558 installs Op1 on the parent button. Children 0-9 only draw its frame
        // and label, so If3Button reports the root subcomponent (-1), not label child 9.
        private const val NATIVE_BUTTON_OP_SLOT = -1
        internal val BUTTON_OP_SLOTS = NATIVE_BUTTON_OP_SLOT..NATIVE_BUTTON_OP_SLOT
        private const val PARTY_LIST_SET_PARTY_BUTTON = 6597
        private const val PARTY_LIST_ADD_LINE = 6601
        private const val PARTY_DETAILS_ADD_MEMBER = 6722
        private const val PARTY_DETAILS_ADD_APPLICANT = 6727
        private const val PARTY_DETAILS_REFRESH = 6729
        private const val BLANK_MEMBER = "-||||||||||"
        private const val PARTY_LIST_FIRST_ROW_COMPONENT = 17
        private const val PARTY_LIST_LAST_ROW_COMPONENT = 61
        private const val PARTY_LIST_ROW_ENUM = 4793
        private const val PARTY_LIST_CONTROL_ENUM = 4794
        private const val PARTY_DETAILS_CONTROL_ENUM = 4792
        private const val INVOCATION_ENUM = 4664
        private const val INVOCATION_ID_PARAM = 1159
        private const val INVOCATION_NAME_PARAM = 1160
        private const val INVOCATION_CATEGORY_PARAM = 1161
        private const val INVOCATION_LEVEL_PARAM = 1162
        internal val PARTY_LIST_ROW_OP_SLOTS = 0..0
        internal val PARTY_DETAILS_MEMBER_OP_SLOTS = 0 until TOA_MAX_PARTY_SIZE
        internal val PARTY_DETAILS_APPLICANT_OP_SLOTS = 0 until TOA_MAX_PARTY_SIZE
        internal val PARTY_DETAILS_PRESET_OP_SLOTS = 0 until ToaInvocationPresetCodec.SLOT_COUNT
        internal val PARTY_LIST_ROW_COMPONENTS =
            (PARTY_LIST_FIRST_ROW_COMPONENT..PARTY_LIST_LAST_ROW_COMPONENT).map { component ->
                (PARTY_LIST shl 16) or component
            }
        internal val PARTY_LIST_ROOT_OP_COMPONENTS =
            setOf(PARTY_LIST_REFRESH_BUTTON, PARTY_LIST_PARTY_BUTTON, PARTY_LIST_FILTER_BUTTON)
        internal val PARTY_DETAILS_ROOT_OP_COMPONENTS =
            setOf(
                PARTY_DETAILS_BACK,
                PARTY_DETAILS_MEMBERS_TAB,
                PARTY_DETAILS_APPLICANTS_TAB,
                PARTY_DETAILS_INVOCATIONS_TAB,
                PARTY_DETAILS_SUMMARY_TAB,
                PARTY_DETAILS_REFRESH_BUTTON,
                PARTY_DETAILS_ACTION_BUTTON,
                PARTY_DETAILS_UNBLOCK_BUTTON,
                PARTY_DETAILS_CLEAR_INVOCATIONS,
                PARTY_DETAILS_REWARDS,
                PARTY_DETAILS_PRESETS,
                PARTY_DETAILS_COMPLETIONS,
                PARTY_DETAILS_LOAD_PRESET,
                PARTY_DETAILS_SAVE_PRESET,
            )
        private val PARTY_LIST_CACHE_CONTROLS =
            mapOf(
                PARTY_LIST_REFRESH_BUTTON to 0,
                PARTY_LIST_PARTY_BUTTON to 1,
                PARTY_LIST_FILTER_BUTTON to 2,
            )
        private val PARTY_DETAILS_CACHE_CONTROLS =
            mapOf(
                PARTY_DETAILS_BACK to 0,
                PARTY_DETAILS_REFRESH_BUTTON to 1,
                PARTY_DETAILS_UNBLOCK_BUTTON to 2,
                PARTY_DETAILS_COMPLETIONS to 3,
                PARTY_DETAILS_ACTION_BUTTON to 4,
                PARTY_DETAILS_CLEAR_INVOCATIONS to 5,
                PARTY_DETAILS_LOAD_PRESET to 6,
                PARTY_DETAILS_SAVE_PRESET to 7,
                PARTY_DETAILS_MEMBERS_TAB to 8,
                PARTY_DETAILS_APPLICANTS_TAB to 9,
                PARTY_DETAILS_INVOCATIONS_TAB to 10,
                PARTY_DETAILS_SUMMARY_TAB to 11,
            )
        private val INTERFACE_COMPONENTS =
            mapOf(
                PARTY_LIST to (PARTY_LIST_FIRST_ROW_COMPONENT..65).toSet(),
                PARTY_DETAILS to
                    setOf(4, 6, 7, 8, 9, 32, 48, 52, 62, 63, 64, 69, 70, 71, 72, 83, 84, 98),
                INVOCATIONS to setOf(4, 5),
            )
        private val INTERFACE_ON_LOAD_SCRIPTS =
            mapOf(PARTY_LIST to 6594, PARTY_DETAILS to 6615, INVOCATIONS to 6609)

        private val INTERFACES = setOf(PARTY_LIST, PARTY_DETAILS, INVOCATIONS, MIDRAID_LOOT)
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

        internal fun partyListSlotFromComponent(packed: Int): Int? {
            if (packed ushr 16 != PARTY_LIST) return null
            val component = packed and 0xFFFF
            if (component !in PARTY_LIST_FIRST_ROW_COMPONENT..PARTY_LIST_LAST_ROW_COMPONENT) {
                return null
            }
            return component - PARTY_LIST_FIRST_ROW_COMPONENT
        }
    }
}

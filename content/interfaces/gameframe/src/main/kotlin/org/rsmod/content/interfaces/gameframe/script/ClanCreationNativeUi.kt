package org.rsmod.content.interfaces.gameframe.script

import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.net.social.ClanCreationDraftView
import org.rsmod.api.net.social.ClanManagementResult
import org.rsmod.api.net.social.ClanManagementService
import org.rsmod.api.net.social.setClanCreationClientState
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.script.onIfClose
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onIfOpen
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Server controller for the stock revision-240 clan founding side panel (interface 705). */
public class ClanCreationNativeUiScript @Inject constructor(private val ui: ClanCreationNativeUi) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onIfOpen(ClanCreationUi.INTERFACE) {
            setClanCreationClientState(player, true)
            player.enableCreationEvents()
        }
        onIfClose(ClanCreationUi.INTERFACE) { setClanCreationClientState(player, false) }
        onIfModalButton(ClanCreationUi.REFRESH) { ui.refresh(this) }
        onIfModalButton(ClanCreationUi.NAME) { ui.chooseName(this) }
        onIfModalButton(ClanCreationUi.INVITE) { ui.invite(this) }
        onIfModalButton(ClanCreationUi.CANCEL) { ui.cancel(this) }
        onIfModalButton(ClanCreationUi.CONFIRM) { ui.confirm(this) }
        onIfModalButton(ClanCreationUi.CONTENTS) { event -> ui.removeFounder(this, event.comsub) }
    }
}

/** Shared entry point used by the Clan Hub clerk and the interface event script. */
@Singleton
public class ClanCreationNativeUi
@Inject
constructor(private val management: ClanManagementService) {
    public suspend fun open(access: ProtectedAccess) {
        setClanCreationClientState(access.player, true)
        access.ifOpenSide(ClanCreationUi.INTERFACE)
        access.player.enableCreationEvents()
        refresh(access)
    }

    internal suspend fun chooseName(access: ProtectedAccess) {
        val current = management.draft(access.player)
        if (
            current != null &&
                (current.acceptedFounders.isNotEmpty() || current.pendingFounders.isNotEmpty())
        ) {
            access.mes("Remove the other founders before changing the clan name.")
            return
        }
        val name = access.stringDialog("Enter a unique clan name:").trim()
        if (name.isEmpty()) return
        if (current != null) management.cancelDraft(access.player.characterId)
        when (val result = management.beginDraft(access.player, name)) {
            is ClanManagementResult.Denied -> access.mes(result.message)
            is ClanManagementResult.Success -> {
                access.mes("Clan name selected: ${result.value.requestedName}.")
                render(access, result.value)
            }
        }
    }

    internal suspend fun invite(access: ProtectedAccess) {
        if (management.draft(access.player) == null) {
            access.mes("Choose a clan name before inviting founders.")
            return
        }
        val name = access.stringDialog("Invite which player as a founder?").trim()
        if (name.isEmpty()) return
        when (val result = management.inviteFounder(access.player, name)) {
            is ClanManagementResult.Denied -> access.mes(result.message)
            is ClanManagementResult.Success -> {
                access.mes("A founding invitation was sent to $name.")
                render(access, result.value)
            }
        }
    }

    internal fun cancel(access: ProtectedAccess) {
        management.cancelDraft(access.player.characterId)
        access.ifCloseSub(ClanCreationUi.INTERFACE)
        access.mes("You cancelled clan creation.")
    }

    internal suspend fun confirm(access: ProtectedAccess) {
        when (val result = management.confirmClan(access.player)) {
            is ClanManagementResult.Denied -> access.mes(result.message)
            is ClanManagementResult.Success -> {
                access.ifCloseSub(ClanCreationUi.INTERFACE)
                access.mes("Your clan, ${result.value.displayName}, has been founded.")
            }
        }
    }

    internal fun removeFounder(access: ProtectedAccess, clientIndex: Int) {
        val draft = management.draft(access.player) ?: return
        val removable = draft.acceptedFounders + draft.pendingFounders
        val founder = removable.getOrNull(clientIndex - OWNER_ROW_OFFSET) ?: return
        when (val result = management.removeFounder(access.player, founder.displayName)) {
            is ClanManagementResult.Denied -> access.mes(result.message)
            is ClanManagementResult.Success -> {
                access.mes("${founder.displayName} was removed from the founding group.")
                render(access, result.value)
            }
        }
    }

    internal fun refresh(access: ProtectedAccess) {
        val draft = management.draft(access.player)
        if (draft == null) {
            renderEmpty(access)
        } else {
            render(access, draft)
        }
    }

    private fun renderEmpty(access: ProtectedAccess) {
        access.runClientScript(
            ClanCreationUi.RENDER_SCRIPT,
            ClanCreationUi.CONTENTS.asRSCM(RSCMType.COMPONENT),
            access.player.displayName,
            "",
            "",
            "",
            "",
            access.player.displayName,
        )
    }

    private fun render(access: ProtectedAccess, draft: ClanCreationDraftView) {
        val founders =
            buildList {
                    add(draft.owner.displayName)
                    addAll(draft.acceptedFounders.map { it.displayName })
                    addAll(draft.pendingFounders.map { it.displayName })
                }
                .take(FOUNDING_SLOTS)
                .toMutableList()
        while (founders.size < FOUNDING_SLOTS) founders += ""
        access.runClientScript(
            ClanCreationUi.RENDER_SCRIPT,
            ClanCreationUi.CONTENTS.asRSCM(RSCMType.COMPONENT),
            founders[0],
            founders[1],
            founders[2],
            founders[3],
            founders[4],
            access.player.displayName,
        )
    }

    private companion object {
        const val FOUNDING_SLOTS = 5
        const val OWNER_ROW_OFFSET = 1
    }
}

private object ClanCreationUi {
    const val INTERFACE = "interface.clans_creation_sidepanel"
    const val REFRESH = "component.clans_creation_sidepanel:refresh"
    const val CONTENTS = "component.clans_creation_sidepanel:contents"
    const val NAME = "component.clans_creation_sidepanel:name"
    const val INVITE = "component.clans_creation_sidepanel:invite"
    const val CANCEL = "component.clans_creation_sidepanel:cancel"
    const val CONFIRM = "component.clans_creation_sidepanel:confirm"
    const val RENDER_SCRIPT = 4448
}

private fun Player.enableCreationEvents() {
    ifSetEvents(ClanCreationUi.REFRESH, -1..-1, IfEvent.Op1)
    ifSetEvents(ClanCreationUi.NAME, -1..-1, IfEvent.Op1)
    ifSetEvents(ClanCreationUi.INVITE, -1..-1, IfEvent.Op1)
    ifSetEvents(ClanCreationUi.CANCEL, -1..-1, IfEvent.Op1)
    ifSetEvents(ClanCreationUi.CONFIRM, -1..-1, IfEvent.Op1)
    ifSetEvents(ClanCreationUi.CONTENTS, 0..4, IfEvent.Op1)
}

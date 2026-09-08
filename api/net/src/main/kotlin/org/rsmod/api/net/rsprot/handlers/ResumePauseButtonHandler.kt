package org.rsmod.api.net.rsprot.handlers

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import net.rsprot.protocol.game.incoming.resumed.ResumePauseButton
import org.rsmod.annotations.InternalApi
import org.rsmod.api.net.rsprot.player.InterfaceEvents
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.ui.IfModalPauseButton
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.ui.Component
import org.rsmod.game.ui.UserInterface

class ResumePauseButtonHandler
@Inject
constructor(private val eventBus: EventBus, private val protectedAccess: ProtectedAccessLauncher) :
    MessageHandler<ResumePauseButton> {
    private val logger = InlineLogger()

    private val ResumePauseButton.asComponent: Component
        get() = Component(interfaceId, componentId)

    @OptIn(InternalApi::class)
    override fun handle(player: Player, message: ResumePauseButton) {
        val componentType = ServerCacheManager.fromComponent(message.asComponent.packed)
        val interfaceType = ServerCacheManager.fromInterface(message.asComponent.packed)
        val userInterface = UserInterface(interfaceType)
        logger.debug { "[Raw] ResumePauseButton: $message (component=$componentType)" }

        val pauseEnabled =
            InterfaceEvents.isEnabled(player.ui, componentType, message.sub, IfEvent.PauseButton)
        if (!pauseEnabled) {
            logger.debug { "[Gated] ResumePauseButton: $message (component=$componentType)" }
            return
        }

        val input =
            ResumePauseButtonInput(
                RSCM.getReverseMapping(RSCMType.COMPONENT, componentType.packed),
                message.sub,
                componentType.packed,
            )
        val modal = player.ui.modals.getComponent(userInterface)

        // Dialogue interfaces can be nested beneath the chatbox modal. In that case, looking up
        // the clicked child interface in the modal map may return null even though the dialogue
        // coroutine is still waiting for this exact input. Resume by the awaited input type first;
        // the coroutine performs its own expected-component and expected-modal validation. Do not
        // queue the current chat modal for closing here: coroutine resumption is deferred and the
        // queued close can be processed first, invalidating the dialogue before it can continue.
        if (player.activeCoroutine?.isAwaiting(ResumePauseButtonInput::class) == true) {
            player.resumeActiveCoroutine(input)
            return
        }

        if (modal != null) {
            if (player.isModalButtonProtected) {
                player.ui.queueClose(modal)
                player.resumeActiveCoroutine(input)
            } else {
                val event = IfModalPauseButton(componentType, message.sub)
                protectedAccess.launchLenient(player) { eventBus.publish(this, event) }
            }
            return
        }

        val overlay = player.ui.overlays.getComponent(userInterface)
        if (overlay != null) {
            player.ui.queueClose(overlay)
            player.resumeActiveCoroutine(input)
            return
        }
    }
}

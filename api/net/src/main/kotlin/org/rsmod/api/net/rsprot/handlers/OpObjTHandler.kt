package org.rsmod.api.net.rsprot.handlers

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.widget.IfEvent
import jakarta.inject.Inject
import net.rsprot.protocol.game.incoming.objs.OpObjT
import org.rsmod.api.net.rsprot.player.InterfaceEvents
import org.rsmod.api.player.events.interact.ObjTargetEvent
import org.rsmod.api.player.protect.clearPendingAction
import org.rsmod.api.player.vars.ctrlMoveSpeed
import org.rsmod.api.registry.obj.ObjRegistry
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.interact.InteractionObj
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.interact.ObjTargetData
import org.rsmod.game.movement.RouteRequestCoord
import org.rsmod.game.ui.Component
import org.rsmod.map.CoordGrid

/** Routes targeted ground clicks through the same visibility/reach pipeline as ordinary obj ops. */
public class OpObjTHandler
@Inject constructor(private val events: EventBus, private val objects: ObjRegistry) :
    MessageHandler<OpObjT> {
    override fun handle(player: Player, message: OpObjT) {
        if (player.isDelayed) return
        val component = Component(message.selectedInterfaceId, message.selectedComponentId)
        val interfaceType = ServerCacheManager.fromInterface(component.packed)
        val componentType = ServerCacheManager.fromComponent(component.packed)
        if (!player.ui.containsOverlay(interfaceType) && !player.ui.containsModal(interfaceType)) return
        if (!InterfaceEvents.isEnabled(player.ui, componentType, message.selectedSub, IfEvent.TgtObj)) return
        val coords = CoordGrid(message.x, message.z, player.level)
        val obj = objects.findAll(coords).firstOrNull { it.type == message.id && it.isVisibleTo(player) } ?: return
        val selectedObj = message.selectedObj.takeIf { it != -1 }?.let(ServerCacheManager::getItem)
        if (message.selectedObj != -1 && selectedObj == null) return
        val selected = ObjTargetData(component, message.selectedSub, selectedObj)
        val event = ObjTargetEvent(obj, selected)
        if (!events.contains(event::class.java, event.id)) return
        player.clearPendingAction(events)
        player.resetFaceEntity()
        player.faceSquare(coords)
        player.interaction = InteractionObj(obj, InteractionOp.Op1, hasOpTrigger = true,
            hasApTrigger = false, targeted = selected)
        player.routeRequest = RouteRequestCoord(coords, clientRequest = true)
        player.tempMoveSpeed = if (message.controlKey) player.ctrlMoveSpeed() else null
    }
}

package org.rsmod.api.player.events.interact

import org.rsmod.game.interact.ObjTargetData
import org.rsmod.game.obj.Obj

/** A selected interface component used on a ground object after normal reach/visibility checks. */
public class ObjTargetEvent(public val obj: Obj, public val selected: ObjTargetData) :
    OpEvent(selected.component.packed.toLong())
